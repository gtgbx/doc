/*
 * Copyright (c) 2006-2013 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.integration.testng;

import static org.testng.Assert.*;
import org.testng.annotations.*;

import mockit.*;

public final class TestNGSharedMockFieldTest
{
   public interface Dependency
   {
      boolean doSomething();
      void doSomethingElse();
   }

   @Mocked Dependency mock1;
   @Mocked Runnable mock2;

   @Test
   public void recordAndReplayExpectationsOnSharedMocks()
   {
      // Strict mocking:
      new Expectations() {{
         mock1.doSomething(); result = true;
         mock2.run();
      }};

      assertTrue(mock1.doSomething());
      mock2.run();
   }

   @Test
   public void recordAndReplayExpectationsOnSharedMocksAgain()
   {
      new NonStrictExpectations() {{
         mock1.doSomething(); result = true;
         mock1.doSomethingElse();
      }};

      assertTrue(mock1.doSomething());
      mock2.run(); // mock2 should be non-strict here
   }
}
