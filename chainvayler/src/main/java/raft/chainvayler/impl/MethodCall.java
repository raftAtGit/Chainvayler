package raft.chainvayler.impl;

import java.io.Serializable;


/**
 * A <code>Serializable</code> representation of a
 * <code>Method</code>.
 *
 * @since 0_1
 * @author Jay Sachs [jay@contravariant.org]
 * @author raft
 */
public class MethodCall implements Serializable {
    private static final long serialVersionUID = 1;

    private final String name;
    private final String className;
    private final String[] argTypes;
    
    public MethodCall(java.lang.reflect.Method method) {
    	this(method.getName(), method.getDeclaringClass().getName(), Utils.getTypeNames(method.getParameterTypes()));
    }

    public MethodCall(String name, Class<?> clazz, Class<?>[] argTypes) {
    	this(name, clazz.getName(), Utils.getTypeNames(argTypes));
    }
    
    public MethodCall(String name, String className, String[] argTypes) {
		this.name = name;
		this.className = className;
        this.argTypes = argTypes;
	}

    /** reconstructs the wrapped java method */
    public java.lang.reflect.Method getJavaMethod() {
    	
    	try {
	        Class<?> [] args = new Class[argTypes.length];
	        for (int i = 0; i < args.length; ++i) {
	            args[i] = Utils.getClass(argTypes[i]);
	        }
	        return Class.forName(className).getDeclaredMethod(name, args);
    	} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
    	}
    }


}