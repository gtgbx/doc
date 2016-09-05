/*
 * Copyright (c) 2006-2013 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit;

import java.util.*;

import org.junit.*;
import static org.junit.Assert.*;

public final class MockUpForGenericsTest
{
   // Mock-ups for generic classes/methods ////////////////////////////////////////////////////////////////////////////

   static final class Collaborator
   {
      <N extends Number> N genericMethod(@SuppressWarnings("UnusedParameters") N n) { return null; }
   }

   @Test
   public void mockGenericMethod()
   {
      new MockUp<Collaborator>() {
         @Mock <T extends Number> T genericMethod(T t) { return t; }

         // This also works (same erasure):
         // @Mock Number genericMethod(Number t) { return t; }
      };

      Integer n = new Collaborator().genericMethod(123);
      assertEquals(123, n.intValue());

      Long l = new Collaborator().genericMethod(45L);
      assertEquals(45L, l.longValue());

      Short s = new Collaborator().genericMethod((short) 6);
      assertEquals(6, s.shortValue());

      Double d = new Collaborator().genericMethod(0.5);
      assertEquals(0.5, d, 0);
   }

   @SuppressWarnings("UnusedParameters")
   public static final class GenericClass<T1, T2>
   {
      public void aMethod(T1 t) { throw new RuntimeException("t=" + t); }
      public int anotherMethod(T1 t, int i, T2 p) { return 2 * i; }
      public int anotherMethod(Integer t, int i, String p) { return -2 * i; }
   }

   @Test
   public void mockGenericClassWithUnspecifiedTypeArguments()
   {
      new MockUp<GenericClass<?, ?>>() {
         @Mock(minInvocations = 1)
         void aMethod(Object o)
         {
            StringBuilder s = (StringBuilder) o;
            s.setLength(0);
            s.append("mock");
            s.toString();
         }

         @Mock
         int anotherMethod(Object o, int i, Object list)
         {
            assertTrue(o instanceof StringBuilder);
            //noinspection unchecked
            assertEquals(0, ((Collection<String>) list).size());
            return -i;
         }
      };

      StringBuilder s = new StringBuilder("test");
      GenericClass<StringBuilder, List<String>> g = new GenericClass<StringBuilder, List<String>>();

      g.aMethod(s);
      int r1 = g.anotherMethod(new StringBuilder("test"), 58, Collections.<String>emptyList());
      int r2 = g.anotherMethod(123, 65, "abc");

      assertEquals("mock", s.toString());
      assertEquals(-58, r1);
      assertEquals(-130, r2);
   }

   @Test
   public void mockBothGenericAndNonGenericMethodsInGenericClass()
   {
      new MockUp<GenericClass<String, Boolean>>() {
         @Mock int anotherMethod(Integer t, int i, String p) { return 2; }
         @Mock int anotherMethod(String t, int i, Boolean p) { return 1; }
      };

      GenericClass<String, Boolean> o = new GenericClass<String, Boolean>();
      assertEquals(1, o.anotherMethod("generic", 1, true));
      assertEquals(2, o.anotherMethod(123, 2, "non generic"));
   }

   @Test(expected = IllegalArgumentException.class)
   public void cannotMockGenericClassMethodWhenParameterTypeInMockMethodDiffersFromTypeArgument()
   {
      new MockUp<GenericClass<String, Boolean>>() {
         @Mock void aMethod(Integer t) {}
      };
   }

   static class GenericBaseClass<T, U> { U find(@SuppressWarnings("UnusedParameters") T id) { return null; } }

   @Test
   public void mockGenericMethodWithMockMethodHavingParameterTypesMatchingTypeArguments()
   {
      new MockUp<GenericBaseClass<String, Integer>>() {
         @Mock
         Integer find(String id) { return id.hashCode(); }
      };

      int i = new GenericBaseClass<String, Integer>().find("test");
      assertEquals("test".hashCode(), i);
   }

   @Test
   public void cannotCallGenericMethodWhenSomeMockMethodExpectsDifferentTypes()
   {
      new MockUp<GenericBaseClass<String, Integer>>() { @Mock Integer find(String id) { return 1; } };

      try {
         new GenericBaseClass<Integer, String>().find(1);
         fail();
      }
      catch (IllegalArgumentException e) {
         assertTrue(e.getMessage().startsWith("Failure to invoke method: "));
      }
   }

   static class NonGenericSuperclass extends GenericBaseClass<Integer, String> {}
   final class NonGenericSubclass extends NonGenericSuperclass {}

   @Test
   public void mockGenericMethodFromInstantiationOfNonGenericSubclass()
   {
      new MockUp<NonGenericSubclass>() {
         @Mock
         String find(Integer id) { return "mocked" + id; }
      };

      String s = new NonGenericSubclass().find(1);
      assertEquals("mocked1", s);
   }

   static class GenericSuperclass<I> extends GenericBaseClass<I, String> {}
   final class AnotherNonGenericSubclass extends GenericSuperclass<Integer> {}

   @Test
   public void mockGenericMethodFromInstantiationOfNonGenericSubclassWhichExtendsAGenericIntermediateSuperclass()
   {
      new MockUp<AnotherNonGenericSubclass>() {
         @Mock
         String find(Integer id) { return "mocked" + id; }
      };

      String s = new AnotherNonGenericSubclass().find(1);
      assertEquals("mocked1", s);
   }

   @SuppressWarnings("UnusedParameters")
   static class NonGenericClassWithGenericMethods
   {
      static <T> T staticMethod(Class<T> cls, String s) { throw new RuntimeException(); }
      <C> void instanceMethod(Class<C> cls, String s) { throw new RuntimeException(); }
      <N extends Number> void instanceMethod(Class<N> cls) { throw new RuntimeException(); }
   }

   @Test
   public void mockGenericMethodsOfNonGenericClass()
   {
      new MockUp<NonGenericClassWithGenericMethods>() {
         @Mock <T> T staticMethod(Class<T> cls, String s) { return null; }
         @Mock <C> void instanceMethod(Class<C> cls, String s) {}
         @Mock void instanceMethod(Class<?> cls) {}
      };

      new NonGenericClassWithGenericMethods().instanceMethod(Integer.class);
      NonGenericClassWithGenericMethods.staticMethod(Collaborator.class, "test1");
      new NonGenericClassWithGenericMethods().instanceMethod(Byte.class, "test2");
   }

   // Mock-ups for generic interfaces /////////////////////////////////////////////////////////////////////////////////

   public interface GenericInterface<T> { void method(T t); }

   @Test
   public void mockGenericInterfaceMethodWithMockMethodHavingParameterOfTypeObject()
   {
      GenericInterface<Boolean> mock = new MockUp<GenericInterface<Boolean>>() {
         @Mock
         void method(Object b) { assertTrue((Boolean) b); }
      }.getMockInstance();

      mock.method(true);
   }

   public interface NonGenericSubInterface extends GenericInterface<Long> {}

   @Test
   public void mockMethodOfSubInterfaceWithGenericTypeArgument()
   {
      NonGenericSubInterface mock = new MockUp<NonGenericSubInterface>() {
         @Mock(invocations = 1)
         void method(Long l) { assertTrue(l > 0); }
      }.getMockInstance();

      mock.method(123L);
   }

   @Test(expected = IllegalArgumentException.class)
   public void cannotMockGenericInterfaceMethodWhenParameterTypeInMockMethodDiffersFromTypeArgument()
   {
      new MockUp<Comparable<String>>() { @Mock int compareTo(Integer i) { return 1; } };
   }

   @Test
   public void mockGenericInterfaceMethod()
   {
      Comparable<Integer> cmp = new MockUp<Comparable<Integer>>() {
         @Mock
         int compareTo(Integer i) { assertEquals(123, i.intValue()); return 2; }
      }.getMockInstance();

      assertEquals(2, cmp.compareTo(123));
   }
}
