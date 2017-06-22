/*
 * Copyright (c) 2006-2013 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit;

import org.junit.*;
import static org.junit.Assert.*;

public final class ReusableNestedExpectationsTest
{
   static class Collaborator { int doSomething(String s) { return s.length(); } }
   @Mocked Collaborator mock;

   static class BaseExpectations extends Expectations {}

   static class Nested1Expectations extends BaseExpectations
   {
      Nested1Expectations(Collaborator c)
      {
         c.doSomething(anyString);
         result = 5;
      }
   }

   @Test
   public void usingNestedExpectationsThatGetLoadedLateByJVM()
   {
      new Nested1Expectations(mock) {};

      assertEquals(5, mock.doSomething("test"));
   }

   static class Nested2Expectations extends Expectations
   {
      Nested2Expectations(Collaborator c)
      {
         c.doSomething(anyString);
         result = 5;
      }

      void doNothing() {}
   }

   @Test
   public void usingNestedExpectationsThatGetLoadedEarlyByJVM()
   {
      Nested2Expectations expectations = new Nested2Expectations(mock) {};

      // Somehow, calling a method here causes the base class as well as the anonymous class
      // to be loaded early, before JMockit gets initialized, and therefore they never go through
      // the ExpectationsTransformer.
      expectations.doNothing();

      assertEquals(5, mock.doSomething("test"));
   }
}