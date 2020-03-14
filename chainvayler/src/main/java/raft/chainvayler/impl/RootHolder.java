package raft.chainvayler.impl;

import java.io.Serializable;

/** 
 * Container class that hold chained root. 
 * 
 * @author r a f t
 */
public class RootHolder implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	private final Pool pool = new Pool();
	private IsChained root;
	
	public final IsChained getObject(Long id) {
		return pool.get(id);
	}

	public final Long putObject(IsChained chained) {
		if (chained.__chainvayler_getId() != null) {
			// we throw error to halt Prevayler
			throw new Error(String.format("object already has an id: %s, %s", 
					chained.__chainvayler_getId(), Utils.identityCode(chained)));
		}
		
		return pool.put(chained); 
	}
	
	boolean hasObject(long id) {
		return pool.get(id) != null;
	}

	// TODO revert/fix to weak values
	public final void onRecoveryCompleted(boolean switchToWeakValues) {
		if (switchToWeakValues) pool.switchToWeakValues();
	}

	public final boolean isInitialized() {
		return (root != null);
	}
	
	public final IsChained getRoot() {
		return root;
	}

	final void setRoot(IsChained root) {
		assert (this.root == null);
		this.root = root;
	}
	
	String printPool() {
		return pool.toString();
	}
}
