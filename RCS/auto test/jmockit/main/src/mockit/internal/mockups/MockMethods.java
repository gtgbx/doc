/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.mockups;

import java.lang.reflect.*;
import java.util.*;

import org.jetbrains.annotations.*;

import mockit.internal.state.*;
import mockit.internal.util.*;
import mockit.internal.util.GenericTypeReflection.*;

/**
 * A container for the mock methods "collected" from a mock class, separated in two sets: one with all the mock methods,
 * and another with just the subset of static methods.
 */
final class MockMethods
{
   @NotNull final Class<?> realClass;
   private final boolean mockedTypeIsAClass;
   @NotNull private final List<MockMethod> methods;
   @NotNull private final GenericTypeReflection typeParametersToTypeArguments;
   @NotNull private String mockClassInternalName;
   @Nullable private List<MockState> mockStates;

   final class MockMethod
   {
      @NotNull final String name;
      @NotNull final String desc;
      final boolean isStatic;
      final boolean hasInvocationParameter;
      @NotNull String mockedMethodDesc;
      @NotNull private final String mockDescWithoutInvocationParameter;
      @Nullable private GenericSignature mockSignature;
      private int indexForMockState;
      private boolean nativeRealMethod;

      private MockMethod(@NotNull String nameAndDesc, boolean isStatic)
      {
         int p = nameAndDesc.indexOf('(');
         name = nameAndDesc.substring(0, p);
         desc = nameAndDesc.substring(p);
         this.isStatic = isStatic;
         hasInvocationParameter = desc.startsWith("(Lmockit/Invocation;");
         mockedMethodDesc = "";
         mockDescWithoutInvocationParameter = hasInvocationParameter ? '(' + desc.substring(20) : desc;
         indexForMockState = -1;
      }

      boolean isMatch(@NotNull String methodName, @NotNull String methodDesc, @Nullable String signature)
      {
         if (this.name.equals(methodName)) {
            if (hasMatchingParameters(methodDesc, signature)) {
               mockedMethodDesc = methodDesc;
               return true;
            }
         }

         return false;
      }

      private boolean hasMatchingParameters(@NotNull String methodDesc, @Nullable String signature)
      {
         boolean sameParametersIgnoringGenerics = mockDescWithoutInvocationParameter.equals(methodDesc);

         if (sameParametersIgnoringGenerics || signature == null) {
            return sameParametersIgnoringGenerics;
         }

         if (mockSignature == null) {
            mockSignature = typeParametersToTypeArguments.parseSignature(mockDescWithoutInvocationParameter);
         }

         return mockSignature.satisfiesGenericSignature(signature);
      }

      @NotNull Class<?> getRealClass() { return realClass; }
      @NotNull String getMockNameAndDesc() { return name + desc; }
      int getIndexForMockState() { return indexForMockState; }

      boolean isForGenericMethod() { return mockSignature != null; }
      boolean isForConstructor() { return "$init".equals(name); }

      boolean isForNativeMethod() { return nativeRealMethod; }
      void markAsNativeRealMethod() { nativeRealMethod = true; }

      boolean canBeReentered()
      {
         return hasInvocationParameter && mockedTypeIsAClass && !nativeRealMethod && !isForConstructor();
      }

      boolean isReentrant()
      {
         return indexForMockState >= 0 && mockStates != null && mockStates.get(indexForMockState).isReentrant();
      }

      boolean isDynamic() { return isReentrant() || hasInvocationParameter && isForConstructor(); }

      boolean hasMatchingRealMethod() { return !mockedMethodDesc.isEmpty(); }

      @NotNull String errorMessage(@NotNull String quantifier, int numExpectedInvocations, int timesInvoked)
      {
         String nameAndDesc = getMockNameAndDesc();
         return
            "Expected " + quantifier + ' ' + numExpectedInvocations + " invocation(s) of " +
            new MethodFormatter(mockClassInternalName, nameAndDesc) + ", but was invoked " + timesInvoked + " time(s)";
      }

      @Override
      public boolean equals(Object obj)
      {
         MockMethod other = (MockMethod) obj;
         return realClass == other.getRealClass() && name.equals(other.name) && desc.equals(other.desc);
      }

      @Override
      public int hashCode()
      {
         return 31 * (31 * realClass.hashCode() + name.hashCode()) + desc.hashCode();
      }
   }

   MockMethods(@NotNull Class<?> realClass, @Nullable Type mockedType)
   {
      this.realClass = realClass;

      if (mockedType == null || realClass == mockedType) {
         mockedTypeIsAClass = true;
      }
      else {
         Class<?> mockedClass = Utilities.getClassType(mockedType);
         mockedTypeIsAClass = !mockedClass.isInterface();
      }

      methods = new ArrayList<MockMethod>();
      typeParametersToTypeArguments = new GenericTypeReflection(realClass, mockedType);
      mockClassInternalName = "";
   }

   @NotNull Class<?> getRealClass() { return realClass; }

   @Nullable MockMethod addMethod(boolean fromSuperClass, @NotNull String name, @NotNull String desc, boolean isStatic)
   {
      if (fromSuperClass && isMethodAlreadyAdded(name, desc)) {
         return null;
      }

      String nameAndDesc = name + desc;
      MockMethod mockMethod = new MockMethod(nameAndDesc, isStatic);
      methods.add(mockMethod);
      return mockMethod;
   }

   private boolean isMethodAlreadyAdded(@NotNull String name, @NotNull String desc)
   {
      int p = desc.lastIndexOf(')');
      String params = desc.substring(0, p + 1);

      for (MockMethod mockMethod : methods) {
         if (mockMethod.name.equals(name) && mockMethod.desc.startsWith(params)) {
            return true;
         }
      }

      return false;
   }

   void addMockState(@NotNull MockState mockState)
   {
      if (mockStates == null) {
         mockStates = new ArrayList<MockState>(4);
      }

      mockState.mockMethod.indexForMockState = mockStates.size();
      mockStates.add(mockState);
   }

   /**
    * Verifies if a mock method with the same signature of a given real method was previously collected from the mock
    * class.
    * This operation can be performed only once for any given mock method in this container, so that after the last real
    * method is processed there should be no mock methods left unused in the container.
    */
   @Nullable MockMethod containsMethod(@NotNull String name, @NotNull String desc, @Nullable String signature)
   {
      for (MockMethod mockMethod : methods) {
         if (mockMethod.isMatch(name, desc, signature)) {
            return mockMethod;
         }
      }

      return null;
   }

   @NotNull String getMockClassInternalName() { return mockClassInternalName; }

   void setMockClassInternalName(@NotNull String mockClassInternalName)
   {
      this.mockClassInternalName = mockClassInternalName;
   }

   boolean hasUnusedMocks()
   {
      for (MockMethod method : methods) {
         if (!method.hasMatchingRealMethod()) {
            return true;
         }
      }

      return false;
   }

   @NotNull List<String> getUnusedMockSignatures()
   {
      List<String> signatures = new ArrayList<String>(methods.size());

      for (MockMethod mockMethod : methods) {
         if (!"$clinit()V".equals(mockMethod.getMockNameAndDesc()) && !mockMethod.hasMatchingRealMethod()) {
            signatures.add(mockMethod.getMockNameAndDesc());
         }
      }

      return signatures;
   }

   void registerMockStates(boolean forStartupMock)
   {
      if (mockStates != null) {
         MockStates globalMockStates = TestRun.getMockStates();

         if (!forStartupMock) {
            globalMockStates.addMockStates(this.mockStates);
         }

         globalMockStates.addMockClassAndItsStates(mockClassInternalName, this.mockStates);
      }
   }
}
