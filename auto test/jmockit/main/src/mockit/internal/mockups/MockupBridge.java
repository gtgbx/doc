/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.mockups;

import java.lang.reflect.*;

import org.jetbrains.annotations.*;

import mockit.internal.*;
import mockit.internal.state.*;

public final class MockupBridge extends MockingBridge
{
   @NotNull public static final MockingBridge MB = new MockupBridge();

   private MockupBridge() { super(MockupBridge.class); }

   @Override @Nullable
   public Object invoke(@Nullable Object mocked, Method method, @NotNull Object[] args) throws Throwable
   {
      boolean enteringMethod = (Boolean) args[0];
      String mockClassDesc = (String) args[1];

      if (notToBeMocked(mocked, mockClassDesc)) {
         return enteringMethod ? false : Void.class;
      }

      if (enteringMethod) {
         int mockStateIndex = (Integer) args[2];
         return TestRun.updateMockState(mockClassDesc, mockStateIndex);
      }

      return null;
   }
}
