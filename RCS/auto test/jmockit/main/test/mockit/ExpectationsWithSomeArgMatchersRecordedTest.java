/*
 * Copyright (c) 2006-2013 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit;

import java.util.*;

import org.junit.*;

import static java.util.Arrays.*;
import static org.junit.Assert.*;

import mockit.internal.*;

public final class ExpectationsWithSomeArgMatchersRecordedTest
{
   @SuppressWarnings("UnusedDeclaration")
   static class Collaborator
   {
      void setValue(int value) {}
      void setValue(double value) {}
      void setValue(float value) {}
      String setValue(String value) { return ""; }

      void setValues(long value1, byte value2, double value3, short value4) {}
      boolean booleanValues(long value1, byte value2, double value3, short value4) { return true; }
      static void staticSetValues(long value1, byte value2, double value3, short value4) {}
      static long staticLongValues(long value1, byte value2, double value3, short value4) { return -2; }

      List<?> complexOperation(Object input1, Object... otherInputs)
      {
         return input1 == null ? Collections.emptyList() : asList(otherInputs);
      }

      final void simpleOperation(int a, String b) {}
      final void simpleOperation(int a, String b, Date c) {}
      long anotherOperation(byte b, Long l) { return -1; }

      static void staticVoidMethod(long l, char c, double d) {}
      static boolean staticBooleanMethod(boolean b, String s, int[] array) { return false; }
      void methodWithArrayParameters(char[][] c, String[] s, Object[][][] matrix) {}

      void methodWithManyParameters(
         byte b1, short s1, int i1, long l1, String str1, boolean bo1, float f1, double d1, int[] ii1, String[] ss1,
         byte b2, short s2, int i2, long l2, String str2, boolean bo2, float f2, double d2, int[] ii2, String[] ss2,
         char c) {}
   }

   @Mocked Collaborator mock;

   @Test
   public void useMatcherOnlyForOneArgument()
   {
      final Object o = new Object();

      new Expectations() {{
         mock.simpleOperation(withEqual(1), "", null);
         mock.simpleOperation(withNotEqual(1), null, (Date) withNull());
         mock.simpleOperation(1, withNotEqual("arg"), null); minTimes = 1; maxTimes = 2;
         mock.simpleOperation(12, "arg", (Date) withNotNull());

         mock.anotherOperation((byte) 0, anyLong); result = 123L;
         mock.anotherOperation(anyByte, 5L); result = -123L;

         Collaborator.staticVoidMethod(34L, anyChar, 5.0);
         Collaborator.staticBooleanMethod(true, withSuffix("end"), null); result = true;
         Collaborator.staticBooleanMethod(true, "", new int[] {1, 2, 3}); result = true;

         char[][] chars = {{'a', 'b'}, {'X', 'Y', 'Z'}};
         Object[][][] matrix = {null, {{1, 'X', "test"}}, {{o}}};
         mock.methodWithArrayParameters(chars, (String[]) any, matrix);
      }};

      mock.simpleOperation(1, "", null);
      mock.simpleOperation(2, "str", null);
      mock.simpleOperation(1, "", null);
      mock.simpleOperation(12, "arg", new Date());

      assertEquals(123L, mock.anotherOperation((byte) 0, 5L));
      assertEquals(-123L, mock.anotherOperation((byte) 3, 5L));

      Collaborator.staticVoidMethod(34L, '8', 5.0);
      assertTrue(Collaborator.staticBooleanMethod(true, "start-end", null));
      assertTrue(Collaborator.staticBooleanMethod(true, "", new int[] {1, 2, 3}));

      mock.methodWithArrayParameters(
         new char[][] {{'a', 'b'}, {'X', 'Y', 'Z'}}, null, new Object[][][] {null, {{1, 'X', "test"}}, {{o}}});
   }

   @Test(expected = UnexpectedInvocation.class)
   public void useMatcherOnlyForFirstArgumentWithUnexpectedReplayValue()
   {
      new Expectations() {{
         mock.simpleOperation(withEqual(1), "", null);
      }};

      mock.simpleOperation(2, "", null);
   }

   @Test(expected = UnexpectedInvocation.class)
   public void useMatcherOnlyForSecondArgumentWithUnexpectedReplayValue()
   {
      new Expectations() {{
         mock.simpleOperation(1, withPrefix("arg"), null);
      }};

      mock.simpleOperation(1, "Xyz", null);
   }

   @Test(expected = UnexpectedInvocation.class)
   public void useMatcherOnlyForLastArgumentWithUnexpectedReplayValue()
   {
      new Expectations() {{
         mock.simpleOperation(12, "arg", (Date) withNotNull());
      }};

      mock.simpleOperation(12, "arg", null);
   }

   @Test
   public void useMatchersForParametersOfAllSizes()
   {
      new NonStrictExpectations() {{
         mock.setValues(123L, withEqual((byte) 5), 6.4, withNotEqual((short) 14));
         mock.booleanValues(12L, (byte) 4, withEqual(6.0, 0.1), withEqual((short) 14));
         Collaborator.staticSetValues(withNotEqual(1L), (byte) 4, 6.1, withEqual((short) 3));
         Collaborator.staticLongValues(12L, anyByte, withEqual(6.1), (short) 4);
      }};

      mock.setValues(123L, (byte) 5, 6.4, (short) 41);
      assertFalse(mock.booleanValues(12L, (byte) 4, 6.1, (short) 14));
      Collaborator.staticSetValues(2L, (byte) 4, 6.1, (short) 3);
      assertEquals(0L, Collaborator.staticLongValues(12L, (byte) -7, 6.1, (short) 4));
   }

   @Test
   public void useAnyIntField()
   {
      new Expectations() {{ mock.setValue(anyInt); }};

      mock.setValue(1);
   }

   @Test
   public void useAnyStringField()
   {
      new NonStrictExpectations() {{
         mock.setValue(anyString); returns("one", "two");
      }};

      assertEquals("one", mock.setValue("test"));
      assertEquals("two", mock.setValue(""));
      assertEquals("two", mock.setValue(null));
   }

   @Test
   public void useSeveralAnyFields()
   {
      final Date now = new Date();

      new Expectations() {{
         mock.simpleOperation(anyInt, null, null);
         mock.simpleOperation(anyInt, "test", null);
         mock.simpleOperation(3, "test2", null);
         mock.simpleOperation(-1, null, (Date) any);
         mock.simpleOperation(1, anyString, now);

         Collaborator.staticSetValues(2L, anyByte, 0.0, anyShort);

         mock.methodWithManyParameters(
            anyByte, anyShort, anyInt, anyLong, anyString, anyBoolean, anyFloat, anyDouble, (int[]) any, (String[]) any,
            anyByte, anyShort, anyInt, anyLong, anyString, anyBoolean, anyFloat, anyDouble, (int[]) any, (String[]) any,
            anyChar);
      }};

      mock.simpleOperation(2, "abc", now);
      mock.simpleOperation(5, "test", null);
      mock.simpleOperation(3, "test2", null);
      mock.simpleOperation(-1, "Xyz", now);
      mock.simpleOperation(1, "", now);

      Collaborator.staticSetValues(2, (byte) 1, 0, (short) 2);

      mock.methodWithManyParameters(
         (byte) 1, (short) 2, 3, 4L, "5", false, 7.0F, 8.0, null, null,
         (byte) 10, (short) 20, 30, 40L, "50", true, 70.0F, 80.0, null, null, 'x');
   }

   @Test
   public void useWithMethodsMixedWithAnyFields()
   {
      new Expectations() {{
         mock.simpleOperation(anyInt, null, (Date) any);
         mock.simpleOperation(anyInt, withEqual("test"), null);
         mock.simpleOperation(3, withPrefix("test"), (Date) any);
         mock.simpleOperation(-1, anyString, (Date) any);
         mock.simpleOperation(1, anyString, (Date) withNotNull());
      }};

      mock.simpleOperation(2, "abc", new Date());
      mock.simpleOperation(5, "test", null);
      mock.simpleOperation(3, "test2", null);
      mock.simpleOperation(-1, "Xyz", new Date());
      mock.simpleOperation(1, "", new Date());
   }

   public interface Scheduler
   {
      List<String> getAlerts(Object o, int i, boolean b);
   }

   @Test
   public void useMatchersInInvocationsToInterfaceMethods(@Mocked final Scheduler mock)
   {
      new NonStrictExpectations() {{
         mock.getAlerts(any, 1, anyBoolean); result = asList("A", "b");
      }};

      assertEquals(2, mock.getAlerts("123", 1, true).size());
   }

   // The following tests failed only when compiled with the Eclipse compiler /////////////////////////////////////////

   @Test
   public void expectationWithMatchersSpanningMultipleLines()
   {
      new Expectations() {{
         mock.simpleOperation(1,
            (String) withNull());
      }};

      mock.simpleOperation(1, null);
   }

   @Test
   public void expectationWithMatcherInSecondLineAndConstantArgumentInThirdLine()
   {
      new Expectations() {{
         mock.simpleOperation(
            anyInt,
            "test");
      }};

      mock.simpleOperation(123, "test");
   }

   @Test
   public void expectationsWithPartialMatchersInEveryCombinationForMethodWithThreeParameters()
   {
      final Date now = new Date();

      new Expectations() {{
         // Expectations with one matcher:
         mock.simpleOperation(
            anyInt,
            "test", null);
         mock.simpleOperation(-2, anyString,
            null);
         mock.simpleOperation(
            0,
            "test", (Date) withNotNull());
         mock.simpleOperation(
            1,
            null,
            (Date) withNull());
         mock.simpleOperation(
            0, "test",
            (Date) any);

         // Expectations with two matchers:
         mock.simpleOperation(-3, anyString,
            (Date) any);
         mock.simpleOperation(
            withNotEqual(0), anyString,
            now);
         mock.simpleOperation(anyInt,
            "",
            (Date) any);
      }};

      mock.simpleOperation(123, "test", null);
      mock.simpleOperation(-2, "", now);
      mock.simpleOperation(0, "test", now);
      mock.simpleOperation(1, "test", null);
      mock.simpleOperation(0, "test", null);
      mock.simpleOperation(-3, "xyz", now);
      mock.simpleOperation(123, null, now);
      mock.simpleOperation(123, "", null);
   }
}