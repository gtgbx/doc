/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.mockups;

import java.lang.reflect.*;
import java.util.concurrent.*;

import org.jetbrains.annotations.*;

import mockit.*;

/**
 * An invocation to a {@code @Mock} method.
 */
public final class MockInvocation extends Invocation implements Runnable, Callable<Method>
{
   @NotNull private final MockState mockState;
   private boolean proceedIntoConstructor;

   public MockInvocation(
      @Nullable Object invokedInstance, @NotNull Object[] invokedArguments, @NotNull MockState mockState)
   {
      super(
         invokedInstance, invokedArguments,
         mockState.getTimesInvoked(), mockState.getMinInvocations(), mockState.getMaxInvocations());
      this.mockState = mockState;
   }

   /**
    * To be called if and when the min/max number of invocations is set by user code.
    */
   @Override
   public void run()
   {
      mockState.minExpectedInvocations = getMinInvocations();
      mockState.maxExpectedInvocations = getMaxInvocations();
   }

   /**
    * Returns the {@code Method} object corresponding to the mocked method, or {@code null} if it's a mocked
    * constructor.
    */
   @Override @Nullable
   public Method call()
   {
      Method methodToExecute = mockState.getMethodToExecute();

      if (methodToExecute != null) {
         return methodToExecute;
      }

      proceedIntoConstructor = true;
      return null;
   }

   public boolean shouldProceedIntoConstructor() { return proceedIntoConstructor; }
   public void prepareToProceed() { mockState.prepareToProceed(); }
}
