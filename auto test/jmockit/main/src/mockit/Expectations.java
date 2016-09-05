/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit;

import java.util.*;

import org.jetbrains.annotations.*;

import mockit.internal.expectations.*;

/**
 * Used to record <em>strict</em> expectations on {@linkplain Mocked mocked} types and mocked instances.
 * It should be noted that strict expectations are rather stringent, and can lead to brittle tests.
 * Users should first consider <em>non-strict</em> expectations instead, which can be recorded with the
 * {@link NonStrictExpectations} class.
 * <p/>
 * A recorded expectation is intended to match one or more method or constructor invocations, that we expect will occur
 * during the execution of some code under test.
 * When a match is detected, the recorded {@linkplain #result result} is returned to the caller.
 * Alternatively, a recorded exception/error is thrown, or an arbitrary {@linkplain Delegate delegate} method is
 * executed.
 * Expectations are recorded simply by invoking the desired method or constructor on the mocked type/instance, during
 * the initialization of an {@code Expectations} object.
 * Typically, this is done by instantiating an anonymous subclass containing an instance initialization body, or as we
 * call it, an <em>expectation block</em>:
 * <pre>
 * // <em>Record</em> one or more expectations on available mocked types/instances.
 * new Expectations() {{
 *    <strong>mock1</strong>.expectedMethod(anyInt); result = 123; times = 2;
 *    <strong>mock2</strong>.anotherExpectedMethod(1, "test"); result = new String[] {"Abc", "xyz"};
 * }};
 *
 * // Exercise tested code, with previously recorded expectations now available for <em>replay</em>.
 * codeUnderTest.doSomething();
 * </pre>
 * During replay, invocations matching recorded expectations must occur in the exact same number and order as specified
 * in the expectation block.
 * Invocations that don't match any recorded expectation, on the other hand, will cause an "unexpected invocation" error
 * to be thrown.
 * Even more, if an expectation was recorded but no matching invocations occurred, a "missing invocation" error will
 * be thrown at the end of the test.
 * <p/>
 * There are several special fields and methods which can be used in the expectation block, to: a) record desired return
 * values or exceptions/errors to be thrown ({@link #result}, {@link #returns(Object, Object...)}); b) relax or
 * constrain the matching of argument values ({@link #anyInt}, {@link #anyString}, {@link #withNotNull()}, etc.);
 * c) relax or constrain the expected and/or allowed number of matching invocations ({@link #times}, {@link #minTimes},
 * {@link #maxTimes}).
 * <p/>
 * By default, the exact instance on which instance method invocations will occur is <em>not</em> verified to be the
 * same as the instance used when recording the expectation.
 * That said, instance-specific matching can be obtained by annotating the mock field/parameter as
 * {@linkplain Injectable @Injectable}, or by using the {@link #onInstance(Object)} method.
 *
 * @see #Expectations()
 * @see #Expectations(Object...)
 * @see #Expectations(Integer, Object...)
 * @see <a href="http://jmockit.googlecode.com/svn/trunk/www/tutorial/BehaviorBasedTesting.html#strictness">Tutorial</a>
 */
public abstract class Expectations extends Invocations
{
   @NotNull private final RecordAndReplayExecution execution;

   /**
    * A value assigned to this field will be taken as the result for the expectation that is being recorded.
    * <p/>
    * If the value is a {@link Throwable} then it will be <em>thrown</em> when a matching invocation later occurs.
    * Otherwise, it's assumed to be a <em>return value</em> for a non-<code>void</code> method, and will be returned
    * from a matching invocation.
    * <p/>
    * If no result is recorded for a given expectation, then all matching invocations will return the appropriate
    * default value according to the method return type:
    * <ul>
    * <li>{@code String}: returns {@code null}.</li>
    * <li>Primitive or primitive wrapper: the standard default value is returned (ie {@code false} for
    * {@code boolean/Boolean}, {@code '\0'} for {@code char/Character}, {@code 0} for {@code int/Integer}, and so on).
    * </li>
    * <li>{@code java.util.Collection} or {@code java.util.List}: returns {@link Collections#EMPTY_LIST}.</li>
    * <li>{@code java.util.Set}: returns {@link Collections#EMPTY_SET}.</li>
    * <li>{@code java.util.SortedSet}: returns an unmodifiable empty sorted set.</li>
    * <li>{@code java.util.Map}: returns {@link Collections#EMPTY_MAP}.</li>
    * <li>{@code java.util.SortedMap}: returns an unmodifiable empty sorted map.</li>
    * <li>A reference type, except for the collection types above: returns {@code null}.</li>
    * <li>An array type: returns an array with zero elements (empty) in each dimension.</li>
    * </ul>
    * <p/>
    * When an expectation is recorded for a method which actually <em>returns</em> an exception or error (as opposed to
    * <em>throwing</em> one), then the {@link #returns(Object, Object...)} method should be used instead, as it only
    * applies to return values.
    * <p/>
    * Assigning a value whose type differs from the method return type will cause an {@code IllegalArgumentException} to
    * be thrown, unless it can be safely converted to the return type.
    * One such conversion is from an array to a collection or iterator.
    * Another is from an array of at least two dimensions to a map, with the first dimension providing the keys and the
    * second the values.
    * Yet another conversion is from a single value to a container type holding that value.
    * <p/>
    * Additionally, if the value assigned to the field is an array or is of a type assignable to {@link Iterable} or
    * {@link Iterator}, and the return type is single-valued, then the assigned multi-valued result is taken as a
    * sequence of <em>consecutive results</em> for the expectation.
    * <p/>
    * Results that depend on some programming logic can be provided through a {@linkplain mockit.Delegate} object
    * assigned to the field.
    * This applies to {@code void} and non-<code>void</code> methods, as well as to constructors.
    * <p/>
    * Finally, when recording an expectation on a <em>constructor</em> of a mocked class, an arbitrary instance of said
    * class can be assigned to the field.
    * In this case, the assigned instance will be used as a "replacement" for all invocations to
    * <em>instance methods</em> made on <em>other</em> instances, provided they get created sometime later through a
    * matching constructor invocation.
    *
    * @see #returns(Object, Object...)
    * @see <a href="http://jmockit.googlecode.com/svn/trunk/www/tutorial/BehaviorBasedTesting.html#results">Tutorial</a>
    */
   protected Object result;

   /**
    * Registers a sequence of one or more strict expectations recorded on available mocked types and/or mocked
    * instances, as written inside the instance initialization body of an anonymous subclass or the called constructor
    * of a named subclass.
    *
    * @see #Expectations(Object...)
    * @see #Expectations(Integer, Object...)
    */
   protected Expectations()
   {
      execution = new RecordAndReplayExecution(this, (Object[]) null);
   }

   /**
    * Same as {@link #Expectations()}, except that one or more classes will be partially mocked according to the
    * expectations recorded in the expectation block; this feature is known as <em>dynamic</em> partial mocking, in
    * contrast with <em>static</em> partial mocking as specified with the {@link Mocked#value} annotation attribute.
    * <p/>
    * The classes to be partially mocked are those directly specified through their {@code Class} objects as well as
    * those to which any given objects belong.
    * During replay, any invocations to one of these classes or objects will execute real production code, unless a
    * matching expectation was recorded.
    * <p/>
    * For a given {@code Class} object, all constructors and methods will be considered for mocking, from the specified
    * class up to but not including {@code java.lang.Object}.
    * <p/>
    * For a given <em>object</em>, all methods will be considered for mocking, from the concrete class of the given
    * object up to but not including {@code java.lang.Object}.
    * The constructors of those classes will <em>not</em> be considered.
    * During replay, invocations to instance methods will only match expectations recorded on the given instance
    * (or instances, if more than one was given).
    *
    * @param classesOrObjectsToBePartiallyMocked one or more classes or objects whose classes are to be considered for
    * partial mocking
    *
    * @throws IllegalArgumentException if given a class literal for an interface, an annotation, an array, a
    * primitive/wrapper type, or a {@linkplain java.lang.reflect.Proxy#isProxyClass(Class) proxy class} created for an
    * interface, or if given a value/instance of such a type
    * 
    * @see #Expectations(Integer, Object...)
    * @see <a href="http://jmockit.googlecode.com/svn/trunk/www/tutorial/BehaviorBasedTesting.html#dynamicPartial">Tutorial</a>
    */
   protected Expectations(Object... classesOrObjectsToBePartiallyMocked)
   {
      execution = new RecordAndReplayExecution(this, classesOrObjectsToBePartiallyMocked);
   }

   /**
    * Same as {@link #Expectations(Object...)}, but considering that the invocations inside the block will occur in a
    * given number of iterations.
    * <p/>
    * The effect of specifying a number of iterations larger than 1 (one) is equivalent to duplicating (like in "copy &
    * paste") the whole sequence of expectations in the block.
    * <p/>
    * It's also valid to have multiple expectation blocks for the same test, each with an arbitrary number of
    * iterations.
    *
    * @param numberOfIterations the positive number of iterations for the whole set of expectations recorded inside the
    * block; when not specified, 1 (one) iteration is assumed
    * @param classesOrObjectsToBePartiallyMocked one or more classes or objects whose classes are to be considered for
    * partial mocking
    *
    * @see #Expectations()
    * @see <a href="http://jmockit.googlecode.com/svn/trunk/www/tutorial/BehaviorBasedTesting.html#iteratedExpectations">Tutorial</a>
    */
   protected Expectations(Integer numberOfIterations, Object... classesOrObjectsToBePartiallyMocked)
   {
      this(classesOrObjectsToBePartiallyMocked);
      getCurrentPhase().setNumberOfIterations(numberOfIterations);
   }

   @Override
   @NotNull final RecordPhase getCurrentPhase() { return execution.getRecordPhase(); }

   /**
    * Specifies that the previously recorded method invocation will return a given sequence of values during replay.
    * <p/>
    * Calling this method is equivalent to assigning the {@link #result} field two or more times in sequence, or
    * assigning it a single time with an array or iterable containing the same sequence of values.
    * <p/>
    * Certain data conversions will be applied, depending on the return type of the recorded method:
    * <ol>
    * <li>If the return type is iterable and can receive a {@link List} value, then the given sequence of values will be
    * converted into an {@code ArrayList}; this list will then be returned by matching invocations at replay time.</li>
    * <li>If the return type is {@code SortedSet} or a sub-type, then the given sequence of values will be converted
    * into a {@code TreeSet}; otherwise, if it is {@code Set} or a sub-type, then a {@code LinkedHashSet} will be
    * created to hold the values; the set will then be returned by matching invocations at replay time.</li>
    * <li>If the return type is {@code Iterator} or a sub-type, then the given sequence of values will be converted into
    * a {@code List} and the iterator created from this list will be returned by matching invocations at replay
    * time.</li>
    * <li>If the return type is an array, then the given sequence of values will be converted to an array of the same
    * type, which will be returned by matching invocations at replay time.</li>
    * </ol>
    * The current expectation will have its upper invocation count automatically set to the total number of values
    * specified to be returned.
    * This upper limit can be overridden through the {@code maxTimes} field, if necessary.
    * <p/>
    * If this method is used for a constructor or {@code void} method, the given return values will be ignored,
    * but matching invocations will be allowed during replay; they will simply do nothing.
    *
    * @param firstValue the first value to be returned at replay time
    * @param remainingValues the remaining values to be returned, in the same order
    *
    * @see <a href="http://jmockit.googlecode.com/svn/trunk/www/tutorial/BehaviorBasedTesting.html#results">Tutorial</a>
    */
   protected final void returns(Object firstValue, Object... remainingValues)
   {
      getCurrentPhase().addSequenceOfReturnValues(firstValue, remainingValues);
   }
}
