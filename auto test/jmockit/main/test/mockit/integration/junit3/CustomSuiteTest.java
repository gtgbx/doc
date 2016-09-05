/*
 * Copyright (c) 2006-2011 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.integration.junit3;

import junit.framework.*;

public final class CustomSuiteTest extends TestCase
{
   public CustomSuiteTest()
   {
      super("NamedTest");
   }

   @Override
   protected void runTest()
   {
      assertEquals("NamedTest", getName());
   }

   public static Test suite()
   {
      TestSuite suite = new TestSuite();
      suite.addTest(new CustomSuiteTest());
      return suite;
   }
}