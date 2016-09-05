/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.mockups;

import java.lang.reflect.*;

import org.jetbrains.annotations.*;

import mockit.internal.*;
import mockit.internal.util.*;
import mockit.internal.mockups.MockMethods.MockMethod;

final class MockState
{
   @NotNull final MockMethod mockMethod;
   @Nullable private RealMethod realMethod;
   @Nullable private Method actualMockMethod;

   // Expectations on the number of invocations of the mock as specified by the @Mock annotation,
   // initialized with the default values as specified in @Mock annotation definition:
   int expectedInvocations = -1;
   int minExpectedInvocations;
   int maxExpectedInvocations = -1;

   // Current mock invocation state:
   private int invocationCount;
   @Nullable private ThreadLocal<Boolean> proceeding;

   // Helper field just for synchronization:
   @NotNull private final Object invocationCountLock = new Object();

   MockState(@NotNull MockMethod mockMethod) { this.mockMethod = mockMethod; }

   @NotNull Class<?> getRealClass() { return mockMethod.getRealClass(); }

   boolean isReentrant() { return proceeding != null; }

   void makeReentrant()
   {
      proceeding = new ThreadLocal<Boolean>() { @Override protected Boolean initialValue() { return false; } };
   }

   boolean isWithExpectations()
   {
      return
         expectedInvocations >= 0 || minExpectedInvocations > 0 || maxExpectedInvocations >= 0 ||
         mockMethod.hasInvocationParameter;
   }

   boolean update()
   {
      if (proceeding != null && proceeding.get()) {
         proceeding.set(Boolean.FALSE);
         return false;
      }

      synchronized (invocationCountLock) {
         invocationCount++;
      }

      return true;
   }

   void verifyExpectations()
   {
      int timesInvoked = getTimesInvoked();

      if (expectedInvocations >= 0 && timesInvoked != expectedInvocations) {
         String message = mockMethod.errorMessage("exactly", expectedInvocations, timesInvoked);
         throw timesInvoked < expectedInvocations ?
            new MissingInvocation(message) : new UnexpectedInvocation(message);
      }
      else if (timesInvoked < minExpectedInvocations) {
         throw new MissingInvocation(mockMethod.errorMessage("at least", minExpectedInvocations, timesInvoked));
      }
      else if (maxExpectedInvocations >= 0 && timesInvoked > maxExpectedInvocations) {
         throw new UnexpectedInvocation(mockMethod.errorMessage("at most", maxExpectedInvocations, timesInvoked));
      }
   }

   int getMinInvocations() { return expectedInvocations >= 0 ? expectedInvocations : minExpectedInvocations; }
   int getMaxInvocations() { return expectedInvocations >= 0 ? expectedInvocations : maxExpectedInvocations; }

   int getTimesInvoked()
   {
      synchronized (invocationCountLock) {
         return invocationCount;
      }
   }

   void reset()
   {
      synchronized (invocationCountLock) {
         invocationCount = 0;
      }
   }

   @Nullable Method getMethodToExecute()
   {
      if (mockMethod.isForConstructor()) {
         return null;
      }

      if (proceeding == null) {
         throw new UnsupportedOperationException("Cannot proceed into abstract/interface method");
      }

      RealMethod realMethodToExecute = getRealMethod();
      proceeding.set(Boolean.TRUE);
      return realMethodToExecute.method;
   }

   void prepareToProceed() { assert proceeding != null; proceeding.set(Boolean.TRUE); }

   @NotNull RealMethod getRealMethod()
   {
      if (realMethod == null) {
         if (mockMethod.isForNativeMethod()) {
            throw new UnsupportedOperationException("Cannot proceed into real implementation of native method");
         }

         realMethod = new RealMethod(getRealClass(), mockMethod.name, mockMethod.mockedMethodDesc);
      }

      return realMethod;
   }

   @NotNull
   Method getMockMethod(@NotNull Class<?> mockClass, @NotNull Class<?>[] paramTypes)
   {
      if (actualMockMethod == null) {
         actualMockMethod = MethodReflection.findCompatibleMethod(mockClass, mockMethod.name, paramTypes);
      }

      return actualMockMethod;
   }

   @Override
   public boolean equals(@NotNull Object other) { return mockMethod.equals(((MockState) other).mockMethod); }

   @Override
   public int hashCode() { return mockMethod.hashCode(); }
}
