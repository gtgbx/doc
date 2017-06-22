/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.mockups;

import java.lang.reflect.*;
import java.util.*;

import org.jetbrains.annotations.*;

/**
 * Holds state associated with mock class containing {@linkplain mockit.Mock annotated mocks}.
 */
public final class MockStates
{
   /**
    * For each mock class containing @Mock annotations with at least one invocation expectation specified or at least
    * one reentrant mock, a runtime state will be kept here.
    */
   @NotNull private final Map<String, List<MockState>> mockClassToMockStates;

   /**
    * For each annotated mock method with at least one invocation expectation, its mock state will
    * also be kept here, as an optimization.
    */
   @NotNull private final Set<MockState> mockStatesWithExpectations;

   public MockStates()
   {
      mockClassToMockStates = new HashMap<String, List<MockState>>(8);
      mockStatesWithExpectations = new LinkedHashSet<MockState>(10);
   }

   void addMockStates(@NotNull List<MockState> mockStates)
   {
      for (MockState mockState : mockStates) {
         if (mockState.isWithExpectations()) {
            mockStatesWithExpectations.add(mockState);
         }
      }
   }

   void addMockClassAndItsStates(@NotNull String mockClassInternalName, @NotNull List<MockState> mockStates)
   {
      mockClassToMockStates.put(mockClassInternalName, mockStates);
   }

   public void removeClassState(@NotNull Class<?> redefinedClass, @Nullable String internalNameForOneOrMoreMockClasses)
   {
      removeMockStates(redefinedClass);

      if (internalNameForOneOrMoreMockClasses != null) {
         if (internalNameForOneOrMoreMockClasses.indexOf(' ') < 0) {
            removeMockStates(internalNameForOneOrMoreMockClasses);
         }
         else {
            String[] mockClassesInternalNames = internalNameForOneOrMoreMockClasses.split(" ");

            for (String mockClassInternalName : mockClassesInternalNames) {
               removeMockStates(mockClassInternalName);
            }
         }
      }
   }

   private void removeMockStates(@NotNull Class<?> redefinedClass)
   {
      for (Iterator<List<MockState>> itr = mockClassToMockStates.values().iterator(); itr.hasNext(); ) {
         List<MockState> mockStates = itr.next();
         MockState mockState = mockStates.get(0);

         if (mockState.getRealClass() == redefinedClass) {
            mockStatesWithExpectations.removeAll(mockStates);
            mockStates.clear();
            itr.remove();
         }
      }
   }

   private void removeMockStates(@NotNull String mockClassInternalName)
   {
      List<MockState> mockStates = mockClassToMockStates.remove(mockClassInternalName);

      if (mockStates != null) {
         mockStatesWithExpectations.removeAll(mockStates);
      }
   }

   public boolean updateMockState(@NotNull String mockClassName, int mockStateIndex)
   {
      MockState mockState = getMockState(mockClassName, mockStateIndex);
      return mockState != null && mockState.update();
   }

   @Nullable MockState getMockState(@NotNull String mockClassInternalName, int mockStateIndex)
   {
      List<MockState> mockStates = mockClassToMockStates.get(mockClassInternalName);
      return mockStates.get(mockStateIndex);
   }

   @Nullable
   public Method getMockMethod(
      @NotNull String mockClassDesc, int mockStateIndex, @NotNull Class<?> mockClass, @NotNull Class<?>[] paramTypes)
   {
      MockState mockState = getMockState(mockClassDesc, mockStateIndex);

      if (mockState != null) {
         return mockState.getMockMethod(mockClass, paramTypes);
      }

      return null;
   }

   @Nullable
   public MockInvocation createMockInvocation(
      @NotNull String mockClassInternalName, int mockStateIndex, @Nullable Object invokedInstance,
      @NotNull Object[] invokedArguments)
   {
      MockState mockState = getMockState(mockClassInternalName, mockStateIndex);
      return mockState == null ? null : new MockInvocation(invokedInstance, invokedArguments, mockState);
   }

   public void verifyExpectations()
   {
      for (MockState mockState : mockStatesWithExpectations) {
         mockState.verifyExpectations();
      }
   }

   public void resetExpectations()
   {
      for (MockState mockState : mockStatesWithExpectations) {
         mockState.reset();
      }
   }
}
