/*
 * Copyright (c) 2006-2012 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.integration.junit3;

import junit.framework.*;

import mockit.*;
import mockit.integration.*;

public final class JUnit3ExpectationsTest extends TestCase
{
   @Injectable MockedClass mock;

   @Override
   protected void setUp()
   {
      new NonStrictExpectations() {{ mock.getValue(); result = "mocked"; }};
   }

   @Override
   protected void tearDown()
   {
      new Verifications() {{ mock.doSomething(anyInt); }};
   }

   public void testSomething()
   {
      new NonStrictExpectations() {{
         mock.doSomething(anyInt); result = true;
      }};

      assertTrue(mock.doSomething(5));
      assertEquals("mocked", mock.getValue());
      assertTrue(mock.doSomething(-5));

      new FullVerifications() {{
         mock.doSomething(anyInt); times = 2;
         mock.getValue();
      }};
   }

   public void testSomethingElse()
   {
      assertEquals("mocked", mock.getValue());
      assertFalse(mock.doSomething(41));

      new FullVerificationsInOrder() {{
         mock.getValue();
         mock.doSomething(anyInt);
      }};
   }
}
