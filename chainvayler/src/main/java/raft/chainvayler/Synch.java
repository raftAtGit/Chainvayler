package raft.chainvayler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>Marks a method to be synchronized with @{@link Modification} methods. Just like @Modification 
 * methods, @Synch methods are also synchronized on persistence root.</p>  
 * 
 * <p>It's not allowed to call directly or indirectly a @Modification method inside a @Synch method 
 * and will result in a {@link ModificationInSynchException}. </p>
 * 
 * @see Modification
 * @see ModificationInSynchException
 * 
 * @author  r a f t
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Synch {

}
