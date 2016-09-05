/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.Assert.*;
import org.junit.*;

import mockit.internal.*;

@SuppressWarnings("deprecation")
public final class DynamicPartialMockingTest
{
   @SuppressWarnings("UnusedDeclaration")
   @Deprecated
   static class Collaborator
   {
      @Deprecated
      protected int value;

      Collaborator() { value = -1; }
      @Deprecated Collaborator(@Deprecated int value) { this.value = value; }

      final int getValue() { return value; }
      void setValue(int value) { this.value = value; }

      final boolean simpleOperation(int a, String b, Date c) { return true; }
      static void doSomething(boolean b, String s) { throw new IllegalStateException(); }

      @Ignore("test")
      boolean methodWhichCallsAnotherInTheSameClass()
      {
         return simpleOperation(1, "internal", null);
      }
      
      String overridableMethod() { return "base"; }
      @Deprecated native void nativeMethod();

      void readFile(File f) {}
      private void initialize() {}
   }

   interface Dependency
   {
      boolean doSomething();
      List<?> doSomethingElse(int n);
   }

   @Test
   public void dynamicallyMockAClass()
   {
      final Collaborator toBeMocked = new Collaborator();

      new Expectations(Collaborator.class) {{
         toBeMocked.getValue(); result = 123;
      }};

      // Not mocked:
      Collaborator collaborator = new Collaborator();
      assertEquals(-1, collaborator.value);
      assertTrue(collaborator.simpleOperation(1, "b", null));
      assertEquals(45, new Collaborator(45).value);

      // Mocked:
      assertEquals(123, collaborator.getValue());
   }

   @Test
   public void dynamicallyMockJREClass() throws Exception
   {
      new Expectations(ByteArrayOutputStream.class) {{
         new ByteArrayOutputStream().size(); result = 123;
      }};

      // Mocked:
      ByteArrayOutputStream collaborator = new ByteArrayOutputStream();
      assertNull(Deencapsulation.getField(collaborator, "buf"));
      assertEquals(123, collaborator.size());

      // Not mocked:
      ByteArrayOutputStream buf = new ByteArrayOutputStream(200);
      buf.write(65);
      assertEquals("A", buf.toString("UTF-8"));
   }

   @Test
   public void dynamicallyMockClassNonStrictly()
   {
      new NonStrictExpectations(Collaborator.class) {{
         new Collaborator().getValue(); result = 123;
      }};

      // Mocked:
      assertEquals(123, new Collaborator().getValue());

      // Not mocked:
      Collaborator col1 = new Collaborator(200);
      col1.setValue(45);
      assertEquals(45, col1.value);

      // Still mocked:
      assertEquals(123, col1.getValue());

      new Verifications() {{
         Collaborator col2 = new Collaborator(200); times = 1;
         col2.getValue(); times = 2;
      }};
   }

   @Test
   public void dynamicallyMockAMockedClass(@Mocked final Collaborator mock)
   {
      assertEquals(0, mock.value);

      new Expectations(mock) {{
         mock.getValue(); result = 123;
      }};

      // Mocked:
      assertEquals(123, mock.getValue());

      // Not mocked:
      Collaborator collaborator = new Collaborator();
      assertEquals(-1, collaborator.value);
      assertTrue(collaborator.simpleOperation(1, "b", null));
      assertEquals(45, new Collaborator(45).value);
   }

   @Test
   public void dynamicallyMockAnInstance()
   {
      final Collaborator collaborator = new Collaborator();

      new Expectations(collaborator) {{
         collaborator.getValue(); result = 123;
      }};

      // Mocked:
      assertEquals(123, collaborator.getValue());

      // Not mocked:
      assertTrue(collaborator.simpleOperation(1, "b", null));
      assertEquals(45, new Collaborator(45).value);
      assertEquals(-1, new Collaborator().value);
   }

   @Test(expected = MissingInvocation.class)
   public void expectTwoInvocationsOnStrictDynamicMockButReplayOnce()
   {
      final Collaborator collaborator = new Collaborator();

      new Expectations(collaborator) {{
         collaborator.getValue(); times = 2;
      }};

      assertEquals(0, collaborator.getValue());
   }

   @Test
   public void expectOneInvocationOnStrictDynamicMockButReplayTwice()
   {
      final Collaborator collaborator = new Collaborator(1);

      new Expectations(collaborator) {{
         collaborator.methodWhichCallsAnotherInTheSameClass(); result = false;
      }};

      // Mocked:
      assertFalse(collaborator.methodWhichCallsAnotherInTheSameClass());

      // No longer mocked, since it's strict:
      assertTrue(collaborator.methodWhichCallsAnotherInTheSameClass());
   }

   @Test
   public void expectTwoInvocationsOnStrictDynamicMockButReplayMoreTimes()
   {
      final Collaborator collaborator = new Collaborator(1);

      new Expectations(collaborator) {{
         collaborator.getValue(); times = 2;
      }};

      // Mocked:
      assertEquals(0, collaborator.getValue());
      assertEquals(0, collaborator.getValue());

      // No longer mocked, since it's strict and all expected invocations were already replayed:
      assertEquals(1, collaborator.getValue());
   }

   @Test(expected = MissingInvocation.class)
   public void expectTwoOrderedInvocationsOnStrictDynamicMockButReplayOutOfOrder()
   {
      final Collaborator collaborator = new Collaborator(1);

      new Expectations(collaborator) {{
         collaborator.setValue(1);
         collaborator.setValue(2);
      }};

      // Not mocked since the first expectation that can be matched is the one setting the value to 1:
      collaborator.setValue(2);
      assertEquals(2, collaborator.value);

      // Mocked since the first expectation wasn't yet matched by a replayed one:
      collaborator.setValue(1);
      assertEquals(2, collaborator.value);

      // The recorded call to "setValue(2)" is missing at this point.
   }

   @Test(expected = UnexpectedInvocation.class)
   public void nonStrictDynamicMockFullyVerified_verifyOnlyOneOfMultipleRecordedInvocations()
   {
      final Collaborator collaborator = new Collaborator(0);

      new NonStrictExpectations(collaborator) {{
         collaborator.setValue(1);
         collaborator.setValue(2);
      }};

      collaborator.setValue(2);
      collaborator.setValue(1);

      // Verifies all the *mocked* (recorded) invocations, ignoring those not mocked:
      new FullVerifications() {{
         collaborator.setValue(1);
         // Should also verify "setValue(2)" since it was recorded.
      }};
   }

   @Test
   public void nonStrictDynamicMockFullyVerified_verifyAllRecordedExpectationsButNotAllOfTheReplayedOnes()
   {
      final Collaborator collaborator = new Collaborator(0);

      new NonStrictExpectations(collaborator) {{
         collaborator.setValue(1);
      }};

      collaborator.setValue(1);
      collaborator.setValue(2);

      // Verifies all the *mocked* (recorded) invocations, ignoring those not mocked:
      new FullVerifications() {{
         collaborator.setValue(1);
         // No need to verify "setValue(2)" since it was not recorded.
      }};
   }

   @Test
   public void nonStrictDynamicMockFullyVerifiedInOrder_verifyAllRecordedExpectationsButNotAllOfTheReplayedOnes()
   {
      final Collaborator collaborator = new Collaborator(0);

      new NonStrictExpectations(collaborator) {{
         collaborator.setValue(2);
         collaborator.setValue(3);
      }};

      collaborator.setValue(1);
      collaborator.setValue(2);
      collaborator.setValue(3);

      // Verifies all the *mocked* (recorded) invocations, ignoring those not mocked:
      new FullVerificationsInOrder() {{
         // No need to verify "setValue(1)" since it was not recorded.
         collaborator.setValue(2);
         collaborator.setValue(3);
      }};
   }

   @Test
   public void nonStrictDynamicallyMockedClassFullyVerified_verifyRecordedExpectationButNotReplayedOne()
   {
      final Collaborator collaborator = new Collaborator();

      new NonStrictExpectations(Collaborator.class) {{
         collaborator.simpleOperation(1, "internal", null);
         result = false;
      }};

      assertFalse(collaborator.methodWhichCallsAnotherInTheSameClass());

      new FullVerifications() {{
         collaborator.simpleOperation(anyInt, anyString, null);
      }};
   }

   @Test(expected = MissingInvocation.class)
   public void expectTwoInvocationsOnNonStrictDynamicMockButReplayOnce()
   {
      final Collaborator collaborator = new Collaborator();

      new NonStrictExpectations(collaborator) {{
         collaborator.getValue(); times = 2;
      }};

      assertEquals(0, collaborator.getValue());
   }

   @Test(expected = UnexpectedInvocation.class)
   public void expectOneInvocationOnNonStrictDynamicMockButReplayTwice()
   {
      final Collaborator collaborator = new Collaborator(1);

      new NonStrictExpectations(collaborator) {{
         collaborator.getValue(); times = 1;
      }};

      // Mocked:
      assertEquals(0, collaborator.getValue());

      // Still mocked because it's non-strict:
      assertEquals(0, collaborator.getValue());
   }

   @Test
   public void dynamicallyMockAnInstanceWithNonStrictExpectations()
   {
      final Collaborator collaborator = new Collaborator(2);

      new NonStrictExpectations(collaborator) {{
         collaborator.simpleOperation(1, "", null); result = false;
         Collaborator.doSomething(anyBoolean, "test");
      }};

      // Mocked:
      assertFalse(collaborator.simpleOperation(1, "", null));
      Collaborator.doSomething(true, "test");

      // Not mocked:
      assertEquals(2, collaborator.getValue());
      assertEquals(45, new Collaborator(45).value);
      assertEquals(-1, new Collaborator().value);

      try {
         Collaborator.doSomething(false, null);
         fail();
      }
      catch (IllegalStateException ignore) {}

      new Verifications() {{
         Collaborator.doSomething(anyBoolean, "test");
         collaborator.getValue(); times = 1;
      }};
   }

   @Test
   public void mockMethodInSameClass()
   {
      final Collaborator collaborator = new Collaborator();

      new NonStrictExpectations(collaborator) {{
         collaborator.simpleOperation(1, anyString, null); result = false;
      }};

      assertFalse(collaborator.methodWhichCallsAnotherInTheSameClass());
      assertTrue(collaborator.simpleOperation(2, "", null));
      assertFalse(collaborator.simpleOperation(1, "", null));
   }

   static final class SubCollaborator extends Collaborator
   {
      SubCollaborator() { this(1); }
      SubCollaborator(int value) { super(value); }

      @Override
      String overridableMethod() { return super.overridableMethod() + " overridden"; }

      String format() { return String.valueOf(value); }
      static void causeFailure() { throw new RuntimeException(); }
   }

   @Test
   public void dynamicallyMockASubCollaboratorInstance()
   {
      final SubCollaborator collaborator = new SubCollaborator();

      new NonStrictExpectations(collaborator) {{
         collaborator.getValue(); result = 5;
         collaborator.format(); result = "test";
         SubCollaborator.causeFailure();
      }};

      // Mocked:
      assertEquals(5, collaborator.getValue());
      SubCollaborator.causeFailure();

      // Not mocked:
      assertTrue(collaborator.simpleOperation(0, null, null)); // not recorded
      assertEquals("1", new SubCollaborator().format()); // was recorded but on a different instance

      try {
         Collaborator.doSomething(true, null); // not recorded
         fail();
      }
      catch (IllegalStateException ignore) {}
   }

   @Test
   public void dynamicallyMockClassHierarchyForSpecifiedSubclass()
   {
      final SubCollaborator collaborator = new SubCollaborator();

      new NonStrictExpectations(SubCollaborator.class) {{
         collaborator.getValue(); result = 123;
         collaborator.format(); result = "test";
      }};

      // Mocked:
      assertEquals("test", collaborator.format());
      assertEquals(123, collaborator.getValue());

      // Not mocked:
      assertTrue(collaborator.simpleOperation(0, null, null));

      // Mocked sub-constructor/not mocked base constructor:
      assertEquals(-1, new SubCollaborator().value);

      new VerificationsInOrder() {{
         collaborator.format();
         new SubCollaborator();
      }};
   }

   @Test
   public void mockTheBaseMethodWhileExercisingTheOverride()
   {
      final Collaborator collaborator = new Collaborator();
      
      new Expectations(Collaborator.class) {{
         collaborator.overridableMethod(); result = "";
         collaborator.overridableMethod(); result = "mocked";
      }};

      assertEquals("", collaborator.overridableMethod());
      assertEquals("mocked overridden", new SubCollaborator().overridableMethod());
   }

   @Test
   public void dynamicallyMockAnAnonymousClassInstanceThroughTheImplementedInterface()
   {
      final Collaborator collaborator = new Collaborator();

      final Dependency dependency = new Dependency() {
         @Override public boolean doSomething() { return false; }
         @Override public List<?> doSomethingElse(int n) { return null; }
      };
      
      new NonStrictExpectations(collaborator, dependency) {{
         collaborator.getValue(); result = 5;
         dependency.doSomething(); result = true;
      }};

      // Mocked:
      assertEquals(5, collaborator.getValue());
      assertTrue(dependency.doSomething());

      // Not mocked:
      assertTrue(collaborator.simpleOperation(0, null, null));
      assertNull(dependency.doSomethingElse(3));

      new FullVerifications() {{
         dependency.doSomething();
         collaborator.getValue();
         dependency.doSomethingElse(anyInt);
         collaborator.simpleOperation(0, null, null);
      }};
   }

   @Test
   public void dynamicallyMockInstanceOfJREClass()
   {
      final List<String> list = new LinkedList<String>();
      @SuppressWarnings("UseOfObsoleteCollectionType") List<String> anotherList = new Vector<String>();

      new NonStrictExpectations(list, anotherList) {{
         list.get(1); result = "an item";
         list.size(); result = 2;
      }};

      // Use mocked methods:
      assertEquals(2, list.size());
      assertEquals("an item", list.get(1));

      // Use unmocked methods:
      assertTrue(list.add("another"));
      assertEquals("another", list.remove(0));

      anotherList.add("one");
      assertEquals("one", anotherList.get(0));
      assertEquals(1, anotherList.size());
   }

   public interface AnotherInterface {}

   @Test
   public void attemptToUseDynamicMockingForInvalidTypes(@Mocked AnotherInterface mockedInterface)
   {
      assertInvalidTypeForDynamicMocking(Dependency.class);
      assertInvalidTypeForDynamicMocking(Test.class);
      assertInvalidTypeForDynamicMocking(int[].class);
      assertInvalidTypeForDynamicMocking(new String[1]);
      assertInvalidTypeForDynamicMocking(char.class);
      assertInvalidTypeForDynamicMocking(123);
      assertInvalidTypeForDynamicMocking(Boolean.class);
      assertInvalidTypeForDynamicMocking(true);
      assertInvalidTypeForDynamicMocking(2.5);
      assertInvalidTypeForDynamicMocking(mockedInterface);

      final Dependency mockInstance = new MockUp<Dependency>() {}.getMockInstance();
      assertInvalidTypeForDynamicMocking(mockInstance);
   }

   private void assertInvalidTypeForDynamicMocking(Object classOrObject)
   {
      try {
         new Expectations(classOrObject) {};
         fail();
      }
      catch (IllegalArgumentException e) {
         assertTrue(e.getMessage().contains("dynamic mocking"));
      }
   }

   @Test
   public void dynamicPartialMockingWithExactArgumentMatching()
   {
      final Collaborator collaborator = new Collaborator();

      new NonStrictExpectations(collaborator) {{
         collaborator.simpleOperation(1, "s", null); result = false;
      }};

      assertFalse(collaborator.simpleOperation(1, "s", null));
      assertTrue(collaborator.simpleOperation(2, "s", null));
      assertTrue(collaborator.simpleOperation(1, "S", null));
      assertTrue(collaborator.simpleOperation(1, "s", new Date()));
      assertTrue(collaborator.simpleOperation(1, null, new Date()));
      assertFalse(collaborator.simpleOperation(1, "s", null));

      new FullVerifications() {{
         collaborator.simpleOperation(anyInt, null, null);
      }};
   }

   @Test
   public void dynamicPartialMockingWithFlexibleArgumentMatching(@Mocked final Collaborator mock)
   {
      new NonStrictExpectations(mock) {{
         mock.simpleOperation(anyInt, withPrefix("s"), null); result = false;
      }};

      assertFalse(mock.simpleOperation(1, "sSs", null));
      assertTrue(mock.simpleOperation(2, " s", null));
      assertTrue(mock.simpleOperation(1, "S", null));
      assertFalse(mock.simpleOperation(-1, "s", new Date()));
      assertTrue(mock.simpleOperation(1, null, null));
      assertFalse(mock.simpleOperation(0, "string", null));

      Collaborator collaborator = new Collaborator();
      assertTrue(collaborator.simpleOperation(1, "sSs", null));
      assertTrue(collaborator.simpleOperation(-1, null, new Date()));
   }

   @Test
   public void dynamicPartialMockingWithInstanceSpecificMatching()
   {
      final Collaborator collaborator1 = new Collaborator();
      final Collaborator collaborator2 = new Collaborator(4);

      new NonStrictExpectations(collaborator1, collaborator2) {{
         collaborator1.getValue(); result = 3;
      }};

      assertEquals(3, collaborator1.getValue());
      assertEquals(4, collaborator2.getValue());

      new FullVerificationsInOrder() {{
         collaborator1.getValue(); times = 1;
         collaborator2.getValue(); times = 1;
      }};
   }

   @Test
   public void dynamicPartialMockingWithInstanceSpecificMatchingOnTwoInstancesOfSameClass()
   {
      final Collaborator mock1 = new Collaborator();
      final Collaborator mock2 = new Collaborator();

      new NonStrictExpectations(mock1, mock2) {{
         mock1.getValue(); result = 1;
         mock2.getValue(); result = 2;
      }};

      assertEquals(2, mock2.getValue());
      assertEquals(1, mock1.getValue());

      new FullVerifications() {{
         mock1.getValue(); times = 1;
         mock2.getValue(); times = 1;
      }};
   }

   @Test
   public void methodWithNoRecordedExpectationCalledTwiceDuringReplay()
   {
      final Collaborator collaborator = new Collaborator(123);

      new NonStrictExpectations(collaborator) {};

      assertEquals(123, collaborator.getValue());
      assertEquals(123, collaborator.getValue());

      new FullVerifications() {{
         collaborator.getValue(); times = 2;
      }};
   }

   static final class TaskWithConsoleInput
   {
      boolean finished;

      void doIt()
      {
         int input = '\0';

         while (input != 'A') {
            try {
               input = System.in.read();
            }
            catch (IOException e) {
               throw new RuntimeException(e);
            }

            if (input == 'Z') {
               finished = true;
               break;
            }
         }
      }
   }

   private boolean runTaskWithTimeout(long timeoutInMillis) throws InterruptedException, ExecutionException
   {
      final TaskWithConsoleInput task = new TaskWithConsoleInput();
      Runnable asynchronousTask = new Runnable() { @Override public void run() { task.doIt(); } };
      ExecutorService executor = Executors.newSingleThreadExecutor();

      while (!task.finished) {
         Future<?> worker = executor.submit(asynchronousTask);

         try {
            worker.get(timeoutInMillis, TimeUnit.MILLISECONDS);
         }
         catch (TimeoutException ignore) {
            executor.shutdownNow();
            return false;
         }
      }

      return true;
   }

   @Test
   public void taskWithConsoleInputTerminatingNormally() throws Exception
   {
      new Expectations(System.in) {{
         System.in.read(); returns((int) 'A', (int) 'x', (int) 'Z');
      }};

      assertTrue(runTaskWithTimeout(5000));
   }

   @Test
   public void taskWithConsoleInputTerminatingOnTimeout() throws Exception
   {
      new Expectations(System.in) {{
         System.in.read();
         result = new Delegate() {
            @Mock void takeTooLong() throws InterruptedException { Thread.sleep(5000); }
         };
      }};

      assertFalse("no timeout", runTaskWithTimeout(10));
   }

   static class ClassWithStaticInitializer
   {
      static boolean initialized = true;
      static int doSomething() { return initialized ? 1 : -1; }
   }

   @Test
   public void doNotStubOutStaticInitializersWhenDynamicallyMockingAClass()
   {
      new Expectations(ClassWithStaticInitializer.class) {{
         ClassWithStaticInitializer.doSomething(); result = 2;
      }};

      assertEquals(2, ClassWithStaticInitializer.doSomething());
      assertTrue(ClassWithStaticInitializer.initialized);
   }

   static final class ClassWithNative
   {
      int doSomething() { return nativeMethod(); }
      private native int nativeMethod();
   }

   @Test(expected = UnsatisfiedLinkError.class)
   public void attemptToPartiallyMockNativeMethod()
   {
      final ClassWithNative mock = new ClassWithNative();

      new Expectations(mock) {{
         // The native method is ignored when using dynamic mocking, so this actually tries to execute the real method,
         // failing since there is no native implementation.
         mock.nativeMethod();
      }};
   }

   @Test // with FileIO compiled with "target 1.1", this produced a VerifyError
   public void mockClassCompiledForJava11() throws Exception
   {
      final FileIO f = new FileIO();

      new Expectations(f) {{
         f.writeToFile("test");
      }};

      f.writeToFile("test");
   }

   static class Base { Base(boolean b) { if (!b) throw new IllegalAccessError(); } }
   static class Derived extends Base { Derived() { super(true); } }

   @Ignore @Test
   public void mockConstructorsInClassHierarchyWithMockedCallToSuperWhichChecksArgumentReceived()
   {
      new Expectations(Derived.class) {};

      new Derived();
   }

   static class Base2 { final int i; Base2(int i) { this.i = i; } }
   static class Derived2 extends Base2 { Derived2(int i) { super(i); } }

   @Ignore @Test
   public void mockConstructorsInClassHierarchyWithMockedCallToSuper()
   {
      new NonStrictExpectations(Derived2.class) {};

      Derived2 d = new Derived2(123);

      assertEquals(123, d.i);
   }

   static class AClass {
      static int i = -1;
      AClass() { this(123); }
      AClass(int i) { AClass.i = i; }
   }

   @Ignore @Test
   public void mockConstructorsInSingleClassWithMockedCallToThis()
   {
      new NonStrictExpectations(AClass.class) {};

      new AClass();
      assertEquals(123, AClass.i);
   }

   @Test
   public void mockedClassWithAnnotatedElements() throws Exception
   {
      new NonStrictExpectations(Collaborator.class) {};

      Collaborator mock = new Collaborator(123);
      Class<?> mockedClass = mock.getClass();

      assertTrue(mockedClass.isAnnotationPresent(Deprecated.class));
      assertTrue(mockedClass.getDeclaredField("value").isAnnotationPresent(Deprecated.class));

      boolean jreDiscardsAnnotationsOnConstructors = System.getProperty("java.specification.version").equals("1.6");

      if (!jreDiscardsAnnotationsOnConstructors) {
         Constructor<?> mockedConstructor = mockedClass.getDeclaredConstructor(int.class);
         assertTrue(mockedConstructor.isAnnotationPresent(Deprecated.class));
         assertTrue(mockedConstructor.getParameterAnnotations()[0][0] instanceof Deprecated);
      }

      Method mockedMethod = mockedClass.getDeclaredMethod("methodWhichCallsAnotherInTheSameClass");
      Ignore ignore = mockedMethod.getAnnotation(Ignore.class);
      assertNotNull(ignore);
      assertEquals("test", ignore.value());

      assertTrue(mockedClass.getDeclaredMethod("nativeMethod").isAnnotationPresent(Deprecated.class));
   }

   @Test
   public void regularMockedMethodCallingOverriddenEqualsInDynamicallyMockedClass(@Mocked final Collaborator mock)
   {
      @SuppressWarnings("TooBroadScope") final File f = new File("test");

      new NonStrictExpectations(File.class) {};

      mock.readFile(new File("test"));

      new Verifications() {{
         mock.readFile(f);
      }};
   }

   static final class TestedClass
   {
      private boolean value;

      TestedClass() { this(true); }
      TestedClass(boolean value) { initialize(value); }

      private void initialize(boolean value) { this.value = value; }
   }

   @Test
   public void mockClassWithConstructorWhichCallsPrivateMethod()
   {
      new NonStrictExpectations(TestedClass.class) {};

      assertTrue(new TestedClass(true).value);

      final TestedClass t = new TestedClass(false);
      assertFalse(t.value);

      new Verifications() {{
         new TestedClass(anyBoolean); times = 2;
         t.initialize(anyBoolean); times = 2;
      }};
   }
}
