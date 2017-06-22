/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.expectations;

import java.util.*;

import org.jetbrains.annotations.*;

import mockit.internal.expectations.invocation.*;
import mockit.internal.state.*;

final class ReplayPhase extends Phase
{
   // Fields for the handling of strict invocations:
   private int initialStrictExpectationIndexForCurrentBlock;
   int currentStrictExpectationIndex;
   @Nullable private Expectation strictExpectation;

   // Fields for the handling of non-strict invocations:
   @NotNull final List<Expectation> nonStrictInvocations;
   @NotNull final List<Object[]> nonStrictInvocationArguments;

   ReplayPhase(@NotNull RecordAndReplayExecution recordAndReplay)
   {
      super(recordAndReplay);
      nonStrictInvocations = new ArrayList<Expectation>();
      nonStrictInvocationArguments = new ArrayList<Object[]>();
      initialStrictExpectationIndexForCurrentBlock =
         Math.max(recordAndReplay.lastExpectationIndexInPreviousReplayPhase, 0);
      positionOnFirstStrictExpectation();
   }

   private void positionOnFirstStrictExpectation()
   {
      List<Expectation> expectations = getExpectations();

      if (expectations.isEmpty()) {
         currentStrictExpectationIndex = -1;
         strictExpectation = null ;
      }
      else {
         currentStrictExpectationIndex = initialStrictExpectationIndexForCurrentBlock;
         strictExpectation =
            currentStrictExpectationIndex < expectations.size() ?
               expectations.get(currentStrictExpectationIndex) : null;
      }
   }

   @NotNull private List<Expectation> getExpectations() { return recordAndReplay.executionState.expectations; }

   @Override
   @Nullable
   Object handleInvocation(
      @Nullable Object mock, int mockAccess, @NotNull String mockClassDesc, @NotNull String mockDesc,
      @Nullable String genericSignature, @Nullable String exceptions, boolean withRealImpl, @NotNull Object[] args)
      throws Throwable
   {
      Expectation nonStrictExpectation =
         recordAndReplay.executionState.findNonStrictExpectation(mock, mockClassDesc, mockDesc, args);
      Object replacementInstance =
         recordAndReplay.executionState.getReplacementInstanceForMethodInvocation(mock, mockDesc);

      if (nonStrictExpectation == null) {
         nonStrictExpectation = createExpectationIfNonStrictInvocation(
            replacementInstance == null ? mock : replacementInstance,
            mockAccess, mockClassDesc, mockDesc, genericSignature, exceptions, args);
      }

      if (nonStrictExpectation != null) {
         nonStrictInvocations.add(nonStrictExpectation);
         nonStrictInvocationArguments.add(args);

         if (withRealImpl && replacementInstance != null) {
            return updateConstraintsAndProduceResult(nonStrictExpectation, replacementInstance, args);
         }

         return updateConstraintsAndProduceResult(nonStrictExpectation, mock, withRealImpl, args);
      }

      return handleStrictInvocation(mock, mockClassDesc, mockDesc, withRealImpl, args);
   }

   @Nullable
   private Expectation createExpectationIfNonStrictInvocation(
      @Nullable Object mock, int mockAccess, @NotNull String mockClassDesc, @NotNull String mockNameAndDesc,
      @Nullable String genericSignature, @Nullable String exceptions, @NotNull Object[] args)
   {
      Expectation expectation = null;

      if (!TestRun.getExecutingTest().isStrictInvocation(mock, mockClassDesc, mockNameAndDesc)) {
         ExpectedInvocation invocation =
            new ExpectedInvocation(
               mock, mockAccess, mockClassDesc, mockNameAndDesc, false, genericSignature, exceptions, args);
         expectation = new Expectation(null, invocation, true);
         recordAndReplay.executionState.addExpectation(expectation, true);
      }

      return expectation;
   }

   @Nullable
   private Object updateConstraintsAndProduceResult(
      @NotNull Expectation expectation, @NotNull Object replacementInstance, @NotNull Object[] args)
      throws Throwable
   {
      expectation.constraints.incrementInvocationCount();

      if (expectation.recordPhase == null) {
         expectation.executedRealImplementation = true;
      }
      else if (expectation.constraints.isInvocationCountMoreThanMaximumExpected()) {
         recordAndReplay.setErrorThrown(expectation.invocation.errorForUnexpectedInvocation(args));
         return null;
      }

      return expectation.executeRealImplementation(replacementInstance, args);
   }

   @Nullable
   private Object updateConstraintsAndProduceResult(
      @NotNull Expectation expectation, @Nullable Object mock, boolean withRealImpl, @NotNull Object[] args)
      throws Throwable
   {
      boolean executeRealImpl = withRealImpl && expectation.recordPhase == null;
      expectation.constraints.incrementInvocationCount();

      if (executeRealImpl) {
         expectation.executedRealImplementation = true;
         return Void.class;
      }

      if (expectation.constraints.isInvocationCountMoreThanMaximumExpected()) {
         recordAndReplay.setErrorThrown(expectation.invocation.errorForUnexpectedInvocation(args));
         return null;
      }

      return expectation.produceResult(mock, args);
   }

   @SuppressWarnings("OverlyComplexMethod")
   @Nullable
   private Object handleStrictInvocation(
      @Nullable Object mock, @NotNull String mockClassDesc, @NotNull String mockNameAndDesc,
      boolean withRealImpl, @NotNull Object[] replayArgs)
      throws Throwable
   {
      Map<Object, Object> instanceMap = getInstanceMap();

      while (true) {
         if (strictExpectation == null) {
            return handleUnexpectedInvocation(mock, mockClassDesc, mockNameAndDesc, withRealImpl, replayArgs);
         }

         ExpectedInvocation invocation = strictExpectation.invocation;

         if (invocation.isMatch(mock, mockClassDesc, mockNameAndDesc, instanceMap)) {
            if (mock != invocation.instance) {
               instanceMap.put(invocation.instance, mock);
            }

            Error error = invocation.assertThatArgumentsMatch(replayArgs, instanceMap);

            if (error != null) {
               if (strictExpectation.constraints.isInvocationCountInExpectedRange()) {
                  moveToNextExpectation();
                  continue;
               }

               if (withRealImpl) {
                  return Void.class;
               }

               recordAndReplay.setErrorThrown(error);
               return null;
            }

            Expectation expectation = strictExpectation;

            if (expectation.constraints.incrementInvocationCount()) {
               moveToNextExpectation();
            }
            else if (expectation.constraints.isInvocationCountMoreThanMaximumExpected()) {
               recordAndReplay.setErrorThrown(invocation.errorForUnexpectedInvocation(replayArgs));
               return null;
            }

            return expectation.produceResult(mock, replayArgs);
         }
         else if (strictExpectation.constraints.isInvocationCountInExpectedRange()) {
            moveToNextExpectation();
         }
         else if (withRealImpl) {
            return Void.class;
         }
         else {
            recordAndReplay.setErrorThrown(
               invocation.errorForUnexpectedInvocation(mock, mockClassDesc, mockNameAndDesc, replayArgs));
            return null;
         }
      }
   }

   @Nullable
   private Object handleUnexpectedInvocation(
      @Nullable Object mock, @NotNull String mockClassDesc, @NotNull String mockNameAndDesc, boolean withRealImpl,
      @NotNull Object[] replayArgs)
   {
      if (withRealImpl) {
         return Void.class;
      }

      recordAndReplay.setErrorThrown(
         new ExpectedInvocation(mock, mockClassDesc, mockNameAndDesc, replayArgs).errorForUnexpectedInvocation());

      return null;
   }

   private void moveToNextExpectation()
   {
      List<Expectation> expectations = getExpectations();

      assert strictExpectation != null;
      RecordPhase expectationBlock = strictExpectation.recordPhase;
      assert expectationBlock != null;

      currentStrictExpectationIndex++;

      strictExpectation =
         currentStrictExpectationIndex < expectations.size() ? expectations.get(currentStrictExpectationIndex) : null;

      if (expectationBlock.numberOfIterations <= 1) {
         if (strictExpectation != null && strictExpectation.recordPhase != expectationBlock) {
            initialStrictExpectationIndexForCurrentBlock = currentStrictExpectationIndex;
         }
      }
      else if (strictExpectation == null || strictExpectation.recordPhase != expectationBlock) {
         expectationBlock.numberOfIterations--;
         positionOnFirstStrictExpectation();
         resetInvocationCountsForStrictExpectations(expectationBlock);
      }
   }

   private void resetInvocationCountsForStrictExpectations(@NotNull RecordPhase expectationBlock)
   {
      for (Expectation expectation : getExpectations()) {
         if (expectation.recordPhase == expectationBlock) {
            expectation.constraints.invocationCount = 0;
         }
      }
   }

   @Nullable Error endExecution()
   {
      Expectation strict = strictExpectation;
      strictExpectation = null;

      if (strict != null && strict.constraints.isInvocationCountLessThanMinimumExpected()) {
         return strict.invocation.errorForMissingInvocation();
      }

      List<Expectation> nonStrictExpectations = recordAndReplay.executionState.nonStrictExpectations;

      // New expectations might get added to the list, so a regular loop would cause a CME.
      for (int i = 0, n = nonStrictExpectations.size(); i < n; i++) {
         Expectation nonStrict = nonStrictExpectations.get(i);
         InvocationConstraints constraints = nonStrict.constraints;

         if (constraints.isInvocationCountLessThanMinimumExpected()) {
            return constraints.errorForMissingExpectations(nonStrict.invocation);
         }
      }

      int nextStrictExpectationIndex = currentStrictExpectationIndex + 1;

      if (nextStrictExpectationIndex < getExpectations().size()) {
         Expectation nextStrictExpectation = getExpectations().get(nextStrictExpectationIndex);

         if (nextStrictExpectation.constraints.isInvocationCountLessThanMinimumExpected()) {
            return nextStrictExpectation.invocation.errorForMissingInvocation();
         }
      }

      return null;
   }
}
