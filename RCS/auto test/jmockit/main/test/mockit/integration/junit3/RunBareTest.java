/*
 * Copyright (c) 2006-2011 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.integration.junit3;

import junit.framework.*;

public final class RunBareTest extends TestCase
{
   private int runBareEntered;

   @Override
   public void runBare() throws Throwable
   {
      runBareEntered++;

      try {
         super.runBare();
      }
      finally {
         runBareEntered--;
      }
   }

   public void testRunBare()
   {
      assertEquals(1, runBareEntered);
   }
}