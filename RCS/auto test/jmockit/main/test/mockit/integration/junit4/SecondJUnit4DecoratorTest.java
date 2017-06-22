/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.integration.junit4;

import org.junit.*;

import mockit.*;

import static org.junit.Assert.assertEquals;

public final class SecondJUnit4DecoratorTest
{
   public static final class RealClass3
   {
      public String getValue() { return "REAL3"; }
   }

   public static final class MockClass3 extends MockUp<RealClass3>
   {
      @Mock public String getValue() { return "TEST3"; }
   }

   @BeforeClass
   public static void setUpMocks()
   {
      new MockClass3();
   }

   @Test
   public void realClassesMockedInPreviousTestClassMustNoLongerBeMocked()
   {
      assertEquals("REAL0", new BaseJUnit4DecoratorTest.RealClass0().getValue());
      assertEquals("REAL1", new BaseJUnit4DecoratorTest.RealClass1().getValue());
      assertEquals("REAL2", new JUnit4DecoratorTest.RealClass2().getValue());
   }

   @Test
   public void useClassScopedMockDefinedForThisClass()
   {
      assertEquals("TEST3", new RealClass3().getValue());
   }
}