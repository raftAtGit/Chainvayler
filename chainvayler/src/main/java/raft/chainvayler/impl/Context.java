package raft.chainvayler.impl;

import java.util.concurrent.locks.ReentrantLock;

import org.prevayler.Prevayler;

/**
 * 
 * @author r a f t
 */
// TODO maybe made methods of this class protected and override in __Chainvayler? 
public abstract class Context {

	static final boolean DEBUG = false;
	
	private static Context instance;
	
	public final Prevayler<RootHolder> prevayler;
	public final RootHolder root;
	public final ClassCache classCache;
	
	final ReentrantLock poolLock = new ReentrantLock();
	
	private final ThreadLocal<Boolean> transactionStatus = new ThreadLocal<Boolean>() {
		@Override
		protected Boolean initialValue() {
			return false;
		};
	};
	
	private final ThreadLocal<Boolean> queryStatus = new ThreadLocal<Boolean>() {
		@Override
		protected Boolean initialValue() {
			return false;
		};
	};
	
	private final ThreadLocal<Boolean> remoteTransactionStatus = new ThreadLocal<Boolean>() {
		@Override
		protected Boolean initialValue() {
			return false;
		};
	};
	
	private final ThreadLocal<ConstructorCall<? extends IsChained>> constructorCall = new ThreadLocal<ConstructorCall<? extends IsChained>>() {
		@Override
		protected ConstructorCall<? extends IsChained> initialValue() {
			return null;
		};
	};
	
	private final ThreadLocal<IsChained> constructorTransactionInitiater = new ThreadLocal<IsChained>() {
		@Override
		protected IsChained initialValue() {
			return null;
		};
	};
	
	private final ThreadLocal<IsChained> poolLocker = new ThreadLocal<IsChained>() {
		@Override
		protected IsChained initialValue() {
			return null;
		};
	};
	
	private final ThreadLocal<Class<? extends IsChained>> poolLockerClass = new ThreadLocal<Class<? extends IsChained>>() {
		@Override
		protected Class<? extends IsChained> initialValue() {
			return null;
		};
	};
	
	
	
	
	static RootHolder recoveryRoot;

	protected Context(Prevayler<RootHolder> prevayler, RootHolder root) {
		synchronized (Context.class) {
			if (instance != null)
				throw new IllegalStateException("an instance already created");
			
			this.prevayler = prevayler;
			this.root = root;
			this.classCache = new ClassCache(root.getClass().getName());
			
			instance = this;
		}
	}
	
	public static final boolean isBound() {
		return (instance != null);
	}
	
	public static final Context getInstance() {
		return instance;
	}
	
	public static final boolean isInRecovery() {
		return (recoveryRoot != null);
	}
	
	public static final RootHolder getRecoveryRoot() {
		return recoveryRoot;
	}
	
	public final boolean isInTransaction() {
		return transactionStatus.get();
	}
	
	public final void setInTransaction(boolean bool) {
		transactionStatus.set(bool);
//		new Exception("-- context setInTransaction: " + bool).printStackTrace(System.out);
	}
	
	public final boolean isInRemoteTransaction() {
		return remoteTransactionStatus.get();
	}
	
	public final void setInRemoteTransaction(boolean bool) {
		remoteTransactionStatus.set(bool);
	}
	
	public final boolean isInQuery() {
		return queryStatus.get();
	}
	
	public final void setInQuery(boolean bool) {
		queryStatus.set(bool);
	}

	public final ConstructorCall<? extends IsChained> getConstructorCall() {
		return constructorCall.get();
	}

	public final void setConstructorCall(ConstructorCall<? extends IsChained> call) {
		constructorCall.set(call);
	}

	public final IsChained getConstructorTransactionInitiater() {
		return constructorTransactionInitiater.get();
	}

	public final void setConstructorTransactionInitiater(IsChained initiater) {
		constructorTransactionInitiater.set(initiater);
	}
	
	public final void maybeEndTransaction(IsChained caller) {
		if (isInTransaction() && (getConstructorTransactionInitiater() == caller)) {
			if (DEBUG) System.out.printf("Context catch, ending transaction for %s \n", Utils.identityCode(caller));
			setInTransaction(false);
		}
	}
	
	public final void maybeEndTransaction(IsChained caller, Class<? extends IsChained> clazz) {
		if (isInTransaction() && (getConstructorTransactionInitiater() == caller)
				&& (getConstructorTransactionInitiater().getClass() == clazz)) {

			if (DEBUG) System.out.printf("Context finally, ending transaction for %s, class: %s \n", Utils.identityCode(caller), clazz);
			setInTransaction(false);
		} else {
			if (DEBUG) System.out.printf("Context finally, NOT ending transaction for %s, class: %s \n", Utils.identityCode(caller), clazz);
		}
	}
	
	public final void lockObjectPool(IsChained caller) {
		poolLock.lock();
		poolLocker.set(caller);
		poolLockerClass.set(caller.getClass());
		if (DEBUG) System.out.printf("Context locked pool for %s, class: %s, thread: %s \n", Utils.identityCode(poolLocker), poolLockerClass, Thread.currentThread());
	}
	
	public final void maybeUnlockObjectPool(IsChained caller) {
		if (poolLock.isLocked() && poolLocker.get() == caller) {
			if (DEBUG) System.out.printf("Context catch, unlocking pool for %s, thread: %s \n", Utils.identityCode(caller), Thread.currentThread());
			poolLock.unlock();
			poolLocker.set(null);
			if (DEBUG) System.out.printf("Context catch, unlocked pool for %s, thread: %s \n", Utils.identityCode(caller), Thread.currentThread());
		} else {
			if (DEBUG) System.out.printf("Context catch, NOT unlocking pool for %s, is locked: %s, thread: %s \n", Utils.identityCode(caller), poolLock.isLocked(), Thread.currentThread());
		}
	}
	
	public final void maybeUnlockObjectPool(IsChained caller, Class<? extends IsChained> clazz) {
		if (poolLock.isLocked() && poolLocker.get() == caller && clazz == poolLockerClass.get()) {
			if (DEBUG) System.out.printf("Context finally, unlocking pool for %s, thread: %s \n", Utils.identityCode(caller), Thread.currentThread());
			poolLock.unlock();
			poolLocker.set(null);
			if (DEBUG) System.out.printf("Context finally, unlocked pool for %s, thread: %s \n", Utils.identityCode(caller), Thread.currentThread());
		} else {
			if (DEBUG) System.out.printf("Context finally, NOT unlocking pool for %s, class %s, is locked: %s, thread: %s \n", Utils.identityCode(caller), clazz, poolLock.isLocked(), Thread.currentThread());
		}
	}
}
