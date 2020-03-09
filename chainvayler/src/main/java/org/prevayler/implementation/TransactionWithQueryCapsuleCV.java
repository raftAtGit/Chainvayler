package org.prevayler.implementation;

import org.prevayler.TransactionWithQuery;
import org.prevayler.foundation.serialization.Serializer;

/** Subclass of Prevayler's package private TransactionWithQueryCapsule class to make it accessible */
public class TransactionWithQueryCapsuleCV<P,R> extends TransactionWithQueryCapsule<P,R> {

	private static final long serialVersionUID = 1L;
	
	public TransactionWithQueryCapsuleCV(TransactionWithQuery<? super P,R> transactionWithQuery, Serializer journalSerializer, boolean transactionDeepCopyMode) {
		super(transactionWithQuery, journalSerializer, transactionDeepCopyMode);
	}
	
	
}
