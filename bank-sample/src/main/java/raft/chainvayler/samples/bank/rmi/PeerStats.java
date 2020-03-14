package raft.chainvayler.samples.bank.rmi;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import raft.chainvayler.impl.IsChained;
import raft.chainvayler.samples.bank.Account;
import raft.chainvayler.samples.bank.Bank;
import raft.chainvayler.samples.bank.Customer;
import raft.chainvayler.samples.bank.secret.SecretCustomer;

public class PeerStats {

	private final List<Map<Long, IsChained>> pools = new ArrayList<>();
	private final List<Long> txCounts = new LinkedList<>();
	private final List<Bank> banks = new LinkedList<>();
	
	private final PeerManager peerManager;
	private boolean allCompleted = false;
	private boolean allWritersCompleted = false;
	
	public PeerStats() throws Exception {
		this.peerManager = registerPeerManager();
	}
	
	private PeerManager registerPeerManager() throws Exception {
		Registry registry = LocateRegistry.createRegistry(1099);
		System.out.println("created RMI registry");
		
		PeerManager manager = new PeerManager.Impl(); 
		registry.bind("manager", manager);
		System.out.println("bound PeerManager to registry");
		
		return manager;
	}

	void run() throws Exception {
		while (!allWritersCompleted) {
			printStats();
			Thread.sleep(5000);
		}
		stopReaders();
		while (!allCompleted) {
			printStats();
			Thread.sleep(5000);
		}
		System.out.println("will compare results..");
		
		for (Peer peer : peerManager.getPeers()) {
			txCounts.add(peer.getTransactionCount());
			banks.add(peer.getBank());
			pools.add(peer.getPool());
		}
		
		checkEqual();
		printAverage();
	}
	
	private void printAverage() throws Exception {
		float sum = 0f;
		int count = 0;
		
		float sumOwn = 0f;
		int countOwn = 0;
		
		List<Peer> peers = peerManager.getPeers();
		for (Peer peer : peers) {
			float txPerSecond = peer.getTransactionsPerSecond();
			if (txPerSecond < 0)
				continue;
			
			sum += txPerSecond;
			count++;
			
			if (peer.hasWriters()) {
				sumOwn += peer.getOwnTransactionsPerSecond();
				countOwn++;
			}
		}
		float average = sum / count;
		float ownAverage = sumOwn / countOwn;
		System.out.printf("average tx/second: %.2f, own tx/second: %.2f \n", average, ownAverage);
	}

	private void printStats() throws Exception {
		boolean allWritersCompleted = true;
		boolean allCompleted = true;
		
		List<Peer> peers = peerManager.getPeers();
		int peerCount = peers.size();
		int writerCount = 0; 
		
		System.out.println("-- started | completed | pool size  | tx count   | tx/second | own tx count | own tx/sec | read count     | reads/sec --");
		for (Peer peer : peers) {
			try {
				System.out.printf("   %-9s %-11s %-12s %-12s %-11.2f %-14s %-12.2f %-16s %.2f \n", peer.isStarted(), peer.isCompleted(), peer.getPoolSize(),  
						peer.getTransactionCount(), peer.getTransactionsPerSecond(), peer.getOwnTransactionCount(), peer.getOwnTransactionsPerSecond(), peer.getReadCount(), peer.getReadsPerSecond());
				
				if (peer.hasWriters())
					writerCount++;
				
				if (!peer.isCompleted()) {
					allCompleted = false;
					if (peer.hasWriters())
						allWritersCompleted = false;
				}
			} catch (RemoteException e) {
				e.printStackTrace();
				System.out.println("peer seems terminated, removing..");
				peerManager.unregisterPeer(peer);
				peerCount--;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		System.out.println("------------------------------------------------------------------------------------------------------------------------");
		
		if (allWritersCompleted && writerCount > 0)
			this.allWritersCompleted = true;
		if (allCompleted && peerCount > 0)
			this.allCompleted = true;
	}
	
	private void stopReaders() throws Exception {
		List<Peer> peers = peerManager.getPeers();
		for (Peer peer : peers) {
			peer.stopReaders();
		}
		System.out.println("stopped all readers");
	}
	
	private void checkEqual() throws Exception {
		Long prevTxCount = null;
		for (Long txCount : txCounts) {
			if (prevTxCount != null) {
				if (!prevTxCount.equals(txCount))
					System.out.printf("transaction counts differ %s != %s \n", prevTxCount, txCount);
			}
			prevTxCount = txCount;
		}
		System.out.println("transaction counts are the same! " + txCounts.get(0));
		
		Bank prevBank = null;
		for (Bank bank : banks) {
			if (prevBank != null) {
				checkEqual(prevBank, bank);
			}
			prevBank = bank;
		}
		System.out.println("--all banks are identical!!");
		
		Map<Long, IsChained> previousPool = null;
		for (Map<Long, IsChained> pool : pools) {
			if (previousPool != null) {
				if (previousPool.size() != pool.size()) {
					System.out.printf("pool sizes differ, %s != %s \n", previousPool.size(), pool.size());
				} else {
					System.out.printf("pool sizes are same  %s == %s \n", previousPool.size(), pool.size());
				}
				
				SortedSet<Long> firstKeys = new TreeSet<>(pool.keySet());
				SortedSet<Long> secondKeys = new TreeSet<>(previousPool.keySet());
				long firstKey = firstKeys.first();
				long lastKey = Math.min(firstKeys.last(), secondKeys.last());
				
				for (long key = firstKey; key <= lastKey; key++) {
					IsChained first = pool.get(key);
					IsChained second = previousPool.get(key);
					
					if (first.getClass() != second.getClass()) {
						System.out.printf("classes differ for %s, %s != %s \n", key, first.getClass(), second.getClass());
					}
				}
			}
			
			previousPool = pool;
		}
		System.out.println("all pool classes are the same");
		
		for (Map<Long, IsChained> pool : pools) {
			if (previousPool != null) {
				SortedSet<Long> firstKeys = new TreeSet<>(pool.keySet());
				SortedSet<Long> secondKeys = new TreeSet<>(previousPool.keySet());
				long firstKey = firstKeys.first();
				long lastKey = Math.min(firstKeys.last(), secondKeys.last());
				
				for (long key = firstKey; key <= lastKey; key++) {
					IsChained first = pool.get(key);
					IsChained second = previousPool.get(key);
					
					if (first instanceof Bank) {
						checkEqual((Bank)first, (Bank)second);
					}
					if (first instanceof Customer) {
						checkEqual((Customer)first, (Customer)second);
					}
					if (first instanceof Account) {
						checkEqual((Account)first, (Account)second);
					}
				}
				System.out.println("this pair looks identical");
			}
			
			previousPool = pool;
		}
		System.out.println("-- all pools are identical!!");
	}
	
	
	private static void checkEqual(Bank bankOne, Bank bankTwo) throws Exception {
		List<Customer> customersOne = bankOne.getCustomers();
		List<Customer> customersTwo = bankTwo.getCustomers();
		
		if (customersOne.size() != customersTwo.size())
			throw new Exception("customer sizes differ " + customersOne.size() + ", " + customersTwo.size());
		
		for (int i = 0; i < customersOne.size(); i++) {
			Customer customerOne = customersOne.get(i);
			Customer customerTwo = customersTwo.get(i);
			
			checkEqual(customerOne, customerTwo);
			
			for (Account account : customerOne.getAccounts()) {
				// check object identity
				if (bankOne.getAccount(account.getId()) != account)
					throw new Exception("test failed");
			}
		}

		List<Account> accountsOne = bankOne.getAccounts();
		List<Account> accountsTwo = bankTwo.getAccounts();
		
		if (accountsOne.size() != accountsTwo.size())
			throw new Exception("account sizes differ " + accountsOne.size() + ", " + accountsTwo.size());
		
		for (int i = 0; i < accountsOne.size(); i++) {
			Account accountOne = accountsOne.get(i);
			Account accountTwo = accountsTwo.get(i);
			
			checkEqual(accountOne, accountTwo);
		}
		
		List<Date> auditsOne = bankOne.getAudits();
		List<Date> auditsTwo = bankTwo.getAudits();
		
		if (auditsOne.size() != auditsTwo.size())
			throw new Exception("audits sizes differ " + auditsOne.size() + ", " + auditsTwo.size());
		
		for (int i = 0; i < auditsOne.size(); i++) {
			Date auditOne = auditsOne.get(i);
			Date auditTwo = auditsTwo.get(i);
			
			if (!equals(auditOne, auditTwo)) 
				throw new Exception(String.format("test failed, %s != %s", auditOne, auditTwo));
		}
		
	}

	private static void checkEqual(Customer customerOne, Customer customerTwo) throws Exception {
		if (customerOne.getId() != customerTwo.getId())
			throw new Exception("test failed");
		
		try {
			if (!equals(customerOne.getName(), customerTwo.getName()))
				throw new Exception("test failed");
		} catch (UnsupportedOperationException e) {
			assert (customerOne.getClass() == SecretCustomer.class 
					&& customerTwo.getClass() == SecretCustomer.class); 
		}

		if (!equals(customerOne.getPhone(), customerTwo.getPhone()))
			throw new Exception("test failed");
		
		if (customerOne.getAccounts().size() != customerTwo.getAccounts().size())
			throw new Exception("test failed");
	}
	
	private static void checkEqual(Account accountOne, Account accountTwo) throws Exception {
		if (accountOne.getId() != accountTwo.getId())
			throw new Exception("test failed");
		
		if (accountOne.getBalance() != accountTwo.getBalance())
			throw new Exception("test failed");
		
		if (!equals(accountOne.getName(), accountTwo.getName()))
			throw new Exception("test failed");

		Customer ownerOne = accountOne.getOwner();
		Customer ownerTwo = accountTwo.getOwner();
		
		if ((ownerOne == null) ^ (ownerTwo == null))
			throw new Exception("test failed");
		
		if (ownerOne != null)
			checkEqual(ownerOne, ownerTwo);
	}
	
	private static boolean equals(Object o1, Object o2) {
		if (o1 == null) return (o2 == null);
		if (o2 == null) return false;
		return o1.equals(o2);
	}

	public static void main(String[] args) throws Exception {
		new PeerStats().run();
	}

}
