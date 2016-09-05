/*
 * Copyright (c) 2006-2012 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.integration.junit3;

import java.util.*;

import junit.framework.*;

import mockit.integration.*;

// These tests are expected to fail, so they are kept inactive to avoid busting the full test run.
public final class JUnit3ViolatedExpectationsTests //extends TestCase
{
   // Tests that fail with a "missing invocation" error ///////////////////////////////////////////////////////////////

   public void testExpectInvocationWhichDoesNotOccurInTestedCodeThatThrowsAnException_mockUp1()
   {
      new CollaboratorMockUp();

      try { new Collaborator().doSomething(); } catch (IllegalFormatCodePointException ignore) {}
   }

   public void testExpectInvocationWhichDoesNotOccurInTestedCodeThatThrowsAnException_strict1(Collaborator mock)
   {
      new CollaboratorStrictExpectations(mock);

      try { mock.doSomething(); } catch (IllegalFormatCodePointException ignore) {}
   }

   public void testExpectInvocationWhichDoesNotOccurInTestedCodeThatThrowsAnException_nonStrict1(Collaborator mock)
   {
      new CollaboratorNonStrictExpectations(mock);

      try { mock.doSomething(); } catch (IllegalFormatCodePointException ignore) {}
   }

   // Tests that fail with the exception thrown by tested code ////////////////////////////////////////////////////////

   public void testExpectInvocationWhichDoesNotOccurInTestedCodeThatThrowsAnException_mockUp2()
   {
      new CollaboratorMockUp();

      new Collaborator().doSomething();
   }

   public void testExpectInvocationWhichDoesNotOccurInTestedCodeThatThrowsAnException_strict2(Collaborator mock)
   {
      new CollaboratorStrictExpectations(mock);

      mock.doSomething();
   }

   public void testExpectInvocationWhichDoesNotOccurInTestedCodeThatThrowsAnException_nonStrict2(Collaborator mock)
   {
      new CollaboratorNonStrictExpectations(mock);

      mock.doSomething();
   }

   // Tests that fail with an "unexpected invocation" error ///////////////////////////////////////////////////////////

   public void testExpectInvocationWhichDoesNotOccurInTestedCodeThatThrowsAnException_mockUp3()
   {
      new CollaboratorMockUp();

      new Collaborator();
      new Collaborator();
   }

   public void testExpectInvocationWhichDoesNotOccurInTestedCodeThatThrowsAnException_strict3(Collaborator mock)
   {
      new CollaboratorStrictExpectations(mock);

      new Collaborator();
   }

   public void testExpectInvocationWhichDoesNotOccurInTestedCodeThatThrowsAnException_nonStrict3(Collaborator mock)
   {
      new CollaboratorNonStrictExpectations(mock);

      new Collaborator();
      new Collaborator();
   }
}
