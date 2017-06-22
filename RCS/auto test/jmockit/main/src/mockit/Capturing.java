/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit;

import java.lang.annotation.*;

/**
 * Indicates a mock field or a mock parameter for which all classes extending/implementing the
 * {@linkplain Mocked mocked} type will <em>also</em> get mocked.
 * <p/>
 * <em>Future</em> instances of a captured class (ie, instances created sometime later during the test) are also said to
 * be captured.
 * Once captured, they become associated with the corresponding mock field/parameter, and are considered as equivalent
 * to the original mock instance created for the mock field/parameter, when matching invocations to expectations.
 * <p/>
 * The {@link #maxInstances} attribute allows an upper limit to the number of captured instances to be specified.
 * If multiple capturing mock fields/parameters of the same type are declared, this attribute can be used so that each
 * distinct instance gets associated with a separate mock field/parameter.
 * <p/>
 * Note that, once a capturing mocked type is in scope, the capture of implementation classes and their instances can
 * happen at any moment before the first expected invocation is recorded, or during the recording and replay phases.
 *
 * @see <a href="http://jmockit.googlecode.com/svn/trunk/www/tutorial/BehaviorBasedTesting.html#capturing">Tutorial</a>
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface Capturing
{
   /**
    * This attribute specifies the maximum number of new instances to <em>capture</em> while the test is running,
    * between those instances which are assignable to the mocked type and are created during the test.
    * <p/>
    * Every new instance that gets captured is automatically associated with the corresponding mock field or mock
    * parameter.
    * When matching invocations to recorded or verified expectations, such captured instances are regarded as equivalent
    * to the original mocked instance created for the mock field/parameter.
    * <p/>
    * It is valid to declare two or more mock fields/parameters of the same mocked type with a positive number of
    * {@code maxInstances} for each one of them, say {@code n1}, {@code n2}, etc.
    * In this case, the first {@code n1} new instances will be associated with the first field/parameter, the following
    * {@code n2} new instances to the second, and so on.
    */
   int maxInstances() default Integer.MAX_VALUE;
}
