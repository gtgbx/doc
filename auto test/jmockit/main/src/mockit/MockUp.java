/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit;

import java.lang.reflect.*;
import java.util.*;

import org.jetbrains.annotations.*;

import mockit.internal.mockups.*;
import mockit.internal.startup.*;
import mockit.internal.state.*;
import mockit.internal.util.*;

/**
 * A base class used in the creation of a <em>mock-up</em> for a class or interface.
 * Such mock-ups can be used in <em>state-based</em> unit tests or as <em>fake</em> implementations for use in
 * integration tests.
 * <pre>
 * // Define and apply one or more mock-ups.
 * new MockUp&lt;<strong>SomeClass</strong>>() {
 *    &#64;Mock int someMethod(int i) { assertTrue(i > 0); return 123; }
 *    &#64;Mock(maxInvocations = 2) void anotherMethod(int i, String s) { &#47;* validate arguments *&#47; }
 * };
 *
 * // Exercise code under test.
 * codeUnderTest.doSomething();
 * </pre>
 * One or more <em>mock methods</em> annotated {@linkplain Mock as such} must be defined in the concrete subclass.
 * Each {@code @Mock} method should have a matching method or constructor in the mocked class/interface.
 * At runtime, the execution of a mocked method/constructor will get redirected to the corresponding mock method.
 * <p/>
 * When the type to be mocked is specified indirectly through a {@linkplain TypeVariable type variable}, there are two
 * other possible outcomes:
 * <ol>
 * <li>If the type variable "<code>extends</code>" two or more interfaces, a mocked proxy class that implements all
 * interfaces is created, with the proxy instance made available through a call to {@link #getMockInstance()}.
 * Example:
 * <pre>
 * &#64;Test
 * public &lt;<strong>M extends Runnable & ResultSet</strong>> void someTest() {
 *     M mock = new MockUp&lt;<strong>M</strong>>() {
 *        &#64;Mock void run() { ...do something... }
 *        &#64;Mock boolean next() { return true; }
 *     }.getMockInstance();
 *
 *     mock.run();
 *     assertTrue(mock.next());
 * }
 * </pre>
 * </li>
 * <li>If the type variable extends a <em>single</em> type (either an interface or a class), then that type is taken
 * as a <em>base</em> type whose concrete implementation classes should <em>also</em> get mocked.
 * Example:
 * <pre>
 * &#64;Test
 * public &lt;<strong>BC extends SomeBaseClass</strong>> void someTest() {
 *     new MockUp&lt;<strong>BC</strong>>() {
 *        &#64;Mock int someMethod(int i) { return i + 1; }
 *     };
 *
 *     int i = new AConcreteSubclass().someMethod(1);
 *     assertEquals(2, i);
 * }
 * </pre>
 * </li>
 * </ol>
 *
 * @param <T> specifies the type (class, interface, etc.) to be mocked; multiple interfaces can be mocked by defining
 * a <em>type variable</em> in the test class or test method, and using it as the type argument;
 * if a type variable is used but it extends a single type, then all implementation classes extending/implementing that
 * base type are also mocked;
 * if the type argument itself is a parameterized type, then only its raw type is considered for mocking
 *
 * @see #MockUp()
 * @see #MockUp(Class)
 * @see #getMockInstance()
 * @see #tearDown()
 * @see <a href="http://jmockit.googlecode.com/svn/trunk/www/tutorial/StateBasedTesting.html#setUp">Tutorial</a>
 */
public abstract class MockUp<T>
{
   static { Startup.verifyInitialization(); }

   private Set<Class<?>> classesToRestore;
   private final T mockInstance;

   /**
    * Applies the {@linkplain Mock mock methods} defined in the concrete subclass to the class or interface specified
    * through the type parameter.
    *
    * @throws IllegalArgumentException if no type to be mocked was specified;
    * or if multiple types were specified through a type variable but not all of them are interfaces;
    * or if there is a mock method for which no corresponding real method or constructor is found;
    * or if the real method matching a mock method is {@code abstract}
    *
    * @see #MockUp(Class)
    */
   protected MockUp()
   {
      validateMockingAllowed();
      Type typeToMock = validateTypeToMock();

      tearDownPreviousMockUpIfSameMockClassAlreadyApplied();

      if (typeToMock instanceof Class<?>) {
         //noinspection unchecked
         Class<T> classToMock = (Class<T>) typeToMock;
         mockInstance = redefineClassOrImplementInterface(classToMock, typeToMock);
      }
      else if (typeToMock instanceof ParameterizedType) {
         ParameterizedType parameterizedType = (ParameterizedType) typeToMock;
         //noinspection unchecked
         Class<T> realClass = (Class<T>) parameterizedType.getRawType();
         mockInstance = redefineClassOrImplementInterface(realClass, typeToMock);
      }
      else {
         Type[] typesToMock = ((TypeVariable<?>) typeToMock).getBounds();

         if (typesToMock.length > 1) {
            mockInstance = createMockInstanceForMultipleInterfaces(typesToMock);
         }
         else {
            mockInstance = new CaptureOfMockedUpImplementations(this, typesToMock[0]).apply();
         }
      }
   }

   private void validateMockingAllowed()
   {
      if (TestRun.isInsideNoMockingZone()) {
         throw new IllegalStateException("Invalid place to apply a mock-up");
      }
   }

   @NotNull
   private Type validateTypeToMock()
   {
      Type typeToMock = getTypeToMock();

      if (typeToMock instanceof WildcardType || typeToMock instanceof GenericArrayType) {
         String errorMessage = "Argument " + typeToMock + " for type parameter T of an unsupported kind";
         throw new UnsupportedOperationException(errorMessage);
      }

      return typeToMock;
   }

   @NotNull
   private Type getTypeToMock()
   {
      Class<?> currentClass = getClass();

      do {
         Type superclass = currentClass.getGenericSuperclass();

         if (superclass instanceof ParameterizedType) {
            return ((ParameterizedType) superclass).getActualTypeArguments()[0];
         }
         else if (superclass == MockUp.class) {
            throw new IllegalArgumentException("No type to be mocked");
         }

         currentClass = (Class<?>) superclass;
      }
      while (true);
   }

   private void tearDownPreviousMockUpIfSameMockClassAlreadyApplied()
   {
      Class<?> mockClass = getClass();
      MockUp<?> previousMock = TestRun.getMockClasses().findMock(mockClass);

      if (previousMock != null) {
         previousMock.tearDown();
      }
   }

   @Nullable
   private T redefineClassOrImplementInterface(@NotNull Class<T> classToMock, @Nullable Type typeToMock)
   {
      if (classToMock.isInterface()) {
         return new MockedImplementationClass<T>(this).generate(classToMock, typeToMock);
      }

      classesToRestore = redefineMethods(classToMock, typeToMock);
      return null;
   }

   @Nullable private Set<Class<?>> redefineMethods(@NotNull Class<T> realClass, @Nullable Type mockedType)
   {
      return new MockClassSetup(realClass, mockedType, this, null).redefineMethods();
   }

   @NotNull private T createMockInstanceForMultipleInterfaces(@NotNull Type[] interfacesToMock)
   {
      T proxy = EmptyProxy.Impl.newEmptyProxy(null, interfacesToMock);
      //noinspection unchecked
      Class<T> proxyClass = (Class<T>) proxy.getClass();
      redefineMethods(proxyClass, null);
      return proxy;
   }

   /**
    * Applies the {@linkplain Mock mock methods} defined in the concrete subclass to the given class/interface.
    * <p/>
    * In most cases, the constructor with no parameters can be used. This variation should be used only when the type
    * to be mocked is not accessible or known to the test.
    *
    * @see #MockUp()
    */
   @SuppressWarnings("unchecked")
   protected MockUp(Class<?> classToMock)
   {
      validateMockingAllowed();
      tearDownPreviousMockUpIfSameMockClassAlreadyApplied();

      if (classToMock.isInterface()) {
         mockInstance = new MockedImplementationClass<T>(this).generate((Class<T>) classToMock, classToMock);
      }
      else {
         classesToRestore = redefineMethods((Class<T>) classToMock, null);
         mockInstance = null;
      }
   }

   /**
    * Returns the mock instance created for the mocked interface(s), or {@code null} if a class was specified to be
    * mocked instead.
    * This mock instance belongs to a dynamically generated class which implements the mocked interface(s).
    * <p/>
    * For a given mock-up instance, this method always returns the same mock instance.
    * <p/>
    * All methods in the generated implementation class are empty, with non-void methods returning a default value
    * according to the return type: {@literal 0} for {@code int}, {@literal null} for a reference type, and so on.
    * <p/>
    * The {@code equals}, {@code hashCode}, and {@code toString} methods inherited from {@code java.lang.Object} are
    * overridden with an appropriate implementation in each case:
    * {@code equals} is implemented by comparing the two object references (the mock instance and the method argument)
    * for equality; {@code hashCode} is implemented to return the identity hash code for the mock instance; and
    * {@code toString} returns the standard string representation that {@code Object#toString} would have returned.
    *
    * @see <a href="http://jmockit.googlecode.com/svn/trunk/www/tutorial/StateBasedTesting.html#interfaces">Tutorial</a>
    */
   public final T getMockInstance() { return mockInstance; }

   /**
    * Discards the mock methods originally set up by instantiating this mock-up object, restoring mocked methods to
    * their original behaviors.
    * <p/>
    * JMockit will automatically restore classes mocked by a test at the end of its execution, as well as classes
    * mocked for the whole test class before the first test in the next test class is executed.
    * Therefore, this method should rarely be used, if ever.
    */
   public final void tearDown()
   {
      if (classesToRestore != null) {
         TestRun.mockFixture().restoreAndRemoveRedefinedClasses(classesToRestore);
         TestRun.getMockClasses().removeMock(this);
         classesToRestore = null;
      }
   }
}
