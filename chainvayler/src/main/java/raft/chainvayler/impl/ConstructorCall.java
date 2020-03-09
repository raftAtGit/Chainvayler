package raft.chainvayler.impl;

import java.io.Serializable;
import java.lang.reflect.Constructor;


/**
 * @author raft
 */
public class ConstructorCall<T> implements Serializable {
    private static final long serialVersionUID = 1;

    private final String className;
    private final String[] argTypes;
	final Object[] arguments;
    
    public ConstructorCall(Class<? extends T> clazz, Class<?>[] argTypes, Object[] arguments) {
    	this(clazz.getName(), Utils.getTypeNames(argTypes), arguments);
    }
    
    public ConstructorCall(String className, String[] argTypes, Object[] arguments) {
		this.className = className;
        this.argTypes = argTypes;
		this.arguments = Utils.referenceArguments(arguments);
	}

    public T newInstance(RootHolder root) throws Exception {
		Constructor<T> cons = getJavaConstructor();
		cons.setAccessible(true);
		return cons.newInstance(Utils.dereferenceArguments(root, arguments));
    }
    
    /** reconstructs the wrapped java Constructor */
    @SuppressWarnings("unchecked")
	Constructor<T> getJavaConstructor() {
    	
    	try {
	        Class<?> [] args = new Class[argTypes.length];
	        for (int i = 0; i < args.length; ++i) {
	            args[i] = Utils.getClass(argTypes[i]);
	        }
	        return (Constructor<T>) Class.forName(className).getDeclaredConstructor(args);
    	} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
    	}
    }
    

}