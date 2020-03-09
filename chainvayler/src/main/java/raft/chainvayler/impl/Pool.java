package raft.chainvayler.impl;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class Pool implements Serializable {

	private static final long serialVersionUID = 1L;

	/** the objects. we start with a regular {@link Map} and after recovery is completed switch to a 
	 * {@link WeakValueHashMap}. This is necessary because during recovery there are no external references
	 * to created objects and nothing prevents them from garbage collected from a WeakMap.
	 * 
	 *  @see RootHolder#onRecoveryCompleted()
	 *  @see #switchToWeakValues() */
	private Map<Long, IsChained> objects = new HashMap<Long, IsChained>();

	private long lastId = 1;
	
	public Pool() {
	}
	
	final Long put(IsChained chained) {
		if (Context.isInRecovery()) {
			synchronized (this) {
				return putWithoutLock(chained);
			}
		}
		
		Context.getInstance().poolLock.lock();
		try {
			return putWithoutLock(chained);
		} finally {
			Context.getInstance().poolLock.unlock();	
		}
	}
	
	private Long putWithoutLock(IsChained chained) {
		Long id = lastId++;
		IsChained old = objects.put(id, chained);
		assert (old == null) : String.format("id: %s, old: %s, new %s", id, old, chained);
		if (Context.DEBUG) System.out.printf("put object to pool %s: %s, size %s \n", id, Utils.identityCode(chained), objects.size());
		return id;
	}
	
	final IsChained get(Long id) {
		if (Context.isInRecovery()) {
			synchronized (this) {
				return objects.get(id);
			}
		}
		
		Context.getInstance().poolLock.lock();
		try {
			return objects.get(id);
		} finally {
			Context.getInstance().poolLock.unlock();	
		}
	} 

	public final void switchToWeakValues() {
		System.out.println("--switching to weak values");
		
		Context.getInstance().poolLock.lock();
		try {
			this.objects = new WeakValueHashMap<Long, IsChained>(objects); 
			System.out.println("--done");
		} finally {
			Context.getInstance().poolLock.unlock();	
		}
	}
	
	/** replaces the WeakValueMap with a regular HashMap so after a snapshot read we always start with a HashMap */
	private void writeObject(java.io.ObjectOutputStream out) throws IOException {
		Map<Long, IsChained> nonWeakObjects = new HashMap<Long, IsChained>(objects);
		this.objects = nonWeakObjects;
		out.defaultWriteObject();
	}

	@Override
	public String toString() {
		Context.getInstance().poolLock.lock();
		try {
			return "Pool: " + objects; 
		} finally {
			Context.getInstance().poolLock.unlock();	
		}
	}
	
	
}
