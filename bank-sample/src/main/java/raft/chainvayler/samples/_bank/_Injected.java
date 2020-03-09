package raft.chainvayler.samples._bank;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * 
 * Dummy annotation to mark injected methods and fields
 * 
 * @author r a f t
 */
@Target({ ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR } )
public @interface _Injected {

	String value() default "";
}
