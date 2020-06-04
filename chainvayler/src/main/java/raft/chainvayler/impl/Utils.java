package raft.chainvayler.impl;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 
 * @author  hakan eryargi (r a f t)
 */
public class Utils {

	static boolean doParametersMatch(Class<?>[] paramTypes, Object[] values) {
		if (paramTypes.length != values.length)
			return false;
		
		for (int i = 0; i < paramTypes.length; i++) {
			if (values[i] == null)
				continue;
			if (!paramTypes[i].isAssignableFrom(values[i].getClass()))
				return false;
		}
		return true;
	}
	
	/** replaces {@link IsChained} arguments with {@link Reference} */
	static Object[] referenceArguments(Object[] arguments) {
		Object[] result = new Object[arguments.length];
		
		for (int i = 0; i < arguments.length; i++) {
			Object arg = arguments[i];
			if (arg instanceof IsChained) {
				IsChained chained = (IsChained) arg;
				result[i] = new Reference(chained);
			} else {
				result[i] = arg;
			}
		}
		return result;
	}
	
	/** replaces {@link Reference} arguments with {@link IsChained} */
	static Object[] dereferenceArguments(RootHolder root, Object[] arguments) {
		for (int i = 0; i < arguments.length; i++) {
			Object arg = arguments[i];
			if (arg instanceof Reference) {
				Long id = ((Reference)arg).id;
				IsChained chained = root.getObject(id);
				if (chained == null)
					throw new Error("couldnt get object from the pool, id: " + id); // we throw error to halt Prevayler
				arguments[i] = chained;
			}
		}
		return arguments;
	}
	
	public static String identityCode(Object object) {
		return object.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(object));
	}
	
    static String[] getTypeNames(Class<?>[] types) {
    	String[] names = new String[types.length];
    	
        for (int i = 0; i < types.length; ++i) {
        	names[i] = types[i].getName();
        }
        return names;
    }
    
    static Class<?> getClass(String name) throws ClassNotFoundException {
    	if (boolean.class.getName().equals(name)) {
    		return boolean.class; 
    	} else if (char.class.getName().equals(name)) {
    		return char.class; 
    	} else if (int.class.getName().equals(name)) {
    		return int.class; 
    	} else if (long.class.getName().equals(name)) {
    		return long.class; 
    	} else if (float.class.getName().equals(name)) {
    		return float.class; 
    	} else if (double.class.getName().equals(name)) {
    		return double.class; 
    	} else {
    		return Class.forName(name);
    	} 
    }

	@SuppressWarnings("unchecked")
	public static <T> T getDeclaredFieldValue(String fieldName, Object o) throws Exception {
		return (T) getDeclaredField(fieldName, o).get(o);
	}

	public static Field getDeclaredField(String fieldName, Object o) throws Exception {
		Field field = o.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		return field;
	}
    
	public static Method getDeclaredMethod(String methodName, Object o, Class<?>... parameterTypes) throws Exception {
		Method method = o.getClass().getDeclaredMethod(methodName, parameterTypes);
		method.setAccessible(true);
		return method;
	}
}
