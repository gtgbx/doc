/*
 * Copyright (c) 2006-2011 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package integrationTests;

import org.junit.*;

public final class InterfaceWithExecutableCodeTest extends CoverageTest
{
   InterfaceWithExecutableCode tested;

   @Test
   public void exerciseExecutableLineInInterface()
   {
      assertTrue(InterfaceWithExecutableCode.N > 0);

      assertLines(7, 7, 1);
      assertLine(7, 1, 1, 1);
   }
}