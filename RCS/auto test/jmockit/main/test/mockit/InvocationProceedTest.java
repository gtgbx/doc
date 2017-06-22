/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import org.junit.*;
import static org.junit.Assert.*;

@SuppressWarnings("UnusedDeclaration")
public final class InvocationProceedTest
{
   public static class BaseClassToBeMocked
   {
      protected String name;

      public final String getName() { return name; }
      public final int baseMethod(int i) { return i + 1; }
      protected int methodToBeMocked(int i) throws IOException { return i; }
   }

   public static class ClassToBeMocked extends BaseClassToBeMocked
   {
      public ClassToBeMocked() { name = ""; }
      public ClassToBeMocked(String name) { this.name = name; }

      public boolean methodToBeMocked() { return true; }

      @Override
      protected int methodToBeMocked(int i) throws IOException { return i; }

      private int methodToBeMocked(int i, Object... args)
      {
         int result = i;

         for (Object arg : args) {
            if (arg != null) result++;
         }

         return result;
      }

      String anotherMethodToBeMocked(String s, boolean b, List<Integer> ints)
      { return (b ? s.toUpperCase() : s.toLowerCase()) + ints; }

      public static boolean staticMethodToBeMocked() throws FileNotFoundException { throw new FileNotFoundException(); }

      static native void nativeMethod();
   }

   /// Tests for "@Mock" methods //////////////////////////////////////////////////////////////////////////////////////

   @Test
   public void proceedFromMockMethodWithoutParameters()
   {
      new MockUp<ClassToBeMocked>() {
         @Mock(invocations = 1) boolean methodToBeMocked(Invocation inv) { return inv.proceed(); }
      };

      assertTrue(new ClassToBeMocked().methodToBeMocked());
   }

   @Test
   public void proceedFromMockMethodWithParameters() throws Exception
   {
      new MockUp<ClassToBeMocked>() {
         @Mock int methodToBeMocked(Invocation inv, int i) { Integer j = inv.proceed(); return j + 1; }

         @Mock(maxInvocations = 1)
         private int methodToBeMocked(Invocation inv, int i, Object... args)
         {
            args[2] = "mock";
            return inv.<Integer>proceed();
         }
      };

      ClassToBeMocked mocked = new ClassToBeMocked();

      assertEquals(124, mocked.methodToBeMocked(123));
      assertEquals(-8, mocked.methodToBeMocked(-9));
      assertEquals(7, mocked.methodToBeMocked(3, "Test", new Object(), null, 45));
   }

   @Test
   public void proceedConditionallyFromMockMethod() throws Exception
   {
      new MockUp<ClassToBeMocked>() {
         @Mock
         String anotherMethodToBeMocked(Invocation inv, String s, boolean b, List<Number> ints)
         {
            if (!b) {
               return s;
            }

            ints.add(45);
            return inv.proceed();
         }
      };

      ClassToBeMocked mocked = new ClassToBeMocked();

      // Do not proceed:
      assertNull(mocked.anotherMethodToBeMocked(null, false, null));

      // Do proceed:
      List<Integer> values = new ArrayList<Integer>();
      assertEquals("TEST[45]", mocked.anotherMethodToBeMocked("test", true, values));

      // Do not proceed again:
      assertEquals("No proceed", mocked.anotherMethodToBeMocked("No proceed", false, null));
   }

   @Test
   public void proceedFromMockMethodWhichThrowsCheckedException()
   {
      new MockUp<ClassToBeMocked>() {
         @Mock(minInvocations = 1)
         boolean staticMethodToBeMocked(Invocation inv) throws Exception
         {
            if (inv.getInvocationIndex() == 0) {
               return inv.<Boolean>proceed();
            }

            throw new InterruptedException("fake");
         }
      };

      try { ClassToBeMocked.staticMethodToBeMocked(); fail(); } catch (FileNotFoundException ignored) {}

      try {
         ClassToBeMocked.staticMethodToBeMocked();
         fail();
      }
      catch (Exception e) {
         //noinspection ConstantConditions
         assertTrue(e instanceof InterruptedException);
      }
   }

   @Test
   public void proceedFromMockMethodIntoRealMethodWithModifiedArguments() throws Exception
   {
      class MockUpWhichModifiesArguments extends MockUp<ClassToBeMocked>
      {
         @Mock
         final int methodToBeMocked(Invocation invocation, int i) { return invocation.<Integer>proceed(i + 2); }
      }

      new MockUpWhichModifiesArguments() {
         @Mock
         synchronized int methodToBeMocked(Invocation inv, int i, Object... args)
         {
            return inv.<Integer>proceed(1, 2, "3");
         }
      };

      ClassToBeMocked mocked = new ClassToBeMocked();
      assertEquals(3, mocked.methodToBeMocked(1));
      assertEquals(3, mocked.methodToBeMocked(-2, null, "Abc", true, 'a'));
   }

   @Test(expected = UnsupportedOperationException.class)
   public void cannotProceedFromMockMethodIntoNativeMethod()
   {
      new MockUp<ClassToBeMocked>() {
         @Mock void nativeMethod(Invocation inv)
         {
            inv.proceed();
            fail("Should not get here");
         }
      };

      ClassToBeMocked.nativeMethod();
   }

   @Test
   public void proceedFromMockMethodIntoConstructor()
   {
      new MockUp<ClassToBeMocked>() {
         @Mock void $init(Invocation inv)
         {
            assertNotNull(inv.<ClassToBeMocked>getInvokedInstance());
            inv.proceed();
         }
      };

      assertEquals("", new ClassToBeMocked().name);
   }

   @Test
   public void proceedConditionallyFromMockMethodIntoConstructor()
   {
      new MockUp<ClassToBeMocked>() {
         @Mock void $init(Invocation inv, String name)
         {
            assertNotNull(inv.getInvokedInstance());

            if ("proceed".equals(name)) {
               inv.proceed();
            }
         }
      };

      assertEquals("proceed", new ClassToBeMocked("proceed").name);
      assertNull(new ClassToBeMocked("do not proceed").name);
   }

   @Test(expected = UnsupportedOperationException.class)
   public void cannotProceedFromMockMethodIntoConstructorWithNewArguments()
   {
      new MockUp<ClassToBeMocked>() {
         @Mock void $init(Invocation inv, String name) { inv.proceed("mock"); }
      };

      new ClassToBeMocked("will fail");
   }

   @Test
   public void proceedConditionallyFromMockMethodIntoJREConstructor()
   {
      new MockUp<File>() {
         @Mock void $init(Invocation inv, String name)
         {
            if ("proceed".equals(name)) {
               inv.proceed();
            }
         }
      };

      assertEquals("proceed", new File("proceed").getPath());
      assertNull(new File("do not proceed").getPath());
   }

   @Test
   public void proceedFromMockMethodIntoMethodInheritedFromBaseClass()
   {
      new MockUp<ClassToBeMocked>() {
         @Mock int baseMethod(Invocation inv, int i) { return inv.proceed(i + 1); }
      };

      assertEquals(3, new ClassToBeMocked().baseMethod(1));
   }

   /// Tests for "Delegate" methods ///////////////////////////////////////////////////////////////////////////////////

   @Test
   public void proceedFromDelegateMethodOnRegularMockedClass(@Mocked final ClassToBeMocked mocked)
   {
      new NonStrictExpectations() {{
         mocked.methodToBeMocked();
         result = new Delegate() {
            boolean delegate(Invocation inv) { return inv.proceed(); }
         };
      }};

      assertTrue(mocked.methodToBeMocked());
   }

   @Test
   public void proceedFromDelegateMethodOnInjectableMockedClass(@Injectable final ClassToBeMocked mocked)
   {
      new Expectations() {{
         mocked.methodToBeMocked();
         result = new Delegate() {
            boolean delegate(Invocation inv) { return inv.proceed(); }
         };
      }};

      assertTrue(mocked.methodToBeMocked());
   }

   @Test
   public void proceedFromDelegateMethodWithParameters() throws Exception
   {
      final ClassToBeMocked mocked = new ClassToBeMocked();

      new NonStrictExpectations(mocked) {{
         mocked.methodToBeMocked(anyInt);
         result = new Delegate() { int delegate(Invocation inv, int i) { Integer j = inv.proceed(); return j + 1; } };

         mocked.methodToBeMocked(anyInt, (Object[]) any); maxTimes = 1;
         result = new Delegate() {
            Integer delegate(Invocation inv, int i, Object... args)
            {
               args[2] = "mock";
               return inv.proceed();
            }
         };
      }};

      assertEquals(124, mocked.methodToBeMocked(123));
      assertEquals(-8, mocked.methodToBeMocked(-9));
      assertEquals(7, mocked.methodToBeMocked(3, "Test", new Object(), null, 45));
   }

   @Test
   public void proceedConditionallyFromDelegateMethod()
   {
      final ClassToBeMocked mocked = new ClassToBeMocked();

      new NonStrictExpectations(mocked) {{
         mocked.anotherMethodToBeMocked(anyString, anyBoolean, null);
         result = new Delegate() {
            String delegate(Invocation inv, String s, boolean b, List<Number> ints)
            {
               if (!b) {
                  return s;
               }

               ints.add(45);
               return inv.proceed();
            }
         };
      }};

      // Do not proceed:
      assertNull(mocked.anotherMethodToBeMocked(null, false, null));

      // Do proceed:
      List<Integer> values = new ArrayList<Integer>();
      assertEquals("TEST[45]", mocked.anotherMethodToBeMocked("test", true, values));

      // Do not proceed again:
      assertEquals("No proceed", mocked.anotherMethodToBeMocked("No proceed", false, null));
   }

   @Test
   public void proceedFromDelegateMethodIntoRealMethodWithModifiedArguments() throws Exception
   {
      final ClassToBeMocked mocked = new ClassToBeMocked();

      new Expectations(ClassToBeMocked.class) {{
         mocked.methodToBeMocked(anyInt);
         result = new Delegate() {
            Integer delegate1(Invocation invocation, int i) { return invocation.proceed(i + 2); }
         };

         mocked.methodToBeMocked(anyInt, (Object[]) any);
         result = new Delegate() {
            Integer delegate2(Invocation inv, int i, Object... args) { return inv.proceed(1, 2, "3"); }
         };
      }};

      assertEquals(3, mocked.methodToBeMocked(1));
      assertEquals(3, mocked.methodToBeMocked(-2, null, "Abc", true, 'a'));
   }

   @Test
   public void proceedFromDelegateMethodIntoConstructor()
   {
      new NonStrictExpectations(ClassToBeMocked.class) {{
         new ClassToBeMocked();
         result = new Delegate() {
            void init(Invocation inv)
            {
               assertNotNull(inv.getInvokedInstance());
               inv.proceed();
            }
         };
      }};

      assertEquals("", new ClassToBeMocked().name);
   }

   @Test
   public void proceedConditionallyFromDelegateMethodIntoConstructor()
   {
      new NonStrictExpectations(ClassToBeMocked.class) {{
         new ClassToBeMocked(anyString);
         result = new Delegate() {
            void init(Invocation inv, String name)
            {
               assertNotNull(inv.getInvokedInstance());

               if ("proceed".equals(name)) {
                  inv.proceed();
               }
            }
         };
      }};

      assertEquals("proceed", new ClassToBeMocked("proceed").name);
      assertNull(new ClassToBeMocked("do not proceed").name);
   }

   @Test(expected = UnsupportedOperationException.class)
   public void cannotProceedFromDelegateMethodIntoConstructorWithNewArguments()
   {
      new NonStrictExpectations(ClassToBeMocked.class) {{
         new ClassToBeMocked(anyString);
         result = new Delegate() {
            void init(Invocation inv, String name) { inv.proceed("mock"); }
         };
      }};

      new ClassToBeMocked("will fail");
   }

   @Test
   public void proceedConditionallyFromDelegateMethodIntoJREConstructor()
   {
      new NonStrictExpectations(File.class) {{
         new File(anyString);
         result = new Delegate() {
            void init(Invocation inv, String name)
            {
               if ("proceed".equals(name)) {
                  inv.proceed();
               }
            }
         };
      }};

      assertEquals("proceed", new File("proceed").getPath());
      assertNull(new File("do not proceed").getPath());
   }

   @SuppressWarnings("UseOfObsoleteCollectionType")
   @Test
   public void proceedFromDelegateMethodIntoJREConstructorWhichCallsAnotherInTheSameClass()
   {
      new NonStrictExpectations(Vector.class) {{
         new Vector<String>(anyInt);
         result = new Delegate() {
            void init(Invocation inv, int i) { inv.proceed(); }
         };
      }};

      assertEquals(1, new Vector<String>(1).capacity());
      assertEquals(10, new Vector<String>().capacity());
   }

   static class MyVector
   {
      private final int capacity;
      MyVector() { this(10); }
      MyVector(int capacity) { this.capacity = capacity; }
      int capacity() { return capacity; }
   }

   @Test
   public void proceedFromDelegateMethodIntoConstructorWhichCallsAnotherInTheSameClass()
   {
      new NonStrictExpectations(MyVector.class) {{
         new MyVector(anyInt);
         result = new Delegate() {
            void init(Invocation inv, int i) { inv.proceed(); }
         };
      }};

      assertEquals(1, new MyVector(1).capacity());

      // This could only be made to pass by requiring use of "-noverify".
      // assertEquals(10, new MyVector().capacity());
   }

   @Test
   public void proceedFromDelegateMethodIntoMethodInheritedFromBaseClass()
   {
      final ClassToBeMocked obj = new ClassToBeMocked();

      new NonStrictExpectations(obj) {{
         obj.baseMethod(anyInt);
         result = new Delegate() {
            int baseMethod(Invocation inv, int i) { return inv.proceed(i + 1); }
         };
      }};

      assertEquals(3, obj.baseMethod(1));
   }

   @Test
   public void replaceMockedInstanceWithRealOne()
   {
      final ClassToBeMocked notMocked = new ClassToBeMocked("not mocked");

      new NonStrictExpectations(ClassToBeMocked.class) {{
         new ClassToBeMocked(anyString); result = notMocked;
      }};

      assertEquals("not mocked", new ClassToBeMocked("test").getName());
   }

   @Test
   public void throwExceptionFromProceedIntoJREMethod(
      @Injectable final AbstractExecutorService c1, @Mocked final ClassToBeMocked c2)
      throws Exception
   {
      new NonStrictExpectations() {{
         c1.submit((Runnable) any);
         result = new Delegate() {
            void delegate(Invocation inv) { inv.proceed(); }
         };
      }};

      try {
         c1.submit((Runnable) null);
         fail();
      }
      catch (NullPointerException ignored) {
         new NonStrictExpectations() {{
            c2.methodToBeMocked(anyInt); result = 123;
         }};

         assertEquals(123, c2.methodToBeMocked(1));
      }
   }
}
