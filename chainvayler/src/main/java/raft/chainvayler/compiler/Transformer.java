package raft.chainvayler.compiler;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;

import javassist.CtClass;

/** {@link ClassFileTransformer} for load time weaving. This is a proof of concept implementation, works as it is,
 * but cannot cooperate with other agents at the moment*/
public class Transformer implements ClassFileTransformer {
	 
	private final Compiler compiler;
	private final Tree tree;

	private CtClass contextClass;
	private Map<String, CtClass> cachedClasses = new HashMap<String, CtClass>();
	
	public Transformer(String rootClassName) throws Exception {
		this.compiler = new Compiler(rootClassName); 
		this.tree = compiler.getTree();
	}

	@Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                  ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {

		// sometime happens, possibly some synthetic class created by Spring?
		if (className == null)
			return null;
		
		// Tomcat's classloader triggers ClassFileTransformer while a new class is created via Javaassist 
		// which results in ClassCircularityError. so avoid any operation if compiler is in createContextClass method
    	if (compiler.inCreateContextClassMethod) {
//    		System.out.println("warning increateContextClass");
    		return null;
    	}
    	
    	try {
	    	if (this.contextClass == null) {
	    		this.contextClass = compiler.createContextClass();
	    		
	    		// TODO is this enough for web apps?
	    		contextClass.toClass(loader, protectionDomain);
	    		System.out.println("created context class: " + contextClass);
	    	}
	    	// TODO load class via ByteArrayClassPath into javaassist and make modifications
	
	    	//System.out.println("transform: " + className);

	    	// class name in another format here (like java/util/List), transform it
	    	className = className.replace('/', '.');
	    	
	    	Node node = tree.findNode(className);
	    	if (node == null) {
	    		// none of our business, class is not in chained class hierarchy, just omit it
	    		return null;
	    	}
	    	
	    	System.out.println("--instrumenting " + className);
	    	
	    	CtClass cached = cachedClasses.get(className);
	    	if (cached != null) {
	    		System.out.println("class is already instrumented and cached, returning it: " + className);
	    		return cached.toBytecode();
	    	}
	    	
    		// TODO replace CtClass of node here, with given classfileBuffer.
    		// o/w we will override other transformer's modifications
	    	
	    	boolean isTopLevelClass = tree.roots.containsKey(className);
	    	if (isTopLevelClass) {
	    		System.out.println("class " + className + " is top level, injecting IsChained");
	    		node = tree.roots.get(className);
	    		compiler.injectIsChained(node);
	    	} else {
	    		Node topLevelNode = node.getTopLevelNode();
	    		if (cachedClasses.containsKey(topLevelNode.clazz.getName())) {
		    		System.out.println("class " + className + " is NOT top level, and its top level parent " + topLevelNode.clazz.getName() + " is already instrumented, skipping it");
	    		} else {
		    		System.out.println("class " + className + " is NOT top level, injecting IsChained to its top level parent " + topLevelNode.clazz.getName());
		    		compiler.injectIsChained(topLevelNode);
		    		compiler.instrumentNode(topLevelNode);
		    		cachedClasses.put(topLevelNode.clazz.getName(), topLevelNode.clazz);
	    		}
	    	}
    		CtClass clazz = compiler.instrumentNode(node);
	    	return clazz.toBytecode();
	    	
    	} catch (Exception e) {
    		e.printStackTrace();
    		throw new RuntimeException(e);
    	}
    }
    

}