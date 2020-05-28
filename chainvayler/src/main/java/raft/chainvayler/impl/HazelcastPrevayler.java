package raft.chainvayler.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

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
import org.prevayler.implementation.PrevaylerImpl;
import org.prevayler.implementation.TransactionCapsuleCV;
import org.prevayler.implementation.TransactionGuide;
import org.prevayler.implementation.TransactionTimestamp;
import org.prevayler.implementation.TransactionWithQueryCapsuleCV;
import org.prevayler.implementation.journal.Journal;
import org.prevayler.implementation.journal.TransientJournal;
import org.prevayler.implementation.publishing.TransactionPublisher;
import org.prevayler.implementation.publishing.TransactionSubscriber;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cp.IAtomicLong;
import com.hazelcast.spi.exception.RetryableHazelcastException;
import com.hazelcast.topic.ITopic;
import com.hazelcast.topic.Message;
import com.hazelcast.topic.MessageListener;


public class HazelcastPrevayler implements Prevayler<RootHolder> {

	private static boolean assertionsEnabled = false;
	
	static {
		assert assertionsEnabled = true;		
	}
	
	private static final boolean PRINT_POOL = false; 
	private static final boolean SAVE_POOL = false; 
	
	private final Prevayler<RootHolder> prevayler;
	private final TransactionPublisher publisher;
	private final Serializer journalSerializer;
	private final PrevalentSystemGuard<RootHolder> prevalerGuard;
	private final Journal journal;
	private final Field systemVersionField;
	private final Field nextTransactionField;
	
	private final HazelcastInstance hazelcast;
	
	private final IAtomicLong globalTxId;
	private final ITopic<TransactionTimestamp> transactionsTopic;
//	private final IMap<Long, TransactionTimestamp> globalTxIMap;
//	private final ReplicatedMap<Long, TransactionTimestamp> globalTxRepliatedMap;
	private final Map<Long, TransactionTimestamp> localTxMap = Collections.synchronizedMap(new HashMap<>());
	private long lastTxId = 0;
	private long ownTxCount = 0;
	private SortedSet<Long> localTxIds = Collections.synchronizedSortedSet(new TreeSet<>());
	private final Object lastTxIdLock = new Object();
	private final Object commitLock = new Object();
	private boolean initialized = false;
	private boolean stopped = false;
	
	private final int txIdReserveSize;
	private final Queue<Long> reservedTxIds = new LinkedList<>();
	
	private Executor assertionExecutor = null;
	
	public HazelcastPrevayler(PrevaylerImpl<RootHolder> prevayler, raft.chainvayler.Config.Replication replicationConfig) throws Exception {
		this.prevayler = prevayler;
	
		this.prevalerGuard = Utils.getDeclaredFieldValue("_guard", prevayler);
		this.publisher = Utils.getDeclaredFieldValue("_publisher", prevayler);
		this.journalSerializer = Utils.getDeclaredFieldValue("_journalSerializer", prevayler);
		this.journal = Utils.getDeclaredFieldValue("_journal", publisher);
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
		if (assertionsEnabled)
			assertionExecutor = Executors.newCachedThreadPool();
		
		this.globalTxId = getGlobalTxIdWithRetries(10);
		
		this.transactionsTopic = hazelcast.getReliableTopic("transactions");
		transactionsTopic.addMessageListener(transactionsTopicListener);
		System.out.println("got transactions ReliableTopic");
		
		final long txId = getGlobalTxId();
		if ((txId > 1) && (txId > lastTxId)) {
			receiveInitialTransactions(txId, 10);
		}
	}
	
	public void start() {
		commitCheckerThread.start();
	}
	
	public void shutdown() {
		stopped = true;
	}
	
	private Config createHazelcastConfig(raft.chainvayler.Config.Replication replicationConfig) {
		Config hazelcastConfig = new Config();
		
		hazelcastConfig.getCPSubsystemConfig().setCPMemberCount(replicationConfig.getNumberOfRaftNodes());
		
		hazelcastConfig.getMapConfig("default")
			// we know for sure that entries in global txMap cannot be overridden, so we are safe to read from backups
			.setReadBackupData(true)
			.setBackupCount(replicationConfig.getImapBackupCount())
			.setAsyncBackupCount(replicationConfig.getImapAsyncBackupCount());
		
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
		// TODO
		System.out.println("//TODO: Implement me, receiveInitialTransactions " + txId);
	}
	
//	private void receiveInitialTransactionsFromReplicatedMap(long txId, int maxRetries) throws Exception {
//		// the very first tx, #1, is always local (InitRootTransaction) and never stored at Hazelcast
//		// so will request txs either starting from 2 or local lastTxId+1, whichever bigger
//		long startTx = Math.max(2, lastTxId + 1);
//		System.out.printf("requesting initial transactions [%s - %s] \n", startTx, txId);
//		
//		Set<Long> keys = new HashSet<>();
//		for (long l = startTx; l <= txId; l++)
//			keys.add(l);
//		
//		Set<Long> keySet = new HashSet<>(globalTxRepliatedMap.keySet());
//		
//		int retryCount = 0;
//		
//		while (!keySet.containsAll(keys) && retryCount < maxRetries) {
//			Set<Long> remainingKeys = new HashSet<>(keySet);
//			remainingKeys.removeAll(keys);
//			
//			System.out.printf("retrieved %s of %s initial transactions, requesting remaining %s: %s \n", 
//					keySet.size(), keys.size(), remainingKeys.size(), remainingKeys);
//
//			// Hazelcast eventually returns all keys, but not immediately, so wait a bit.. 
//			Thread.sleep(500);
//			
//			keySet = new HashSet<>(globalTxRepliatedMap.keySet());
//			retryCount++;
//		}
//		
//		if (!keySet.containsAll(keys)) {
//			System.out.printf("couldnt received all initial transactions after %s attempts, giving up \n", retryCount);
//			throw new IllegalStateException("couldnt received all initial transactions");
//		}
//		
//		System.out.printf("received all initial transactions [%s - %s], count: %s \n", startTx, txId, keys.size());
//	}
//	
//	private void receiveInitialTransactionsFromIMap(long txId, int maxRetries) throws Exception {
//		// the very first tx, #1, is always local (InitRootTransaction) and never stored at Hazelcast
//		// so will request txs either starting from 2 or local lastTxId+1, whichever bigger
//		long startTx = Math.max(2, lastTxId + 1);
//		System.out.printf("requesting initial transactions [%s - %s] \n", startTx, txId);
//		
//		Set<Long> keys = new HashSet<>();
//		for (long l = startTx; l <= txId; l++)
//			keys.add(l);
//		
//		Map<Long, TransactionTimestamp> receivedTxs = globalTxIMap.getAll(keys);
//		// returned Map is unmodifiable, so create another copy
//		receivedTxs = new HashMap<>(receivedTxs);
//		
//		int retryCount = 0;
//		
//		// looks like for large numbers of keys, Hazelcast dont return all values
//		// so we iterate with batches for remaining values
//		while (!receivedTxs.keySet().containsAll(keys) && retryCount < maxRetries) {
//			Set<Long> remainingKeys = new HashSet<>(keys);
//			remainingKeys.removeAll(receivedTxs.keySet());
//			System.out.printf("retrieved %s of %s initial transactions, requesting remaining %s: %s \n", 
//					receivedTxs.size(), keys.size(), remainingKeys.size(), remainingKeys);
//			
//			// Hazelcast eventually returns all keys, but not immediately, so wait a bit.. 
//			// NOT true indeed, sometimes some keys are missing 
//			Thread.sleep(500);
//			
//			Map<Long, TransactionTimestamp> newBatch = globalTxIMap.getAll(remainingKeys);
//			System.out.printf("retrieved %s more initial transactions \n", newBatch.size());
//			
//			receivedTxs.putAll(newBatch);
//			retryCount++;
//		}
//		if (!receivedTxs.keySet().containsAll(keys)) {
//			System.out.printf("couldnt received all initial transactions after %s attempts, giving up \n", retryCount);
//			throw new IllegalStateException("couldnt received all initial transactions");
//		}
//		
//		System.out.printf("received all initial transactions [%s - %s], count: %s \n", startTx, txId, receivedTxs.size());
//		
//		if (USE_LOCAL_TX_MAP) {
//			localTxMap.putAll(receivedTxs);
//			System.out.printf("stored all initial transactions in local map [%s - %s], count: %s \n", startTx, txId, receivedTxs.size());
//		}
//		
//	}
	
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
		
		if (stopped) 
			throw new IllegalStateException("Chainvayler is shutting down, not accepting any more transactions");
		
		try {
			final long nextTxId = getNextGlobalTxId();
			localTxIds.add(nextTxId);
			
			// TODO possibly a major improvement will be, send transaction to network at this stage 
			// but can we actually pause clock here? what about time ordering of transactions regarding clock()?
			
			TransactionCapsuleCV<? super RootHolder> capsule = new TransactionCapsuleCV<>(transaction, journalSerializer, true);
			TransactionTimestamp timestamp = new TransactionTimestamp(capsule, nextTxId, new Date());
			TransactionGuide guide = new TransactionGuide(timestamp, Turn.first());

			storeGlobalTransaction(nextTxId, timestamp);
			
			synchronized(lastTxIdLock) {
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
				synchronized (lastTxIdLock) {
					lastTxId = nextTxId;
					lastTxIdLock.notifyAll();
				}
				
				Context.getInstance().poolLock.unlock();
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

		if (stopped) 
			throw new IllegalStateException("Chainvayler is shutting down, not accepting any more transactions");
		
		final long nextTxId = getNextGlobalTxId();
		localTxIds.add(nextTxId);

		TransactionWithQueryCapsuleCV<? super RootHolder, R> capsule = new TransactionWithQueryCapsuleCV<>(transactionWithQuery, journalSerializer, true);
		TransactionTimestamp timestamp = new TransactionTimestamp(capsule, nextTxId, new Date());
		TransactionGuide guide = new TransactionGuide(timestamp, Turn.first());

		storeGlobalTransaction(nextTxId, timestamp);
		
		synchronized(lastTxIdLock) {
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
			synchronized (lastTxIdLock) {
				lastTxId = nextTxId;
				lastTxIdLock.notifyAll();
			}
			
			Context.getInstance().poolLock.unlock();
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

	private final MessageListener<TransactionTimestamp> transactionsTopicListener = new MessageListener<TransactionTimestamp>() {

		@Override
		public void onMessage(Message<TransactionTimestamp> message) {
			
			final long txId = message.getMessageObject().systemVersion();
			
			if (Context.DEBUG) System.out.printf("transaction topic message received, local: %s, txId: %s, thread: %s \n", 
					message.getPublishingMember().localMember(), txId, Thread.currentThread());
			
			if (message.getPublishingMember().localMember() || (txId == 1L)) {
				if (Context.DEBUG) System.out.printf("skipping topic message, txId: %s \n", txId);
				return;
			}
			
			localTxMap.put(txId, message.getMessageObject());
			
			try {
				if (initialized) {
					//maybeCommitTransactions();
					
					// we cannot commit in this thread.
					// if we are not fast enough, Hazelcast terminates topic listener
					// notifying will make commitCheckerThread commit transactions
					// TODO maybe even waiting for the lock is too much?
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
	
//	private final EntryListener<Long, TransactionTimestamp> replicatedMapEntryAddedListener = new EntryAdapter<Long, TransactionTimestamp>() {
//
//		@Override
//		public void entryAdded(EntryEvent<Long, TransactionTimestamp> event) {
//			if (Context.DEBUG) System.out.printf("map entry added, local: %s, key: %s, thread: %s \n", event.getMember().localMember(), event.getKey(), Thread.currentThread());
//			
//			if (event.getMember().localMember() || event.getKey().equals(Long.valueOf(1))) {
//				if (Context.DEBUG) System.out.printf("skipping map entry %s \n", event.getKey());
//				return;
//			}
//			
//			try {
//				if (initialized) {
//					maybeCommitTransactions();
//				}
//			} catch (RuntimeException e) {
//				e.printStackTrace();
//				throw e;
//			} catch (Exception e) {
//				e.printStackTrace();
//				throw new RuntimeException(e);
//			}
//		}
//	};
//	
//	private final EntryAddedListener<Long, TransactionTimestamp> iMapEntryAddedListener = new EntryAddedListener<Long, TransactionTimestamp>() {
//		@Override
//		public void entryAdded(EntryEvent<Long, TransactionTimestamp> event) {
//			if (Context.DEBUG) System.out.printf("map entry added, local: %s, key: %s, thread: %s \n", event.getMember().localMember(), event.getKey(), Thread.currentThread());
//			
//			if (event.getMember().localMember() || event.getKey().equals(Long.valueOf(1))) {
//				if (Context.DEBUG) System.out.printf("skipping map entry %s \n", event.getKey());
//				return;
//			}
//			
//			if (USE_LOCAL_TX_MAP) {
//				// looks like this approach, instead of retrieving value from global map is much faster
//				// possibly because every get call to global map makes a network call
//				// but this all depends on Hazelcast dont miss any entryAdded event
//				localTxMap.put(event.getKey(), event.getValue());
//			}
//			
//			try {
//				if (initialized) {
//					maybeCommitTransactions();
//				}
//			} catch (RuntimeException e) {
//				e.printStackTrace();
//				throw e;
//			} catch (Exception e) {
//				e.printStackTrace();
//				throw new RuntimeException(e);
//			}
//		}
//	};
//	
//	private final MapPartitionLostListener mapPartitionLostListener = new MapPartitionLostListener() {
//
//		@Override
//		public void partitionLost(MapPartitionLostEvent event) {
//			System.out.printf("WARN: map partition lost: %s \n", event);
//		}
//		
//	};
	
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
	
}
