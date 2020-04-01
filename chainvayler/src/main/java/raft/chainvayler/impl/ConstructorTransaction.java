package raft.chainvayler.impl;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.prevayler.TransactionWithQuery;

/**
 * A special Transaction which behaves different at <i>recovery</i> and <i>regular</i> run. 
 * This transaction stores given target object in a temporary memory location and returns it as it is during regular run. 
 * At recovery, an object is created with given constructor. This mechanism escapes target object from 
 * Prevayler's transaction serialization. Coordinated with injected code to object's constructor, 
 * at either recovery or regular run, after this transaction is executed the object gets a unique id. Since Prevayler
 * guarantees transaction ordering, it's guaranteed that the target object will get the same id.     
 * 
 * @author r a f t
 */
public class ConstructorTransaction implements TransactionWithQuery<RootHolder, Long> {

	private static final long serialVersionUID = 1L;

	private static final Map<Long, IsChained> tempTargets = new HashMap<Long, IsChained>();
	private static long lastTempId;
	
	private static final Long putTemp(IsChained target) {
		synchronized (tempTargets) {
			Long id = lastTempId++;
			tempTargets.put(id, target);
			//System.out.printf("put target to tempTargets id %s: %s \n tempTargets: %s \n", id, target, tempTargets);
			return id;
		}
	} 
	
	private final Long tempTargetId;
	private final ConstructorCall<? extends IsChained> constructor;

	public ConstructorTransaction(IsChained target, ConstructorCall<? extends IsChained> constructor) {
		this.tempTargetId = putTemp(target);
		this.constructor = constructor;
	}

	@Override
	public Long executeAndQuery(RootHolder root, Date date) throws Exception {
		ClockBase.setDate(date);
		try {
			if (Context.isBound()) {
				if (Context.getInstance().isInRemoteTransaction()) {
					IsChained target = constructor.newInstance(root);
					assert (target.__chainvayler_getId() != null);
					assert (root.hasObject(target.__chainvayler_getId()));
					return null; // return type is not actually used in this case
				} else {
					// regular run, just retrieve object from temp storage and put into pool
					// IMPORTANT: this part runs inside constructor of the target object
					// so when this transaction completes, we will be still in the constructor of the target object
					IsChained target = tempTargets.remove(tempTargetId);
					if (target == null) {
						throw new Error("couldnt get object from temp pool, id: " + tempTargetId); // we throw error to halt Prevayler
					}
					if (Context.DEBUG) System.out.printf("ConstructorTransaction, retrieved object %s from tempTargets %s \n %s \n", tempTargetId, Utils.identityCode(target), tempTargets);
					
					// as explained above, when this transaction completes, we will still be inside the constructor
					// we need to prevent any other transaction kicking in and create objects
					// that's the reason we first lock object pool
					// it's unlocked by injected bytecode to constructors
					Context.getInstance().lockObjectPool(target);
					if (Context.DEBUG) System.out.printf("ConstructorTransaction, locked object pool for %s \n", Utils.identityCode(target));
					
					return root.putObject(target);
				}
			} else {
				// recovery
				Context.recoveryRoot = root;
				try {
					IsChained target = constructor.newInstance(root);
					assert (target.__chainvayler_getId() != null);
					return null; // return type is not actually used in this case
				} finally {
					Context.recoveryRoot = null;
				}
			}
//		} catch (Exception e) {
//			e.printStackTrace();
//			throw e;
		} finally {
			ClockBase.setDate(null);
		}
	}

	@Override
	public String toString() {
		return String.format("ConstructorTransaction constructor: %s, arguments: %s", 
				constructor.getJavaConstructor(), Arrays.toString(constructor.arguments));
	}
	
}
