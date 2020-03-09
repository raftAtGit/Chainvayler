package raft.chainvayler.samples.bank.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public interface PeerManager extends Remote {

	void registerPeer(Peer peer) throws Exception;
	
	void unregisterPeer(Peer peer) throws Exception;
	
	List<Peer> getPeers() throws Exception;

	public static class Impl extends UnicastRemoteObject implements PeerManager {

		private static final long serialVersionUID = 1L;
		
		private final List<Peer> peers = new LinkedList<>();

		public Impl() throws RemoteException {
		}

		@Override
		public void registerPeer(Peer peer) throws Exception {
			synchronized (peers) {
				peers.add(peer);
				System.out.printf("registered peer, count: %s \n", peers.size());
			}
		}

		@Override
		public void unregisterPeer(Peer peer) throws Exception {
			synchronized (peers) {
				boolean removed = peers.remove(peer);
				System.out.printf("unregistered peer, removed: %s, count: %s \n", removed, peers.size());
			}
		}
		
		@Override
		public List<Peer> getPeers() throws Exception {
			synchronized (peers) {
				return new ArrayList<>(peers);
			}
		}
	}
}
