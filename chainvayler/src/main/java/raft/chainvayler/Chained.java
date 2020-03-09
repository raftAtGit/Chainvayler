package raft.chainvayler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as Chained. Chained is inherited so subclasses of a chained class 
 * is also chained. But it's a good practice to annotate them too. 
 * 
 * @see Modification
 * 
 * @author r a f t
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface Chained {

}
