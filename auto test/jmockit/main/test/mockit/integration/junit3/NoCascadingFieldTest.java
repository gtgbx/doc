/*
 * Copyright (c) 2006-2012 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.integration.junit3;

import junit.framework.*;

import mockit.*;

public final class NoCascadingFieldTest extends TestCase
{
   @Mocked ProcessBuilder builder;

   public void testShouldNotObtainCascadedInstance() throws Exception
   {
      assertNull(builder.start());
   }
}
