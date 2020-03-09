package raft.chainvayler.impl;

import java.util.HashSet;
import java.util.Set;

import raft.chainvayler.NotCompiledException;
import raft.chainvayler.Chained;

/**
 * 
 * @author r a f t
 */
public class ClassCache {

	
	private final String rootClassName;
	private final Set<Class<?>> knownInstrumentedClasses = new HashSet<Class<?>>();
	
	public ClassCache(String rootClassName) {
		this.rootClassName = rootClassName;
	}

	// no need to synchronize this, at worst case same class is validated more than once
	public void validateClass(Class<?> clazz) throws Exception {
		if (knownInstrumentedClasses.contains(clazz))
			return;
		
		while (true) {
			try {
				String classSuffix = getClassNameForJavaIdentifier(clazz);
				// TODO possibly remove validation for root class, root has no special meaning any more 
				String instrumentationRoot = (String) clazz.getField("__chainvayler_root_" + classSuffix).get(null);
//				if (!rootClassName.equals(instrumentationRoot))
//					throw new NotCompiledException("class " + clazz.getName() + " is not instrumented for root class " + 
//							rootClassName + " but for class " + instrumentationRoot); 

				knownInstrumentedClasses.add(clazz);
			
				clazz = clazz.getSuperclass();
				if (clazz == null)
					break;
				if (clazz.getAnnotation(Chained.class) == null) 
					break;
				
			} catch (NoSuchFieldException e) {
				throw new NotCompiledException(clazz.getName(), e);
			}
		}
	}
	
	private static String getClassNameForJavaIdentifier(Class<?> clazz) {
		return clazz.getName().replace('.', '_').replace('$', '_');
	}
	
}
