package raft.chainvayler.impl;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Date;

import org.prevayler.TransactionWithQuery;

import raft.chainvayler.NotChainedException;

/**
 * 
 * @author  hakan eryargi (r a f t)
 */
public class MethodTransactionWithQuery<R> implements TransactionWithQuery<RootHolder, R> {

	private static final long serialVersionUID = 1L;

	private final Long targetId;
	private final MethodCall method;
	private final Object[] arguments;
	
	/** 
	 * we keep a transient reference to our target. GCPreventingPrevayler keeps a reference to this Transaction. these two references 
	 * safely prevents garbage collector cleaning our target before we are done
	 * 
	 * @see GCPreventingPrevayler
	 */  
	@SuppressWarnings("unused")
	private transient IsChained transientTarget;
	@SuppressWarnings("unused")
	private transient Object[] transientArguments;

	public MethodTransactionWithQuery(IsChained target, MethodCall method, Object[] arguments) {
		this.transientTarget = target;
		this.transientArguments = arguments;
		
		this.targetId = target.__chainvayler_getId();
		if (targetId == null)
			throw new NotChainedException("object has no id, did you create this object before Chainvayler is created?\n" + Utils.identityCode(target));
		this.method = method;
		this.arguments = Utils.referenceArguments(arguments);
	}

	@SuppressWarnings("unchecked")
	@Override
	public R executeAndQuery(RootHolder root, Date date) throws Exception {
		if (!Context.isBound()) Context.recoveryRoot = root;
		ClockBase.setDate(date);
		
		Object[] origArguments = Arrays.copyOf(arguments, arguments.length);
		try {
			IsChained target = root.getObject(targetId);
			
			if (target == null) {
				// System.out.println(root.printPool());
				throw new Error(String.format("couldnt get object from the pool, id: %s for method %s", targetId, method.getJavaMethod())); // we throw error to halt Prevayler
			}
			Method m = method.getJavaMethod();
			m.setAccessible(true);
			return (R) m.invoke(target, Utils.dereferenceArguments(root, arguments));
		} catch (RuntimeException e) {
			e.printStackTrace();
			System.out.printf("%s, targetId: %s, target: %s, args: %s, deref-args: %s \n", method.getJavaMethod(), targetId, 
					root.getObject(targetId), Arrays.toString(origArguments), Arrays.toString(Utils.dereferenceArguments(root, arguments)));
			throw e;
		} catch (Exception e) {
			e.printStackTrace();
			System.out.printf("%s, targetId: %s, target: %s, args: %s, deref-args: %s \n", method.getJavaMethod(), targetId, 
					root.getObject(targetId), Arrays.toString(origArguments), Arrays.toString(Utils.dereferenceArguments(root, arguments)));
			throw new RuntimeException(e);
		} finally {
			Context.recoveryRoot = null;
			ClockBase.setDate(null);
		}
	}


	@Override
	public String toString() {
		return String.format("MethodTransactionWithQuery targetId: %s, method: %s, arguments: %s", targetId, method.getJavaMethod(), Arrays.toString(arguments));
	}
	
}
