/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit;

import java.util.*;

import org.junit.*;
import org.junit.rules.*;
import static org.junit.Assert.*;

import static mockit.Deencapsulation.*;

@SuppressWarnings("unused")
public final class DelegateTest
{
   @Rule public final ExpectedException thrown = ExpectedException.none();

   static class Collaborator
   {
      Collaborator() {}
      Collaborator(int i) {}

      int getValue() { return -1; }
      String doSomething(boolean b, int[] i, String s) { return s + b + i[0]; }
      static boolean staticMethod() { return true; }
      static boolean staticMethod(int i) { return i > 0; }
      native long nativeMethod(boolean b);
      final char finalMethod() { return 's'; }
      private float privateMethod() { return 1.2F; }
      void addElements(List<String> elements) { elements.add("one element"); }
      Foo getFoo() { return null; }
   }

   static final class Foo { int doSomething() { return 1; } }

   @Test
   public void resultFromDelegate(@Mocked final Collaborator collaborator)
   {
      final boolean bExpected = true;
      final int[] iExpected = new int[0];
      final String sExpected = "test";

      new Expectations() {{
         collaborator.getValue(); result = new Delegate() { int getValue() { return 2; } };

         collaborator.doSomething(bExpected, iExpected, sExpected);
         result = new Delegate() {
            String doSomething(boolean b, int[] i, String s)
            {
               assertEquals(bExpected, b);
               assertArrayEquals(iExpected, i);
               assertEquals(sExpected, s);
               return "";
            }
         };
      }};

      assertEquals(2, collaborator.getValue());
      assertEquals("", collaborator.doSomething(bExpected, iExpected, sExpected));
   }

   @Test
   public void consecutiveResultsThroughDelegatesHavingDifferentValues(@Mocked final Collaborator mock)
   {
      new NonStrictExpectations() {{
         mock.getValue();
         result = new Delegate() { int getValue() { return 1; } };
         result = new Delegate() { int getValue() { return 2; } };
      }};

      Collaborator collaborator = new Collaborator();
      assertEquals(1, collaborator.getValue());
      assertEquals(2, collaborator.getValue());
   }

   @Test
   public void consecutiveReturnValuesThroughDelegatesUsingSingleReturnsWithVarargs(
      @Mocked final Collaborator collaborator)
   {
      final int[] array = {1, 2};

      new NonStrictExpectations() {{
         collaborator.doSomething(true, array, "");
         returns(
            new Delegate() {
               String execute(boolean b, int[] i, String s)
               {
                  assertEquals(1, i[0]);
                  return "a";
               }
            },
            new Delegate() {
               String execute(boolean b, int[] i, String s)
               {
                  assertEquals(2, i[0]);
                  return "b";
               }
            });
      }};

      assertEquals("a", collaborator.doSomething(true, array, ""));

      array[0] = 2;
      assertEquals("b", collaborator.doSomething(true, array, ""));
   }

   @Test
   public void resultWithMultipleReturnValuesThroughSingleDelegate(@Mocked final Collaborator collaborator)
   {
      new NonStrictExpectations() {{
         collaborator.getValue();
         result = new Delegate() {
            int i = 1;
            int getValue() { return i++; }
         };
      }};

      assertEquals(1, collaborator.getValue());
      assertEquals(2, collaborator.getValue());
      assertEquals(3, collaborator.getValue());
   }

   @Test
   public void constructorDelegateWithSingleMethod(@Mocked Collaborator mock)
   {
      final ConstructorDelegate delegate = new ConstructorDelegate();

      new Expectations() {{
         new Collaborator(anyInt); result = delegate;
      }};

      new Collaborator(4);

      assertTrue(delegate.capturedArgument > 0);
   }

   static class ConstructorDelegate implements Delegate<Void>
   {
      int capturedArgument;
      void delegate(int i) { capturedArgument = i; }
   }

   @Test
   public void constructorDelegateWithMultipleMethods(@Mocked Collaborator mock)
   {
      new NonStrictExpectations() {{
         new Collaborator(anyInt);
         result = new Delegate() {
            void init(int i) { if (i < 0) throw new IllegalArgumentException(); }
            private void anotherMethod() {}
         };
      }};

      new Collaborator(123);

      try {
         new Collaborator(-123);
         fail();
      }
      catch (IllegalArgumentException ignore) {}
   }

   @Test
   public void attemptToUseConstructorDelegateWithPrivateMethodsOnly(@Mocked Collaborator mock)
   {
      thrown.expect(IllegalArgumentException.class);
      thrown.expectMessage("No non-private delegate method found");

      new NonStrictExpectations() {{
         new Collaborator();
         result = new Delegate() {
            private void delegate() {}
            private void anotherMethod() {}
         };
      }};
   }

   @Test
   public void delegateForStaticMethod(@Mocked Collaborator unused)
   {
      new Expectations() {{
         Collaborator.staticMethod();
         result = new Delegate() { boolean staticMethod() { return false; } };
      }};

      assertFalse(Collaborator.staticMethod());
   }

   @Test
   public void delegateWithStaticMethod(@Mocked final Collaborator mock)
   {
      new NonStrictExpectations() {{
         Collaborator.staticMethod(anyInt); result = new StaticDelegate();
      }};

      assertTrue(Collaborator.staticMethod(34));
   }

   static final class StaticDelegate implements Delegate<Object>
   {
      static boolean staticMethod(int i)
      {
         assertEquals(34, i);
         return true;
      }
   }

   @Test
   public void delegateForNativeMethod(@Mocked final Collaborator mock)
   {
      new NonStrictExpectations() {{
         mock.nativeMethod(anyBoolean);
         result = new Delegate() {
            Long nativeMethod(boolean b) { assertTrue(b); return 0L; }
         };
      }};

      assertEquals(0L, new Collaborator().nativeMethod(true));
   }

   @Test
   public void delegateForFinalMethod(@Mocked final Collaborator mock)
   {
      new NonStrictExpectations() {{
         mock.finalMethod();
         result = new Delegate() { char finalMethod() { return 'M'; } };
      }};

      assertEquals('M', new Collaborator().finalMethod());
   }

   @Test
   public void delegateForPrivateMethod(@Mocked final Collaborator collaborator)
   {
      new NonStrictExpectations() {{
         invoke(collaborator, "privateMethod");
         result = new Delegate() { float privateMethod() { return 0.5F; } };
      }};

      assertEquals(0.5F, collaborator.privateMethod(), 0);
   }

   @Test
   public void delegateForMethodWithCompatibleButDistinctParameterType(@Mocked final Collaborator collaborator)
   {
      new NonStrictExpectations() {{
         collaborator.addElements(this.<List<String>>withNotNull());
         result = new Delegate() {
            void delegate(Collection<String> elements) { elements.add("test"); }
         };
      }};

      List<String> elements = new ArrayList<String>();
      new Collaborator().addElements(elements);

      assertTrue(elements.contains("test"));
   }

   @Test
   public void delegateReceivingNullArguments(@Mocked final Collaborator collaborator)
   {
      new NonStrictExpectations() {{
         collaborator.doSomething(true, null, null);
         result = new Delegate() {
            String delegate(boolean b, int[] i, String s) {
               //noinspection ImplicitArrayToString
               return b + " " + i + " " + s;
            }
         };
      }};

      String s = new Collaborator().doSomething(true, null, null);
      assertEquals("true null null", s);
   }

   @Test
   public void delegateWithTwoMethods(@Mocked final Collaborator collaborator)
   {
      new NonStrictExpectations() {{
         collaborator.doSomething(true, null, "str");
         result = new Delegate() {
            private String someOther() { return ""; }
            void doSomething(boolean b, int[] i, String s) {}
         };
      }};

      assertNull(collaborator.doSomething(true, null, "str"));
   }

   @Test
   public void delegateWithSingleMethodHavingADifferentName(@Mocked final Collaborator collaborator)
   {
      new NonStrictExpectations() {{
         collaborator.doSomething(true, null, "str");
         result = new Delegate() {
            void onReplay(boolean b, int[] i, String s)
            {
               assertTrue(b);
               assertNull(i);
               assertEquals("str", s);
            }
         };
      }};

      assertNull(new Collaborator().doSomething(true, null, "str"));
   }

   @Test
   public void delegateWithSingleMethodHavingNoParameters(@Mocked final Collaborator collaborator)
   {
      new NonStrictExpectations() {{
         collaborator.doSomething(anyBoolean, null, null);
         result = new Delegate() { String onReplay() { return "action"; } };
      }};

      assertEquals("action", new Collaborator().doSomething(true, null, null));
   }

   @Test
   public void delegateWithSingleMethodHavingNoParametersExceptForInvocationContext(
      @Mocked final Collaborator collaborator)
   {
      new NonStrictExpectations() {{
         collaborator.doSomething(anyBoolean, null, null);
         result = new Delegate() {
            void doSomething(Invocation inv) { assertEquals(1, inv.getInvocationCount()); }
         };
      }};

      assertNull(new Collaborator().doSomething(false, new int[] {1, 2}, "test"));
   }

   @Test
   public void delegateWithOneMethodHavingDifferentParameters(@Mocked final Collaborator collaborator)
   {
      thrown.expect(IllegalArgumentException.class);
      thrown.expectMessage("delegate(");

      new NonStrictExpectations() {{
         collaborator.doSomething(true, null, "str");
         result = new Delegate() { void delegate(boolean b, String s) {} };
      }};

      collaborator.doSomething(true, null, "str");
   }

   @Test
   public void delegateWithTwoNonPrivateMethods(@Mocked final Collaborator collaborator)
   {
      thrown.expect(IllegalArgumentException.class);
      thrown.expectMessage("More than one candidate delegate method found: ");
      thrown.expectMessage("someOther()");
      thrown.expectMessage("doSomethingElse(boolean,int[],String)");

      new NonStrictExpectations() {{
         collaborator.doSomething(true, null, "str");
         result = new Delegate() {
            String someOther() { return ""; }
            void doSomethingElse(boolean b, int[] i, String s) {}
         };
      }};
   }

   @Test
   public void delegateCausingConcurrentMockInvocation(@Mocked final Collaborator mock)
   {
      final Collaborator collaborator = new Collaborator();
      final Thread t = new Thread(new Runnable() {
         @Override
         public void run() { collaborator.doSomething(false, null, ""); }
      });

      new NonStrictExpectations() {{
         mock.getValue(); times = 1;
         result = new Delegate() {
            int executeInAnotherThread() throws Exception
            {
               t.start();
               t.join();
               return 1;
            }
         };
      }};

      assertEquals(1, collaborator.getValue());
   }

   @Test
   public void delegateWhichCallsTheSameMockedMethod(@Mocked final Collaborator mock)
   {
      new NonStrictExpectations() {{
         mock.getValue();
         result = new Delegate() {
            int count;
            // Would result in a StackOverflowError without a termination condition.
            int delegate() { return count++ > 1 ? 123 : 1 + mock.getValue(); }
         };
      }};

      assertEquals(125, mock.getValue());
   }

   @Test
   public void delegateWhichCallsAnotherMockedMethod_fullMocking(@Mocked final Collaborator mock)
   {
      new NonStrictExpectations() {{
         mock.getValue();
         result = new Delegate() {
            int delegate() { return mock.finalMethod(); }
         };

         mock.finalMethod(); result = 'A';
      }};

      assertEquals('A', mock.getValue());
   }

   @Test
   public void delegateWhichCallsAnotherMockedMethod_partialMockingOfClass()
   {
      final Collaborator collaborator = new Collaborator();

      new NonStrictExpectations(Collaborator.class) {{
         Collaborator.staticMethod(); result = false;

         collaborator.getValue();
         result = new Delegate() {
            int delegate() { return Collaborator.staticMethod() ? 1 : collaborator.finalMethod(); }
         };

         collaborator.finalMethod(); result = 'A';
      }};

      assertEquals('A', collaborator.getValue());
   }

   @Test
   public void delegateWhichCallsAnotherMockedMethod_partialMockingOfInstance()
   {
      final Collaborator collaborator = new Collaborator();

      new NonStrictExpectations(collaborator) {{
         collaborator.getValue();
         result = new Delegate() {
            int delegate() { return collaborator.finalMethod(); }
         };

         collaborator.finalMethod(); result = 'A';
      }};

      assertEquals('A', collaborator.getValue());
   }

   @Test
   public void delegateWhichCallsAnotherMockedMethod_injectableMocking(@Injectable final Collaborator mock)
   {
      new NonStrictExpectations() {{
         mock.getValue();
         result = new Delegate() {
            int delegate() { return mock.finalMethod(); }
         };

         mock.finalMethod(); result = 'A';
      }};

      assertEquals('A', mock.getValue());
   }

   @Test
   public void delegateWhichCallsAnotherMockedMethodProducingACascadedInstance(@Cascading final Collaborator mock)
   {
      new NonStrictExpectations() {{
         mock.getFoo().doSomething(); result = 123;

         mock.getValue();
         result = new Delegate() {
            int delegate() { return mock.getFoo().doSomething(); }
         };
      }};

      assertEquals(123, mock.getFoo().doSomething());
      assertEquals(123, mock.getValue());
   }

   @Test
   public void delegateCallingMockedMethodLaterVerified(
      @Mocked final Collaborator collaborator, @Mocked final Runnable action)
   {
      new NonStrictExpectations() {{
         collaborator.getFoo();
         result = new Delegate() {
            void delegate() { action.run(); }
         };
      }};

      collaborator.getFoo();

      new Verifications() {{ action.run(); }};
   }
}
