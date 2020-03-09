package raft.chainvayler.samples.bank.rmi;

import java.lang.reflect.Field;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Map;

import raft.chainvayler.Chainvayler;
import raft.chainvayler.impl.Context;
import raft.chainvayler.impl.IsChained;
import raft.chainvayler.samples.bank.Bank;

public interface Peer extends Remote {

	boolean isStarted() throws Exception;
	
	boolean isCompleted() throws Exception;
	
	boolean hasWriters() throws Exception;
	
	long getTransactionCount() throws Exception;
	
	float getTransactionsPerSecond() throws Exception;
	
	long getOwnTransactionCount() throws Exception;
	
	float getOwnTransactionsPerSecond() throws Exception;
	
	long getReadCount() throws Exception;
	
	float getReadsPerSecond() throws Exception;
	
	Bank getBank() throws Exception;
	
	long getPoolSize() throws Exception;
	
	Map<Long, IsChained> getPool() throws Exception;
	
	void stopReaders() throws Exception;
	
	interface ReaderStopper {
		void stopReaders() throws Exception;
	}
	
	public static class Impl extends UnicastRemoteObject implements Peer {

		private static final long serialVersionUID = 1L;
		
		public boolean started = false;
		public boolean completed = false;
		public boolean hasWriters = true;
		public long startTime = System.currentTimeMillis();
		public float lastTxPerSecond;
		public float lastOwnTxPerSecond;
		private float lastReadsPerSecond;
		private long readCount = 0;
		
		private transient final ReaderStopper readerStopper;
		
		
		public Impl(ReaderStopper readerStopper) throws RemoteException {
			this.readerStopper = readerStopper;
		}

		@Override
		public boolean isCompleted() throws Exception {
			return completed;
		}

		@Override
		public boolean isStarted() throws Exception {
			return started;
		}
		
		@Override
		public boolean hasWriters() throws Exception {
			return hasWriters;
		}

		@Override
		public long getTransactionCount() throws Exception {
			Chainvayler<?> chainvayler = Chainvayler.getInstance(); 
			return (chainvayler == null) ? -1 : chainvayler.getTransactionCount();
		}

		@Override
		public float getTransactionsPerSecond() throws Exception {
			if (completed)
				return lastTxPerSecond;
			
			lastTxPerSecond = 1000f * getTransactionCount() / (System.currentTimeMillis() - startTime);
			return lastTxPerSecond;
		}
		
		@Override
		public long getOwnTransactionCount() throws Exception {
			Chainvayler<?> chainvayler = Chainvayler.getInstance(); 
			return (chainvayler == null) ? -1 : chainvayler.getOwnTransactionCount();
		}

		@Override
		public float getOwnTransactionsPerSecond() throws Exception {
			if (completed)
				return lastOwnTxPerSecond;
			
			lastOwnTxPerSecond = 1000f * getOwnTransactionCount() / (System.currentTimeMillis() - startTime);
			return lastOwnTxPerSecond;
		}
		
		@Override
		public long getReadCount() throws Exception {
			return readCount;
		}
		
		@Override
		public float getReadsPerSecond() throws Exception {
			if (completed)
				return lastReadsPerSecond;
			
			lastReadsPerSecond = 1000f * getReadCount() / (System.currentTimeMillis() - startTime);
			return lastReadsPerSecond;
		}
		
		
		@Override
		public Bank getBank() throws Exception {
			Context context = Context.getInstance();
			return (context == null) ? null : (Bank) context.root.getRoot();
		}

		@Override
		public Map<Long, IsChained> getPool() throws Exception {
			Context context = Context.getInstance();
			return (context == null) ? null : getDeclaredFieldValue("objects", getDeclaredFieldValue("pool", context.root));
		}
		
		@Override
		public long getPoolSize() throws Exception {
			Map<Long, IsChained> pool = getPool();
			return (pool == null) ? -1 : pool.size();
		}
		
		@Override
		public void stopReaders() throws Exception {
			readerStopper.stopReaders();
		}
		
		@SuppressWarnings("unchecked")
		private <T> T getDeclaredFieldValue(String fieldName, Object o) throws Exception {
			return (T) getDeclaredField(fieldName, o).get(o);
		}

		private Field getDeclaredField(String fieldName, Object o) throws Exception {
			Field field = o.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			return field;
		}

		public synchronized void incrementReadCount(int readCount) {
			this.readCount += readCount;
		}
	}
	
}
