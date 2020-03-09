package raft.chainvayler.impl;

import java.io.Serializable;

import raft.chainvayler.NotChainedException;

/**
 * Reference to an IsChained object 
 * 
 * @author  hakan eryargi (r a f t)
 */
class Reference implements Serializable {
	
	private static final long serialVersionUID = 1L;

	final Long id;

	Reference(IsChained chained) {
		this.id = chained.__chainvayler_getId();
		if (id == null)
			throw new NotChainedException("object has no id, did you create this object before Chainvayler is created?\n" + Utils.identityCode(chained));
	}
	
	@Override
	public String toString() {
		return "Reference:" + id;
	}
	
}
