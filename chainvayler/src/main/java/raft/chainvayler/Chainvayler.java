package raft.chainvayler;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import org.prevayler.Prevayler;
import org.prevayler.PrevaylerFactory;
import org.prevayler.implementation.PrevaylerImpl;

import raft.chainvayler.impl.GCPreventingPrevayler;
import raft.chainvayler.impl.HazelcastPrevayler;
import raft.chainvayler.impl.InitRootTransaction;
import raft.chainvayler.impl.IsChained;
import raft.chainvayler.impl.RootHolder;
import raft.chainvayler.impl.Utils;

/**
 * <p>Entry point to create a @Chained object. After @Chained classes is instrumented 
 * (i.e. they are compiled with Chainvayler compiler), it's enough to call 
 * <code>Chainvayler.create(MyRoot.class)</code> to get chained instance of <code>MyRoot</code> class.</p>
 * 
 * <p>Chainvayler persists an <a href="http://en.wikipedia.org/wiki/Object_graph">object graph</a> 
 * via <a href="http://prevayler.org/">prevalence</a>. MyRoot here denotes the entry point of 
 * the object graph. It may be the root node of a tree, a container class around other data structures
 * or something else.</p>
 * 
 * <p>Chainvayler compiler instruments classes in regard to root class. In runtime, 
 * {@link Chainvayler#create(Class)} should be called with the same root class or with an instance of it.  
 *  This suggests there can be only one root instance per JVM (indeed per {@link ClassLoader}).</p>
 *  
 *  <p>Chainvayler creates {@link Prevayler} with default settings and 
 *  <i>./persist/root.class.qualified.Name</i> directory as prevalance directory. These can be 
 *  configured by passing a {@link PrevaylerFactory} instance to Chainvayler before calling {@link #create()}.</p>
 * 
 * @see Chained
 * @see Modification
 * @see PrevaylerFactory
 * 
 * @author r a f t
 */
public class Chainvayler<T> {

	private static Chainvayler<?> instance;
	
	private final Class<T> rootClass;
	private final Config config;
	
	private RootHolder rootHolder;
	private HazelcastPrevayler hazelcastPrevayler;
	
	private Object prevalerGuard;
	private Field systemVersionField;
	
	private Chainvayler(Class<T> rootClass, Config config) throws Exception {
		this.rootClass = rootClass;
		this.config = config; // TODO clone maybe?
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public T create() throws Exception {
		synchronized (Chainvayler.class) {
			if (instance != null) 
				throw new IllegalStateException("an instance is already created");
			
			// TODO check root class is actually instrumented
			if (!IsChained.class.isAssignableFrom(rootClass))
				throw new NotCompiledException(rootClass.getName());
			
			String contextClassName = rootClass.getPackage().getName() + ".__Chainvayler";
			Class<?> contextClass;
			Field contextRootField;
			Constructor<?> contextRootConstructor;
			
			try {
				contextClass = Class.forName(contextClassName);
				contextRootField = contextClass.getField("rootClass");
				contextRootConstructor = contextClass.getConstructor(Prevayler.class, RootHolder.class);
				
				if (!contextRootField.get(null).equals(rootClass)) 
					throw new NotCompiledException("cannot create Chainvayler for " + rootClass.getName() 
							+ ", root class is " + contextRootField.getType().getName());
				
				String classSuffix = getClassNameForJavaIdentifier(rootClass);
				String instrumentationRoot = (String) rootClass.getField("__chainvayler_root_" + classSuffix).get(null);
				if (!rootClass.getName().equals(instrumentationRoot))
					throw new NotCompiledException("given root class " + rootClass.getName() + " is instrumented for root class " + instrumentationRoot); 
				
			} catch (ClassNotFoundException e) {
				throw new NotCompiledException(rootClass.getName(), e);
			} catch (NoSuchMethodException e) {
				throw new NotCompiledException(rootClass.getName(), e);
			} catch (NoSuchFieldException e) {
				throw new NotCompiledException(rootClass.getName(), e);
			}
			
			final Prevayler<RootHolder> prevayler;
			if (config.getPersistence().isEnabled()) {
				PrevaylerFactory<RootHolder> factory = new PrevaylerFactory<RootHolder>();
				factory.configurePrevalenceDirectory(config.getPersistence().getPersistDir());
				factory.configurePrevalentSystem(new RootHolder());
				
				prevayler = factory.create();
			} else {
				prevayler = PrevaylerFactory.createTransientPrevayler(new RootHolder());
			}
			
			this.prevalerGuard = Utils.getDeclaredFieldValue("_guard", prevayler);
			this.systemVersionField = Utils.getDeclaredField("_systemVersion", prevalerGuard); 
			
			this.rootHolder = prevayler.prevalentSystem();
			
			if (config.getReplication().isEnabled()) {
				this.hazelcastPrevayler = new HazelcastPrevayler((PrevaylerImpl<RootHolder>) prevayler, config.getReplication());
				contextRootConstructor.newInstance(new GCPreventingPrevayler(hazelcastPrevayler), rootHolder);
			} else {
				contextRootConstructor.newInstance(new GCPreventingPrevayler(prevayler), rootHolder);
			}
			
			if (!rootHolder.isInitialized()) {
				prevayler.execute(new InitRootTransaction((Class)rootClass));
			}
			rootHolder.onRecoveryCompleted(!config.getReplication().isEnabled());
			
			instance = this;
			
			if (hazelcastPrevayler != null)
				hazelcastPrevayler.start();
			
			return (T) rootHolder.getRoot();
		}
	}
	
	public long getTransactionCount() throws Exception {
		if (hazelcastPrevayler != null) 
			return hazelcastPrevayler.getTransactionCount();
		else if (systemVersionField != null)
			return systemVersionField.getLong(prevalerGuard);
		else return -1L;
	}
	
	public long getOwnTransactionCount() throws Exception {
		if (hazelcastPrevayler != null) 
			return hazelcastPrevayler.getOwnTransactionCount();
		else if (systemVersionField != null)
			return systemVersionField.getLong(prevalerGuard);
		else return -1L;
	}
	
	public IsChained getRoot() {
		return rootHolder.getRoot();
	}
	
	public static void shutdown() {
		if (instance != null && instance.hazelcastPrevayler != null)
			instance.hazelcastPrevayler.shutdown();
	}
	
	public static <T> T create(Class<T> clazz) throws Exception {
		return create(clazz, new Config());
	}
	
	/** Creates a Chainvayler with only persistence enabled using giving persistence directory */
	public static <T> T create(Class<T> clazz, String persistDir) throws Exception {
		return create(clazz, Config.persistence(persistDir));
	}
	
	public static <T> T create(Class<T> clazz, Config config) throws Exception {
		return new Chainvayler<T>(clazz, config).create();
	}
	
	public static Chainvayler<?> getInstance() {
		synchronized (Chainvayler.class) {
			return instance;
		}
	}
	
	private static String getClassNameForJavaIdentifier(Class<?> clazz) {
		return clazz.getName().replace('.', '_').replace('$', '_');
	}

}
