package raft.chainvayler.samples.bank;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import raft.chainvayler.Chained;
import raft.chainvayler.Clock;
import raft.chainvayler.Include;
import raft.chainvayler.Modification;
import raft.chainvayler.Synch;
import raft.chainvayler.samples.bank.secret.SecretCustomer;

/**
 * A bank.
 * 
 * @author r a f t
 */
@Include(SecretCustomer.class)
@Chained 
public class Bank extends Company {

	private static final long serialVersionUID = 1L;

	private final Map<Integer, Customer> customers = new TreeMap<Integer, Customer>();
	private final Map<Integer, Account> accounts = new TreeMap<Integer, Account>();
	private final List<Date> audits = new LinkedList<>();

	private int lastCustomerId = 1;
	private int lastAccountId = 1;
	
	private Bank sister;
	
	public Bank() {
	}

	@Modification
	public Customer createCustomer(String name) {
		Customer customer = new Customer(name);
		addCustomer(customer);
		return customer;
	}
	
	@Modification
	public int addCustomer(Customer customer) {
		customer.setId(lastCustomerId++);
		customers.put(customer.getId(), customer);
		return customer.getId();
	}

	@Modification
	void addCustomers(Customer... customers) {
		for (Customer customer : customers) {
			addCustomer(customer);
		}
	}
	
	@Modification
	public Account createAccount() throws Exception {
		Account account = new Account(lastAccountId++);
		accounts.put(account.getId(), account);
		return account;
	}
	
	@Modification
	public void transferAmount(Account from, Account to, int amount) throws Exception {
		if (from.getId() == to.getId()) {
			assert (from == to);
			throw new IllegalArgumentException("from and to are same accounts");
		}
		if (amount <= 0)
			throw new IllegalArgumentException("amount: " + amount);
		if (from.getBalance() < amount)
			throw new IllegalArgumentException("balance < amount");
		
		from.withdraw(amount);
		to.deposit(amount);
	}
	
	public Customer getCustomer(int customerId) {
		return customers.get(customerId);
	}
	
	@Synch
	public List<Customer> getCustomers() {
		return new ArrayList<Customer>(customers.values());
	}

	@Modification
	public void removeCustomer(Customer customer) {
		customers.remove(customer.getId());
	}

	@Synch
	public List<Account> getAccounts() {
		return new ArrayList<Account>(accounts.values());
	}
	
	public Account getAccount(int id) {
		return accounts.get(id);
	}

	@Synch
	public Bank getSister() {
		return sister;
	}

	@Modification
	public void setSister(Bank sister) {
		this.sister = sister;
	}

	@Modification
	public void addAudit() {
		audits.add(Clock.now());
	}
	
	@Synch
	public List<Date> getAudits() {
		return new ArrayList<Date>(audits);
	}
	
}
