package raft.chainvayler;

/**
 * <p>Thrown if a Chainvayler related operation performed on a @Chained object and object has no id. This typically
 * happens if that object is created before Chainvayler is created (i.e. {@link Chainvayler#create()} is called)
 * and later a @Modification method is called on that object or it is passed as an argument to 
 * another @Chained object's method.</p>
 * 
 * <p>As a rule of thumb, {@link Chainvayler#create()} should be called before any other @Chained 
 * objects are created.</p>
 * 
 * @see Chained
 * @see Chainvayler
 * 
 * @author r a f t
 */
public class NotChainedException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public NotChainedException() {
		super();
	}

	public NotChainedException(String message, Throwable cause) {
		super(message, cause);
	}

	public NotChainedException(String message) {
		super(message);
	}

	public NotChainedException(Throwable cause) {
		super(cause);
	}
	
	

}
