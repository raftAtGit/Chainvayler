package raft.chainvayler.samples.bank;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import raft.chainvayler.Chained;
import raft.chainvayler.Modification;
import raft.chainvayler.Synch;
import raft.chainvayler.impl.Utils;

/**
 * A customer.
 * 
 * @author r a f t
 */
@Chained
public class Customer extends Person {
	private static final long serialVersionUID = 1L;

	private int id;

	private final Map<Integer, Account> accounts = new TreeMap<Integer, Account>();
	
	public Customer(String name) {
		super(name);
	}
	
	public int getId() {
		return id;
	}

	void setId(int id) {
		this.id = id;
	}
	
	@Modification
	public void addAccount(Account account) {
		if (account.getOwner() != null)
			throw new IllegalStateException("Account already has an owner");
		
		accounts.put(account.getId(), account);
	}
	
	@Synch
	public List<Account> getAccounts() {
		return new ArrayList<Account>(accounts.values());
	}

	@Override
	public String toString() {
		return String.format("%s, id: %s, name: %s", Utils.identityCode(this), id, getName());
	}
	
}
