package raft.chainvayler.samples._bank;

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

/**
 * A customer.
 * 
 * @author r a f t
 */
@Chained
public class _Customer extends _Person {
	private static final long serialVersionUID = 1L;

	private int id;

	private final Map<Integer, _Account> accounts = new TreeMap<Integer, _Account>();

	@_Injected("this method is not actually injected but contents is injected before invkoing super type's constructor."
			+ "as there is no way to emulate this behaviour in Java code, we use this workaround")
	private static String __chainvayler_maybeInitConstructorTransaction(String name) { 
		if (__Chainvayler.isBound()) { 
			Context context = __Chainvayler.getInstance();
			
			if (!context.isInTransaction() && (context.getConstructorCall() == null)) {
				context.setConstructorCall(new ConstructorCall<_Customer>(
						_Customer.class, new Class[]{ String.class }, new Object[] {name}));
			}
		}
		
		return name; 
	}
	
	public _Customer(String name) throws Exception {
		// @_Injected
		super(__chainvayler_maybeInitConstructorTransaction(name));
		
		if (__Chainvayler.isBound()) {
			__Chainvayler.getInstance().maybeEndTransaction(this, _Customer.class);
		}
		
	}
	
	@Modification
	public void addAccount(_Account account) {
		if (!__Chainvayler.isBound()) { 
			__chainvayler__addAccount(account);
			return;
		}
		
		Context context = __Chainvayler.getInstance();
		if (context.isInTransaction() || context.isInRemoteTransaction()) { 
			__chainvayler__addAccount(account);
			return;
		}
		
		context.setInTransaction(true);
		try {
			context.prevayler.execute(new MethodTransaction(
					this, new MethodCall("__chainvayler__addAccount", _Customer.class, new Class[] {_Account.class}), new Object[] { account} ));
		} finally {
			context.setInTransaction(false);
		}
	}
	
	@_Injected
	private void __chainvayler__addAccount(_Account account) {
		if (account.getOwner() != null)
			throw new IllegalArgumentException("Account already has an owner");
		
		accounts.put(account.getId(), account);
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
	
	@_Injected
	private List<_Account> __chainvayler__getAccounts() {
		return new ArrayList<_Account>(accounts.values());
	}

	public int getId() {
		return id;
	}

	void setId(int id) {
		this.id = id;
	}

	@Override
	public String toString() {
		return "Customer:" + id + ":" + getName();
	}

}
