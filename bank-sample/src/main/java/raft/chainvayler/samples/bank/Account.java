package raft.chainvayler.samples.bank;

import java.io.Serializable;

import raft.chainvayler.Chained;
import raft.chainvayler.Modification;

/**
 * An account in a bank
 * 
 * @author  r a f t
 */
@Chained
public class Account implements Serializable {

	private static final long serialVersionUID = 1L;

	private final int id;
	private String name;
	private Customer owner;
	private int balance = 0;

	Account(int id) throws Exception {
		this.id = id;
	}	
	
	public int getBalance() {
		return balance;
	}

	public int getId() {
		return id;
	}

	public Customer getOwner() {
		return owner;
	}

	void setOwner(Customer owner) {
		this.owner = owner;
	}

	public String getName() {
		return name;
	}

	@Modification
	public void setName(String name) {
		this.name = name;
	}
	
	@Modification
	public void deposit(int amount) {
		if (amount <= 0)
			throw new IllegalArgumentException("amount: " + amount);
		this.balance += amount;
	}
	
	@Modification
	public void withdraw(int amount) {
		if (amount <= 0)
			throw new IllegalArgumentException("amount: " + amount);
		if (balance < amount)
			throw new IllegalArgumentException("balance < amount");
		
		this.balance -= amount;
	}
	
	
}
