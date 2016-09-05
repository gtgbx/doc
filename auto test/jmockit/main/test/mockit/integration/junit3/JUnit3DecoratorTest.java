/*
 * Copyright (c) 2006-2013 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.integration.junit3;

import junit.framework.*;
import mockit.*;

public final class JUnit3DecoratorTest extends TestCase
{
   public static class RealClass1
   {
      public String getValue() { return "REAL1"; }
   }

   public static class MockClass1 extends MockUp<RealClass1>
   {
      @Mock public String getValue() { return "TEST1"; }
   }

   @Override
   protected void setUp()
   {
      assertEquals("REAL1", new RealClass1().getValue());
      new MockClass1();
      assertEquals("TEST1", new RealClass1().getValue());
   }

   @Override
   protected void tearDown()
   {
      assertEquals("REAL2", new RealClass2().getValue());
      assertEquals("TEST1", new RealClass1().getValue());
   }

   public static class RealClass2
   {
      public String getValue() { return "REAL2"; }
   }

   public static class MockClass2 extends MockUp<RealClass2>
   {
      @Mock
      public String getValue() { return "TEST2"; }
   }

   public void testSetUpAndUseSomeMocks()
   {
      new MockClass2();

      assertEquals("TEST2", new RealClass2().getValue());
      assertEquals("TEST1", new RealClass1().getValue());
   }

   public void testMockParameter(@Mocked RealClass2 mock)
   {
      assertEquals("TEST1", new RealClass1().getValue());
      assertNotNull(mock);
      assertNull(mock.getValue());
   }

   // To verify that static methods are not considered as tests.
   public static void testSomething(Object arg1, String arg2)
   {
      System.out.println("arg1=" + arg1 + " arg2=" + arg2);
   }
}
