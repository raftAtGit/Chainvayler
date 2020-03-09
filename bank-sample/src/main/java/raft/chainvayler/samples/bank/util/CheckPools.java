package raft.chainvayler.samples.bank.util;

import java.io.FileInputStream;
import java.io.ObjectInputStream;
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

public class CheckPools {

	final List<Map<Long, IsChained>> pools = new ArrayList<>();
	
	@SuppressWarnings("unchecked")
	CheckPools(String... poolFiles) throws Exception {
		List<Long> lastTxIds = new LinkedList<>();
		
		for (String poolFile : poolFiles) {
			try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(poolFile))) {
//				lastTxIds.add(in.readLong());
				
				pools.add((Map<Long, IsChained>) in.readObject());
				System.out.println("loaded from " + poolFile);
				
//				System.out.println(pools.get(pools.size()-1).get(529L));
			}
		}
		System.out.println("lastTxIds: " + lastTxIds);
	}
	
	void checkEqual() throws Exception {
		Map<Long, IsChained> previousPool = null;
		for (Map<Long, IsChained> pool : pools) {
			if (previousPool != null) {
				if (previousPool.size() != pool.size()) {
					System.out.printf("sizes differ, %s != %s \n", previousPool.size(), pool.size());
				} else {
					System.out.printf("sizes are same  %s == %s \n", previousPool.size(), pool.size());
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
		System.out.println("all classes are the same");
		
		for (Map<Long, IsChained> pool : pools) {
			if (previousPool != null) {
				SortedSet<Long> firstKeys = new TreeSet<>(pool.keySet());
				SortedSet<Long> secondKeys = new TreeSet<>(previousPool.keySet());
				long firstKey = firstKeys.first();
				long lastKey = Math.min(firstKeys.last(), secondKeys.last());
				
				for (long key = firstKey; key <= lastKey; key++) {
					IsChained first = pool.get(key);
					IsChained second = previousPool.get(key);
					
//					checkEqual("root", first, second);
					
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
		System.out.println("-- all look identical!!");
	}
	
//	private void checkEqual(String prefix, Object one, Object two) throws Exception {
//		if (one == null ^ two == null)
//			throw new Exception(String.format("%s, only one of the objects is null one: %s two: %s", prefix, one, two));
//		if (one == null && two == null)
//			return;
//		if (one.getClass() != two.getClass())
//			throw new Exception(String.format("%s, classes differ %s != %s", prefix, one.getClass(), two.getClass()));
//
//		Class<?> clazz = one.getClass();
//		if (clazz.getName().startsWith("java.lang")) {
////			System.out.println("-- " + clazz.getName());
//			if (!one.equals(two)) {
//				throw new Exception(String.format("%s, java.lang values differ, type: %s, %s != %s", prefix, one.getClass(), one, two));
//			}
//			return;
//		}
//		
//		if (clazz.isPrimitive()) {
//			if (!one.equals(two)) {
//				throw new Exception(String.format("%s, primitive values differ, type: %s, %s != %s", prefix, one.getClass(), one, two));
//			}
//			return;
//		}
//		
//		if (Map.class.isAssignableFrom(clazz)) {
//			checkMapsEqual(prefix, (Map<?,?>)one, (Map<?,?>)two);
//			return;
//		}  
//		
//		if (Collection.class.isAssignableFrom(clazz)) {
//			checkCollectionsEqual(prefix, (Collection<?>)one, (Collection<?>)two);
//			return;
//		}
//		
//		if (clazz.isArray()) {
//			// TODO this doesnt work
//			checkArraysEqual(prefix, (Object[])one, (Object[])two);
//			return;
//		}
//		
//		while (clazz != null) {
//			for (Field field : clazz.getDeclaredFields()) {
////				System.out.println(field);
//				if (Modifier.isStatic(field.getModifiers()))
//					continue;
//				field.setAccessible(true);
//				if (field.getType().isPrimitive()) {
//					if (!field.get(one).equals(field.get(two))) {
//						throw new Exception(String.format("%s, primitive values differ, type: %s, %s != %s", prefix, one.getClass(), one, two));
//					}
//				} else {
//					checkEqual(prefix + "-" + String.valueOf(field), field.get(one), field.get(two));
//				}
//			}
//			clazz = clazz.getSuperclass();
//		}
//		
//	}
//	
//	private void checkArraysEqual(String prefix, Object[] one, Object[] two) throws Exception {
//		System.out.println("-- checkArraysEqual");
//		if (one.length != two.length) {
//			throw new Exception(String.format("%s, array lengths differ: %s != %s", prefix, one.length, two.length));
//		}
//	}
//	
//	private void checkCollectionsEqual(String prefix, Collection<?> one, Collection<?> two) throws Exception {
//		if (one.size() != two.size()) {
//			throw new Exception(String.format("%s, collection sizes differ: %s != %s", prefix, one.size(), two.size()));
//		}
//		Iterator<?> oneItr = one.iterator();
//		for (Object twoItem : two) {
//			Object oneItem = oneItr.next();
//			checkEqual(prefix + "-item", oneItem, twoItem);
//		}
//	}
//
//	private void checkMapsEqual(String prefix, Map<?,?> one, Map<?,?> two) throws Exception {
//		if (one.size() != two.size()) {
//			throw new Exception(String.format("%s, map sizes differ: %s != %s", prefix, one.size(), two.size()));
//		}
//		
//		Iterator<?> oneItr = one.entrySet().iterator();
//		for (Map.Entry<?,?> twoEntry : two.entrySet()) {
//			Map.Entry<?,?> oneEntry = (Map.Entry<?, ?>) oneItr.next();
//			checkEqual(prefix + "-key", oneEntry.getKey(), twoEntry.getKey());
//			checkEqual(prefix + "-value", oneEntry.getValue(), twoEntry.getValue());
//		}
//	}
	
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
		String[] poolFiles = {
			"./runs/1/pool.ser",
			"./runs/2/pool.ser",
			"./runs/3/pool.ser",
//			"./runs/4/pool.ser",
//			"./runs/5/pool.ser",
		};
		new CheckPools(poolFiles).checkEqual();
	}

}
