/*
 * Copyright (c) 2006-2011 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.integration.junit3;

import junit.framework.*;

public final class RunTestTest extends TestCase
{
   private int testExecuted;

   @Override
   protected void runTest() throws Throwable
   {
      super.runTest();
   }

   public void testRunTest()
   {
      testExecuted++;
      assertEquals(1, testExecuted);
   }
}