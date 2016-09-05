/*
 * Copyright (c) 2006-2013 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.expectations.invocation;

import org.jetbrains.annotations.*;

import mockit.internal.state.*;
import mockit.internal.util.*;

final class RealImplementationResult extends DynamicInvocationResult
{
   RealImplementationResult(@NotNull Object instanceToInvoke, @NotNull String methodToInvoke)
   {
      super(instanceToInvoke, new RealMethod(instanceToInvoke.getClass(), methodToInvoke).method);
   }

   @Override
   @Nullable
   Object produceResult(@NotNull Object[] args)
   {
      TestRun.getExecutingTest().markAsProceedingIntoRealImplementation();
      return executeMethodToInvoke(args);
   }
}
