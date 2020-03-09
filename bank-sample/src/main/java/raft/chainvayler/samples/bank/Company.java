package raft.chainvayler.samples.bank;

import java.io.Serializable;

import raft.chainvayler.Chained;
import raft.chainvayler.Modification;

/**
 * A company.
 * 
 * @author r a f t
 */
@Chained 
public class Company implements Serializable {

	private static final long serialVersionUID = 1L;

	private RichPerson owner = new RichPerson("<no name>");
	
	public Company() {
	}

	public RichPerson getOwner() {
		return owner;
	}

	@Modification
	public void setOwner(RichPerson newOwner) {
		if (this.owner != null) {
			this.owner.removeCompany(this);
		}
		this.owner = newOwner;
		
		if (newOwner != null) {
			newOwner.addCompany(this);
		}
	}
	
}
