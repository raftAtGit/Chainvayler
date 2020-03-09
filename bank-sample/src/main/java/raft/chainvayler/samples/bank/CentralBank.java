package raft.chainvayler.samples.bank;

import raft.chainvayler.Chained;
import raft.chainvayler.Modification;

/**
 * Central bank.
 * 
 * @author r a f t
 */
@Chained
public class CentralBank extends Bank {

	private static final long serialVersionUID = 1L;
	
	private Person accountant;

	public CentralBank() {
		super();
		accountant = new Person();
	}

	public Person getAccountant() {
		return accountant;
	}

	@Modification
	public void setAccountant(Person accountant) {
		this.accountant = accountant;
	}

	
}
