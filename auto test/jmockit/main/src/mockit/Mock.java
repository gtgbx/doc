/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit;

import java.lang.annotation.*;

/**
 * Used inside a {@linkplain MockUp mock-up} class to indicate a <em>mock method</em> whose implementation will
 * temporarily replace the implementation of a matching "real" method.
 * <p/>
 * The mock method must have the same name and the same parameters as the matching real method, except for an optional
 * first parameter of type {@link Invocation}; if this extra parameter is present, the remaining ones must match the
 * parameters in the real method.
 * The mock method must also have the same return type as the matching real method.
 * <p/>
 * Method modifiers (<code>public</code>, <code>final</code>, <code>static</code>, etc.) between mock and mocked
 * methods <em>don't</em> have to be the same.
 * It's perfectly fine to have a non-<code>static</code> mock method for a {@code static} mocked method (or vice-versa),
 * for example.
 * <p/>
 * Checked exceptions in the <code>throws</code> clause (if any) can also differ between the two matching methods.
 * A mock <em>method</em> can also target a <em>constructor</em>, in which case the previous considerations still apply,
 * except for the name of the mock method which must be "<code>$init</code>".
 * <p/>
 * A mock method can specify <em>constraints</em> on the number of invocations it should receive while in effect
 * (ie, from the time a real method/constructor is mocked to the time it is restored to its original definition).
 * <p/>
 * The special mock methods <strong>{@code void $init(...)}</strong> and <strong>{@code void $clinit()}</strong>
 * correspond to constructors and to {@code static} class initializers, respectively.
 * (Notice that it makes no difference if the real class contains more than one static initialization block, because the
 * compiler merges the sequence of static blocks into a single internal "&lt;clinit>" static method in the class file.)
 * Mock methods named {@code $init} will apply to the corresponding constructor in the real class, by matching the
 * declared parameters; just like regular mock methods, they can also have a first parameter of type {@link Invocation}.
 *
 * @see #invocations invocations
 * @see #minInvocations minInvocations
 * @see #maxInvocations maxInvocations
 * @see <a href="http://jmockit.googlecode.com/svn/trunk/www/tutorial/StateBasedTesting.html#mocks">Tutorial</a>
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Mock
{
   /**
    * Number of expected invocations of the mock method.
    * If 0 (zero), no invocations will be expected.
    * A negative value (the default) means there is no expectation on the number of invocations;
    * that is, the mock can be called any number of times or not at all during any test which uses it.
    * <p/>
    * A non-negative value is equivalent to setting {@link #minInvocations minInvocations} and
    * {@link #maxInvocations maxInvocations} to that same value.
    *
    * @see <a href="http://jmockit.googlecode.com/svn/trunk/www/tutorial/StateBasedTesting.html#constraints">Tutorial</a>
    */
   int invocations() default -1;

   /**
    * Minimum number of expected invocations of the mock method, starting from 0 (zero, which is the default).
    * 
    * @see #invocations invocations
    * @see #maxInvocations maxInvocations
    * @see <a href="http://jmockit.googlecode.com/svn/trunk/www/tutorial/StateBasedTesting.html#constraints">Tutorial</a>
    */
   int minInvocations() default 0;

   /**
    * Maximum number of expected invocations of the mock method, if positive.
    * If zero the mock is not expected to be called at all.
    * A negative value (the default) means there is no expectation on the maximum number of invocations.
    * 
    * @see #invocations invocations
    * @see #minInvocations minInvocations
    * @see <a href="http://jmockit.googlecode.com/svn/trunk/www/tutorial/StateBasedTesting.html#constraints">Tutorial</a>
    */
   int maxInvocations() default -1;
}
