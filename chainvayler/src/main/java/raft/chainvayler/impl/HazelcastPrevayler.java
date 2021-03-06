package raft.chainvayler.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

import org.prevayler.Clock;
import org.prevayler.Prevayler;
import org.prevayler.Query;
import org.prevayler.SureTransactionWithQuery;
import org.prevayler.Transaction;
import org.prevayler.TransactionWithQuery;
import org.prevayler.foundation.Turn;
import org.prevayler.foundation.serialization.JavaSerializer;
import org.prevayler.foundation.serialization.Serializer;
import org.prevayler.implementation.PrevalentSystemGuard;
import org.prevayler.implementation.PrevaylerDirectory;
import org.prevayler.implementation.PrevaylerImpl;
import org.prevayler.implementation.TransactionCapsuleCV;
import org.prevayler.implementation.TransactionGuide;
import org.prevayler.implementation.TransactionTimestamp;
import org.prevayler.implementation.TransactionWithQueryCapsuleCV;
import org.prevayler.implementation.journal.Journal;
import org.prevayler.implementation.journal.PersistentJournal;
import org.prevayler.implementation.journal.TransientJournal;
import org.prevayler.implementation.publishing.TransactionPublisher;
import org.prevayler.implementation.publishing.TransactionSubscriber;
import org.prevayler.implementation.snapshot.GenericSnapshotManager;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cp.IAtomicLong;
import com.hazelcast.cp.IAtomicReference;
import com.hazelcast.spi.exception.RetryableHazelcastException;
import com.hazelcast.topic.ITopic;
import com.hazelcast.topic.Message;
import com.hazelcast.topic.MessageListener;

import raft.chainvayler.Chainvayler;


/**
 * Hazelcast based replication layer over a PrevaylerImpl.   
 * 
 * @author  hakan eryargi (r a f t)
 */
public class HazelcastPrevayler implements Prevayler<RootHolder> {

	private static boolean assertionsEnabled = false;
	static {
		assert assertionsEnabled = true;		
	}
	
	private static final boolean PRINT_POOL = false; 
	private static final boolean SAVE_POOL = false;
	
	private static final long MINUTE = 60 * 1000; // milliseconds
	
	/** After this much time of waiting for a transaction, assume sending peer died 
	 * and send network a NoOp transaction */
	private static final long TX_EXPIRE_PERIOD = 20 * 1000;
	
	private static final long TX_EXPIRE_CHECK_PERIOD = 1000;
	
	private static int MAX_TRANSACTIONS_PER_BATCH = 100000;
	
	private final Prevayler<RootHolder> prevayler;
	private final TransactionPublisher publisher;
	private final Serializer journalSerializer;
	private final PrevalentSystemGuard<RootHolder> prevalerGuard;
	private final Journal journal;
	private final GenericSnapshotManager<RootHolder> snapshotManager;
	private final PrevaylerDirectory prevaylerDirectory;
	private final Field systemVersionField;
	private final Field nextTransactionField;
	
	private final HazelcastInstance hazelcast;
	
	private final IAtomicLong globalTxId;
	private final ITopic<TransactionTimestamp> transactionsTopic;
	private final ITopic<List<TransactionTimestamp>> expiredTransactionsTopic;
	private final ITopic<Request> requestsTopic;
	
	private final Map<Long, TransactionTimestamp> localTxMap = Collections.synchronizedMap(new HashMap<>());
	private final SortedSet<Long> localTxIds = Collections.synchronizedSortedSet(new TreeSet<>());
	
	/** local last committed transaction Id */
	private long lastTxId = 0;
	private long ownTxCount = 0;
	private long lastExpiredTxId = 0;
	
	private final Object lastTxIdLock = new Object();
	private final Object commitLock = new Object();
	
	private boolean initialized = false;
	private boolean closed = false;
	private boolean error = false;
	
	private long waitingForTx = -1L;
	private long waitingTxSince;
	
	private final int txIdReserveSize;
	private final Queue<Long> reservedTxIds = new LinkedList<>();
	
	public HazelcastPrevayler(PrevaylerImpl<RootHolder> prevayler, raft.chainvayler.Config.Replication replicationConfig) throws Exception {
		this.prevayler = prevayler;
	
		this.prevalerGuard = Utils.getDeclaredFieldValue("_guard", prevayler);
		this.publisher = Utils.getDeclaredFieldValue("_publisher", prevayler);
		this.journalSerializer = Utils.getDeclaredFieldValue("_journalSerializer", prevayler);
		this.journal = Utils.getDeclaredFieldValue("_journal", publisher);
		this.snapshotManager = Utils.getDeclaredFieldValue("_snapshotManager", prevayler);
		this.prevaylerDirectory = Utils.getDeclaredFieldValue("_directory", snapshotManager);
		this.systemVersionField = Utils.getDeclaredField("_systemVersion", prevalerGuard); 
		this.nextTransactionField = Utils.getDeclaredField("_nextTransaction", publisher);
		
		lastTxId = systemVersionField.getLong(prevalerGuard);
		publisher.subscribe(transactionSubscriber, lastTxId + 1);
		
		System.out.printf("local lastTxId: %s \n", lastTxId);
		
		this.txIdReserveSize = replicationConfig.getTxIdReserveSize(); 
		
		System.out.println("initializing Hazelcast");
		this.hazelcast = Hazelcast.newHazelcastInstance(createHazelcastConfig(replicationConfig));
		
		System.out.println("waiting for Hazelcast is ready..");
		boolean ready = hazelcast.getCPSubsystem().getCPSubsystemManagementService().awaitUntilDiscoveryCompleted(10, TimeUnit.MINUTES);
		if (!ready) 
			throw new IllegalStateException("Hazelcast is not ready");
		
		System.out.println("Hazelcast is ready");
		
		System.out.println("assertionsEnabled: " + assertionsEnabled);
		
		this.globalTxId = getGlobalTxIdWithRetries(10);
		
		this.transactionsTopic = hazelcast.getReliableTopic("transactions");
		transactionsTopic.addMessageListener(transactionsTopicListener);
		System.out.println("got ReliableTopic for transactions");
		
		this.expiredTransactionsTopic = hazelcast.getReliableTopic("expired-transactions");
		expiredTransactionsTopic.addMessageListener(expiredTransactionsTopicListener);
		System.out.println("got ReliableTopic for expired-transactions");
		
		this.requestsTopic = hazelcast.getReliableTopic("requests");
		System.out.println("got ReliableTopic for requests");
		
		final long txId = getGlobalTxId();
		if ((txId > 1) && (txId > lastTxId)) {
			receiveInitialTransactions(txId, 10);
		}

		requestsTopic.addMessageListener(requestsTopicListener);
	}
	
	public void start() {
		commitCheckerThread.start();
		new Timer("Expired Tx Checker", true).scheduleAtFixedRate(expiredTxTimerTask, TX_EXPIRE_CHECK_PERIOD, TX_EXPIRE_CHECK_PERIOD);
	}
	
//	public void shutdown() {
//		stopped = true;
//		expiredTxTimerTask.cancel();
//	}
	
	private Config createHazelcastConfig(raft.chainvayler.Config.Replication replicationConfig) {
		Config hazelcastConfig = new Config();
		
		hazelcastConfig.getCPSubsystemConfig().setCPMemberCount(replicationConfig.getNumberOfRaftNodes());
		
		hazelcastConfig.getMapConfig("default")
			// we know for sure that entries in global txMap cannot be overridden, so we are safe to read from backups
			.setReadBackupData(true)
			.setBackupCount(replicationConfig.getImapBackupCount())
			.setAsyncBackupCount(replicationConfig.getImapAsyncBackupCount());
		
		// Reliable ITopic uses the RingBuffer with the same name "transactions" 
		hazelcastConfig.getRingbufferConfig("transactions")
			.setCapacity(replicationConfig.getReliableTopicCapacity());
		
		if (replicationConfig.isKubernetes()) {
			if (replicationConfig.getKubernetesServiceName() == null)
				throw new IllegalArgumentException("Kubernetes service name is required when Kubernetes mode is enabled");
			
			hazelcastConfig.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
			hazelcastConfig.getNetworkConfig().getJoin().getKubernetesConfig().setEnabled(true)
					.setProperty("service-dns", replicationConfig.getKubernetesServiceName());
			
		} else {
			hazelcastConfig.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(true);
			hazelcastConfig.getNetworkConfig().getJoin().getKubernetesConfig().setEnabled(false);
		}
		return hazelcastConfig;
	} 
	
	private long getGlobalTxId() {
		Lock txIdLock = hazelcast.getCPSubsystem().getLock("txId");
		txIdLock.lock();
		final long txId;
		try {
			System.out.println("locked global txIdLock");
			txId = globalTxId.get();
			if (txId == 0L) {
				long willSetTo = lastTxId == 0 ? 1 : lastTxId;
				System.out.println("globalTxId is still zero, setting to " + willSetTo);
				globalTxId.set(willSetTo);
			} else {
				System.out.printf("globalTxId is not zero (%s), skipping \n", txId);
			}
		} finally {
			txIdLock.unlock();
		}
		return txId;
	}

	private IAtomicLong getGlobalTxIdWithRetries(int maxRetries) throws Exception {
		IAtomicLong globalTxId = null;
		int retryCount = 0;
		while (globalTxId == null) {
			try {
				globalTxId = hazelcast.getCPSubsystem().getAtomicLong("txId");
			} catch (RetryableHazelcastException e) {
				retryCount++;
				if (retryCount < maxRetries) {
					System.out.printf("caught RetryableHazelcastException, retryCount: %s, will retry \n", retryCount);
					e.printStackTrace();
				} else {
					System.out.printf("caught RetryableHazelcastException, out of retries giving up \n");
					throw new IllegalStateException("couldnt get AtomicLong, giving up", e);
				}
				Thread.sleep(1000);
			}
		}
		return globalTxId;
	}
	
	private void receiveInitialTransactions(long txId, int maxRetries) throws Exception {
		
		// the very first TX, #1, is always local (InitRootTransaction) and never stored at Hazelcast
		// so will request TXs either starting from 2 or local lastTxId+1, whichever bigger
		long startTx = Math.max(2, lastTxId + 1);

		final Request request = new Request(startTx, txId, UUID.randomUUID());
		final boolean[] receivedResponse = { false };
		final Set<Integer> receivedBatches = new HashSet<>(); 
		
		ITopic<Response> responseTopic = hazelcast.getReliableTopic(request.uuid.toString());
		responseTopic.addMessageListener(new MessageListener<HazelcastPrevayler.Response>() {

			@Override
			public void onMessage(Message<Response> message) {
				try {
					final Response response = message.getMessageObject();
					
					System.out.printf("received response to request: %s, batch: %s, batch count: %s, snapshotVersion: %s, transactions [%s - %s] \n",
							request.uuid, response.batchNo, response.batchCount, response.snapshotVersion, response.transactions.get(0).systemVersion(), response.transactions.get(response.transactions.size()-1).systemVersion());
					
					if (response.snapshotVersion > 0) {
						System.out.printf("response has snapshot, version: %s, setting snapshot fields \n", response.snapshotVersion);
						assert response.rootHolder != null;
						
						lastTxId = response.snapshotVersion;
						
						Utils.getDeclaredField("_prevalentSystem", prevalerGuard).set(prevalerGuard, response.rootHolder);
						Utils.getDeclaredField("_systemVersion", prevalerGuard).set(prevalerGuard, response.snapshotVersion);
						Utils.getDeclaredField("_nextTransaction", publisher).set(publisher, response.snapshotVersion + 1);
						
						if (journal instanceof PersistentJournal) {
							Utils.getDeclaredField("_nextTransaction", journal).set(journal, response.snapshotVersion + 1);
						} else {
							assert journal instanceof TransientJournal;
							Utils.getDeclaredField("_initialTransaction", journal).set(journal, response.snapshotVersion + 1);
						}

						// we need to take snapshot here, otherwise there might be a gap in transactions in next restart 
						prevayler.takeSnapshot();
					}
					
					response.transactions.forEach(tx -> localTxMap.put(tx.systemVersion(), tx));
					
					synchronized (receivedResponse) {
						receivedBatches.add(response.batchNo);
						
						if (receivedBatches.size() == response.batchCount) {
							receivedResponse[0] = true;
							receivedResponse.notify();

							responseTopic.destroy();
							System.out.printf("destroyed the responseTopic for uuid: %s \n", request.uuid);
							
							TimerTask timerTask = new TimerTask() {
								@Override
								public void run() {
									IAtomicReference<?> reference = hazelcast.getCPSubsystem().getAtomicReference(request.uuid.toString());
									reference.destroy();
									System.out.printf("destroyed the IAtomicReference for uuid: %s \n", request.uuid);
								}
							};
							new Timer().schedule(timerTask, MINUTE);
						}
					}
					
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		
		int retryCount = 0;
		
		while (!receivedResponse[0] && (retryCount < maxRetries)) {
			retryCount++;
			System.out.printf("requesting initial transactions [%s - %s], retryCount: %s \n", startTx, txId, retryCount);
			requestsTopic.publish(request);
			
			synchronized (receivedResponse) {
				receivedResponse.wait(20000); // 20 seconds
			}
		}
		
		if (!receivedResponse[0])
			throw new IllegalStateException("couldnt receive initial transactions");
		
		System.out.printf("received all initial transactions [%s - %s] \n", startTx, txId);
	}
	
	public long getOwnTransactionCount() {
		return ownTxCount;
	}
	
	public long getTransactionCount() {
		return lastTxId;
	}

	@Override
	public RootHolder prevalentSystem() {
		return prevayler.prevalentSystem();
	}

	@Override
	public Clock clock() {
		return prevayler.clock();
	}

	@Override
	public void execute(Transaction<? super RootHolder> transaction) {
		checkStillValid();
		
		try {
			final long nextTxId = getNextGlobalTxId();
			localTxIds.add(nextTxId);
			
			// TODO possibly a major improvement will be, send transaction to network at this stage 
			// but can we actually pause clock here? what about time ordering of transactions regarding clock()?
			
			TransactionCapsuleCV<? super RootHolder> capsule = new TransactionCapsuleCV<>(transaction, journalSerializer, true);
			TransactionTimestamp timestamp = new TransactionTimestamp(capsule, nextTxId, new Date());
			TransactionGuide guide = new TransactionGuide(timestamp, Turn.first());

			storeGlobalTransaction(nextTxId, timestamp);
			
			synchronized (lastTxIdLock) {
				while (!isProcessed(nextTxId - 1)) {
					lastTxIdLock.wait();
				}
			}
			
			if (Context.DEBUG) System.out.printf("will execute next txId: %s, thread: %s \n", nextTxId, Thread.currentThread());
			Context.getInstance().poolLock.lock();
			try {
				if (Context.DEBUG) System.out.printf("executing next txId: %s, thread: %s \n", nextTxId, Thread.currentThread());
				journal.append(guide);
				if (Context.DEBUG) printJournalSize("execute");
				prevalerGuard.receive(timestamp);
				ownTxCount++;
				if (Context.DEBUG) System.out.printf("executed next txId: %s, thread: %s \n", nextTxId, Thread.currentThread());
				
			} finally {
				Context.getInstance().poolLock.unlock();
				
				synchronized (lastTxIdLock) {
					lastTxId = nextTxId;
					lastTxIdLock.notifyAll();
				}
			}
			
			if (SAVE_POOL) savePool();
			
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public <R> R execute(TransactionWithQuery<? super RootHolder, R> transactionWithQuery) throws Exception {
		checkStillValid();
		
		final long nextTxId = getNextGlobalTxId();
		localTxIds.add(nextTxId);

		TransactionWithQueryCapsuleCV<? super RootHolder, R> capsule = new TransactionWithQueryCapsuleCV<>(transactionWithQuery, journalSerializer, true);
		TransactionTimestamp timestamp = new TransactionTimestamp(capsule, nextTxId, new Date());
		TransactionGuide guide = new TransactionGuide(timestamp, Turn.first());

		storeGlobalTransaction(nextTxId, timestamp);
		
		synchronized (lastTxIdLock) {
			while (!isProcessed(nextTxId - 1)) {
				lastTxIdLock.wait();
			}
		}
		
		if (Context.DEBUG) System.out.printf("will execute next txId: %s, thread: %s \n", nextTxId, Thread.currentThread());
		Context.getInstance().poolLock.lock();
		try {
			if (Context.DEBUG) System.out.printf("executing next txId: %s, thread: %s \n", nextTxId, Thread.currentThread());
			journal.append(guide);
			if (Context.DEBUG) printJournalSize("execute with query");
			prevalerGuard.receive(timestamp);
			ownTxCount++;
			if (Context.DEBUG) System.out.printf("executed next txId: %s, thread: %s \n", nextTxId, Thread.currentThread());
			
		} finally {
			Context.getInstance().poolLock.unlock();
			
			synchronized (lastTxIdLock) {
				lastTxId = nextTxId;
				lastTxIdLock.notifyAll();
			}
		}
		
		if (SAVE_POOL) savePool();
		
		return capsule.result();
	}


	@Override
	public <R> R execute(Query<? super RootHolder, R> sensitiveQuery) throws Exception {
		throw new UnsupportedOperationException();
	}

	@Override
	public <R> R execute(SureTransactionWithQuery<? super RootHolder, R> sureTransactionWithQuery) {
		throw new UnsupportedOperationException();
	}

	@Override
	public File takeSnapshot() throws Exception {
		return prevayler.takeSnapshot();
	}

	@Override
	public void close() throws IOException {
		prevayler.close();
		closed = true;
		expiredTxTimerTask.cancel();
	}
	
	private void checkStillValid() {
		if (error)
			throw new Error("Chainvayler is no longer accepting transactions due to an error from an earlier transaction.");
		if (closed) 
			throw new IllegalStateException("Chainvayler is closed, not accepting any more transactions");
	}
	
	private boolean isProcessed(long txId) throws Exception {
		return lastTxId >= txId;
	}

	private long getNextGlobalTxId() {
		if (txIdReserveSize == 0) {
			long startTime = System.nanoTime();
			long nextTxId = globalTxId.incrementAndGet();
			if (Context.DEBUG) System.out.printf("got next txId: %s in %s nanos \n", nextTxId, (System.nanoTime() - startTime));
			return nextTxId;
		} else {
			synchronized (reservedTxIds) {
				if (!reservedTxIds.isEmpty())
					return reservedTxIds.remove();
				
				long startTime = System.nanoTime();
				long nextTxId = globalTxId.getAndAdd(txIdReserveSize + 1) + 1;
				if (Context.DEBUG) System.out.printf("got next txId: %s, reserved: %s in %s nanos \n", nextTxId, txIdReserveSize, (System.nanoTime() - startTime));
				for (int i = 0; i < txIdReserveSize; i++) {
					reservedTxIds.add(nextTxId + i + 1);
				}
				return nextTxId;
			}
		}
	}

	private TransactionTimestamp getGlobalTransaction(long txId) {
		return localTxMap.remove(txId);
	}
	
	private void storeGlobalTransaction(Long txId, TransactionTimestamp timestamp) {
		transactionsTopic.publish(timestamp);
	}
	
	private void maybeCommitTransactions() throws Exception {
		boolean committedNew = false;
		
		while (true) {
			final long nextTxId;
			synchronized (lastTxIdLock) {
				nextTxId = lastTxId + 1;
			}
			
			synchronized (commitLock) {
//				if (systemVersionField.getLong(prevalerGuard) >= nextTxId) {
					if (isProcessed(nextTxId)) {
						if (Context.DEBUG) System.out.printf("nextTxId %s is already processed skipping \n", nextTxId);
						continue;
					}
//				}
				
				if (Context.DEBUG) System.out.printf("nextTxId %s, localTxIds: %s \n", nextTxId, localTxIds);
				if (localTxIds.contains(nextTxId)) {
					if (Context.DEBUG) System.out.printf("nextTxId %s is local, not committing \n", nextTxId);
					break;
				}
				
				TransactionTimestamp transaction = getGlobalTransaction(nextTxId);
				if (transaction == null) {
					if (Context.DEBUG) System.out.println("globalTxMap doesnt contain key " + nextTxId);
					setTimerForNextTransaction(nextTxId);
					break;
				}
				
				assert (nextTxId == transaction.systemVersion());
				
				commit(transaction);
				
//				assert (nextTxId == lastTxId);
				committedNew = true;
			}
			
		}
		if (committedNew) {
			if (Context.DEBUG) System.out.println("committed some new transactions, notifing all");
			if (Context.DEBUG) printPool("pool objects after all commits");
			synchronized (lastTxIdLock) {
				lastTxIdLock.notifyAll();
			}
		}
	}
	
	private void commit(TransactionTimestamp transaction) throws Exception {
			
		Context.getInstance().setInRemoteTransaction(true);
		
		if (Context.DEBUG) System.out.printf("commiting txId: %s, tx: %s \n", transaction.systemVersion(), transaction.capsule().deserialize(new JavaSerializer()));
		if (Context.DEBUG) printPool("pool objects before commit");
		
		Context.getInstance().poolLock.lock();
		try {
			nextTransactionField.setLong(publisher, transaction.systemVersion() + 1);
			if (Context.DEBUG) System.out.printf("set nextTransactionField for %s to %s \n", transaction.systemVersion(), transaction.systemVersion() + 1);
			
			journal.append(new TransactionGuide(transaction, Turn.first()));
			if (Context.DEBUG) System.out.printf("appended journal %s, thread: %s \n", transaction.systemVersion(), Thread.currentThread());
			if (Context.DEBUG) printJournalSize("remote commit");
			
			prevalerGuard.receive(transaction);
			if (Context.DEBUG) System.out.printf("called prevalerGuard.receive for %s \n", transaction.systemVersion());
			if (Context.DEBUG) System.out.printf("next txId for %s is %s \n", transaction.systemVersion(), nextTransactionField.getLong(publisher));
			if (Context.DEBUG) System.out.printf("appended %s \n", transaction.systemVersion());

			if (Context.DEBUG) printPool("pool objects after commit");
			
			localTxIds.headSet(transaction.systemVersion()).clear();
			
			synchronized (lastTxIdLock) {
				lastTxId++;
				assert (lastTxId == systemVersionField.getLong(prevalerGuard));
				//lastTxIdLock.notifyAll();
			}
			
			if (SAVE_POOL) savePool();

			
		} catch (Throwable t) {
			t.printStackTrace();
			throw t;
		} finally {
			Context.getInstance().setInRemoteTransaction(false);
			Context.getInstance().poolLock.unlock();
		}
	}
	
	private void setTimerForNextTransaction(long nextTxId) {
		waitingForTx = nextTxId;
		waitingTxSince = System.currentTimeMillis();
	} 

	
	private void printPool(String prefix) {
		try {
			if (PRINT_POOL) System.out.println(prefix + ":\n" + Utils.getDeclaredFieldValue("objects", Utils.getDeclaredFieldValue("pool", Context.getInstance().root)));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private int savePoolCount = 0;
	
	private synchronized void savePool() {
		savePoolCount++;
		if (savePoolCount % 10 != 0)
			return;
		try {
			if (SAVE_POOL) {
				synchronized (lastTxIdLock) {
					try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream("pool.ser"))) {
						out.writeLong(lastTxId);
						out.writeObject(Utils.getDeclaredFieldValue("objects", Utils.getDeclaredFieldValue("pool", Context.getInstance().root)));
						out.flush();
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void printJournalSize(String prefix) throws Exception {
		if (journal instanceof TransientJournal) {
			List<?> list = Utils.getDeclaredFieldValue("journal", journal);
			System.out.println(prefix + ", journal size: " + list.size());
		}
	}

	
	private List<Response> createResponse(Request request) throws Exception {
		RootHolder rootHolder = null;
		long snapshotVersion = 0;
		long lastSnapshotVersion = Chainvayler.getLastSnapshotVersion();
		
		if (lastSnapshotVersion >= request.fromTx) {
			snapshotVersion = lastSnapshotVersion;
			
			File snapshotFile = prevaylerDirectory.latestSnapshot();
			Method method = Utils.getDeclaredMethod("readSnapshot", snapshotManager, File.class);
			rootHolder = (RootHolder) method.invoke(snapshotManager, snapshotFile);
			
			System.out.printf("snapshot version: %s, snapshot file: %s \n", snapshotVersion, snapshotFile);
		}
		
		List<List<TransactionTimestamp>> transactions = new ArrayList<>();
		transactions.add(new ArrayList<>());
		
		// we are not doing anything related to pool
		// but since all journal.append calls are guarded with poolLock, we use the same lock here
		// to avoid concurrent modification exception
		Context.getInstance().poolLock.lock();
		try {
			int batch[] = { 0 };
			int count[] = { 0 };
			
			journal.update(new TransactionSubscriber() {
				@Override
				public void receive(TransactionTimestamp transactionTimestamp) {
					if (transactionTimestamp.systemVersion() <= request.toTx) {
						count[0]++;
						if (count[0] >= MAX_TRANSACTIONS_PER_BATCH) {
							count[0] = 0;
							batch[0]++;
							transactions.add(new ArrayList<>());
						}
						
						transactions.get(batch[0]).add(transactionTimestamp);
					}
				}
			}, Math.max(snapshotVersion + 1,  request.fromTx));
	
			System.out.printf("got transactions from journal, first: %s, last: %s, batches: %s \n", 
					transactions.get(0).get(0).systemVersion(), transactions.get(batch[0]).get(transactions.get(batch[0]).size()-1).systemVersion(), transactions.size());

			List<Response> responses = new ArrayList<>();
			for (int i = 0; i <= batch[0]; i++) {
				if (i == 0) {
					// first batch
					responses.add(new Response(rootHolder, snapshotVersion, transactions.get(i), i, batch[0] + 1));
				} else {
					responses.add(new Response(transactions.get(i), i, batch[0] + 1));
				}
			}
			return responses;
		} finally {
			Context.getInstance().poolLock.unlock();
		}
	}
	
	private final MessageListener<TransactionTimestamp> transactionsTopicListener = new MessageListener<TransactionTimestamp>() {

		@Override
		public void onMessage(Message<TransactionTimestamp> message) {
			
			final long txId = message.getMessageObject().systemVersion();
			
			if (Context.DEBUG) System.out.printf("transaction topic message received, local: %s, txId: %s, thread: %s \n", 
					message.getPublishingMember().localMember(), txId, Thread.currentThread());

			if (txId <= lastExpiredTxId) {
				System.out.printf("received a transaction (id: %s) earlier than last expired transaction (id: %s) closing Chainvayler \n", txId, lastExpiredTxId);
				error = true;
			}
			
			if (message.getPublishingMember().localMember() || (txId == 1L)) {
				if (Context.DEBUG) System.out.printf("skipping transaction topic message, txId: %s \n", txId);
				return;
			}
			
			localTxMap.put(txId, message.getMessageObject());
			
			try {
				if (initialized) {
					// we cannot commit in this thread.
					// if we are not fast enough, Hazelcast terminates topic listener
					// notifying will make commitCheckerThread commit transactions
					synchronized (lastTxIdLock) {
						lastTxIdLock.notifyAll();
					}
				}
			} catch (RuntimeException e) {
				e.printStackTrace();
				throw e;
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		}
	};
	
	private final MessageListener<List<TransactionTimestamp>> expiredTransactionsTopicListener = new MessageListener<List<TransactionTimestamp>>() {

		@Override
		public void onMessage(Message<List<TransactionTimestamp>> message) {
			
			final List<TransactionTimestamp> expiredTxs = message.getMessageObject();
			
			if (Context.DEBUG) System.out.printf("expired transactions topic message received, local: %s, txIds size: %s \n", 
					message.getPublishingMember().localMember(), expiredTxs.size());
			
			expiredTxs.forEach(tx -> localTxMap.put(tx.systemVersion(), tx));
			
			lastExpiredTxId = Math.max(lastExpiredTxId, expiredTxs.get(expiredTxs.size()-1).systemVersion());
			
			try {
				if (initialized) {
					maybeCommitTransactions();
				}
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		}
	};
	
	private final MessageListener<Request> requestsTopicListener = new MessageListener<Request>() {

		@Override
		public void onMessage(Message<Request> message) {
			Request request = message.getMessageObject();
			
			System.out.printf("request topic message received, local: %s, tx range: [%s - %s], uuid: %s \n", 
					message.getPublishingMember().localMember(), request.fromTx, request.toTx, request.uuid);
			
			if (message.getPublishingMember().localMember()) {
				System.out.printf("skipping local request topic message, uuid: %s \n", request.uuid);
				return;
			}
			
			synchronized (lastTxIdLock) {
				// we dont have the requested transactions
				if (lastTxId < request.toTx) {
					System.out.printf("skipping request topic message, I dont have the requested transactions. local lastTxId: %s, requested lastTxId: %s, uuid: %s \n", lastTxId, request.toTx, request.uuid);
					return;
				}
			}
			
			Lock responseLock = hazelcast.getCPSubsystem().getLock("responses");
			responseLock.lock();
			try {
				System.out.println("locked global responses lock");
				
				IAtomicReference<Boolean> atomicRef = hazelcast.getCPSubsystem().getAtomicReference(request.uuid.toString());
				if (!atomicRef.isNull()) {
					System.out.printf("request reference is not null, ignoring request: %s \n", request.uuid);
					assert atomicRef.get() == Boolean.TRUE;
					return;
				}

				List<Response> responses = createResponse(request);
				responses.forEach(response -> hazelcast.getReliableTopic(request.uuid.toString()).publish(response));
				System.out.printf("sent %s response(s) to request: %s \n", responses.size(), request.uuid);
				
				atomicRef.set(true);
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				responseLock.unlock();
			}
		}
	};
	
	private final TransactionSubscriber transactionSubscriber = new TransactionSubscriber() {
		
		@Override
		public void receive(TransactionTimestamp transaction) {
			// TODO possibly this is called after writing to disk
			// if so, reversing the order can increase speed a lot
			
			if (Context.DEBUG) System.out.printf("received from subscription id: %s, date: %s \n", transaction.systemVersion(), transaction.executionTime());

			synchronized (lastTxIdLock) {
				lastTxId = transaction.systemVersion();
				lastTxIdLock.notifyAll();
			}
			
			if (transaction.systemVersion() == 1L) {
				if (Context.DEBUG) System.out.printf("skipping subscribed transaction %s \n", transaction.systemVersion());
				return;
			}
			
			if (Context.DEBUG) System.out.printf("putting transaction %s into map \n", transaction.systemVersion());
			storeGlobalTransaction(transaction.systemVersion(), transaction);
		}
		
	};

	private final Thread commitCheckerThread = new Thread("commitCheckerThread") {
		public void run() {
			
			// try committing once initially
			try {
				maybeCommitTransactions();
			} catch (Throwable t) {
				t.printStackTrace();
			}
			initialized = true;
			
			while (true) {
				try {
					synchronized (lastTxIdLock) {
						lastTxIdLock.wait();
					}
					maybeCommitTransactions();
					//Thread.sleep(500);
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}
		} 
	};
	
	private final TimerTask expiredTxTimerTask = new TimerTask() {
		@Override
		public void run() { 
			if (waitingTxSince + TX_EXPIRE_PERIOD > System.currentTimeMillis())
				return;
			
			synchronized (lastTxIdLock) {
				if (lastTxId > waitingForTx)
					return;
			}
			
			final long lastTxId = globalTxId.get();
			if (lastTxId < waitingForTx)
				return;
			
			Lock expiredLock = hazelcast.getCPSubsystem().getLock("expired-transactions");
			expiredLock.lock();
			try {
				System.out.println("locked global expired-transactions lock");
				
				final IAtomicReference<Boolean> atomicRef = hazelcast.getCPSubsystem().getAtomicReference(String.valueOf(waitingForTx));
				if (!atomicRef.isNull()) {
					System.out.printf("expired-transaction reference is not null, skipping: %s \n", waitingForTx);
					assert atomicRef.get() == Boolean.TRUE;
					return;
				}
				
				List<Long> expiredTxIds = new LinkedList<>();
				
				for (long l = waitingForTx; l <= lastTxId; l++) {
					if (!localTxMap.containsKey(l) && !localTxIds.contains(l)) {
						expiredTxIds.add(l);
					}
				} 
				
				System.out.printf("sending NoOp transaction for expired transactions: %s \n", expiredTxIds);
				
				final Date now = new Date();
				final NoOpTransaction noOpTransaction = new NoOpTransaction();
				
				List<TransactionTimestamp> expiredTxs = expiredTxIds.stream().map(txId -> {
						TransactionCapsuleCV<? super RootHolder> capsule = new TransactionCapsuleCV<>(noOpTransaction, journalSerializer, true);
						return new TransactionTimestamp(capsule, txId, now);
					}).collect(Collectors.toList());

				setTimerForNextTransaction(lastTxId + 1);
				
				expiredTransactionsTopic.publish(expiredTxs);
				
				atomicRef.set(true);
				
				TimerTask timerTask = new TimerTask() {
					@Override
					public void run() {
						atomicRef.destroy();
						System.out.printf("destroyed the IAtomicReference for tx id: %s \n", waitingForTx);
					}
				};
				new Timer().schedule(timerTask, MINUTE);
				
				
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				expiredLock.unlock();
			}
			
			
		}
	};
	
	/** State request */
	private static class Request implements Serializable {
		
		private static final long serialVersionUID = 1L;
		
		/** Both inclusive */
		final long fromTx, toTx;
		final UUID uuid;
		
		Request(long fromTx, long toTx, UUID uuid) {
			this.fromTx = fromTx;
			this.toTx = toTx;
			this.uuid = uuid;
		}
	}
	
	/** Response to state request */
	private static class Response implements Serializable {
		
		private static final long serialVersionUID = 1L;
		
		final RootHolder rootHolder;
		final long snapshotVersion;
		final List<TransactionTimestamp> transactions;
		final int batchNo;
		final int batchCount;
		
		Response(List<TransactionTimestamp> transactions, int batchNo, int batchCount) {
			this(null, 0, transactions, batchNo, batchCount);
		}
		
		Response(RootHolder rootHolder, long snapshotVersion, List<TransactionTimestamp> transactions, int batchNo, int batchCount) {
			this.rootHolder = rootHolder;
			this.snapshotVersion = snapshotVersion;
			this.transactions = transactions;
			this.batchNo = batchNo;
			this.batchCount = batchCount;
		}
	}
	
	private static class NoOpTransaction implements Transaction<RootHolder> {

		private static final long serialVersionUID = 1L;
		
		@Override
		public void executeOn(RootHolder prevalentSystem, Date executionTime) {
			// None. NoOp
		}
		
	}

}
