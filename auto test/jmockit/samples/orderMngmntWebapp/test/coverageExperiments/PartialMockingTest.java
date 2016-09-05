/*
 * Copyright (c) 2006-2012 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package coverageExperiments;

import static org.junit.Assert.*;
import org.junit.*;

import mockit.*;

public final class PartialMockingTest
{
   @Test
   public void useStaticPartialMocking(@Mocked("doSomethingElse") final TestedClassPartiallyMocked sut)
   {
      new NonStrictExpectations() {{
         sut.doSomethingElse(); result = 3;
      }};

      int r = sut.doSomething("testing");
      assertEquals(10, r);
   }

   @Test
   public void useDynamicPartialMocking()
   {
      final TestedClassPartiallyMocked sut = new TestedClassPartiallyMocked();

      new NonStrictExpectations(sut) {{
         sut.doSomethingElse(); result = 5;
      }};

      int r = sut.doSomething("testing");
      assertEquals(12, r);
   }

   @Test
   public void useAMockUp()
   {
      new MockUp<TestedClassPartiallyMocked>() {
         @Mock int doSomethingElse() { return 8; }
      };

      int r = new TestedClassPartiallyMocked().doSomething("testing");
      assertEquals(15, r);
   }
}
