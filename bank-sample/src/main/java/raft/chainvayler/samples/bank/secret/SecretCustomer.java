package raft.chainvayler.samples.bank.secret;

import raft.chainvayler.Chained;
import raft.chainvayler.impl.Utils;
import raft.chainvayler.samples.bank.Customer;

@Chained
public class SecretCustomer extends Customer {

	private static final long serialVersionUID = 1L;

	public SecretCustomer() {
		super("anonymous");
	}

	@Override
	public String getName() {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public String toString() {
		return String.format("%s, id: %s", Utils.identityCode(this), getId());
	}
}

