/*
 * Copyright (c) 2006-2013 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.expectations.mocking;

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;

import org.jetbrains.annotations.*;

import mockit.internal.state.*;

public final class ParameterTypeRedefinitions extends TypeRedefinitions
{
   @NotNull private final Type[] paramTypes;
   @NotNull private final Annotation[][] paramAnnotations;
   @NotNull private final Object[] paramValues;
   @NotNull private final MockedType[] mockParameters;
   @NotNull private final List<MockedType> injectableParameters;

   public ParameterTypeRedefinitions(
      @NotNull Object owner, @NotNull Method testMethod, @Nullable Object[] parameterValues)
   {
      super(owner);

      TestRun.enterNoMockingZone();

      try {
         paramTypes = testMethod.getGenericParameterTypes();
         paramAnnotations = testMethod.getParameterAnnotations();
         int n = paramTypes.length;
         paramValues = parameterValues == null || parameterValues.length != n ? new Object[n] : parameterValues;
         mockParameters = new MockedType[n];
         injectableParameters = new ArrayList<MockedType>(n);

         String testClassDesc = mockit.external.asm4.Type.getInternalName(testMethod.getDeclaringClass());
         String testMethodDesc = testMethod.getName() + mockit.external.asm4.Type.getMethodDescriptor(testMethod);

         for (int i = 0; i < n; i++) {
            getMockedTypeFromMockParameterDeclaration(testClassDesc, testMethodDesc, i);
         }

         redefineAndInstantiateMockedTypes();
      }
      finally {
         TestRun.exitNoMockingZone();
      }
   }

   private void getMockedTypeFromMockParameterDeclaration(
      @NotNull String testClassDesc, @NotNull String testMethodDesc, int paramIndex)
   {
      Type paramType = paramTypes[paramIndex];
      Annotation[] annotationsOnParameter = paramAnnotations[paramIndex];

      MockedType mockedType =
         new MockedType(testClassDesc, testMethodDesc, paramIndex, paramType, annotationsOnParameter);
      mockParameters[paramIndex] = mockedType;

      if (mockedType.injectable) {
         injectableParameters.add(mockedType);
         paramValues[paramIndex] = mockedType.providedValue;
      }
   }

   private void redefineAndInstantiateMockedTypes()
   {
      for (int i = 0; i < mockParameters.length; i++) {
         MockedType mockedType = mockParameters[i];

         if (mockedType.isMockableType()) {
            Object mockedInstance = redefineAndInstantiateMockedType(mockedType);
            paramValues[i] = mockedInstance;
            mockedType.providedValue = mockedInstance;
         }
      }
   }

   @NotNull private Object redefineAndInstantiateMockedType(@NotNull MockedType mockedType)
   {
      TypeRedefinition typeRedefinition = new TypeRedefinition(parentObject, mockedType);
      Object mock = typeRedefinition.redefineType().create();
      registerMock(mockedType, mock);

      if (mockedType.withInstancesToCapture()) {
         registerCaptureOfNewInstances(mockedType, mock);
      }

      addTargetClass(mockedType);
      typesRedefined++;

      return mock;
   }

   private void registerCaptureOfNewInstances(@NotNull MockedType mockedType, @NotNull Object originalInstance)
   {
      if (captureOfNewInstances == null) {
         captureOfNewInstances = new CaptureOfNewInstances();
      }

      captureOfNewInstances.registerCaptureOfNewInstances(mockedType, originalInstance);
      captureOfNewInstances.makeSureAllSubtypesAreModified(mockedType.getClassType());
   }

   @NotNull public List<MockedType> getInjectableParameters() { return injectableParameters; }
   @NotNull public Object[] getParameterValues() { return paramValues; }
}
