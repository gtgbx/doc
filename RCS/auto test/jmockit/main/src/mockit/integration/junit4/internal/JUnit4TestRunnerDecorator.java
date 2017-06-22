/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.integration.junit4.internal;

import java.lang.reflect.*;

import org.jetbrains.annotations.*;

import org.junit.*;
import org.junit.runners.model.*;

import mockit.integration.internal.*;
import mockit.internal.expectations.*;
import mockit.internal.mockups.*;
import mockit.internal.state.*;
import mockit.internal.util.*;

final class JUnit4TestRunnerDecorator extends TestRunnerDecorator
{
   /**
    * A "volatile boolean" is as good as a java.util.concurrent.atomic.AtomicBoolean here,
    * since we only need the basic get/set operations.
    */
   private volatile boolean shouldPrepareForNextTest = true;

   @Nullable
   Object invokeExplosively(@NotNull MockInvocation invocation, @Nullable Object target, Object... params)
      throws Throwable
   {
      FrameworkMethod it = invocation.getInvokedInstance();
      Method method = it.getMethod();
      Class<?> testClass = target == null ? method.getDeclaringClass() : target.getClass();

      handleMockingOutsideTestMethods(it, target, testClass);

      // In case it isn't a test method, but a before/after method:
      if (it.getAnnotation(Test.class) == null) {
         if (shouldPrepareForNextTest && it.getAnnotation(Before.class) != null) {
            prepareForNextTest();
            shouldPrepareForNextTest = false;
         }

         TestRun.setRunningIndividualTest(target);

         try {
            invocation.prepareToProceed();
            return it.invokeExplosively(target, params);
         }
         catch (Throwable t) {
            RecordAndReplayExecution.endCurrentReplayIfAny();
            StackTrace.filterStackTrace(t);
            throw t;
         }
         finally {
            if (it.getAnnotation(After.class) != null) {
               shouldPrepareForNextTest = true;
            }
         }
      }

      if (shouldPrepareForNextTest) {
         prepareForNextTest();
      }

      shouldPrepareForNextTest = true;
      assert target != null;

      try {
         executeTestMethod(invocation, target, params);
         return null; // it's a test method, therefore has void return type
      }
      catch (Throwable t) {
         StackTrace.filterStackTrace(t);
         throw t;
      }
      finally {
         TestRun.finishCurrentTestExecution(true);
      }
   }

   private void handleMockingOutsideTestMethods(
      @NotNull FrameworkMethod it, @Nullable Object target, @NotNull Class<?> testClass)
   {
      TestRun.enterNoMockingZone();

      try {
         if (target == null) {
            Class<?> currentTestClass = TestRun.getCurrentTestClass();

            if (
               currentTestClass != null && testClass.isAssignableFrom(currentTestClass) &&
               it.getAnnotation(AfterClass.class) != null
            ) {
               cleanUpMocksFromPreviousTest();
            }

            if (it.getAnnotation(BeforeClass.class) != null) {
               updateTestClassState(null, testClass);
            }
         }
         else {
            updateTestClassState(target, testClass);
         }
      }
      finally {
         TestRun.exitNoMockingZone();
      }
   }

   private void executeTestMethod(@NotNull MockInvocation invocation, @NotNull Object target, Object... parameters)
      throws Throwable
   {
      SavePoint savePoint = new SavePoint();

      TestRun.setRunningIndividualTest(target);

      FrameworkMethod it = invocation.getInvokedInstance();
      Method testMethod = it.getMethod();
      Throwable testFailure = null;
      boolean testFailureExpected = false;

      try {
         Object[] mockParameters = createInstancesForMockParameters(target, testMethod, parameters);
         createInstancesForTestedFields(target);

         invocation.prepareToProceed();

         Object[] params = mockParameters == null ? parameters : mockParameters;
         it.invokeExplosively(target, params);
      }
      catch (Throwable thrownByTest) {
         testFailure = thrownByTest;
         Class<? extends Throwable> expectedType = testMethod.getAnnotation(Test.class).expected();
         testFailureExpected = expectedType.isAssignableFrom(thrownByTest.getClass());
      }
      finally {
         concludeTestMethodExecution(savePoint, testFailure, testFailureExpected);
      }
   }
}
