/*
 * Copyright (c) 2006-2013 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.expectations;

import java.util.*;

import org.jetbrains.annotations.*;

abstract class Phase
{
   @NotNull final RecordAndReplayExecution recordAndReplay;

   Phase(@NotNull RecordAndReplayExecution recordAndReplay) { this.recordAndReplay = recordAndReplay; }

   @NotNull final Map<Object, Object> getInstanceMap() { return recordAndReplay.executionState.instanceMap; }

   @Nullable
   abstract Object handleInvocation(
      @Nullable Object mock, int mockAccess, @NotNull String mockClassDesc, @NotNull String mockNameAndDesc,
      @Nullable String genericSignature, @Nullable String exceptions, boolean withRealImpl, @NotNull Object[] args)
      throws Throwable;
}
