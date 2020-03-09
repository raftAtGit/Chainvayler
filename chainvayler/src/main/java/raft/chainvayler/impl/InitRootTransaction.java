package raft.chainvayler.impl;

import java.util.Date;

import org.prevayler.TransactionWithQuery;

// TODO remove
public class InitRootTransaction implements TransactionWithQuery<RootHolder, Void> {

	private static final long serialVersionUID = 1L;

	private final Class<? extends IsChained> chainedRootClass;
	
	public InitRootTransaction(Class<? extends IsChained> chainedRootClass) {
		this.chainedRootClass = chainedRootClass;
	}

	@Override
	public Void executeAndQuery(RootHolder root, Date date) throws Exception {
		if (!Context.isBound()) 
			Context.recoveryRoot = root;
		else Context.getInstance().setInTransaction(true);
		
		ClockBase.setDate(date);
		try {
			IsChained chainedRoot = chainedRootClass.newInstance();
			root.setRoot(chainedRoot);
			return null;
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		} finally {
			if (Context.isBound()) 
				Context.getInstance().setInTransaction(false);
			ClockBase.setDate(null);
		}
	}

}
