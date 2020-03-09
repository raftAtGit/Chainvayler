package raft.chainvayler.samples._bank;

import java.io.Serializable;

import raft.chainvayler.Chained;
import raft.chainvayler.Modification;
import raft.chainvayler.impl.ConstructorCall;
import raft.chainvayler.impl.ConstructorTransaction;
import raft.chainvayler.impl.Context;
import raft.chainvayler.impl.IsChained;
import raft.chainvayler.impl.MethodCall;
import raft.chainvayler.impl.MethodTransaction;

/**
 * A person.
 * 
 * @author r a f t
 */
@Chained
public class _Person implements Serializable, IsChained {
	private static final long serialVersionUID = 1L;

	@_Injected private final Long __chainvayler_Id;
	
	private String name;
	private String phone;

	public _Person() throws Exception {
		// @_Injected
		try {
			if (__Chainvayler.isBound()) { 
				Context context = __Chainvayler.getInstance();
				
				if (context.isInTransaction() || context.isInRemoteTransaction()) {
					this.__chainvayler_Id = context.root.putObject(this);
				} else {
					//System.out.println("starting constructor transaction @" + _Person.class + " for " + Utils.identityCode(this));
					context.setInTransaction(true);
					context.setConstructorTransactionInitiater(this);
					
					try {
						ConstructorCall<? extends IsChained> constructorCall = context.getConstructorCall(); 
						if (constructorCall == null) {
							if (getClass() != _Person.class)
								throw new Error("subclass constructor " + getClass().getName() + " is running but there is no stored constructorCall");
							
							constructorCall = new ConstructorCall<_Person>(
									_Person.class, new Class[]{}, new Object[]{});
						}
						this.__chainvayler_Id = context.prevayler.execute(new ConstructorTransaction(this, constructorCall));
					} finally {
						//context.setInTransaction(false);
						context.setConstructorCall(null);
					}
				}
			} else if (Context.isInRecovery()) {
				this.__chainvayler_Id = Context.getRecoveryRoot().putObject(this);
			} else {
				// no chainvayler, object will not have an id
				this.__chainvayler_Id = null;
			}
		} catch (Exception e) {
			if (__Chainvayler.isBound()) {
				__Chainvayler.getInstance().maybeEndTransaction(this);
			}
			throw e;
		} finally {
			if (__Chainvayler.isBound()) {
				__Chainvayler.getInstance().maybeEndTransaction(this, _Person.class);
			}
		}
	}
	
	public _Person(String name) throws Exception {
		this();
		// @_Injected
		try {
			
			this.name = name;
			
			if ("HellBoy".equals(name))
				throw new IllegalArgumentException(name);
			
			
		} catch (Exception e) {
			if (__Chainvayler.isBound()) {
				__Chainvayler.getInstance().maybeEndTransaction(this);
			}
			throw e;
		} finally {
			if (__Chainvayler.isBound()) {
				__Chainvayler.getInstance().maybeEndTransaction(this, _Person.class);
			}
		}
	}

	public String getPhone() {
		return phone;
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
					this, new MethodCall("__chainvayler__setName", _Person.class, new Class[] {String.class}), new Object[] { name } ));
		} finally {
			context.setInTransaction(false);
		}
	}

	@Modification
	public void setPhone(String phone) {
		if (!__Chainvayler.isBound()) { 
			__chainvayler__setPhone(phone);
			return;
		}
		
		Context context = __Chainvayler.getInstance();
		if (context.isInTransaction() || context.isInRemoteTransaction()) { 
			__chainvayler__setPhone(phone);
			return;
		}
		
		context.setInTransaction(true);
		try {
			context.prevayler.execute(new MethodTransaction(
					this, new MethodCall("__chainvayler__setPhone", _Person.class, new Class[] {String.class}), new Object[] { phone } ));
		} finally {
			context.setInTransaction(false);
		}
	}

	@_Injected
	private void __chainvayler__setPhone(String phone) {
		this.phone = phone;
	}

	@_Injected
	private void __chainvayler__setName(String name) {
		this.name = name;
	}
	
	@_Injected 
	public final Long __chainvayler_getId() {
		return __chainvayler_Id;
	}
	
	
	
}
