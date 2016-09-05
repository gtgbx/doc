/*
 * Copyright (c) 2006-2013 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package integrationTests;

import static org.junit.Assert.*;
import org.junit.*;

import mockit.*;

@SuppressWarnings("RedundantStringConstructorCall")
public final class ClassInitializationTest
{
   static final class ClassWhichFailsAtInitialization
   {
      static
      {
         //noinspection ConstantIfStatement
         if (true) {
            throw new AssertionError();
         }
      }

      static int value() { return 0; }
   }

   @Test
   public void usingMockUp()
   {
      new MockUp<ClassWhichFailsAtInitialization>() {
         @Mock void $clinit() {}
         @Mock int value() { return 1; }
      };

      assertEquals(1, ClassWhichFailsAtInitialization.value());
   }

   @Test
   public void stubbingOutClassInitializerOnly()
   {
      new MockUp<ClassWhichFailsAtInitialization>() { @Mock void $clinit() {} };

      assertEquals(0, ClassWhichFailsAtInitialization.value());
   }

   @Test
   public void usingExpectations(@Mocked(stubOutClassInitialization = true) ClassWhichFailsAtInitialization unused)
   {
      new Expectations() {{
         ClassWhichFailsAtInitialization.value(); result = 1;
      }};

      assertEquals(1, ClassWhichFailsAtInitialization.value());
   }

   static class ClassWithStaticInitializer1
   {
      static final String CONSTANT = new String("not a compile-time constant");
      static { doSomething(); }
      static void doSomething() { throw new UnsupportedOperationException("must not execute"); }
   }

   @Test
   public void mockClassWithStaticInitializerNotStubbedOut(
      @Mocked(stubOutClassInitialization = false) final ClassWithStaticInitializer1 mock)
   {
      assertNotNull(ClassWithStaticInitializer1.CONSTANT);
      ClassWithStaticInitializer1.doSomething();
   }

   static class ClassWithStaticInitializer2
   {
      static final String CONSTANT = new String("not a compile-time constant");
      static { doSomething(); }
      static void doSomething() { throw new UnsupportedOperationException("must not execute"); }
   }

   @Test
   public void useClassWithStaticInitializerNeverStubbedOutAndNotMockedNow()
   {
      // Allows the class to be initialized without throwing the exception.
      MockUp<?> mockUp = new MockUp<ClassWithStaticInitializer2>() { @Mock void doSomething() {} };

      // Initializes the class:
      assertNotNull(ClassWithStaticInitializer2.CONSTANT);

      // Restore the now initialized class:
      mockUp.tearDown();

      try {
         ClassWithStaticInitializer2.doSomething();
         fail();
      }
      catch (UnsupportedOperationException ignore) {}
   }

   static class AnotherClassWithStaticInitializer1
   {
      static final String CONSTANT = new String("not a compile-time constant");
      static { doSomething(); }
      static void doSomething() { throw new UnsupportedOperationException("must not execute"); }
      int getValue() { return -1; }
   }

   @Test
   public void mockClassWithStaticInitializerStubbedOut(
      @Mocked(stubOutClassInitialization = true) AnotherClassWithStaticInitializer1 mock)
   {
      assertNull(AnotherClassWithStaticInitializer1.CONSTANT);
      AnotherClassWithStaticInitializer1.doSomething();
      assertEquals(0, mock.getValue());
   }

   static class AnotherClassWithStaticInitializer2
   {
      static final String CONSTANT = new String("not a compile-time constant");
      static { doSomething(); }
      static void doSomething() { throw new UnsupportedOperationException("must not execute"); }
      int getValue() { return -1; }
   }

   @Test
   public void useClassWithStaticInitializerPreviouslyStubbedOutButNotMockedNow()
   {
      // Stubs out the static initializer, initializes the class, and then restores it:
      MockUp<?> mockUp = new MockUp<AnotherClassWithStaticInitializer2>() { @Mock void $clinit() {} };
      assertNull(AnotherClassWithStaticInitializer2.CONSTANT);
      mockUp.tearDown();

      try {
         AnotherClassWithStaticInitializer2.doSomething();
         fail();
      }
      catch (UnsupportedOperationException ignore) {}

      assertEquals(-1, new AnotherClassWithStaticInitializer2().getValue());
   }
}
