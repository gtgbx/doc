/*
 * Copyright (c) 2006-2013 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit;

import org.junit.*;
import org.junit.runners.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public final class CapturingNewInstanceTest
{
   static class Collaborator { void doSomething() {} }
   @Capturing Collaborator mock;

   @Test
   public void instantiateMockedClassOnce()
   {
      new Collaborator();
   }

   @Test
   public void recordNonStrictExpectationsOnCapturingMock(@Mocked Collaborator unused)
   {
      new NonStrictExpectations() {{
         mock.doSomething();
         times = 1;
      }};

      Collaborator col = new Collaborator();
      col.doSomething();
   }

   @Test
   public void recordStrictExpectationsOnCapturingMock(@Mocked Collaborator unused)
   {
      new Expectations() {{
         new Collaborator();
         mock.doSomething();
      }};

      new Collaborator().doSomething();
   }
}
