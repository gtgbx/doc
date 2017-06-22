/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

import org.junit.*;
import static org.junit.Assert.*;

public final class GenericMockedTypesTest
{
   @Mocked Callable<Integer> mock2;

   @Test
   public void mockGenericInterfaces(@Mocked final Callable<?> mock1) throws Exception
   {
      Class<?> mockedClass1 = mock1.getClass();
      assertEquals(1, mockedClass1.getGenericInterfaces().length);

      new Expectations() {{
         Class<?> mockedClass2 = mock2.getClass();

         ParameterizedType genericType2 = (ParameterizedType) mockedClass2.getGenericInterfaces()[0];
         assertSame(Callable.class, genericType2.getRawType());
         assertSame(Integer.class, genericType2.getActualTypeArguments()[0]);

         Method mockedMethod = mockedClass2.getDeclaredMethod("call");
         assertSame(Integer.class, mockedMethod.getGenericReturnType());

         mock1.call(); result = "mocked";
         mock2.call(); result = 123;
      }};

      assertEquals("mocked", mock1.call());
      assertEquals(123, mock2.call().intValue());
   }

   @Test
   public void mockGenericAbstractClass(@Mocked final Dictionary<Integer, String> mock) throws Exception
   {
      new Expectations() {{
         Class<?> mockedClass = mock.getClass();

         ParameterizedType genericBase = (ParameterizedType) mockedClass.getGenericSuperclass();
         assertSame(Dictionary.class, genericBase.getRawType());
         assertSame(Integer.class, genericBase.getActualTypeArguments()[0]);
         assertSame(String.class, genericBase.getActualTypeArguments()[1]);

         Method mockedMethod1 = mockedClass.getDeclaredMethod("keys");
         assertEquals("java.util.Enumeration<java.lang.Integer>", mockedMethod1.getGenericReturnType().toString());

         Method mockedMethod2 = mockedClass.getDeclaredMethod("elements");
         assertEquals("java.util.Enumeration<java.lang.String>", mockedMethod2.getGenericReturnType().toString());

         Method mockedMethod3 = mockedClass.getDeclaredMethod("put", Object.class, Object.class);
         assertSame(String.class, mockedMethod3.getGenericReturnType());

         mock.put(123, "test"); result = "mocked";
      }};

      assertEquals("mocked", mock.put(123, "test"));
   }

   @Test
   public void mockRawMapInterface(@SuppressWarnings("rawtypes") @Mocked final Map rawMap)
   {
      new NonStrictExpectations() {{
         rawMap.get("test");
         result = new Object();
      }};

      Object value = rawMap.get("test");
      assertNotNull(value);
   }

   public interface InterfaceWithMethodParametersMixingGenericTypesAndArrays
   {
      <T> void doSomething(int[] i, T b);
      void doSomething(Callable<int[]> pc, int[] ii);
      void doSomething(Callable<String> pc, int[] i, boolean[] currencies, int[] ii);
   }

   @Test
   public void mockMethodsHavingGenericsAndArrays(@Mocked InterfaceWithMethodParametersMixingGenericTypesAndArrays mock)
   {
      mock.doSomething((Callable<int[]>) null, new int[] {1, 2});
      mock.doSomething(null, new int[] {1, 2}, null, new int[] {3, 4, 5});
   }

   public interface NonGenericInterfaceWithGenericMethods
   {
      <T> T genericMethodWithUnboundedReturnType();
      <T extends CharSequence> T genericMethodWithBoundedReturnType();
   }

   @Test
   public void resultFromGenericMethodsOfNonGenericInterface(@Mocked final NonGenericInterfaceWithGenericMethods mock)
   {
      new NonStrictExpectations() {{
         mock.genericMethodWithUnboundedReturnType(); result = 123;
         mock.genericMethodWithBoundedReturnType(); result = "test";
      }};

      Object v1 = mock.genericMethodWithUnboundedReturnType();
      assertEquals(123, v1);

      Object v2 = mock.genericMethodWithBoundedReturnType();
      assertEquals("test", v2);
   }

   static class Item implements Serializable {}
   static class GenericContainer<T extends Serializable> { final T getItem() { return null; } }

   @Test
   public void createFirstLevelCascadedMockFromTypeParameter(@Cascading GenericContainer<Item> mockContainer)
   {
      Serializable mock = mockContainer.getItem();

      assertSame(Item.class, mock.getClass());
   }

   static class Factory1 { static GenericContainer<Item> getContainer() { return null; } }

   @Ignore @Test
   public void createSecondLevelCascadedMockFromTypeParameterInGenericMethodResolvedFromFirstLevelReturnType(
      @Cascading Factory1 mockFactory)
   {
      GenericContainer<Item> mockContainer = Factory1.getContainer();
      Serializable cascadedMock = mockContainer.getItem();

      assertNotNull(cascadedMock);
      assertSame(Item.class, cascadedMock.getClass());
   }

   static class ConcreteContainer extends GenericContainer<Item> { ConcreteContainer() {} }
   static class Factory2 { ConcreteContainer getContainer() { return null; } }

   @Ignore @Test
   public void createSecondLevelCascadedMockFromTypeParameterInBaseTypeOfMethodReturn(@Cascading Factory2 mockFactory)
   {
      ConcreteContainer mockContainer = mockFactory.getContainer();
      Serializable cascadedMock = mockContainer.getItem();

      assertSame(Item.class, cascadedMock.getClass());
   }
}
