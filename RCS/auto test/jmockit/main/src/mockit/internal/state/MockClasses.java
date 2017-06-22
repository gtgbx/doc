/*
 * Copyright (c) 2006-2013 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.state;

import org.jetbrains.annotations.*;

import mockit.*;
import mockit.internal.mockups.*;

public final class MockClasses
{
   @NotNull final MockInstances regularMocks;
   @NotNull final MockInstances startupMocks;
   @NotNull private final MockStates mockStates;

   MockClasses()
   {
      regularMocks = new MockInstances();
      startupMocks = new MockInstances();
      mockStates = new MockStates();
   }

   @NotNull public MockInstances getRegularMocks() { return regularMocks; }
   @NotNull public MockInstances getMocks(boolean forStartup) { return forStartup ? startupMocks : regularMocks; }
   @NotNull public MockStates getMockStates() { return mockStates; }

   @Nullable public MockUp<?> findMock(Class<?> mockClass)
   {
      MockUp<?> mock = regularMocks.findMock(mockClass);
      if (mock == null) mock = startupMocks.findMock(mockClass);
      return mock;
   }

   public void removeMock(@NotNull MockUp<?> mock)
   {
      regularMocks.removeInstance(mock);
      startupMocks.removeInstance(mock);
   }
}
