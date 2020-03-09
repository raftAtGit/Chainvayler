package raft.chainvayler.impl;

import java.io.File;
import java.io.IOException;

import org.prevayler.Clock;
import org.prevayler.Prevayler;
import org.prevayler.Query;
import org.prevayler.SureTransactionWithQuery;
import org.prevayler.Transaction;
import org.prevayler.TransactionWithQuery;

/** 
 * Holds a reference to Transaction's before delegating call to another Prevayler. Combined with
 * references to target objects in {@link MethodTransaction} and {@link MethodTransactionWithQuery}, 
 * this safely prevents garbage collector cleaning our target before we are done. 
 * 
 * @author r a f t
 * @see MethodTransaction
 * @see MethodTransactionWithQuery
 * */
public class GCPreventingPrevayler implements Prevayler<RootHolder> {

	final Prevayler<RootHolder> delegate;
	final Prevayler<RootHolder> dummy = new NullPrevayler(); 
	
	public GCPreventingPrevayler(Prevayler<RootHolder> delegate) {
		this.delegate = delegate;
	}

	public RootHolder prevalentSystem() {
		return delegate.prevalentSystem();
	}

	public Clock clock() {
		return delegate.clock();
	}

	@Override
	public void execute(Transaction<? super RootHolder> transaction) {
		delegate.execute(transaction);
		dummy.execute(transaction);
	}
	
	@Override
	public <R> R execute(TransactionWithQuery<? super RootHolder, R> transactionWithQuery) throws Exception {
		R result = delegate.execute(transactionWithQuery);
		dummy.execute(transactionWithQuery);
		return result;
	}


	@Override
	public <R> R execute(Query<? super RootHolder, R> sensitiveQuery) throws Exception {
		throw new UnsupportedOperationException();
//		R result = delegate.execute(sensitiveQuery);
//		dummy.execute(sensitiveQuery);
//		return result;
	}

	@Override
	public <R> R execute(SureTransactionWithQuery<? super RootHolder, R> sureTransactionWithQuery) {
		throw new UnsupportedOperationException();
//		R result = delegate.execute(sureTransactionWithQuery);
//		dummy.execute(sureTransactionWithQuery);
//		return result;
	}

	@Override
	public File takeSnapshot() throws Exception {
		return delegate.takeSnapshot();
	}

	@Override
	public void close() throws IOException {
		delegate.close();
	}
	
	/** A Prevayler which does nothing :) */
	private static class NullPrevayler implements Prevayler<RootHolder> {

		@Override
		public RootHolder prevalentSystem() {
			return null;
		}

		@Override
		public Clock clock() {
			return null;
		}

		@Override
		public void execute(Transaction<? super RootHolder> transaction) {
		}

		@Override
		public <R> R execute(Query<? super RootHolder, R> sensitiveQuery) throws Exception {
			return null;
		}

		@Override
		public <R> R execute(TransactionWithQuery<? super RootHolder, R> transactionWithQuery) throws Exception {
			return null;
		}

		@Override
		public <R> R execute(SureTransactionWithQuery<? super RootHolder, R> sureTransactionWithQuery) {
			return null;
		}

		@Override
		public File takeSnapshot() throws Exception {
			return null;
		}

		@Override
		public void close() throws IOException {
		}
		
	}

}
