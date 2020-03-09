package raft.chainvayler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as modification. {@link Chainvayler} records calls to such methods with parameters 
 * and executes them again in the same order on other peers and also when system is restarted. The calls are  
 * synchronized on chained root. 
 * 
 * @see Chained
 * @see Synch
 * 
 * @author  r a f t
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Modification {

}
