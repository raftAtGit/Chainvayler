package raft.chainvayler;

public class Config {

	private boolean persistenceEnabled = true;
	private boolean replicationEnabled = true;
	
	private ReplicationConfig replicationConfig = new ReplicationConfig();
	
	private com.hazelcast.config.Config hazelcastConfig = new com.hazelcast.config.Config();
	
	public Config() {
		hazelcastConfig.getCPSubsystemConfig().setCPMemberCount(3);
		
		hazelcastConfig.getMapConfig("default")
			// we know for sure that entries in global txMap cannot be overridden, so we are safe to read from backups
			.setReadBackupData(true)
			.setBackupCount(0)
			.setAsyncBackupCount(2);
		
		hazelcastConfig.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
		hazelcastConfig.getNetworkConfig().getJoin().getKubernetesConfig().setEnabled(true);
			  // TODO how to make this conveniently configurable
		      // .setProperty("service-dns", "raft-chainvayler-sample.raft.svc.cluster.local"); 			
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

	public com.hazelcast.config.Config getHazelcastConfig() {
		return hazelcastConfig;
	}

	public Config setHazelcastConfig(com.hazelcast.config.Config hazelcastConfig) {
		this.hazelcastConfig = hazelcastConfig;
		return this;
	}
	
	public ReplicationConfig getReplicationConfig() {
		return replicationConfig;
	}
	
	public void setReplicationConfig(ReplicationConfig replicationConfig) {
		this.replicationConfig = replicationConfig;
	}

	public static class ReplicationConfig {
		private int txIdReserveSize = 0;

		public int getTxIdReserveSize() {
			return txIdReserveSize;
		}

		public void setTxIdReserveSize(int txIdReserveSize) {
			if (txIdReserveSize < 0)
				throw new IllegalArgumentException("txIdReserveSize: " + txIdReserveSize);
			this.txIdReserveSize = txIdReserveSize;
		}
	}
}
