package raft.chainvayler.samples._bank;

import raft.chainvayler.Chained;
import raft.chainvayler.impl.ConstructorCall;
import raft.chainvayler.impl.Context;

/**
 * Central bank.
 * 
 * @author r a f t
 */
@Chained
public class _CentralBank extends _Bank {

	private static final long serialVersionUID = 1L;

	@_Injected("this method is not actually injected but contents is injected before invkoing super type's constructor."
			+ "as there is no way to emulate this behaviour in Java code, we use this workaround")
	private static Void __chainvayler_maybeInitConstructorTransaction() { 
		if (__Chainvayler.isBound()) { 
			Context context = __Chainvayler.getInstance();
			
			if (!context.isInTransaction() && (context.getConstructorCall() == null)) {
				context.setConstructorCall(new ConstructorCall<_CentralBank>(
						_CentralBank.class, new Class[]{}, new Object[]{}));
			}
		}
		
		return null; 
	}
	
	public _CentralBank() throws Exception {
		// @Injected
		super(__chainvayler_maybeInitConstructorTransaction());
		
		if (__Chainvayler.isBound()) {
			__Chainvayler.getInstance().maybeEndTransaction(this, _CentralBank.class);
		}
	}

}
