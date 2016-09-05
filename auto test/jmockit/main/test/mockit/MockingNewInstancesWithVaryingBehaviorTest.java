/*
 * Copyright (c) 2006-2013 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit;

import java.text.*;
import java.util.*;

import static org.junit.Assert.*;
import org.junit.*;

public final class MockingNewInstancesWithVaryingBehaviorTest
{
   private static final String DATE_FORMAT = "yyyy-MM-dd";
   private static final String FORMATTED_DATE = "2012-02-28";

   private static final String TIME_FORMAT = "HH";
   private static final String FORMATTED_TIME = "13";

   private void exerciseAndVerifyTestedCode()
   {
      Date now = new Date();

      String hour = new SimpleDateFormat(TIME_FORMAT).format(now);
      assertEquals(FORMATTED_TIME, hour);

      String date = new SimpleDateFormat(DATE_FORMAT).format(now);
      assertEquals(FORMATTED_DATE, date);
   }

   /// Tests using the Mockups API ////////////////////////////////////////////////////////////////////////////////////

   DateFormat dateFormat;
   DateFormat hourFormat;

   @Test
   public void usingMockUpsWithItField()
   {
      new MockUp<SimpleDateFormat>() {
         @Mock
         void $init(Invocation inv, String pattern)
         {
            SimpleDateFormat it = inv.getInvokedInstance();
            if (DATE_FORMAT.equals(pattern)) dateFormat = it;
            else if (TIME_FORMAT.equals(pattern)) hourFormat = it;
         }
      };

      new MockUp<DateFormat>() {
         @Mock
         String format(Invocation inv, Date d)
         {
            assertNotNull(d);

            DateFormat it = inv.getInvokedInstance();
            if (it == dateFormat) return FORMATTED_DATE;
            else if (it == hourFormat) return FORMATTED_TIME;
            else return null;
         }
      };

      exerciseAndVerifyTestedCode();
   }

   @Test
   public void usingMockUpsWithInvocationParameter()
   {
      new MockUp<SimpleDateFormat>() {
         @Mock
         void $init(Invocation inv, String pattern)
         {
            DateFormat dt = inv.getInvokedInstance();
            if (DATE_FORMAT.equals(pattern)) dateFormat = dt;
            else if (TIME_FORMAT.equals(pattern)) hourFormat = dt;
         }
      };

      new MockUp<DateFormat>() {
         @Mock
         String format(Invocation inv, Date d)
         {
            assertNotNull(d);
            DateFormat dt = inv.getInvokedInstance();
            if (dt == dateFormat) return FORMATTED_DATE;
            else if (dt == hourFormat) return FORMATTED_TIME;
            else return null;
         }
      };

      exerciseAndVerifyTestedCode();
   }

   /// Tests using the Expectations API ///////////////////////////////////////////////////////////////////////////////

   @Test // not too complex, but inelegant
   public void usingPartialMockingAndDelegate(@Mocked final SimpleDateFormat sdf)
   {
      new NonStrictExpectations(SimpleDateFormat.class) {{
         sdf.format((Date) any);
         result = new Delegate() {
            @Mock String format(Invocation inv)
            {
               String pattern = inv.<SimpleDateFormat>getInvokedInstance().toPattern();
               if (DATE_FORMAT.equals(pattern)) return FORMATTED_DATE;
               else if (TIME_FORMAT.equals(pattern)) return FORMATTED_TIME;
               else return null;
            }
         };
      }};

      exerciseAndVerifyTestedCode();
   }

   final Map<Object, String> formats = new IdentityHashMap<Object, String>();

   final class SDFFormatDelegate implements Delegate<Object>
   {
      final String format;
      SDFFormatDelegate(String format) { this.format = format; }

      @SuppressWarnings("UnusedDeclaration")
      void saveInstance(Invocation inv) { formats.put(inv.getInvokedInstance(), format); }
   }

   @Test // too complex
   public void usingDelegates(@Mocked final SimpleDateFormat mockSDF)
   {
      new NonStrictExpectations() {{
         new SimpleDateFormat(DATE_FORMAT); result = new SDFFormatDelegate(FORMATTED_DATE);
         new SimpleDateFormat(TIME_FORMAT); result = new SDFFormatDelegate(FORMATTED_TIME);

         mockSDF.format((Date) any);
         result = new Delegate() {
            @Mock String format(Invocation inv) { return formats.get(inv.getInvokedInstance()); }
         };
      }};

      exerciseAndVerifyTestedCode();
   }

   @Test // nice
   public void usingCapturing(@Mocked final SimpleDateFormat dateFmt, @Mocked final SimpleDateFormat hourFmt)
   {
      new NonStrictExpectations() {{
         new SimpleDateFormat(DATE_FORMAT); result = dateFmt;
         dateFmt.format((Date) any); result = FORMATTED_DATE;

         new SimpleDateFormat(TIME_FORMAT); result = hourFmt;
         hourFmt.format((Date) any); result = FORMATTED_TIME;
      }};

      exerciseAndVerifyTestedCode();
   }

   static class Collaborator
   {
      final int value;
      Collaborator() { value = -1; }
      Collaborator(int value) { this.value = value; }
      int getValue() { return value; }
      boolean isPositive() { return value > 0; }
      String doSomething(String s) { return s + ": " + value; }
   }

   @Test
   public void matchMethodCallsOnInstancesCreatedWithConstructorMatchingRecordedOne(@Mocked final Collaborator mock)
   {
      new NonStrictExpectations() {{
         new Collaborator(5); result = mock;
         onInstance(mock).getValue(); result = 123;
      }};

      assertEquals(0, new Collaborator().getValue());

      Collaborator newCol1 = new Collaborator(5);
      assertEquals(123, newCol1.getValue());

      Collaborator newCol2 = new Collaborator(6);
      assertEquals(0, newCol2.getValue());
      assertFalse(newCol2.isPositive());

      Collaborator newCol3 = new Collaborator(5);
      assertEquals(123, newCol3.getValue());
      assertFalse(newCol3.isPositive());

      new Verifications() {{
         // Verify invocations to all mocked instances:
         new Collaborator(); times = 1;
         new Collaborator(anyInt); times = 3;
         mock.getValue(); times = 4;
         mock.isPositive(); times = 2;

         // Verify invocations to mocked instances matching the "mock" instance:
         onInstance(mock).getValue(); times = 2;
         onInstance(mock).isPositive(); times = 1;
      }};
   }

   @Test
   public void mockInstancesMatchingRecordedConstructorInvocationsToHaveSameBehaviorAsOtherUnmockedInstances()
   {
      final Collaborator col1 = new Collaborator(1);
      final Collaborator col2 = new Collaborator(-2);

      new NonStrictExpectations(Collaborator.class) {{
         new Collaborator(3); result = col1;
         new Collaborator(5); result = col2;
         onInstance(col1).doSomething("recorded"); result = "mocked";
      }};

      Collaborator newCol1 = new Collaborator(-10);
      assertEquals(-10, newCol1.getValue());
      assertEquals("not mocked: -10", newCol1.doSomething("not mocked"));
      assertEquals("recorded: -10", newCol1.doSomething("recorded"));

      Collaborator newCol2 = new Collaborator(3);
      assertEquals(1, newCol2.getValue());
      assertEquals("mocked", newCol2.doSomething("recorded"));
      assertEquals("not recorded: 1", newCol2.doSomething("not recorded"));

      Collaborator newCol3 = new Collaborator(5);
      assertEquals(-2, newCol3.getValue());
      assertFalse(newCol3.isPositive());
      assertEquals("null: -2", newCol3.doSomething(null));

      Collaborator newCol4 = new Collaborator(10);
      assertEquals(10, newCol4.getValue());
      assertTrue(newCol4.isPositive());

      Collaborator newCol5 = new Collaborator(3);
      assertEquals(1, newCol5.getValue());
      assertTrue(newCol5.isPositive());
      assertEquals("mocked", newCol5.doSomething("recorded"));
      assertEquals("test: 1", newCol5.doSomething("test"));

      new Verifications() {{
         onInstance(col1).getValue(); times = 2;
         onInstance(col1).isPositive(); times = 1;

         onInstance(col2).getValue(); times = 1;
         onInstance(col2).isPositive(); times = 1;

         col1.doSomething(anyString); times = 7;
         onInstance(col1).doSomething(anyString); times = 4;
         onInstance(col2).doSomething(anyString); times = 1;
      }};
   }
}
