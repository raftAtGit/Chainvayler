package raft.chainvayler.samples.bank;

import java.io.Serializable;

import raft.chainvayler.Chained;
import raft.chainvayler.Modification;

/**
 * A person.
 * 
 * @author r a f t
 */
@Chained
public class Person implements Serializable {
	private static final long serialVersionUID = 1L;

	private String name;
	private String phone;

	public Person() {
	}	
	
	public Person(String name) {
		this(); 
		this.name = name;
		
		if ("HellBoy".equals(name))
			throw new IllegalArgumentException(name);
	}

	public String getPhone() {
		return phone;
	}

	@Modification
	public void setPhone(String phone) {
		this.phone = phone;
	}

	public String getName() {
		return name;
	}

	@Modification
	public void setName(String name) {
		this.name = name;
	}
}
