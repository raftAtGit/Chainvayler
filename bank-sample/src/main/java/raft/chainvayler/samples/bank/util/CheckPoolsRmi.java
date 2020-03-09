package raft.chainvayler.samples.bank.util;

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
import raft.chainvayler.samples.bank.rmi.Peer;
import raft.chainvayler.samples.bank.rmi.PeerManager;

public class CheckPoolsRmi {

	final List<Map<Long, IsChained>> pools = new ArrayList<>();
	final List<Long> txCounts = new LinkedList<>();
	final List<Bank> banks = new LinkedList<>();
	
	CheckPoolsRmi() throws Exception {
		Registry registry = LocateRegistry.getRegistry(1999);
		System.out.println("got RMI registry");
	
		PeerManager manager = (PeerManager) registry.lookup("manager");
		System.out.println("got PeerManager");
		
		for (Peer peer : manager.getPeers()) {
			System.out.printf("txCount: %s, poolSize: %s \n", peer.getTransactionCount(), peer.getPoolSize());
		}
		
		for (Peer peer : manager.getPeers()) {
			txCounts.add(peer.getTransactionCount());
			banks.add(peer.getBank());
			pools.add(peer.getPool());
		}
		
	}
	
	void checkEqual() throws Exception {
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
				System.out.println("this pair looks identical!!");
			}
			
			previousPool = pool;
		}
		System.out.println("-- all pools are identical!!");
	}
	
	
	private static void checkEqual(Bank bank, Bank pojoBank) throws Exception {
		List<Customer> customers = bank.getCustomers();
		List<Customer> pojoCustomers = pojoBank.getCustomers();
		
		if (customers.size() != pojoCustomers.size())
			throw new Exception("customer sizes differ " + customers.size() + ", " + pojoCustomers.size());
		
		for (int i = 0; i < customers.size(); i++) {
			Customer customer = customers.get(i);
			Customer pojoCustomer = pojoCustomers.get(i);
			
			checkEqual(customer, pojoCustomer);
			
			for (Account account : customer.getAccounts()) {
				// check object identity
				if (bank.getAccount(account.getId()) != account)
					throw new Exception("test failed");
			}
		}

		List<Account> accounts = bank.getAccounts();
		List<Account> pojoAccounts = pojoBank.getAccounts();
		
		if (accounts.size() != pojoAccounts.size())
			throw new Exception("account sizes differ " + accounts.size() + ", " + pojoAccounts.size());
		
		for (int i = 0; i < accounts.size(); i++) {
			Account account = accounts.get(i);
			Account pojoAccount = pojoAccounts.get(i);
			
			checkEqual(account, pojoAccount);
		}
		
		List<Date> audits = bank.getAudits();
		List<Date> pojoAudits = pojoBank.getAudits();
		
		if (audits.size() != pojoAudits.size())
			throw new Exception("audits sizes differ " + audits.size() + ", " + pojoAudits.size());
		
		for (int i = 0; i < audits.size(); i++) {
			Date audit = audits.get(i);
			Date pojoAudit = pojoAudits.get(i);
			
			if (!equals(audit, pojoAudit)) 
				throw new Exception(String.format("test failed, %s != %s", audit, pojoAudit));
		}
		
	}

	private static void checkEqual(Customer customer, Customer pojoCustomer) throws Exception {
		if (customer.getId() != pojoCustomer.getId())
			throw new Exception("test failed");
		
		try {
			if (!equals(customer.getName(), pojoCustomer.getName()))
				throw new Exception("test failed");
		} catch (UnsupportedOperationException e) {
			assert (customer.getClass().getName().equals("raft.chainvayler.samples.bank.secret.SecretCustomer") 
					&& (pojoCustomer.getClass().getName().equals("raft.chainvayler.samples.bank.secret.SecretCustomer"))); 
		}

		if (!equals(customer.getPhone(), pojoCustomer.getPhone()))
			throw new Exception("test failed");
		
		if (customer.getAccounts().size() != pojoCustomer.getAccounts().size())
			throw new Exception("test failed");
	}
	
	private static void checkEqual(Account account, Account pojoAccount) throws Exception {
		if (account.getId() != pojoAccount.getId())
			throw new Exception("test failed");
		
		if (account.getBalance() != pojoAccount.getBalance())
			throw new Exception("test failed");
		
		if (!equals(account.getName(), pojoAccount.getName()))
			throw new Exception("test failed");

		Customer owner = account.getOwner();
		Customer pojoOwner = pojoAccount.getOwner();
		
		if ((owner == null) ^ (pojoOwner == null))
			throw new Exception("test failed");
		
		if (owner != null)
			checkEqual(owner, pojoOwner);
	}
	
	private static boolean equals(Object o1, Object o2) {
		if (o1 == null) return (o2 == null);
		if (o2 == null) return false;
		return o1.equals(o2);
	}

	public static void main(String[] args) throws Exception {
		new CheckPoolsRmi().checkEqual();
	}

}
