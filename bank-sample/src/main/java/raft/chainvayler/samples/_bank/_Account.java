package raft.chainvayler.samples._bank;

import java.io.Serializable;

import raft.chainvayler.Modification;
import raft.chainvayler.impl.ConstructorCall;
import raft.chainvayler.impl.ConstructorTransaction;
import raft.chainvayler.impl.Context;
import raft.chainvayler.impl.IsChained;
import raft.chainvayler.impl.MethodCall;
import raft.chainvayler.impl.MethodTransaction;


/**
 * An account in a bank
 * 
 * @author  r a f t
 */
public class _Account implements Serializable, IsChained {

	private static final long serialVersionUID = 1L;

	private final int id;
	private String name;
	private _Customer owner;
	private int balance = 0;
	
	@_Injected private Long __chainvayler_Id;
	
	_Account(int id) throws Exception {
		// @_Injected
		try {
			if (__Chainvayler.isBound()) { 
				Context context = __Chainvayler.getInstance();
				
				if (context.isInTransaction() || context.isInRemoteTransaction()) {
					this.__chainvayler_Id = context.root.putObject(this);
				} else {
					//System.out.println("starting constructor transaction @" + _Account.class + " for " + Utils.identityCode(this));
					context.setInTransaction(true);
					try {
						ConstructorCall<? extends IsChained> constructorCall = context.getConstructorCall(); 
						if (constructorCall == null) {
							if (getClass() != _Account.class)
								throw new Error("subclass constructor " + getClass().getName() + " is running but there is no stored constructorCall");
							
							constructorCall = new ConstructorCall<_Account>(
									_Account.class, new Class[]{int.class}, new Object[]{id});
						}
						this.__chainvayler_Id = context.prevayler.execute(new ConstructorTransaction(this, constructorCall));
					} finally {
						context.setInTransaction(false);
						context.setConstructorCall(null);
						System.out.println("ending transaction: " + this);
					}
				}
			} else if (Context.isInRecovery()) {
				this.__chainvayler_Id = Context.getRecoveryRoot().putObject(this);
			} else {
				// no Chainvayler, object will not have an id
			}
			
			this.id = id;
		} catch (Exception e) {
			if (__Chainvayler.isBound()) {
				__Chainvayler.getInstance().maybeEndTransaction(this);
			}
			throw e;
		} finally {
			if (__Chainvayler.isBound()) {
				__Chainvayler.getInstance().maybeEndTransaction(this, _Account.class);
			}
		}
			
	}
	
	public int getBalance() {
		return balance;
	}

	public int getId() {
		return id;
	}

	public _Customer getOwner() {
		return owner;
	}

	void setOwner(_Customer owner) {
		this.owner = owner;
	}

	public String getName() {
		return name;
	}

	@Modification
	public void setName(String name) {
		if (!__Chainvayler.isBound()) { 
			__chainvayler__setName(name);
			return;
		}
		
		Context context = __Chainvayler.getInstance();
		if (context.isInTransaction() || context.isInRemoteTransaction()) { 
			__chainvayler__setName(name);
			return;
		}
		
		context.setInTransaction(true);
		try {
			context.prevayler.execute(new MethodTransaction(
					this, new MethodCall("__chainvayler__setName", _Account.class, new Class[] {String.class}), new Object[] { name } ));
		} finally {
			context.setInTransaction(false);
		}
	}
	
	@_Injected
	private void __chainvayler__setName(String name) {
		this.name = name;
	}
	
	@Modification
	public void deposit(int amount) {
		if (!__Chainvayler.isBound()) { 
			__chainvayler__deposit(amount);
			return;
		}
		
		Context context = __Chainvayler.getInstance();
		if (context.isInTransaction() || context.isInRemoteTransaction()) { 
			__chainvayler__deposit(amount);
			return;
		}
		
		context.setInTransaction(true);
		try {
			context.prevayler.execute(new MethodTransaction(
					this, new MethodCall("__chainvayler__deposit", _Account.class, new Class[] {Integer.TYPE}), new Object[] { amount } ));
		} finally {
			context.setInTransaction(false);
		}
	}
	
	@_Injected
	private void __chainvayler__deposit(int amount) {
		if (amount <= 0)
			throw new IllegalArgumentException("amount: " + amount);
		this.balance += amount;
	}
	
	@Modification
	public void withdraw(int amount) {
		if (!__Chainvayler.isBound()) { 
			__chainvayler__withdraw(amount);
			return;
		}
		
		Context context = __Chainvayler.getInstance();
		if (context.isInTransaction() || context.isInRemoteTransaction()) { 
			__chainvayler__withdraw(amount);
			return;
		}
		
		context.setInTransaction(true);
		try {
			context.prevayler.execute(new MethodTransaction(
					this, new MethodCall("__chainvayler__withdraw", _Account.class, new Class[] {Integer.TYPE}), new Object[] { amount } ));
		} finally {
			context.setInTransaction(false);
		}
	}
	
	@_Injected
	private void __chainvayler__withdraw(int amount) {
		if (amount <= 0)
			throw new IllegalArgumentException("amount: " + amount);
		if (balance < amount)
			throw new IllegalArgumentException("balance < amount");
		
		this.balance -= amount;
	}
	
	@_Injected 
	public final Long __chainvayler_getId() {
		return __chainvayler_Id;
	}
}
