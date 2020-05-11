package raft.chainvayler;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Set;

import org.prevayler.Prevayler;
import org.prevayler.PrevaylerFactory;
import org.prevayler.implementation.PrevaylerDirectory;
import org.prevayler.implementation.PrevaylerImpl;
import org.prevayler.implementation.snapshot.GenericSnapshotManager;

import raft.chainvayler.impl.Context;
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
	private PrevaylerDirectory prevaylerDirectory;
	
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
			
			
			PrevaylerFactory<RootHolder> factory = new PrevaylerFactory<RootHolder>();
			factory.configurePrevalenceDirectory(config.getPersistence().getPersistDir());
			factory.configurePrevalentSystem(new RootHolder());
			factory.configureTransientMode(!config.getPersistence().isEnabled());
			
			Prevayler<RootHolder> prevayler = factory.create();
			
//			final Prevayler<RootHolder> prevayler;
//			if (config.getPersistence().isEnabled()) {
//				PrevaylerFactory<RootHolder> factory = new PrevaylerFactory<RootHolder>();
//				factory.configurePrevalenceDirectory(config.getPersistence().getPersistDir());
//				factory.configurePrevalentSystem(new RootHolder());
//				factory.configureTransientMode(!config.getPersistence().isEnabled());
//				prevayler = factory.create();
//			} else {
//				prevayler = PrevaylerFactory.createTransientPrevayler(new RootHolder());
//			}
			
			this.prevalerGuard = Utils.getDeclaredFieldValue("_guard", prevayler);
			this.systemVersionField = Utils.getDeclaredField("_systemVersion", prevalerGuard);
			
			 GenericSnapshotManager<?> snapshotManager = Utils.getDeclaredFieldValue("_snapshotManager", prevayler);
			 this.prevaylerDirectory = Utils.getDeclaredFieldValue("_directory", snapshotManager);
			
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
	
	/** Takes a snapshot of @Chained object graph. 
	 * Next time the system is restarted, it will first load the snapshot and execute only the transactions later on. */
	public static void takeSnapshot() throws Exception {
		Context.getInstance().prevayler.takeSnapshot();
		// TODO what to do with replication only mode?
	}
	
	/** <p>Deletes redundant files from persistence directory.</p> 
	 * <p>In Prevayler terms, this means removing journal (transaction) and older snapshot files before the last snapshot.
	 * <b>Warning</b>, if there are any other files in the persistence directory, they would also be deleted.</p> 
	 * */
	public static void deleteRedundantFiles() throws Exception {
		long lastSnapshot = getLastSnapshotVersion();
		if (lastSnapshot < 0)
			return;
		
		@SuppressWarnings("unchecked")
		Set<File> necessaryFiles = getInstance().prevaylerDirectory.necessaryFiles();
		
		File persistDir = Utils.getDeclaredFieldValue("_directory", getInstance().prevaylerDirectory); 
		File[] allFiles = persistDir.listFiles();
        if (allFiles == null) 
            throw new IOException("Error reading file list from directory " + persistDir);
        
        for (File file : allFiles) {
        	if (!necessaryFiles.contains(file)) {
        		System.out.println("deleting: " + file);
        		Files.delete(file.toPath());
        	}
        }
	}
	
	/** Returns the version (transaction count) of the @Chained object graph when last snapshot is taken. 
	 * returns -1 if no snapshots are taken so far.
	 * @see {{@link #takeSnapshot()} */
	public static long getLastSnapshotVersion() throws IOException {
		File file = getInstance().prevaylerDirectory.latestSnapshot();
		return (file == null) ? -1 : PrevaylerDirectory.snapshotVersion(file);
	}
	
	public static long getTransactionCount() throws Exception {
		Chainvayler<?> instance = getInstance();
		
		if (instance.hazelcastPrevayler != null) 
			return instance.hazelcastPrevayler.getTransactionCount();
		else if (instance.systemVersionField != null)
			return instance.systemVersionField.getLong(instance.prevalerGuard);
		else return -1L;
	}
	
	public static long getOwnTransactionCount() throws Exception {
		Chainvayler<?> instance = getInstance();
		
		if (instance.hazelcastPrevayler != null) 
			return instance.hazelcastPrevayler.getOwnTransactionCount();
		else if (instance.systemVersionField != null)
			return instance.systemVersionField.getLong(instance.prevalerGuard);
		else return -1L;
	}
	
//	public static IsChained getRoot() {
//		return getInstance().rootHolder.getRoot();
//	}
	
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
	
	private static Chainvayler<?> getInstance() {
		synchronized (Chainvayler.class) {
			if (instance == null)
				throw new IllegalStateException("Chainvayler instance not created yet");

			return instance;
		}
	}
	
	private static String getClassNameForJavaIdentifier(Class<?> clazz) {
		return clazz.getName().replace('.', '_').replace('$', '_');
	}

}
