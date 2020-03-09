package raft.chainvayler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Includes a class and its package to be scanned. Can be in any @Chained class. This is typically required when 
 * some classes cannot be reached by class and package scanning.  
 * 
 * @author  r a f t
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Include {
	
	//TODO is there a way to say this?
	//Class<? extends Chained>[] value();
	
	Class<?>[] value();
}
