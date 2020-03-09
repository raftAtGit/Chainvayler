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
 * A company.
 * 
 * @author r a f t
 */
@Chained
public class _Company implements Serializable, IsChained {

	private static final long serialVersionUID = 1L;

	@_Injected private Long __chainvayler_Id;

	private _RichPerson owner; // = new _RichPerson("<no name>");
	
	public _Company() throws Exception {
		// @_Injected
		try {
			if (__Chainvayler.isBound()) {
				Context context = __Chainvayler.getInstance();
				
				if (context.isInTransaction() || context.isInRemoteTransaction()) {
					this.__chainvayler_Id = context.root.putObject(this);
				} else {
					//System.out.println("starting constructor transaction @" + _Company.class + " for " + Utils.identityCode(this));
					context.setInTransaction(true);
					context.setConstructorTransactionInitiater(this);
					
					try {
						ConstructorCall<? extends IsChained> constructorCall = context.getConstructorCall(); 
						if (constructorCall == null) {
							if (getClass() != _Company.class)
								throw new Error("subclass constructor " + getClass().getName() + " is running but there is no stored constructorCall");
							
							constructorCall = new ConstructorCall<_Company>(
									_Company.class, new Class[]{}, new Object[]{});
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
			}
			
			owner = new _RichPerson("<no name>");
			
		} catch (Exception e) {
			if (__Chainvayler.isBound()) {
				__Chainvayler.getInstance().maybeEndTransaction(this);
			}
			throw e;
		} finally {
			if (__Chainvayler.isBound()) {
				__Chainvayler.getInstance().maybeEndTransaction(this, _Company.class);
			}
		}
	}

	@_Injected("this constructor is not actually injected. we inject some code in sub classes before invkoing super type's constructor."
			+ "as there is no way to emulate this behaviour in Java code, we use this workaround")
	protected _Company(Void v)throws Exception {
		this();
	}

	@_Injected
	public final Long __chainvayler_getId() {
		return __chainvayler_Id;
	}

	public _RichPerson getOwner() {
		return owner;
	}

	@Modification
	public void setOwner(_RichPerson newOwner) {
		if (!__Chainvayler.isBound()) { 
			__chainvayler__setOwner(newOwner);
			return;
		}
		
		Context context = __Chainvayler.getInstance();
		if (context.isInTransaction() || context.isInRemoteTransaction()) { 
			__chainvayler__setOwner(newOwner);
			return;
		}
		
		context.setInTransaction(true);
		try {
			context.prevayler.execute(new MethodTransaction(
					this, new MethodCall("__chainvayler__setOwner", _Company.class, new Class[] {_RichPerson.class}), new Object[] { newOwner } ));
		} finally {
			context.setInTransaction(false);
		}
	}

	@_Injected("renamed from setOwner and made private")
	private void __chainvayler__setOwner(_RichPerson newOwner) {
		if (this.owner != null) {
			this.owner.removeCompany(this);
		}
		this.owner = newOwner;
		
		if (newOwner != null) {
			newOwner.addCompany(this);
		}
	}
	

}
