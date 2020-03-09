package org.prevayler.implementation;

import org.prevayler.Transaction;
import org.prevayler.foundation.serialization.Serializer;

/** Subclass of Prevayler's package private TransactionCapsule class to make it accessible */
public class TransactionCapsuleCV<P> extends TransactionCapsule<P> {

	private static final long serialVersionUID = 1L;
	
	public TransactionCapsuleCV(Transaction<? super P> transaction, Serializer journalSerializer, boolean transactionDeepCopyMode) {
		super(transaction, journalSerializer, transactionDeepCopyMode);
	}
	

}
