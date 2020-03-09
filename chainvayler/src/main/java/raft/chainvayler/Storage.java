package raft.chainvayler;

import java.io.File;

import org.prevayler.Prevayler;


/**
 * <p>Handle to persistent storage. After compilation root class can be casted to this interface.</p> 
 * 
 * @author r a f t
 */
public interface Storage {
	
	/** 
	 * Takes Prevayler snapshot.
	 * 
	 * @see Prevayler#takeSnapshot()
	 * */
	public File takeSnapshot() throws Exception;
}
