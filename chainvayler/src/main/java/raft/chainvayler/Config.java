package raft.chainvayler;

public class Config {

	/** Convenience method to create a persistence only Config */
	public static Config persistence(String persistDir) {
		Config config = new Config()
			.setPersistenceEnabled(true)
			.setReplicationEnabled(false);
		
		config.getPersistenceConfig().setPersistDir(persistDir);
		return config;
	}
	
	private boolean persistenceEnabled = true;
	private boolean replicationEnabled = true;
	
	private ReplicationConfig replicationConfig = new ReplicationConfig();
	private PersistenceConfig persistenceConfig = new PersistenceConfig();
	
	public Config() {
	}

	public boolean isPersistenceEnabled() {
		return persistenceEnabled;
	}

	public Config setPersistenceEnabled(boolean persistenceEnabled) {
		this.persistenceEnabled = persistenceEnabled;
		return this;
	}

	public boolean isReplicationEnabled() {
		return replicationEnabled;
	}

	public Config setReplicationEnabled(boolean replicationEnabled) {
		this.replicationEnabled = replicationEnabled;
		return this;
	}

	public PersistenceConfig getPersistenceConfig() {
		return persistenceConfig;
	}
	
	public ReplicationConfig getReplicationConfig() {
		return replicationConfig;
	}
	
	public void setReplicationConfig(ReplicationConfig replicationConfig) {
		this.replicationConfig = replicationConfig;
	}

	public static class PersistenceConfig {
		private String persistDir = "persist";

		public String getPersistDir() {
			return persistDir;
		}

		public PersistenceConfig setPersistDir(String persistDir) {
			this.persistDir = persistDir;
			return this;
		}
	}
	
	public static class ReplicationConfig {
		
		private int numberOfRaftNodes = 3;
		private boolean kubernetes = false;
		private String kubernetesServiceName;
		
		private int imapBackupCount = 0;
		private int imapAsyncBackupCount = 2;
		
		private int txIdReserveSize = 0;

		public int getNumberOfRaftNodes() {
			return numberOfRaftNodes;
		}

		public ReplicationConfig setNumberOfRaftNodes(int numberOfRaftNodes) {
			this.numberOfRaftNodes = numberOfRaftNodes;
			return this;
		}

		public boolean isKubernetes() {
			return kubernetes;
		}

		public ReplicationConfig setKubernetes(boolean kubernetes) {
			this.kubernetes = kubernetes;
			return this;
		}

		public String getKubernetesServiceName() {
			return kubernetesServiceName;
		}

		public ReplicationConfig setKubernetesServiceName(String kubernetesServiceName) {
			this.kubernetesServiceName = kubernetesServiceName;
			return this;
		}

		public int getImapBackupCount() {
			return imapBackupCount;
		}

		public ReplicationConfig setImapBackupCount(int imapBackupCount) {
			this.imapBackupCount = imapBackupCount;
			return this;
		}

		public int getImapAsyncBackupCount() {
			return imapAsyncBackupCount;
		}

		public ReplicationConfig setImapAsyncBackupCount(int imapAsyncBackupCount) {
			this.imapAsyncBackupCount = imapAsyncBackupCount;
			return this;
		}

		public int getTxIdReserveSize() {
			return txIdReserveSize;
		}

		public ReplicationConfig setTxIdReserveSize(int txIdReserveSize) {
			if (txIdReserveSize < 0)
				throw new IllegalArgumentException("txIdReserveSize: " + txIdReserveSize);
			this.txIdReserveSize = txIdReserveSize;
			return this;
		}
	}
}
