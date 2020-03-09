package raft.chainvayler.compiler;

// TODO find a better name
public class CompileException extends RuntimeException  {

	private static final long serialVersionUID = 1L;
	
	public CompileException() {
		super();
	}
	
	public CompileException(String message) {
		super(message);
	}

	public CompileException(String message, Throwable cause) {
		super(message, cause);
	}

	public CompileException(Throwable cause) {
		super(cause);
	}

	
}
