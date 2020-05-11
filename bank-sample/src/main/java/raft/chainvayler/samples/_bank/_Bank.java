package raft.chainvayler.samples._bank;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import raft.chainvayler.Chained;
import raft.chainvayler.Modification;
import raft.chainvayler.Synch;
import raft.chainvayler.impl.ConstructorCall;
import raft.chainvayler.impl.Context;
import raft.chainvayler.impl.MethodCall;
import raft.chainvayler.impl.MethodTransaction;
import raft.chainvayler.impl.MethodTransactionWithQuery;

/**
 * A bank.
 * 
 * @author r a f t
 */
@Chained 
public class _Bank extends _Company implements Serializable {

	private static final long serialVersionUID = 1L;

	private final Map<Integer, _Customer> customers = new TreeMap<Integer, _Customer>();
	private final Map<Integer, _Account> accounts = new TreeMap<Integer, _Account>();

	private int lastCustomerId = 1;
	private int lastAccountId = 1;
	
	private _Bank sister;
	
	@_Injected("this method is not actually injected but contents is injected before invkoing super type's constructor."
			+ "as there is no way to emulate this behaviour in Java code, we use this workaround")
	private static Void __chainvayler_maybeInitConstructorTransaction() { 
		if (__Chainvayler.isBound()) { 
			Context context = __Chainvayler.getInstance();
			
			if (!context.isInTransaction() && (context.getConstructorCall() == null)) {
				context.setConstructorCall(new ConstructorCall<_Bank>(
						_Bank.class, new Class[]{}, new Object[]{}));
			}
		}
		
		return null; 
	}
	
	public _Bank() throws Exception {
		// @Injected
		super(__chainvayler_maybeInitConstructorTransaction());
		
		if (__Chainvayler.isBound()) {
			__Chainvayler.getInstance().maybeEndTransaction(this, _Bank.class);
		}
	}

	@_Injected("this constructor is not actually injected. we inject some code in sub classes before invkoing super type's constructor."
			+ "as there is no way to emulate this behaviour in Java code, we use this workaround")
	protected _Bank(Void v) throws Exception {
		this();
	}

	@Modification
	public _Customer createCustomer(String name) throws Exception {
		if (!__Chainvayler.isBound()) 
			return __chainvayler__createCustomer(name);
		
		Context context = __Chainvayler.getInstance();
		if (context.isInTransaction() || context.isInRemoteTransaction()) 
			return __chainvayler__createCustomer(name);
		
		context.setInTransaction(true);
		try {
			return context.prevayler.execute(new MethodTransactionWithQuery<_Customer>(
					this, new MethodCall("__chainvayler__createCustomer", _Bank.class, new Class[] {String.class}), new Object[] { name } ));
		} finally {
			context.setInTransaction(false);
		}
	}
	
	@_Injected("renamed from createCustomer and made private")
	private _Customer __chainvayler__createCustomer(String name) throws Exception {
		_Customer customer = new _Customer(name);
		addCustomer(customer);
		_Account account = createAccount();
		account.setName("Default");
		customer.addAccount(account);
		return customer;
	}
	
	@Modification
	public int addCustomer(_Customer customer) throws Exception {
		if (!__Chainvayler.isBound()) 
			return __chainvayler__addCustomer(customer);
		
		Context context = __Chainvayler.getInstance();
		if (context.isInTransaction() || context.isInRemoteTransaction()) 
			return __chainvayler__addCustomer(customer);
		
		context.setInTransaction(true);
		try {
			return context.prevayler.execute(new MethodTransactionWithQuery<Integer>(
					this, new MethodCall("__chainvayler__addCustomer", _Bank.class, new Class[] {_Customer.class}), new Object[] { customer } ));
		} finally {
			context.setInTransaction(false);
		}
	}
	
	@_Injected("renamed from addCustomer and made private")
	private Integer __chainvayler__addCustomer(_Customer customer) {
		customer.setId(lastCustomerId++);
		customers.put(customer.getId(), customer);
		return customer.getId();
	}
	
	@Modification
	public void addCustomers(_Customer... customers) throws Exception {
		if (!__Chainvayler.isBound()) { 
			__chainvayler__addCustomers(customers);
			return;
		}
		
		Context context = __Chainvayler.getInstance();
		if (context.isInTransaction() || context.isInRemoteTransaction()) { 
			__chainvayler__addCustomers(customers);
			return;
		}
		
		context.setInTransaction(true);
		try {
			context.prevayler.execute(new MethodTransaction(
					this, new MethodCall("__chainvayler__addCustomers", _Bank.class, new Class[] {_Customer[].class}), new Object[] { customers } ));
		} finally {
			context.setInTransaction(false);
		}
	}
	
	@_Injected("renamed from addCustomers and made private")
	private void __chainvayler__addCustomers(_Customer... customers) throws Exception {
		for (_Customer customer : customers) {
			addCustomer(customer);
		}
	}
	
	@Modification
	public void removeCustomer(_Customer customer) {
		if (!__Chainvayler.isBound()) { 
			__chainvayler__removeCustomer(customer);
			return;
		}
		
		Context context = __Chainvayler.getInstance();
		if (context.isInTransaction() || context.isInRemoteTransaction()) { 
			__chainvayler__removeCustomer(customer);
			return;
		}
		
		context.setInTransaction(true);
		try {
			context.prevayler.execute(new MethodTransaction(
					this, new MethodCall("__chainvayler__removeCustomer", _Bank.class, new Class[] {_Customer.class}), new Object[] { customer } ));
		} finally {
			context.setInTransaction(false);
		}
	}

	@_Injected("renamed from removeCustomer and made private")
	private void __chainvayler__removeCustomer(_Customer customer) {
		customers.remove(customer.getId());
	}
	
	@Modification
	public _Account createAccount() throws Exception {
		if (!__Chainvayler.isBound()) { 
			return __chainvayler__createAccount();
		}
		
		Context context = __Chainvayler.getInstance();
		if (context.isInTransaction() || context.isInRemoteTransaction()) { 
			return __chainvayler__createAccount();
		}
		
		context.setInTransaction(true);
		try {
			return context.prevayler.execute(new MethodTransactionWithQuery<_Account>(
					this, new MethodCall("__chainvayler__createAccount", _Bank.class, new Class[0]), new Object[0]));
		} finally {
			context.setInTransaction(false);
		}
	}
	
	@_Injected("renamed from createAccountFor and made private")
	private _Account __chainvayler__createAccount() throws Exception {
		_Account account = new _Account(lastAccountId++);
		accounts.put(account.getId(), account);
		return account;
	}
	
	@Modification
	public void transferAmount(_Account from, _Account to, int amount) throws Exception {
		if (!__Chainvayler.isBound()) { 
			__chainvayler__transferAmount(from, to, amount);
			return;
		}
		
		Context context = __Chainvayler.getInstance();
		if (context.isInTransaction() || context.isInRemoteTransaction()) { 
			__chainvayler__transferAmount(from, to, amount);
			return;
		}
		
		context.setInTransaction(true);
		try {
			context.prevayler.execute(new MethodTransaction(
					this, new MethodCall("__chainvayler__transferAmount", _Bank.class, new Class[] {_Account.class, _Account.class, Integer.TYPE}), new Object[] { from, to, amount } ));
		} finally {
			context.setInTransaction(false);
		}
	}
	
	@_Injected("renamed from transferAmount and made private")
	private void __chainvayler__transferAmount(_Account from, _Account to, int amount) throws Exception {
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
	
	public _Customer getCustomer(int customerId) {
		return customers.get(customerId);
	}
	
	@Synch
	public List<_Customer> getCustomers() {
		if (!__Chainvayler.isBound()) 
			return __chainvayler__getCustomers();
		
		Context context = __Chainvayler.getInstance();
		
		if (context.isInQuery()) {
			return __chainvayler__getCustomers();
		}
		
		synchronized (context.root) {
			context.setInQuery(true);
		    try {
				return __chainvayler__getCustomers();
			} finally {
				context.setInQuery(false);
			}
		}
	}

	@_Injected("renamed from getCustomers and made private")
	private List<_Customer> __chainvayler__getCustomers() {
		return new ArrayList<_Customer>(customers.values());
	}

	@Synch
	public List<_Account> getAccounts() {
		if (!__Chainvayler.isBound()) 
			return __chainvayler__getAccounts();
		
		Context context = __Chainvayler.getInstance();
		
		if (context.isInQuery()) {
			return __chainvayler__getAccounts();
		}
		
		synchronized (context.root) {
			context.setInQuery(true);
		    try {
				return __chainvayler__getAccounts();
			} finally {
				context.setInQuery(false);
			}
		}
	}

	@_Injected("renamed from getAccounts and made private")
	private List<_Account> __chainvayler__getAccounts() {
		return new ArrayList<_Account>(accounts.values());
	}
	
	public _Account getAccount(int id) {
		return accounts.get(id);
	}

	public _Bank getSister() {
		return sister;
	}

	public void setSister(_Bank sister) {
		if (!__Chainvayler.isBound()) { 
			__chainvayler__setSister(sister);
			return;
		}
		
		Context context = __Chainvayler.getInstance();
		if (context.isInTransaction() || context.isInRemoteTransaction()) { 
			__chainvayler__setSister(sister);
			return;
		}
		
		context.setInTransaction(true);
		try {
			context.prevayler.execute(new MethodTransaction(
					this, new MethodCall("__chainvayler__setSister", _Bank.class, new Class[] {_Bank.class}), new Object[] { sister } ));
		} finally {
			context.setInTransaction(false);
		}
	}

	@_Injected("renamed from setSister and made private")
	private void __chainvayler__setSister(_Bank sister) {
		this.sister = sister;
	}
	
}
