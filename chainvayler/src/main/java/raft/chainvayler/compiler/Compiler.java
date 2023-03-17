package raft.chainvayler.compiler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.AccessFlag;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.FieldInfo;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.SignatureAttribute;
import javassist.bytecode.SyntheticAttribute;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.ArrayMemberValue;
import javassist.bytecode.annotation.ClassMemberValue;
import javassist.bytecode.annotation.MemberValue;
import raft.chainvayler.Chained;
import raft.chainvayler.Include;
import raft.chainvayler.Modification;
import raft.chainvayler.Synch;
import raft.chainvayler.impl.Context;
import raft.chainvayler.impl.IsChained;
import raft.chainvayler.impl.RootHolder;

/**
 * Chainvayler compiler. Works on <strong>javac</strong> compiled bytecode.
 * 
 * @author r a f t
 */
public class Compiler {

	// TODO somehow must check if a class is already enhanced
	
	private static final boolean DEBUG = false;
	
	private static final Set<String> SKIP_PACKAGES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
			"raft.chainvayler.impl"
			))); 
	
	public static void main(String[] args) throws Exception {
		String rootClassName = args[0];
		
		Compiler compiler = new Compiler(rootClassName);
		compiler.run();
	}
	
	private final String rootClassName;
	private final Tree tree;
	
	private boolean scanSubPackages = false; 
	
	
//	private final String outputFolder;
	private CtClass contextClass;
	
	private final ClassPool classPool;
	
	private final Set<String> scannedClasses = new HashSet<String>();
	private final Set<String> scannedPackages = new HashSet<String>();
	private final Map<String, CtClass> packageQueue = new LinkedHashMap<String, CtClass>();

	
	/** 
	 * @param writeClasses if true classes will be written to same location they are read. 
	 * this only works if they are loaded from directory (as opposed to jar)  
	 * 
	 * */
	public Compiler(String rootClassName) throws Exception {
		this.rootClassName = rootClassName; 
		
		this.classPool = new ClassPool(true);
		classPool.insertClassPath(new ClassClassPath(getClass()));
		
		classPool.importPackage("raft.chainvayler");
		classPool.importPackage("raft.chainvayler.impl");

		CtClass rootClass = classPool.get(rootClassName);
		if (rootClass.getAnnotation(Chained.class) == null)
			throw new CompileException("root class " + rootClassName + " is not annotated with @Chained");
		
		if (rootClass.isInterface())
			throw new CompileException("root class " + rootClassName + " is an Interface: rootClass");
		if (Modifier.isAbstract(rootClass.getModifiers())) 
			throw new CompileException("root class is Abstract: " + rootClassName);
	
		if (rootClass.getPackageName() == null)
			throw new CompileException("@Chained classes in default package is not supported " + rootClass.getName());
		
		this.tree = createTree(rootClass);
		
		System.out.println("-- scanned classes --");
		for (String scanned : scannedClasses)
			System.out.println(scanned);

		System.out.println("-- tree --");
		tree.print(System.out);
		System.out.println("----");
		
		for (Node rootNode : tree.roots.values()) {
			checkCorrectlyInstrumented(rootNode);
		}
	}
	
	/** does the actual instrumentation. creates Chainvayler context class and modifies chained classes */
	public void run() throws Exception {
		createContextClass();
		writeContextClass();
		
		instrumentTree(tree);
	}

	Tree getTree() {
		return tree;
	}

	ClassPool getClassPool() {
		return classPool;
	}

	boolean inCreateContextClassMethod = false;
	
	CtClass createContextClass() throws Exception {
		// TODO we need better encapsulation for Chainvayler and inTransaction fields

		// Tomcat's classloader triggers ClassFileTransformer while a new class is created via Javaassist 
		// which results in ClassCircularityError. so mark that we are inside createContextClass() method and avoid 
		// any operation in ClassFileTransformer
		inCreateContextClassMethod = true;
		try {
			CtClass rootClazz = classPool.get(rootClassName);
			contextClass = classPool.makeClass(rootClazz.getPackageName() + ".__Chainvayler");
			contextClass.setSuperclass(classPool.get(Context.class.getName()));
			
			contextClass.addField(CtField.make("public static final Class rootClass = " + rootClassName + ".class;", contextClass));
			contextClass.addConstructor(CtNewConstructor.make(createSource("Context.init.java.txt", rootClassName), contextClass));
			
			return contextClass;
		} finally {
			inCreateContextClassMethod = false;
		}
	}

	String getContextClassName() throws Exception {
		CtClass rootClazz = classPool.get(rootClassName);
		return rootClazz.getPackageName() + ".__Chainvayler";
	}
	
	private void writeContextClass() throws Exception {
		CtClass rootClazz = classPool.get(rootClassName);
		contextClass.writeFile(getClassWriteDirectory(rootClazz));
	}

	
	private Tree createTree(CtClass rootClass) throws Exception {
		Tree tree = new Tree();
		
		scanPackage(tree, rootClass);
		tree.findNode(rootClassName).isRoot = true;
		
		while (!packageQueue.isEmpty()) {
			String nextPackage = packageQueue.keySet().iterator().next(); 
			if (SKIP_PACKAGES.contains(nextPackage)) {
				System.out.println("-skipping package " + nextPackage);
				packageQueue.remove(nextPackage);
			} else {
				scanPackage(tree, packageQueue.remove(nextPackage));
			}
		}
		
		return tree;
	}
	/** @param clazz a class in package, required to locate package location */
	private void scanPackage(Tree tree, CtClass clazz) throws Exception {
		System.out.println("-scanning package " + clazz.getPackageName());
		scannedPackages.add(clazz.getPackageName());
		
		List<CtClass> packageClasses = getPackageClasses(clazz);
		
		for (CtClass cls : packageClasses) {
			if (cls.isInterface())
				continue;
			if (!isChained(cls)) {
				warnIfHasUnusedAnnotations(cls);
				continue;
			}

			List<CtClass> hierarchy = tree.add(cls);
			
			for (CtClass hClass : hierarchy) {
				// TODO check class is not a inner class. inner classes cannot be created via reflection (sure?)
				scanClass(hClass);
			}
		}
	}	
	
	private void scanClass(CtClass clazz) throws Exception {
		scannedClasses.add(clazz.getName());

		String packageName = clazz.getPackageName();
		if (!scannedPackages.contains(packageName))
			packageQueue.put(packageName, clazz);
		
		for (Object ref : clazz.getRefClasses()) {
			CtClass refClass = classPool.get((String)ref);
			String refPackage = refClass.getPackageName();
			
			if (!scannedPackages.contains(refPackage))
				packageQueue.put(refPackage, refClass);
		}
		
		if (clazz.hasAnnotation(Include.class)) {
			AnnotationsAttribute annotations = (AnnotationsAttribute) clazz.getClassFile().getAttribute(AnnotationsAttribute.visibleTag);
			Annotation annotation = annotations.getAnnotation(Include.class.getName());
			System.out.println(annotation);
			ArrayMemberValue memberValue = (ArrayMemberValue) annotation.getMemberValue("value");
			MemberValue[] includedClassNames = memberValue.getValue();
			System.out.println(Arrays.asList(includedClassNames));
			
			for (MemberValue value : includedClassNames) {
				String includedClassName = ((ClassMemberValue)value).getValue();
				CtClass includedClass = classPool.get(includedClassName);
				String includedPackage = includedClass.getPackageName();
				if (!scannedPackages.contains(includedPackage))
					packageQueue.put(includedPackage, includedClass);
				
			}
			// this doesnt work with javaagent. since class itself is already loaded in this scenario. 
			// we need to access annotation in javaassist way as above  
//			Include include = (Include) clazz.getAnnotation(Include.class);
//			for (Class<?> cls : include.value()) {
//				String includedPackage = cls.getPackage().getName();
//				if (!scannedPackages.contains(includedPackage))
//					packageQueue.put(includedPackage, classPool.get(cls.getName()));
//			}
		}
		
		
		scanFields(clazz);
		
		String genericSignature = clazz.getGenericSignature();
		if (genericSignature != null) {
			SignatureAttribute.ClassSignature classSignature = SignatureAttribute.toClassSignature(genericSignature);
			for (SignatureAttribute.TypeParameter parameter : classSignature.getParameters()) {
				// TODO what?
				scanObjectType(parameter.getClassBound());
			}
		}
	}
	
	/** scans class' fields recursively */
	private void scanFields(CtClass clazz) throws Exception {
		
		for (CtField field : clazz.getDeclaredFields()) {

			if (Modifier.isStatic(field.getModifiers())) {
//				checkStaticFieldAnnotations(field);
				continue;
			}

			CtClass fieldClass = field.getType();
			scanField(clazz, fieldClass);
			
			String genericSignature = field.getGenericSignature();
			if (genericSignature != null) {
				scanObjectType(SignatureAttribute.toFieldSignature(genericSignature));
			}
		}
	}
	
		
	private void scanObjectType(SignatureAttribute.ObjectType objectType) throws Exception {
		if (objectType instanceof SignatureAttribute.ClassType) {
			SignatureAttribute.ClassType classType = (SignatureAttribute.ClassType) objectType;
			if (!scannedClasses.contains(classType.getName())) {
				scanClass(classPool.get(classType.getName()));
			}  
			SignatureAttribute.TypeArgument[] typeArguments = classType.getTypeArguments();
			if (typeArguments== null)
				return;
			
			for (SignatureAttribute.TypeArgument typeArgument : typeArguments) {
				SignatureAttribute.ObjectType type = typeArgument.getType();
				if (type == null) {
					System.out.println("warning, couldnt determine type arguments of field " + objectType);
					continue;
				}
				scanObjectType(type);
			}			
		} else if (objectType instanceof SignatureAttribute.ArrayType) {
			SignatureAttribute.ArrayType arrayType = (SignatureAttribute.ArrayType) objectType;
			SignatureAttribute.Type compType = arrayType.getComponentType();
			if (compType instanceof SignatureAttribute.BaseType) {
				// primitive
				return;
			}
			if (compType instanceof SignatureAttribute.ObjectType) {
				scanObjectType((SignatureAttribute.ObjectType) compType);
			}
		} else if (objectType instanceof SignatureAttribute.TypeVariable) {
			// none of out business
		} else {
			assert false : objectType;
		}
	}

	private void scanField(CtClass declaringClass, CtClass fieldClass) throws Exception {
		if (fieldClass.isPrimitive() || scannedClasses.contains(fieldClass.getName())) {
			return;
		}

		scannedClasses.add(fieldClass.getName());
				
		String fieldPackage = fieldClass.getPackageName(); 
		if (fieldPackage == null) {
			if (isChained(fieldClass))
				throw new CompileException("@Chained classes in default package is not supported, " + fieldClass.getName() 
						+ " @ " + declaringClass.getName() + "." + declaringClass.getName());
		} else {
			if (!scannedPackages.contains(fieldPackage))
				packageQueue.put(fieldPackage, fieldClass);
		} 
		
		if (fieldClass.isArray()) {
			CtClass arrayClass = fieldClass;
			while (arrayClass.isArray()) {
				arrayClass = arrayClass.getComponentType();
			}
			scanField(declaringClass, arrayClass);
		}
	}	

	private void instrumentTree(Tree tree) throws Exception {
		// cleans previously injected code
//		for (Node rootNode : tree.roots.values()) {
//			clean(rootNode);
//		}
		
		for (Node rootNode : tree.roots.values()) {
			
			// inject interfaces to top level classes
			injectIsChained(rootNode);
			instrumentNodeRecursively(rootNode);
		}
	}
	
	/** inject {@link RootHolder} interface and related fields to implement it */
	private void injectIsRoot(Node node) throws Exception {
		CtClass clazz = node.clazz;
		
//		// TODO better move this functionality to Chainvayler class? 
//		String source = createSource("IsRoot.takeSnapshot.java.txt", contextClass.getName());
//		clazz.addMethod(CtNewMethod.make(source, clazz));
//		System.out.println("added public File takeSnapshot() method to " + clazz.getName());
	}

	/** inject {@link IsChained} interface and related fields to implement it */
	void injectIsChained(Node node) throws Exception {
		if (node.compiled)
			return;
		
		CtClass clazz = node.clazz;
		
		clazz.addInterface(classPool.get(IsChained.class.getName()));
		System.out.println("added IsChained interface to " + clazz.getName());
		
		clazz.addField(makeSynthetic(CtField.make("private final Long __chainvayler_Id;", clazz)));
		System.out.println("added Long __chainvaylerId field to " + clazz.getName());
		
		// implement the IsChained interface
		clazz.addMethod(CtNewMethod.make("public final Long __chainvayler_getId() { return __chainvayler_Id;}", clazz));
		System.out.println("added Long __chainvayler_getId() method to " + clazz.getName());

		String validateSource = createSource("IsChained.init.validateClass.java.txt", contextClass.getName());
		
		// add code to validate runtime type
		for (CtConstructor constructor : clazz.getDeclaredConstructors()) {
			// optimization: there is a call to this(constructor), omit validate call  
			if (!constructor.callsSuper())
				continue;
			constructor.insertAfter(validateSource);
			System.out.println("added validateClass call to " + constructor.getLongName());
		}
	}

	CtClass instrumentNode(Node node) throws Exception {
		if (node.compiled)
			return node.clazz;
		
		CtClass clazz = node.clazz;
		
		// add a static final field for Root class to mark this class as instrumented
		String classSuffix = getClassNameForJavaIdentifier(clazz.getName());
		clazz.addField(CtField.make("public static final String __chainvayler_root_" + classSuffix + " = \"" + rootClassName + "\";", clazz));
		
		if (node.isRoot) {
			injectIsRoot(node);
		}
		
		for (CtConstructor constructor : clazz.getDeclaredConstructors()) {
			constructor.insertBefore(createSource("IsChained.init.initTransaction.java.txt", contextClass.getName(), clazz.getName()));
			System.out.println("added initTransaction to " + constructor.getLongName());
			
			constructor.addCatch(createSource("IsChained.init.catch.java.txt", contextClass.getName(), clazz.getName()), classPool.get(Throwable.class.getName()), "$e");
			System.out.println("added catch to " + constructor.getLongName());
			
			// TODO maybe some optimization here
			// skip if calls this(constructor)?
			constructor.insertAfter(createSource("IsChained.init.finally.java.txt", contextClass.getName(), clazz.getName()), true); // as finally
			System.out.printf("added finally to %s, class: %s \n", constructor.getLongName(), clazz.getName());
//			System.out.println(createSource("IsChained.init.finally.java.txt", contextClass.getName(), clazz.getName()));
		}
		
		if (node.isTopLevel()) {
			String initSource = createSource("IsChained.init.putToPool.java.txt", contextClass.getName(), clazz.getName());
			
			for (CtConstructor constructor : clazz.getDeclaredConstructors()) {
				// optimization: there is a call to this(constructor), omit put to pool code 
				if (!constructor.callsSuper())
					continue;
				
				constructor.insertBeforeBody(initSource);
				System.out.println("added add to pool call to " + constructor.getLongName());
			}
		}
		
		for (CtMethod method : clazz.getDeclaredMethods()) {
			System.out.println("method: " + method);

			if (method.hasAnnotation(Modification.class)) {
				createTransaction(method);
			}

			if (method.hasAnnotation(Synch.class)) {
				createSynch(method);
			}
		}
		return clazz;
	}
	
	/** modifies constructors, {@link Modification} and {@link Synch} methods. recursively descents into subclasses  in tree.
	 * also writes modified class file to disk. */
	private void instrumentNodeRecursively(Node node) throws Exception {
		if (node.compiled) {
			System.out.println("class is already instrumented, skipping " + node.clazz.getName());
			return;
		}
		
		CtClass clazz = instrumentNode(node);
		
		String directory = getClassWriteDirectory(clazz);
		System.out.println("writing class " + clazz.getName() + " to " + directory);
		clazz.writeFile(directory);
		
		for (Node subNode : node.subClasses.values()) {
			instrumentNodeRecursively(subNode);
		}
	}

	/** cleans tree recursively */
	private void clean(Node node) throws Exception {
		cleanClass(node.clazz);
		
		for (Node sub : node.subClasses.values()) {
			clean(sub);
		}
	}
	
	/** recursively checks tree if it's already instrumented for same root 
	* TODO a more detailed check is necessary I suppose
	 * */
	private void checkCorrectlyInstrumented(Node node) throws Exception {
		String classSuffix = getClassNameForJavaIdentifier(node.clazz.getName());
		try {
			String fieldName = "__chainvayler_root_" + classSuffix;
			CtField field = node.clazz.getField(fieldName);
			if (field.getType() != classPool.get(String.class.getName())) 
				throw new CompileException("Unexpected type " + field.getType().getName() + " of field " + fieldName + " @ " + node.clazz.getName());
			String value = (String) field.getConstantValue();
			if (!rootClassName.equals(value)) 
				throw new CompileException("Class " + node.clazz.getName() + " is compiled for another root " + value);
			node.compiled = true;
		} catch (NotFoundException e) {
			// ok not instrumented
		}
		
		for (Node sub : node.subClasses.values()) {
			checkCorrectlyInstrumented(sub);
		}
	}
	
	
	/** removes all existing instrumentation by Chainvayler. this is necessary to allow running compiler on same classes again */
	private void cleanClass(CtClass clazz) throws Exception {
		for (CtField field : clazz.getDeclaredFields()) {
			if (field.getName().startsWith("__chainvayler_")) {
				clazz.removeField(field);
				System.out.println("removed old field " + clazz.getName() + "." + field.getName());
			}
		}
		// TODO: a serious flaw here: when compiler is re-run on same class and previously added @Modification and @Synch methods are removed,
		// original methods are lost!!    
		for (CtMethod method : clazz.getDeclaredMethods()) {
			if (method.getName().startsWith("__chainvayler_")) {
				System.out.println(method.getMethodInfo().getAttribute(SyntheticAttribute.tag));
				clazz.removeMethod(method);
				System.out.println("removed old method " + method.getLongName());
			}
		}
		try {
			CtMethod method = clazz.getMethod("takeSnapshot", "()Ljava/io/File;");
			clazz.removeMethod(method);
			System.out.println("removed old method " + method.getLongName());
		} catch (NotFoundException e) {}
		
		List<CtClass> interfaces = new ArrayList<CtClass>(Arrays.asList(clazz.getInterfaces()));
		for (Iterator<CtClass> i = interfaces.iterator(); i.hasNext();) {
			CtClass intf = i.next();
			if (intf.getName().equals(IsChained.class.getName()) 
					|| intf.getName().equals(RootHolder.class.getName())) {
				i.remove();
			}
		}
		clazz.setInterfaces(interfaces.toArray(new CtClass[0]));
		
		// TODO removing a field does not remove its Initializer, so if compiler is called many times, 
		// __chainvayler_Id field may be initialized many times with different values,  but this is not a problem 
		// see https://issues.jboss.org/browse/JASSIST-140
	}

	private void warnIfHasUnusedAnnotations(CtClass clazz) throws Exception {
		assert (!isChained(clazz));
		
		if (clazz.hasAnnotation(Include.class)) {
			warning("class {0} has @Include annotation but not @Chained itself. @Include will not be processed!", clazz.getName());
		}
		
		for (CtMethod method : clazz.getDeclaredMethods()) {
			if (method.hasAnnotation(Modification.class)) {
				warning("class {0} has @Modification method {1} but not @Chained itself. It will not be instrumented!", clazz.getName(), method.getSignature());
			}

			if (method.hasAnnotation(Synch.class)) {
				warning("class {0} has @Synch method {1} but not @Chained itself. It will not be instrumented!", clazz.getName(), method.getSignature());
			}
		}
	}

	
	/** checks if given class or its Chained superclasses implements the given interface.
	 * that is, even if super class implements interface but not Chained, this method will return false;  
	 * */
	private boolean implementedInterface(CtClass clazz, Class<?> interfaceClass) throws Exception {
		assert (clazz.getAnnotation(Chained.class) != null);
		
		while (clazz != null) {
			if (clazz.getAnnotation(Chained.class) == null)
				break;
			for (CtClass interfce : clazz.getInterfaces()) {
				if (interfce.getName().equals(interfaceClass.getName())) {
					return true;
				}
			}
			clazz = clazz.getSuperclass();
		}
		return false;
	}

	private void createTransaction(CtMethod method) throws Exception {
		if (method.getAnnotation(Synch.class) != null)
			throw new CompileException("@Synch and @Modification cannot be on the same method" + method.getLongName());
		if (Modifier.isAbstract(method.getModifiers())) 
			throw new CompileException("abstract method cannot be @Modification " + method.getLongName());

		CtMethod copy = CtNewMethod.copy(method, method.getDeclaringClass(), null);
		String newName = "__chainvayler__" + method.getName();
		copy.setName(newName);
		makeSynthetic(copy);
		makePrivate(copy);
		method.getDeclaringClass().addMethod(copy);
		System.out.println("renamed " + method.getLongName() + " to " + copy.getLongName());
		
		CtClass returnType = method.getReturnType();
				
		final String source; 
		if (returnType == CtClass.voidType) { // no return type
			source = createSource("IsChained.transaction.java.txt", contextClass.getName(), method.getName()); 
		} else {
			if (returnType.isPrimitive()) {
				source = createSource("IsChained.transactionWithQueryUnboxing.java.txt", contextClass.getName(), 
						method.getName(), getBoxingType(returnType), getUnboxingMethod(returnType)); 
			} else {
				source = createSource("IsChained.transactionWithQuery.java.txt", contextClass.getName(), 
						method.getName(), returnType.getName()); 
			}
		}
				
		method.setBody(source);
		method.getMethodInfo().rebuildStackMap(classPool);
		
		// TODO remove @Modification? from both source and copy?
	}

	private String getBoxingType(CtClass primitive) {
		if (primitive == CtClass.booleanType)
			return Boolean.class.getName();
		if (primitive == CtClass.byteType)
			return Byte.class.getName();
		if (primitive == CtClass.charType)
			return Character.class.getName();
		if (primitive == CtClass.doubleType)
			return Double.class.getName();
		if (primitive == CtClass.floatType)
			return Float.class.getName();
		if (primitive == CtClass.intType)
			return Integer.class.getName();
		if (primitive == CtClass.longType)
			return Long.class.getName();
		if (primitive == CtClass.shortType)
			return Short.class.getName();
		
		throw new AssertionError("unknown primitive: " + primitive.getName());
	}

	
	private String getUnboxingMethod(CtClass primitive) {
		
		if (primitive == CtClass.booleanType)
			return "booleanValue()";
		if (primitive == CtClass.byteType)
			return "byteValue()";
		if (primitive == CtClass.charType)
			return "charValue()";
		if (primitive == CtClass.doubleType)
			return "doubleValue()";
		if (primitive == CtClass.floatType)
			return "floatValue()";
		if (primitive == CtClass.intType)
			return "intValue()";
		if (primitive == CtClass.longType)
			return "longValue()";
		if (primitive == CtClass.shortType)
			return "shortValue()";
		
		throw new AssertionError("unknown primitive: " + primitive.getName());
	}
	
	private void createSynch(CtMethod method) throws Exception {
		if (method.getAnnotation(Modification.class) != null)
			throw new CompileException("@Synch and @Modification cannot be on same method" + method.getLongName());
		if (Modifier.isAbstract(method.getModifiers())) 
			throw new CompileException("abstract method cannot be @Synch " + method.getLongName());

		CtMethod copy = CtNewMethod.copy(method, method.getDeclaringClass(), null);
		String newName = "__chainvayler__" + method.getName();
		copy.setName(newName);
		makePrivate(copy);
		method.getDeclaringClass().addMethod(copy);
		System.out.println("renamed " + method.getLongName() + " to " + copy.getLongName());
		
		boolean hasReturnType = (CtClass.voidType != method.getReturnType());
		
		String source = hasReturnType 
				? createSource("IsChained.synch.java.txt", contextClass.getName(), method.getName()) 
				: createSource("IsChained.synchVoid.java.txt", contextClass.getName(), method.getName());
		
		method.setBody(source);
		method.getMethodInfo().rebuildStackMap(classPool);
		
		// TODO remove @Modification? from both source and copy?
	}
	
	// TODO this does not correctly handle arrays
	private String getParams(CtMethod method) throws Exception {
		List<String> params = new ArrayList<String>();
		for (CtClass paramType : method.getParameterTypes()) {
			if (paramType.isArray()) {
				params.add("\"L" + paramType.getComponentType().getName() + ";\"");
			} else {
				params.add("\"" + paramType.getName() + "\"");
			}
			System.out.println("---" + paramType.getName() + "  comp: " + paramType.getComponentType());
		}
		String paramS = params.toString();
		System.out.println("sig: new String[] {" + paramS.substring(1, paramS.length()-1) +  "}");
		return "new String[] {" + paramS.substring(1, paramS.length()-1) +  "}";
	}
	
	private String getMethodWrapperSource(CtMethod method) throws Exception {
		String s = "new raft.chainvayler.impl.MethodWrapper(";
		s += "\"" + method.getName() + "\", ";
		s += "\"" + method.getDeclaringClass().getName() + "\", ";
		
		List<String> params = new ArrayList<String>();
		for (CtClass paramType : method.getParameterTypes()) {
			params.add("\"" + paramType.getName() + "\"");
		}
		String paramS = params.toString();
		s += " new String[] {" + paramS.substring(1, paramS.length()-1) +  "})";
//		s += " null)";
		
		return s;
	}
	
	private String getClassWriteDirectory(CtClass clazz) throws Exception {
		String className = clazz.getName();
		String path = classPool.find(className).toURI().toString();
		int index = path.indexOf(className.replace('.', '/') + ".class");
		if (index < 0)
			throw new IllegalStateException("couldnt find class dir in path " + path);
		
		if (!path.startsWith("file:/"))
			throw new IllegalStateException("class location is not a flat file: " + path);
		
		File dir = new File(new URI(path.substring(0, index)));
//		System.out.println(dir);
		return dir.getAbsolutePath();
	}
	
	private List<CtClass> getPackageClasses(CtClass clazz) throws Exception {
		String className = clazz.getName();
		String path = classPool.find(className).toURI().toString();
		int index = path.indexOf(className.replace('.', '/') + ".class");
		if (index < 0)
			throw new IllegalStateException("couldnt find class dir in path " + path);
		
		List<CtClass> result = new LinkedList<CtClass>();
		String packageName = clazz.getPackageName();
		
		if (path.startsWith("file:/")) {
			File classFile = new File(new URI(path));
			File packageDir = classFile.getParentFile();
			
			for (String file : packageDir.list()) {
				if (!file.endsWith(".class"))
					continue;
				
				String clsName = packageName + "." + file.substring(0, file.length() - 6); // omit the .class part
				result.add(classPool.get(clsName));
			}			
		} else if (path.startsWith("jar:file:/")) {
			String packagePath = packageName.replace('.', '/');
			
			String jarPath = URLDecoder.decode(path, "UTF-8");
			jarPath = jarPath.substring(9, jarPath.lastIndexOf('!'));
			
			JarFile jarFile = new JarFile(jarPath);
			try {
				Enumeration<JarEntry> e = jarFile.entries();
				while (e.hasMoreElements()) {
					JarEntry entry = e.nextElement();
					
					if (entry.isDirectory()) 
						continue;
					
					String name = entry.getName(); 
					if (!name.startsWith(packagePath) || !name.endsWith(".class"))
						continue;
					
					String subPart = name.substring(packagePath.length() + 1, name.length() - 6); // omit the .class part
					if (!scanSubPackages && (subPart.indexOf('/') >= 0))
						continue;
					
					String clsName = packageName + "." + subPart.replace('/', '.');
					result.add(classPool.get(clsName));
				}
			} finally {
				jarFile.close();
			}
			
		} else if (path.startsWith("jrt:/")) {
			FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
			Path fPath = fs.getPath("modules", path.substring(4)); // drop jrt:/ part

			for (Path p : Files.list(fPath.getParent()).collect(Collectors.toList())) {
				Path subPath = p.subpath(2, p.getNameCount());
//				System.out.println(subPath);
				
				if (subPath.getFileName().toString().endsWith(".class")) {
					String pathString = subPath.toString();
					String clsName = pathString.substring(0, pathString.length() - 6).replace('/', '.'); // omit the .class part
					result.add(classPool.get(clsName));
					
				} else if (Files.isDirectory(fPath)) {
					if (scanSubPackages)
						throw new UnsupportedOperationException("scanning subpackages is not implemented!");
				} 
			}
			
		} else { 
			throw new IllegalStateException("could not scan package: " + path);
		}
		
		return result;
	}

	
	private static <T extends CtBehavior> T makePrivate(T behavior) throws Exception {
		behavior.setModifiers(Modifier.setPrivate(behavior.getModifiers()));
		return behavior;
	}
	
	private static <T extends CtBehavior> T makeSynthetic(T behavior) throws Exception {
		MethodInfo info = behavior.getMethodInfo();
		info.setAccessFlags(info.getAccessFlags() | AccessFlag.SYNTHETIC);
		info.addAttribute(new SyntheticAttribute(info.getConstPool()));
		return behavior;
	}
	
	private static <T extends CtField> T makeSynthetic(T field) throws Exception {
		FieldInfo info = field.getFieldInfo();
		info.setAccessFlags(info.getAccessFlags() | AccessFlag.SYNTHETIC);
		info.addAttribute(new SyntheticAttribute(info.getConstPool()));
		return field;
	}
	
	private static String getClassNameForJavaIdentifier(String className) {
		return className.replace('.', '_').replace('$', '_');
	}

	private String createSource(String fileName, Object... arguments) throws Exception {
		String source = MessageFormat.format(readFile(fileName), arguments);
		if (DEBUG) System.out.println(source);
		return source;
	}
	
	private final Map<String, String> fileCache = new HashMap<String, String>();
	
	private String readFile(String fileName) throws Exception {
		String cached = fileCache.get(fileName);
		if (cached != null)
			return cached;
		
		InputStream in = getClass().getResourceAsStream(fileName);
		if (in == null)
			throw new FileNotFoundException(fileName);
		
		try {
			String content = new String();
			
			BufferedReader reader = new BufferedReader(new InputStreamReader(in));
			String line = null;
			while ((line = reader.readLine()) != null) {
				content += line + "\n";
			}
			fileCache.put(fileName, content);
			return content;
			
		} finally {
			in.close();
		}
	}

	private static void warning(String pattern, Object... arguments) {
		System.err.println("WARNING: " + MessageFormat.format(pattern, arguments));
	}

	
	/** returns true if this class or any of it's super classes has @Chained annotation */
	private static boolean isChained(CtClass clazz) throws Exception {
		CtClass supr = clazz;
		while (supr != null) {
			if (supr.hasAnnotation(Chained.class))
				return true;
			supr = supr.getSuperclass();
		}
		return false;
	}
	
	static List<CtClass> getChainedHierarchy(CtClass clazz) throws Exception {
		List<CtClass> list = new LinkedList<CtClass>();
		
		while (isChained(clazz)) {
			list.add(0, clazz);
			clazz = clazz.getSuperclass();
		}
		return list;
	}
}
