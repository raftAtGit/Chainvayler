package raft.chainvayler;

/**
 * <p>Thrown if a @Chained class is not correctly instrumented. Class maybe either 
 * not instrumented at all or instrumented for a different Root class other than current 
 * {@link Chainvayler}'s root.</p>
 * 
 * <p>Note, if there is no Chainvayler context around (i.e. {@link Chainvayler#create()} not called),
 * this exception is never thrown and @Chained classes behave like they are not instrumented at all.</p>
 * 
 * @see Chained
 * @see Chainvayler
 * 
 * @author r a f t
 */
public class NotCompiledException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public NotCompiledException() {
		super();
	}

	public NotCompiledException(String message, Throwable cause) {
		super(message, cause);
	}

	public NotCompiledException(String message) {
		super(message);
	}

	public NotCompiledException(Throwable cause) {
		super(cause);
	}
	
	

}
