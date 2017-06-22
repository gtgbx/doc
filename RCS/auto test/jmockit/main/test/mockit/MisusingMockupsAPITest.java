/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit;

import org.junit.*;
import static org.junit.Assert.*;

public final class MisusingMockupsAPITest
{
   abstract static class AbstractClass
   {
      @SuppressWarnings("UnusedDeclaration")
      abstract void abstractMethod();
   }

   @Test
   public void attemptToMockAbstractMethod()
   {
      new MockUp<AbstractClass>() {
         // Has no effect.
         @Mock void abstractMethod() {}
      };
   }

   public static class Collaborator
   {
      int doSomething() { return 1; }

      @SuppressWarnings("UnusedDeclaration")
      native void nativeMethod();
   }

   @Test
   public void mockNativeMethodUsingInvocationParameter()
   {
      final Collaborator c = new Collaborator();

      new MockUp<Collaborator>() {
         @Mock(invocations = 1)
         void nativeMethod(Invocation inv)
         {
            Collaborator it = inv.getInvokedInstance();
            assertSame(c, it);
         }
      };

      c.nativeMethod();
   }

   @Test
   public void applySameMockClassWhilePreviousApplicationStillActive()
   {
      // Apply then tear-down.
      new SomeMockUp(0).tearDown();
      assertEquals(1, new Collaborator().doSomething());

      // Apply again after tear-down: ok.
      new SomeMockUp(2);
      assertEquals(2, new Collaborator().doSomething());

      // Apply again while still active: not ok, but handled by automatically tearing-down the previous mock-up.
      new SomeMockUp(3);
      assertEquals(3, new Collaborator().doSomething());
   }

   static final class SomeMockUp extends MockUp<Collaborator>
   {
      final int value;
      SomeMockUp(int value) { this.value = value; }
      @Mock(invocations = 1) int doSomething() { return value; }
   }

   @Test
   public void applySameMockClassUsingSecondaryConstructorWhilePreviousApplicationStillActive()
   {
      new AnotherMockUp(0).tearDown();
      assertEquals(1, new Collaborator().doSomething());

      new AnotherMockUp(2);
      assertEquals(2, new Collaborator().doSomething());

      new AnotherMockUp(3);
      assertEquals(3, new Collaborator().doSomething());
   }

   static final class AnotherMockUp extends MockUp<Collaborator>
   {
      final int value;
      AnotherMockUp(int value) { super(Collaborator.class); this.value = value; }
      @Mock(invocations = 1) int doSomething() { return value; }
   }

   @Test
   public void mockSameMethodTwiceWithReentrantMocksFromTwoDifferentMockClasses()
   {
      new MockUp<Collaborator>() {
         @Mock
         int doSomething(Invocation inv)
         {
            int i = inv.proceed();
            return i + 1;
         }
      };

      int i = new Collaborator().doSomething();
      assertEquals(2, i);

      new MockUp<Collaborator>() {
         @Mock
         int doSomething(Invocation inv)
         {
            int i = inv.proceed();
            return i + 2;
         }
      };

      // Should return 4, but returns 6. Chaining mock methods is not supported.
      int j = new Collaborator().doSomething();
      assertEquals(6, j);
   }
}