package raft.chainvayler.samples.bank;

import java.io.PrintStream;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import raft.chainvayler.Chainvayler;
import raft.chainvayler.Config;
import raft.chainvayler.impl.Context;
import raft.chainvayler.samples.bank.rmi.Peer;
import raft.chainvayler.samples.bank.rmi.PeerManager;
import raft.chainvayler.samples.bank.secret.SecretCustomer;
import raft.chainvayler.samples.bank.util.ComLineArgs;

/**
 * Entry point of sample.
 * 
 * @author r a f t
 */
public class Main {
	
	private static final int BANK_WRITE_ACTIONS = 21;
	private static final int BANK_READ_ACTIONS = 7;
	
	private final Options options;
	private Peer.Impl peer;
	private volatile boolean stopReaders = false;
	private volatile boolean stopped = false;
	
    public Main(Options options) {
		this.options = options;
	}

	public void run() throws Exception {
		System.out.println("running with options: \n" + options);
		
		Config config = new Config()
				.setPersistenceEnabled(options.persistence)
				.setReplicationEnabled(options.replication);
		config.getReplicationConfig().setTxIdReserveSize(options.txIdReserveSize);
		
		config.getHazelcastConfig().getMapConfig("default").setAsyncBackupCount(options.hazelcastAsyncBackupCount);
		
		if (options.kubernetes) {
			config.getHazelcastConfig().getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
			config.getHazelcastConfig().getNetworkConfig().getJoin().getKubernetesConfig().setEnabled(true)
					.setProperty("service-dns", options.kubernetesServiceName);
		} else {
			config.getHazelcastConfig().getNetworkConfig().getJoin().getMulticastConfig().setEnabled(true);
			config.getHazelcastConfig().getNetworkConfig().getJoin().getKubernetesConfig().setEnabled(false);
		}

		if (options.peerStatsRegistry != null) 
			registerPeerRmi();
		
		// TODO remove
		Context.DEBUG = options.debug;
		
		registerShutdownHook();
		
		Bank bank = Chainvayler.create(Bank.class, config);
		
		startReaderThreads(bank);
		startWriterThreads(bank);
	}

	private void startWriterThreads(Bank bank) throws Exception {
		List<Thread> threads = new ArrayList<Thread>();
		
		for (int i = 0; i < options.writers; i++) {
			threads.add(new Thread("chainvayler-sample-writer:" + i) {
				public void run() {
					try {
						populateBank(bank, new Random());
						System.out.println("populated bank by thread " + getName());
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				};
			});
		}
		
		if (options.writers == 0) {
			threads.add(new Thread("chainvayler-sample-writer") {
				public void run() {
					try {
						while (!stopReaders) {
							Thread.sleep(1000);
						}
						// execute a local transaction
						// when this method returns, all remote transactions will be also committed 
						doSomethingRandomWithBank(bank, 1, new Random());
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				};
			});
		}
		
		long startTime = System.currentTimeMillis(); 
		for (Thread t : threads) {
			t.start();
		}
		System.out.printf("started %s writer threads\n", options.writers);
		if (peer != null) {
			peer.startTime = startTime;
			peer.started = true;
		}
		
		for (Thread t : threads) {
			t.join();
		}
		long endTime = System.currentTimeMillis();
		
		System.out.println("all writer threads completed");
		if (options.stopReaders) {
			System.out.println("stopping reader threads");
			stopReaders = true;
		}
		
		if (peer != null) {
			peer.completed = true;
			
			peer.lastTxPerSecond = 1000f * Chainvayler.getInstance().getTransactionCount()/(endTime - startTime);
			peer.lastOwnTxPerSecond = 1000f * Chainvayler.getInstance().getOwnTransactionCount()/(endTime - startTime);
		}
	}

	private void startReaderThreads(Bank bank) throws Exception {
		List<Thread> threads = new ArrayList<Thread>();
		
		for (int i = 0; i < options.readers; i++) {
			threads.add(new Thread("chainvayler-sample-reader:" + i) {
				public void run() {
					Random random = new Random();
					while (!stopReaders) {
						try {
							int action = random.nextInt(BANK_READ_ACTIONS);
							int readCount = readSomethingRandomFromBank(bank, action, random);
							if (peer != null) 
								peer.incrementReadCount(readCount);
						} catch (Exception e) {
							throw new RuntimeException(e);
						}
					}
				};
			});
		}
		
		for (Thread t : threads) {
			t.start();
		}
		System.out.printf("started %s reader threads\n", options.readers);
	}

	
	private void registerPeerRmi() throws Exception {
		boolean done = false;
		int count = 0;
		
		while (!done) {
			try {
				Registry registry = LocateRegistry.getRegistry(options.peerStatsRegistry);
				System.out.println("got RMI registry @" + options.peerStatsRegistry);
				PeerManager manager = (PeerManager) registry.lookup("manager");
				System.out.println("got PeerManager from RMI registry");
		
				peer = new Peer.Impl(new Peer.ReaderStopper() {
					@Override
					public void stopReaders() throws Exception {
						Main.this.stopReaders = true;
					}
				}); 
				peer.hasWriters = options.writers > 0;
				manager.registerPeer(peer);
				System.out.println("registered peer to PeerManager");
				done = true;
			} catch (Exception e) {
				if (count >= 20) {
					done = true;
					System.out.printf("tried %s times, giving up registering peer \n", count);
					e.printStackTrace();
				} else {
					count++;
					Thread.sleep(1000);
				}
			}
		}
	}
	
	private void registerShutdownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread()  {
			@Override
			public void run() {
				System.out.println("Shutdown hook is running, shutting down Chainvayler");
				stopped = true;
				Chainvayler.shutdown();
			}
		});
	}

	
	private void populateBank(Bank bank, Random random) throws Exception {
		
		// this call demonstrates @Include is implemented at compiler 
		bank.addCustomer((Customer) Class.forName("raft.chainvayler.samples.bank.secret.SecretCustomer").newInstance());
		
		if (bank.getSister() == null) {
			bank.setSister(new Bank());
			bank.getSister().getOwner().setName("even more rich");
		}
		
		for (int i = 0; i < 50 + random.nextInt(50); i++) {
			if (stopped) return;
			Customer customer = bank.createCustomer("initial:" + random.nextInt());
			customer.addAccount(bank.createAccount());
		}
		
		for (int action = 0; action < options.actions; action++) {
			if (stopped) return;
			int next = random.nextInt(BANK_WRITE_ACTIONS);
			doSomethingRandomWithBank(bank, next, random);
		}
	}
	

	private void doSomethingRandomWithBank(Bank bank, int action, Random random) throws Exception {
		
		switch (action) {
			 case 0: {
				 // create a customer via bank
				 Customer customer = bank.createCustomer("create:" + random.nextInt());
				 break;
			 }
			 case 1: {
				 // create customer
				 Customer customer = new Customer("add:" + random.nextInt());
				 bank.addCustomer(customer);
				 break;
			 	}
			 case 2: {
				 // create a detached customer
				 Customer customer = new Customer("add:" + random.nextInt());
				 customer.setPhone("phone" + random.nextInt());
				 break;
			 	}
			 case 3: {
				 // create customer via bank and set phone
				 Customer customer = bank.createCustomer("create:" + random.nextInt());
				 customer.setPhone("phone:" +  + random.nextInt());
				 break;
			 	}
			 // set phones of some customers
			 case 4: {
				 List<Customer> customers = bank.getCustomers();
				 if (customers.isEmpty())
					 break;
				 for (int ci = 0; ci < random.nextInt(Math.max(1, customers.size()/5)); ci++) {
					 Customer customer = customers.get(random.nextInt(customers.size()));
					 customer.setPhone("phone:ran" + random.nextInt());
				 }
				 break;
			 	}
			 // remove some customers
			 case 5: {
				 List<Customer> customers = bank.getCustomers();
				 if (customers.isEmpty())
					 break;
				 for (int i = 0; i < random.nextInt(Math.max(1, customers.size()/10)); i++) {
					 Customer customer = customers.get(random.nextInt(customers.size()));
					 bank.removeCustomer(customer);
				 }
				 break;
			 	}
			 // create some accounts
			 case 6: {
				 bank.createAccount();
				 break;
			 }
			 // create some accounts and deposit money
			 case 7: {
				 bank.createAccount().deposit(10 + random.nextInt(50));
				 break;
			 }
			 // create some accounts and add to customers
			 case 8: {
				 List<Customer> customers = bank.getCustomers();
				 if (customers.isEmpty())
					 break;
				 for (int ci = 0; ci < random.nextInt(Math.max(1, customers.size()/10)); ci++) {
					 Customer customer = customers.get(random.nextInt(customers.size()));
					 Account account = bank.createAccount();
					 account.setName("account:" + random.nextInt());
					 account.deposit(100 + random.nextInt(100));
					 customer.addAccount(account);
				 }
				 break;
			 	}
			 // create a RichPerson which will throw exception at Person constructor
			 case 9: {
				 try {
					 new RichPerson("HellBoy");
				 } catch (IllegalArgumentException e) {}
				 break;
			 }
			 // create a RichPerson which will throw exception at RichPerson constructor
			 case 10: {
				 try {
					 new RichPerson("Dracula");
				 } catch (IllegalArgumentException e) {}
				 break;
			 }
			 case 11: {
				 RichPerson rich = bank.getOwner(); 
				 if (rich != null) {
					 rich.getSister().setName("sister:" + random.nextInt());
					 rich.getBrother().setName("brother:" + random.nextInt());
				 }
				 break;
			 }
			 // transfer some money
			 case 12: {
				 List<Account> accounts = bank.getAccounts();
				 if (accounts.size() < 2)
					 break;
				 
				 for (int i = 0; i < random.nextInt(10); i++) {
					 Account from, to;
					 while ( (from = accounts.get(random.nextInt(accounts.size()))) == 
							 (to = accounts.get(random.nextInt(accounts.size())))) {}
					 
					 int amount = Math.min(from.getBalance(), to.getBalance()) / 3;
					 if (amount == 0)
						 continue;
					 
					 bank.transferAmount(from, to, amount);
				 }
				 
				 break;
			 }
			 // assign bank an owner 
			 case 13: {
				 RichPerson richPerson = new RichPerson("richie rich:" + random.nextInt());
				 bank.setOwner(richPerson);
				 break;
			 }
			 // create another bank   
			 case 14: {
				 Bank other = new Bank();
				 RichPerson rich = bank.getOwner(); 
				 if (rich != null) {
					 other.setOwner(rich);
				 }
				 break;
			 }
			 // create another bank and create an owner if there is none   
			 case 15: {
				 Bank other = new Bank();
				 RichPerson rich = bank.getOwner(); 
				 if (rich == null) {
					 rich = new RichPerson("richie rich:" + random.nextInt());
					 bank.setOwner(rich);
				 }
				 other.setOwner(rich);
				 break;
			 }
			 // create a central bank and create an owner if there is none   
			 case 16: {
				 CentralBank central = new CentralBank();
				 RichPerson rich = bank.getOwner(); 
				 if (rich == null) {
					 rich = new RichPerson("richie rich:" + random.nextInt());
					 bank.setOwner(rich);
				 }
				 central.setOwner(rich);
				 break;
			 }
			 // set bank owner's name
			 case 17: {
				 bank.getOwner().setName("richie rich " + random.nextInt(100));
				 break;
			 }
			 
			 // do something time based
			 case 18: {
				 bank.addAudit();
				 break;
			 }
			 
			 // create a detached bank and do something random on it   
			 case 19: {
				 Bank other = new Bank();
				 for (int i = 0; i < 50 + random.nextInt(100); i++) {
					 // -2 to avoid stack overflow
					 doSomethingRandomWithBank(other, random.nextInt(BANK_WRITE_ACTIONS-2), random);
				 }
				 break;
			 }
			 // do something random with other banks
			 case 20: {
				 RichPerson owner = bank.getOwner();
				 if (owner != null) {
					 List<Bank> banks = owner.getBanks();
					 if (banks.size() > 1) {
						 Bank otherBank = banks.get(random.nextInt(banks.size()-1)+1);
						 // -2 to avoid stack overflow
						 doSomethingRandomWithBank(otherBank, random.nextInt(BANK_WRITE_ACTIONS-2), random);
					 }
				 }
				 break;
			 }
			 default:
				 throw new IllegalArgumentException("unknown action: " + action);
		}	
	}
	
	private int readSomethingRandomFromBank(Bank bank, int action, Random random) {
		int readCount = 0;
		
		switch (action) {
			case 0: {
				 // read bank owner's name
				 bank.getOwner().getName();
				 readCount++;
				 break;
			 }
			 case 1: {
				 // read account balances
				 for (Account account : bank.getAccounts()) {
					 account.getBalance();
					 readCount++;
				 }
				 break;
			 }
			 case 2: {
				 // read audits
				 bank.getAudits();
				 readCount++;
				 break;
			 }
			 case 3: {
				 // read customer names
				 for (Customer customer : bank.getCustomers()) {
					 try {
						 customer.getName();
						 readCount++;
					 } catch (UnsupportedOperationException e) {
						 assert (customer.getClass() == SecretCustomer.class);
					 }
				 }
				 break;
			 }
			 case 4: {
				 // read customer account balances
				 for (Customer customer : bank.getCustomers()) {
					 for (Account account : customer.getAccounts()) {
						 account.getBalance();
						 readCount++;
					 }
				 }
				 break;
			 }
			 case 5: {
				 // read owner's sister and brother
				 Person sister = bank.getOwner().getSister();
				 readCount++;
				 if (sister != null) {
					 sister.getName();
					 readCount++;
				 }
				 Person brother = bank.getOwner().getBrother();
				 readCount++;
				 if (brother != null) {
					 brother.getName();
					 readCount++;
				 }
				 break;
			 }
			 case 6: {
				 // read something random with other banks
				 List<Bank> banks = bank.getOwner().getBanks();
				 readCount++;
				 if (banks.size() > 1) {
					 Bank otherBank = banks.get(random.nextInt(banks.size()-1)+1);
					 // -1 to avoid stack overflow
					 readCount += readSomethingRandomFromBank(otherBank, random.nextInt(BANK_READ_ACTIONS-1), random);
				 }
				 break;
			 }
			 default:
				 throw new IllegalArgumentException("unknown action: " + action);
		}		
		return readCount;
	}
	
	public static class Options {
		public boolean persistence = true;
		public boolean replication = true;
		public boolean kubernetes = false;
		public String kubernetesServiceName;
		public int txIdReserveSize = 0; 
		public int writers = 5;
		public int readers = 5;
		public int actions = 100;
		public int hazelcastAsyncBackupCount = 2;
		public boolean stopReaders = true;
		public String peerStatsRegistry;
		public boolean debug = false;
		
		@Override
		public String toString() {
			return "Options"
				   + "\n    persistence: " + persistence
				   + "\n    replication: " + replication
				   + "\n    kubernetes: " + kubernetes
				   + "\n    kubernetesServiceName: " + kubernetesServiceName
				   + "\n    txIdReserveSize: " + txIdReserveSize
				   + "\n    writers: " + writers
				   + "\n    readers: " + readers
				   + "\n    actions: " + actions 
				   + "\n    hazelcastAsyncBackupCount: " + hazelcastAsyncBackupCount 
				   + "\n    stopReaders: " + stopReaders
				   + "\n    peerStatsRegistry: " + peerStatsRegistry
				   + "\n    debug: " + debug;
		}
	}
	
    private static Options parseOptions(ComLineArgs comLine, String[] args) {
		Options options = new Options();
		
		if (comLine.containsArg("--persistence"))
            options.persistence = Boolean.parseBoolean(comLine.getArg("--persistence"));
    	
		if (comLine.containsArg("--replication"))
            options.replication = Boolean.parseBoolean(comLine.getArg("--replication"));
    	
		if (comLine.containsArg("--kubernetes"))
            options.kubernetes = Boolean.parseBoolean(comLine.getArg("--kubernetes"));
    	
		if (comLine.containsArg("--kubernetesServiceName"))
            options.kubernetesServiceName = comLine.getArg("--kubernetesServiceName");
    	
		if (comLine.containsArg("--txIdReserveSize"))
            options.txIdReserveSize = Integer.parseInt(comLine.getArg("--txIdReserveSize"));

		if (comLine.containsArg("--writers"))
            options.writers = Integer.parseInt(comLine.getArg("--writers"));

		if (comLine.containsArg("--readers"))
            options.readers = Integer.parseInt(comLine.getArg("--readers"));

		if (comLine.containsArg("--actions"))
            options.actions = Integer.parseInt(comLine.getArg("--actions"));
    	
		if (comLine.containsArg("--stopReaders"))
            options.stopReaders = Boolean.parseBoolean(comLine.getArg("--stopReaders"));
		
		if (comLine.containsArg("--peerStatsRegistry"))
            options.peerStatsRegistry = comLine.getArg("--peerStatsRegistry");

		if (comLine.containsArg("--hazelcastAsyncBackupCount"))
            options.hazelcastAsyncBackupCount = Integer.parseInt(comLine.getArg("--hazelcastAsyncBackupCount"));
		
		if (comLine.containsArg("--debug"))
            options.debug  = Boolean.parseBoolean(comLine.getArg("--debug"));
		
        if (comLine.isUnconsumed())
            throw new IllegalArgumentException("Unknown args: " + comLine.getUnconsumed());
        
        if (options.kubernetes && options.kubernetesServiceName == null)
        	throw new IllegalArgumentException("--kubernetesServiceName is required when kubernetes mode is enabled");
        
        return options;
    }
	
	private static void printUsage(PrintStream ps) {
	    ps.println("usage: Main [options]");
	    ps.println("options:");
	    ps.println("    -h | --help                          : print help and exit");
	    ps.println("    --persistence <true*|false>          : enable persistence?");
	    ps.println("    --replication <true*|false>          : enable replication?");
	    ps.println("    --kubernetes  <true|false*>          : enable Kubernetes mode?");
	    ps.println("    --kubernetesServiceName <name>       : Kubernetes service name for Hazelcast discovery");
	    ps.println("    --txIdReserveSize                    : reserve this much tx id's when receiving a new one (default is 0)");
	    ps.println("    --writers <count>                    : writer threads count (default is 5)");
	    ps.println("    --readers <count>                    : reader threads count (default is 5)");
	    ps.println("    --stopReaders <true*|false>          : stop reader threads after writers completed?");
	    ps.println("    --actions <count>                    : number of actions each writer performs (default is 100)\n" + 
	    		   "                                           one action creates multiple transactions\n" + 
	    		   "                                           note: regardless of actions, initially (50 + random(50))\n" + 
	    		   "                                                 customers and accounts are created");
	    ps.println("    --peerStatsRegistry <host|IP>        : if provided, peer is registered by using this RMI registry");
	    ps.println("    --hazelcastAsyncBackupCount          : async backup count for Hazelcast IMap");
	    ps.println("    --debug <true|false*>        		 : enable debug logging?");
	}
	
	public static void main(String[] args) throws Exception {
        ComLineArgs comLine = new ComLineArgs(args);

		if (comLine.containsArg("-h") || comLine.containsArg("-help")) {
		    printUsage(System.out);
		    System.exit(0);
		}
		
		new Main(parseOptions(comLine, args)).run();
	}
	
}
