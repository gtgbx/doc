/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit;

import java.io.*;

import org.junit.*;
import static org.junit.Assert.*;

public final class ReentrantMockTest
{
   public static class RealClass
   {
      String foo() { return "real value"; }
      static int staticRecursiveMethod(int i) { return i <= 0 ? 0 : 2 + staticRecursiveMethod(i - 1); }
      int recursiveMethod(int i) { return i <= 0 ? 0 : 2 + recursiveMethod(i - 1); }
      static int nonRecursiveStaticMethod(int i) { return -i; }
      int nonRecursiveMethod(int i) { return -i; }
   }

   public static class AnnotatedMockClass extends MockUp<RealClass>
   {
      private static Boolean fakeIt;

      @Mock
      public String foo(Invocation inv)
      {
         if (fakeIt == null) {
            throw new IllegalStateException("null fakeIt");
         }
         else if (fakeIt) {
            return "fake value";
         }
         else {
            return inv.proceed();
         }
      }
   }

   @Test
   public void callMockMethod()
   {
      new AnnotatedMockClass();
      AnnotatedMockClass.fakeIt = true;

      String foo = new RealClass().foo();

      assertEquals("fake value", foo);
   }

   @Test
   public void callOriginalMethod()
   {
      new AnnotatedMockClass();
      AnnotatedMockClass.fakeIt = false;

      String foo = new RealClass().foo();

      assertEquals("real value", foo);
   }

   @Test(expected = IllegalStateException.class)
   public void calledMockThrowsException()
   {
      new AnnotatedMockClass();
      AnnotatedMockClass.fakeIt = null;

      new RealClass().foo();
   }

   public static class MockRuntime extends MockUp<Runtime>
   {
      private int runFinalizationCount;

      @Mock(minInvocations = 3)
      public void runFinalization(Invocation inv)
      {
         if (runFinalizationCount < 2) {
            inv.proceed();
         }

         runFinalizationCount++;
      }

      @Mock
      public boolean removeShutdownHook(Invocation inv, Thread hook)
      {
         if (hook == null) {
            //noinspection AssignmentToMethodParameter
            hook = Thread.currentThread();
         }

         return inv.proceed(hook);
      }

      @Mock(invocations = 1)
      public void runFinalizersOnExit(boolean value)
      {
         assertTrue(value);
      }
   }

   @Test
   public void callMockMethodForJREClass()
   {
      Runtime runtime = Runtime.getRuntime();
      new MockRuntime();

      runtime.runFinalization();
      runtime.runFinalization();
      runtime.runFinalization();

      assertFalse(runtime.removeShutdownHook(null));

      //noinspection deprecation
      Runtime.runFinalizersOnExit(true);
   }

   public static class ReentrantMockForNativeMethod extends MockUp<Runtime>
   {
      @Mock
      public int availableProcessors(Invocation inv)
      {
         assertNotNull(inv.getInvokedInstance());
         return 5;
      }
   }

   @Test
   public void setUpReentrantMockForNativeJREMethod()
   {
      new ReentrantMockForNativeMethod();

      assertEquals(5, Runtime.getRuntime().availableProcessors());
   }

   static class MultiThreadedMock extends MockUp<RealClass>
   {
      private static boolean nobodyEntered = true;

      @Mock
      public String foo(Invocation inv) throws InterruptedException
      {
         String value = inv.proceed();

         synchronized (MultiThreadedMock.class) {
            if (nobodyEntered) {
               nobodyEntered = false;
               //noinspection WaitNotInLoop
               MultiThreadedMock.class.wait(5000);
            }
            else {
               MultiThreadedMock.class.notifyAll();
            }
         }

         return value.replace("real", "fake");
      }
   }

   @Test(timeout = 1000)
   public void twoConcurrentThreadsCallingTheSameReentrantMock() throws Exception
   {
      new MultiThreadedMock();

      final StringBuilder first = new StringBuilder();
      final StringBuilder second = new StringBuilder();

      Thread thread1 = new Thread(new Runnable() {
         public void run() { first.append(new RealClass().foo()); }
      });
      thread1.start();

      Thread thread2 = new Thread(new Runnable() {
         public void run() { second.append(new RealClass().foo()); }
      });
      thread2.start();

      thread1.join();
      thread2.join();

      assertEquals("fake value", first.toString());
      assertEquals("fake value", second.toString());
   }

   static final class RealClass2
   {
      int firstMethod() { return 1; }
      int secondMethod() { return 2; }
   }

   @Test
   public void reentrantMockForNonJREClassWhichCallsAnotherFromADifferentThread()
   {
      new MockUp<RealClass2>() {
         int value;

         @Mock
         int firstMethod(Invocation inv) { return inv.proceed(); }

         @Mock
         int secondMethod(Invocation inv) throws InterruptedException
         {
            final RealClass2 it = inv.getInvokedInstance();

            Thread t = new Thread() {
               @Override
               public void run() { value = it.firstMethod(); }
            };
            t.start();
            t.join();
            return value;
         }
      };

      RealClass2 r = new RealClass2();
      assertEquals(1, r.firstMethod());
      assertEquals(1, r.secondMethod());
   }

   @Test
   public void reentrantMockForJREClassWhichCallsAnotherFromADifferentThread()
   {
      System.setProperty("a", "1");
      System.setProperty("b", "2");

      new MockUp<System>() {
         String property;

         @Mock
         String getProperty(Invocation inv, String key) { return inv.proceed(); }

         @Mock
         String clearProperty(final String key) throws InterruptedException
         {
            Thread t = new Thread() {
               @Override
               public void run() { property = System.getProperty(key); }
            };
            t.start();
            t.join();
            return property;
         }
      };

      assertEquals("1", System.getProperty("a"));
      assertEquals("2", System.clearProperty("b"));
   }

   @Test
   public void mockFileAndForceJREToCallReentrantMockedMethod()
   {
      new MockUp<File>() {
         @Mock
         boolean exists(Invocation inv) { boolean exists = inv.proceed(); return !exists; }
      };

      // Cause the JVM/JRE to load a new class, calling the mocked File#exists() method in the process:
      new Runnable() { public void run() {} };

      assertTrue(new File("noFile").exists());
   }

   static final class RealClass3
   {
      RealClass3 newInstance() { return new RealClass3(); }
   }

   @Test
   public void reentrantMockForMethodWhichInstantiatesAndReturnsNewInstanceOfTheMockedClass()
   {
      new MockUp<RealClass3>() {
         @Mock
         RealClass3 newInstance(Invocation inv) { return null; }
      };

      assertNull(new RealClass3().newInstance());
   }

   public static final class MockClassWithReentrantMockForRecursiveMethod extends MockUp<RealClass>
   {
      @Mock
      int recursiveMethod(Invocation inv, int i) { int j = inv.proceed(); return 1 + j; }

      @Mock
      static int staticRecursiveMethod(Invocation inv, int i) { int j = inv.proceed(); return 1 + j; }
   }

   @Test
   public void reentrantMockMethodForRecursiveMethods()
   {
      assertEquals(0, RealClass.staticRecursiveMethod(0));
      assertEquals(2, RealClass.staticRecursiveMethod(1));

      RealClass r = new RealClass();
      assertEquals(0, r.recursiveMethod(0));
      assertEquals(2, r.recursiveMethod(1));

      new MockClassWithReentrantMockForRecursiveMethod();

      assertEquals(1, RealClass.staticRecursiveMethod(0));
      assertEquals(1 + 2 + 1, RealClass.staticRecursiveMethod(1));
      assertEquals(1, r.recursiveMethod(0));
      assertEquals(4, r.recursiveMethod(1));
   }

   @Test
   public void mockUpThatProceedsIntoRecursiveMethod()
   {
      RealClass r = new RealClass();
      assertEquals(0, r.recursiveMethod(0));
      assertEquals(2, r.recursiveMethod(1));

      new MockUp<RealClass>() {
         @Mock
         int recursiveMethod(Invocation inv, int i)
         {
            int ret = inv.proceed();
            return 1 + ret;
         }
      };

      assertEquals(1, r.recursiveMethod(0));
      assertEquals(4, r.recursiveMethod(1));
   }

   @Test
   public void recursiveMockMethodWithoutInvocationParameter()
   {
      new MockUp<RealClass>() {
         @Mock
         int nonRecursiveStaticMethod(int i)
         {
            if (i > 1) return i;
            return RealClass.nonRecursiveStaticMethod(i + 1);
         }
      };

      int result = RealClass.nonRecursiveStaticMethod(1);
      assertEquals(2, result);
   }

   @Test
   public void recursiveMockMethodWithInvocationParameterNotUsedForProceeding()
   {
      new MockUp<RealClass>() {
         @Mock
         int nonRecursiveMethod(Invocation inv, int i)
         {
            if (i > 1) return i;
            RealClass it = inv.getInvokedInstance();
            return it.nonRecursiveMethod(i + 1);
         }
      };

      int result = new RealClass().nonRecursiveMethod(1);
      assertEquals(2, result);
   }

   @Test
   public void nonRecursiveMockMethodWithInvocationParameterUsedForProceeding()
   {
      new MockUp<RealClass>() {
         @Mock
         int nonRecursiveMethod(Invocation inv, int i)
         {
            if (i > 1) return i;
            return inv.proceed(i + 1);
         }
      };

      int result = new RealClass().nonRecursiveMethod(1);
      assertEquals(-2, result);
   }

   @Test
   public void recursiveDelegateMethodWithoutInvocationParameter()
   {
      new NonStrictExpectations(RealClass.class) {{
         RealClass.nonRecursiveStaticMethod(anyInt);
         result = new Delegate() {
            @Mock
            int delegate(int i)
            {
               if (i > 1) return i;
               return RealClass.nonRecursiveStaticMethod(i + 1);
            }
         };
      }};

      int result = RealClass.nonRecursiveStaticMethod(1);
      assertEquals(2, result);
   }

   @Test
   public void recursiveDelegateMethodWithInvocationParameterNotUsedForProceeding(@Injectable final RealClass rc)
   {
      new NonStrictExpectations() {{
         rc.nonRecursiveMethod(anyInt);
         result = new Delegate() {
            @Mock
            int delegate(Invocation inv, int i)
            {
               if (i > 1) return i;
               RealClass it = inv.getInvokedInstance();
               return it.nonRecursiveMethod(i + 1);
            }
         };
      }};

      int result = rc.nonRecursiveMethod(1);
      assertEquals(2, result);
   }

   @Test
   public void nonRecursiveDelegateMethodWithInvocationParameterUsedForProceeding(@Injectable final RealClass rc)
   {
      new NonStrictExpectations() {{
         rc.nonRecursiveMethod(anyInt);
         result = new Delegate() {
            @Mock
            int nonRecursiveMethod(Invocation inv, int i)
            {
               if (i > 1) return i;
               return inv.proceed(i + 1);
            }
         };
      }};

      int result = rc.nonRecursiveMethod(1);
      assertEquals(-2, result);
   }
}
