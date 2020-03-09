package raft.chainvayler.impl;

import java.util.Date;

import raft.chainvayler.Clock;

/** Base class for {@link Clock} for limiting {@link #setDate(Date)} method to package. */
public abstract class ClockBase {

	private static final ThreadLocal<Date> date  = new ThreadLocal<Date>() {
		@Override
		protected Date initialValue() {
			return null;
		};
	};

	static void setDate(Date d) {
		date.set(d);
	}
	
	static protected Date getDate() {
		return date.get();
	}
}
