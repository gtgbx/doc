/*
 * Copyright (c) 2006-2011 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit;

import junit.framework.*;

public final class DynamicMockingInSetUpMethodTest extends TestCase
{
   static final class MockedClass
   {
      boolean doSomething(int i) { return i > 0; }
   }

   final MockedClass anInstance = new MockedClass();

   @Override
   public void setUp()
   {
      assertTrue(anInstance.doSomething(56));

      new NonStrictExpectations(anInstance)
      {{
         anInstance.doSomething(anyInt); result = true;
      }};
   }

   @Override
   public void tearDown()
   {
      new FullVerifications()
      {{
         anInstance.doSomething(anyInt); times = 1;
      }};
   }

   public void testSomething()
   {
      assertTrue(anInstance.doSomething(56));
   }

   public void testSomethingElse()
   {
      assertTrue(anInstance.doSomething(-129));
   }
}
