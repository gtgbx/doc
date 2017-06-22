/*
 * Copyright (c) 2006-2013 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.expectations.invocation;

import java.lang.reflect.*;
import java.util.concurrent.*;

import org.jetbrains.annotations.*;

import mockit.Invocation;
import mockit.internal.state.*;

final class DelegateInvocation extends Invocation implements Callable<Method>
{
   @NotNull private final InvocationArguments invocationArguments;
   boolean proceedIntoConstructor;

   DelegateInvocation(
      @Nullable Object invokedInstance, @NotNull Object[] invokedArguments,
      @NotNull ExpectedInvocation expectedInvocation, @NotNull InvocationConstraints constraints)
   {
      super(
         invokedInstance, invokedArguments,
         constraints.invocationCount, constraints.minInvocations, constraints.maxInvocations);
      invocationArguments = expectedInvocation.arguments;
   }

   /**
    * Returns the {@code Method} object corresponding to the mocked method, or {@code null} if it's a mocked
    * constructor.
    */
   @Nullable public Method call()
   {
      if (invocationArguments.isForConstructor()) {
         proceedIntoConstructor = true;
         return null;
      }

      TestRun.getExecutingTest().markAsProceedingIntoRealImplementation();
      return invocationArguments.getRealMethod().method;
   }
}
