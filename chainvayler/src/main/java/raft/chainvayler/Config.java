package raft.chainvayler;

public class Config {

	/** Convenience method to create a persistence only Config */
	public static Config persistence(String persistDir) {
		Config config = new Config();
		
		config.getPersistence()
			.setEnabled(true)
			.setPersistDir(persistDir);
		
		config.getReplication().setEnabled(false);
		
		return config;
	}
	
	private Replication replicationConfig = new Replication();
	private Persistence persistenceConfig = new Persistence();
	
	public Config() {
	}

	/** return persistence config */
	public Persistence getPersistence() {
		return persistenceConfig;
	}
	
	/** return replication config */
	public Replication getReplication() {
		return replicationConfig;
	}
	
	public static class Persistence {
		private boolean enabled = true;
		private String persistDir = "persist";

		public boolean isEnabled() {
			return enabled;
		}

		public Persistence setEnabled(boolean enabled) {
			this.enabled = enabled;
			return this;
		}

		public String getPersistDir() {
			return persistDir;
		}

		public Persistence setPersistDir(String persistDir) {
			this.persistDir = persistDir;
			return this;
		}
	}
	
	public static class Replication {
		
		private boolean enabled = true;
		
		private int numberOfRaftNodes = 3;
		private boolean kubernetes = false;
		private String kubernetesServiceName;
		
		private int reliableTopicCapacity = 100000;
		
		private int imapBackupCount = 0;
		private int imapAsyncBackupCount = 2;
		
		private int txIdReserveSize = 0;

		public boolean isEnabled() {
			return enabled;
		}

		public Replication setEnabled(boolean enabled) {
			this.enabled = enabled;
			return this;
		}
		
		public int getNumberOfRaftNodes() {
			return numberOfRaftNodes;
		}

		public Replication setNumberOfRaftNodes(int numberOfRaftNodes) {
			this.numberOfRaftNodes = numberOfRaftNodes;
			return this;
		}

		public boolean isKubernetes() {
			return kubernetes;
		}

		public Replication setKubernetes(boolean kubernetes) {
			this.kubernetes = kubernetes;
			return this;
		}

		public String getKubernetesServiceName() {
			return kubernetesServiceName;
		}

		public Replication setKubernetesServiceName(String kubernetesServiceName) {
			this.kubernetesServiceName = kubernetesServiceName;
			return this;
		}
		
		public int getReliableTopicCapacity() {
			return reliableTopicCapacity;
		}

		public Replication setReliableTopicCapacity(int reliableTopicCapacity) {
			this.reliableTopicCapacity = reliableTopicCapacity;
			return this;
		}

		public int getImapBackupCount() {
			return imapBackupCount;
		}

		public Replication setImapBackupCount(int imapBackupCount) {
			this.imapBackupCount = imapBackupCount;
			return this;
		}

		public int getImapAsyncBackupCount() {
			return imapAsyncBackupCount;
		}

		public Replication setImapAsyncBackupCount(int imapAsyncBackupCount) {
			this.imapAsyncBackupCount = imapAsyncBackupCount;
			return this;
		}

		public int getTxIdReserveSize() {
			return txIdReserveSize;
		}

		public Replication setTxIdReserveSize(int txIdReserveSize) {
			if (txIdReserveSize < 0)
				throw new IllegalArgumentException("txIdReserveSize: " + txIdReserveSize);
			this.txIdReserveSize = txIdReserveSize;
			return this;
		}
	}
}
