/*
 * Copyright (c) 2006-2013 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit;

import org.junit.*;
import org.junit.rules.*;

import static org.junit.Assert.*;

import mockit.internal.*;

public final class FullVerificationsTest
{
   @Rule public final ExpectedException thrown = ExpectedException.none();

   @SuppressWarnings("UnusedParameters")
   public static class Dependency
   {
      public void setSomething(int value) {}
      public void setSomethingElse(char value) {}
      public boolean editABunchMoreStuff() { return false; }
      public void notifyBeforeSave() {}
      public void prepare() {}
      public void save() {}
   }

   @Mocked Dependency mock;

   void exerciseCodeUnderTest()
   {
      mock.prepare();
      mock.setSomething(123);
      mock.setSomethingElse('a');
      mock.setSomething(45);
      mock.editABunchMoreStuff();
      mock.notifyBeforeSave();
      mock.save();
   }

   @Test
   public void verifyAllInvocations()
   {
      exerciseCodeUnderTest();

      new FullVerifications() {{
         mock.prepare(); minTimes = 1;
         mock.editABunchMoreStuff();
         mock.notifyBeforeSave(); maxTimes = 1;
         mock.setSomething(anyInt); minTimes = 0; maxTimes = 2;
         mock.setSomethingElse(anyChar);
         mock.save(); times = 1;
      }};
   }

   @Test
   public void verifyAllInvocationsWithSomeOfThemRecorded()
   {
      new NonStrictExpectations() {{
         mock.editABunchMoreStuff(); result = true;
         mock.setSomething(45);
      }};

      exerciseCodeUnderTest();

      new FullVerifications() {{
         mock.prepare();
         mock.setSomething(anyInt);
         mock.setSomethingElse(anyChar);
         mock.editABunchMoreStuff();
         mock.notifyBeforeSave();
         mock.save();
      }};
   }

   @Test
   public void verifyAllInvocationsWithThoseRecordedAsExpectedToOccurVerifiedImplicitly()
   {
      new NonStrictExpectations() {{
         mock.setSomething(45); times = 1;
         mock.editABunchMoreStuff(); result = true; minTimes = 1;
      }};

      exerciseCodeUnderTest();

      new FullVerifications() {{
         mock.prepare();
         mock.setSomething(123);
         mock.setSomethingElse(anyChar);
         mock.notifyBeforeSave();
         mock.save();
      }};
   }

   @Test
   public void verifyAllInvocationsExceptThoseAlreadyVerifiedInAPreviousVerificationBlock()
   {
      exerciseCodeUnderTest();

      new Verifications() {{
         mock.setSomething(45);
         mock.editABunchMoreStuff();
      }};

      new FullVerifications() {{
         mock.prepare();
         mock.setSomething(123);
         mock.setSomethingElse(anyChar);
         mock.notifyBeforeSave();
         mock.save();
      }};
   }

   @Test
   public void verifyAllInvocationsWithOneMissing()
   {
      thrown.expect(UnexpectedInvocation.class);
      thrown.expectMessage("editABunchMoreStuff()");

      exerciseCodeUnderTest();

      new FullVerifications() {{
         mock.prepare();
         mock.notifyBeforeSave();
         mock.setSomething(anyChar);
         mock.setSomethingElse(anyChar);
         mock.save();
      }};
   }

   @Test
   public void verifyUnrecordedInvocationThatWasExpectedToNotHappen()
   {
      mock.prepare();
      mock.setSomething(123);
      mock.setSomething(45);

      new FullVerifications() {{
         mock.prepare();
         mock.setSomething(anyInt); times = 2;
         mock.notifyBeforeSave(); times = 0;
      }};
   }

   @Test
   public void verifyUnrecordedInvocationThatShouldNotHappenButDoes()
   {
      thrown.expect(UnexpectedInvocation.class);
      thrown.expectMessage("1 unexpected invocation");

      mock.setSomething(1);
      mock.notifyBeforeSave();

      new FullVerifications() {{
         mock.setSomething(1);
         mock.notifyBeforeSave(); times = 0;
      }};
   }

   @Test
   public void verifyInvocationThatIsAllowedToHappenAnyNumberOfTimesAndHappensOnce()
   {
      mock.prepare();
      mock.setSomething(123);
      mock.save();

      new FullVerifications() {{
         mock.prepare();
         mock.setSomething(anyInt);
         mock.save(); minTimes = 0;
      }};
   }

   @Test
   public void verifyRecordedInvocationThatIsAllowedToHappenAnyNoOfTimesAndDoesNotHappen()
   {
      new NonStrictExpectations() {{ mock.save(); }};

      mock.prepare();
      mock.setSomething(123);

      new FullVerifications() {{
         mock.prepare();
         mock.setSomething(anyInt);
         mock.save(); minTimes = 0;
      }};
   }

   @Test
   public void verifyUnrecordedInvocationThatIsAllowedToHappenAnyNoOfTimesAndDoesNotHappen()
   {
      mock.prepare();
      mock.setSomething(123);

      new FullVerifications() {{
         mock.prepare();
         mock.setSomething(anyInt);
         mock.save(); minTimes = 0;
      }};
   }

   @Test
   public void verifyUnrecordedInvocationThatShouldHappenButDoesNot()
   {
      thrown.expect(MissingInvocation.class);

      mock.setSomething(1);

      new FullVerifications() {{ mock.notifyBeforeSave(); }};
   }

   @Test
   public void verifyRecordedInvocationThatShouldHappenButDoesNot()
   {
      thrown.expect(MissingInvocation.class);

      new NonStrictExpectations() {{ mock.notifyBeforeSave(); }};

      mock.setSomething(1);

      new FullVerifications() {{ mock.notifyBeforeSave(); }};
   }

   @Test
   public void verifyAllInvocationsWithExpectationRecordedButOneInvocationUnverified()
   {
      thrown.expect(UnexpectedInvocation.class);
      thrown.expectMessage("with arguments: 123");

      new NonStrictExpectations() {{
         mock.setSomething(anyInt);
      }};

      mock.setSomething(123);
      mock.editABunchMoreStuff();
      mock.setSomething(45);

      new FullVerifications() {{
         mock.setSomething(withNotEqual(123));
         mock.editABunchMoreStuff();
      }};
   }

   @Test
   public void verifyTwoInvocationsWithIteratingBlockHavingExpectationRecordedAndSecondInvocationUnverified()
   {
      thrown.expect(MissingInvocation.class);
      thrown.expectMessage("Missing 1 invocation");
      thrown.expectMessage("with arguments: 123");

      new NonStrictExpectations() {{
         mock.setSomething(anyInt);
      }};

      mock.setSomething(123);
      mock.setSomething(45);

      new FullVerifications(2) {{ mock.setSomething(123); }};
   }

   @Test
   public void verifyAllInvocationsWithExtraVerification()
   {
      thrown.expect(MissingInvocation.class);
      thrown.expectMessage("notifyBeforeSave()");

      mock.prepare();
      mock.setSomething(123);

      new FullVerifications() {{
         mock.prepare();
         mock.setSomething(123);
         mock.notifyBeforeSave();
      }};
   }

   @Test
   public void verifyAllInvocationsWithInvocationCountOneLessThanActual()
   {
      thrown.expect(UnexpectedInvocation.class);
      thrown.expectMessage("with arguments: 45");

      mock.setSomething(123);
      mock.setSomething(45);

      new FullVerifications() {{
         mock.setSomething(anyInt); times = 1;
      }};
   }

   @Test
   public void verifyAllInvocationsWithInvocationCountTwoLessThanActual()
   {
      thrown.expect(UnexpectedInvocation.class);
      thrown.expectMessage("2 unexpected invocations");
      thrown.expectMessage("with arguments: 1");

      mock.setSomething(123);
      mock.setSomething(45);
      mock.setSomething(1);

      new FullVerifications() {{
         mock.setSomething(anyInt); times = 1;
      }};
   }

   @Test
   public void verifyAllInvocationsWithInvocationCountMoreThanActual()
   {
      thrown.expect(MissingInvocation.class);
      thrown.expectMessage("Missing 2 invocations");
      thrown.expectMessage("with arguments: any char");

      mock.setSomethingElse('f');

      new FullVerifications() {{
         mock.setSomethingElse(anyChar);
         times = 3;
      }};
   }

   @Test
   public void verifyAllInvocationsInIteratingBlock()
   {
      mock.setSomething(123);
      mock.save();
      mock.setSomething(45);
      mock.save();

      new FullVerifications(2) {{
         mock.setSomething(anyInt);
         mock.save();
      }};
   }

   @Test
   public void verifySingleInvocationInBlockWithLargerNumberOfIterations()
   {
      thrown.expect(MissingInvocation.class);
      thrown.expectMessage("Missing 2 invocations");
      thrown.expectMessage("with arguments: 123");

      mock.setSomething(123);

      new FullVerifications(3) {{
         mock.setSomething(123);
      }};
   }

   @Test
   public void verifyMultipleInvocationsInBlockWithSmallerNumberOfIterations()
   {
      thrown.expect(UnexpectedInvocation.class);
      thrown.expectMessage("with arguments: -14");

      mock.setSomething(123);
      mock.setSomething(-14);

      new FullVerifications(1) {{
         mock.setSomething(anyInt);
      }};
   }

   @Test
   public void verifyWithArgumentMatcherAndIndividualInvocationCountsInIteratingBlock()
   {
      for (int i = 0; i < 2; i++) {
         exerciseCodeUnderTest();
      }

      new FullVerifications(2) {{
         mock.prepare(); maxTimes = 1;
         mock.setSomething(anyInt); minTimes = 2;
         mock.setSomethingElse('a');
         mock.editABunchMoreStuff(); minTimes = 0; maxTimes = 5;
         mock.notifyBeforeSave();
         mock.save(); times = 1;
      }};
   }

   @Test
   public void verifyNoInvocationsOccurredOnMockedDependencyWithOneHavingOccurred()
   {
      thrown.expect(UnexpectedInvocation.class);

      mock.editABunchMoreStuff();

      new FullVerifications() {};
   }

   @Test
   public void verifyNoInvocationsOnMockedDependencyBeyondThoseRecordedAsExpected()
   {
      new NonStrictExpectations() {{
         mock.prepare(); times = 1;
      }};

      new NonStrictExpectations() {{
         mock.setSomething(anyInt); minTimes = 1;
         mock.save(); times = 1;
      }};

      mock.prepare();
      mock.setSomething(1);
      mock.setSomething(2);
      mock.save();

      new FullVerifications() {};
   }

   @Test
   public void verifyNoInvocationsOnMockedDependencyBeyondThoseRecordedAsExpectedWithOneHavingOccurred()
   {
      thrown.expect(UnexpectedInvocation.class);
      thrown.expectMessage("editABunchMoreStuff()");

      new NonStrictExpectations() {{
         mock.prepare(); times = 1;
         mock.save(); minTimes = 1;
      }};

      mock.prepare();
      mock.editABunchMoreStuff();
      mock.save();

      new FullVerifications() {};
   }

   @Test
   public void replayThenDiscardAllUnverifiedInvocationsThenReplaySomeMore()
   {
      // First replay:
      mock.prepare();
      mock.setSomething(1);
      mock.save();

      // Now discard everything without any verification:
      new FullVerifications() {{ unverifiedInvocations(); }};

      // Replay some more:
      mock.setSomething(2);
      mock.save();

      // Finally, verify the invocations in the second replay, with exact
      // invocation counts relative to the second replay:
      new FullVerifications() {{
         mock.save(); times = 1;
         mock.setSomething(anyInt); times = 1;
      }};
   }

   @Test
   public void replayThenDiscardSomeOfTheUnverifiedInvocationsThenReplaySomeMore()
   {
      // First replay:
      mock.prepare();
      mock.setSomething(1);
      mock.save();

      // Now verify some invocations while discarding the remaining ones:
      new FullVerifications() {{
         mock.prepare();
         unverifiedInvocations();
      }};

      // Replay some more:
      mock.editABunchMoreStuff();
      mock.save();

      // Finally, verify the invocations in the second replay, with exact
      // invocation counts relative to the second replay:
      try {
         new FullVerifications() {{
            mock.prepare(); times = 0;
            mock.save(); maxTimes = 1;
         }};
         fail();
      }
      catch (UnexpectedInvocation e) {
         assertTrue(e.getMessage().contains("editABunchMoreStuff()"));
      }
   }

   @Test
   public void replayThenDiscardAllUnverifiedInvocationsThenReplaySomeMoreForSpecificInstance(
      @Mocked final Dependency mock2)
   {
      // First replay:
      mock2.prepare();
      mock.setSomething(1);
      mock2.setSomething(2);
      mock.save();

      // Now discard all invocations on one of two mocked instances:
      new FullVerifications(mock) {{ unverifiedInvocations(); }};

      // Replay some more:
      mock.setSomething(3);
      mock2.setSomething(4);

      // Finally, verify the invocations in the second replay, with exact
      // invocation counts relative to the second replay:
      new FullVerifications() {{
         mock.setSomething(3); times = 1;
         mock2.prepare();
         mock2.setSomething(anyInt); times = 2;
      }};
   }
}
