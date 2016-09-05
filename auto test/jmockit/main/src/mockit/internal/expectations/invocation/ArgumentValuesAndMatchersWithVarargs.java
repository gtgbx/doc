/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.expectations.invocation;

import java.lang.reflect.*;
import java.util.*;

import org.jetbrains.annotations.*;

import mockit.internal.*;
import mockit.internal.expectations.argumentMatching.*;

final class ArgumentValuesAndMatchersWithVarargs extends ArgumentValuesAndMatchers
{
   ArgumentValuesAndMatchersWithVarargs(@NotNull InvocationArguments signature, @NotNull Object[] values)
   {
      super(signature, values);
   }

   @Override
   boolean isMatch(@NotNull Object[] replayArgs, @NotNull Map<Object, Object> instanceMap)
   {
      if (matchers == null) {
         return areEqual(replayArgs, instanceMap);
      }

      VarargsComparison varargsComparison = new VarargsComparison(replayArgs);
      int n = varargsComparison.getTotalArgumentCountWhenDifferent();

      if (n < 0) {
         return false;
      }

      for (int i = 0; i < n; i++) {
         Object actual = varargsComparison.getOtherArgument(i);
         ArgumentMatcher expected = getArgumentMatcher(i);

         if (expected == null) {
            Object arg = varargsComparison.getThisArgument(i);
            if (arg == null) continue;
            expected = new EqualityMatcher(arg);
         }

         if (!expected.matches(actual)) {
            return false;
         }
      }

      return true;
   }

   private boolean areEqual(@NotNull Object[] replayArgs, @NotNull Map<Object, Object> instanceMap)
   {
      int argCount = replayArgs.length;

      if (!areEqual(values, replayArgs, argCount - 1, instanceMap)) {
         return false;
      }

      VarargsComparison varargsComparison = new VarargsComparison(replayArgs);
      Object[] expectedValues = varargsComparison.getThisVarArgs();
      Object[] actualValues = varargsComparison.getOtherVarArgs();

      return
         varargsComparison.sameVarargArrayLength() &&
         areEqual(expectedValues, actualValues, expectedValues.length, instanceMap);
   }

   @Override
   @Nullable
   Error assertMatch(
      @NotNull Object[] replayArgs, @NotNull Map<Object, Object> instanceMap, @Nullable CharSequence errorMessagePrefix)
   {
      if (matchers == null) {
         return assertEquality(replayArgs, instanceMap, errorMessagePrefix);
      }

      VarargsComparison varargsComparison = new VarargsComparison(replayArgs);
      int n = varargsComparison.getTotalArgumentCountWhenDifferent();

      if (n < 0) {
         return varargsComparison.errorForVarargsArraysOfDifferentLengths();
      }

      for (int i = 0; i < n; i++) {
         Object actual = varargsComparison.getOtherArgument(i);
         ArgumentMatcher expected = getArgumentMatcher(i);

         if (expected == null) {
            Object arg = varargsComparison.getThisArgument(i);
            if (arg == null) continue;
            expected = new EqualityMatcher(arg);
         }

         if (!expected.matches(actual)) {
            int paramIndex = i < replayArgs.length ? i : replayArgs.length - 1;
            return signature.argumentMismatchMessage(paramIndex, expected, actual, errorMessagePrefix);
         }
      }

      return null;
   }

   @Nullable
   private Error assertEquality(
      @NotNull Object[] replayArgs, @NotNull Map<Object, Object> instanceMap, @Nullable CharSequence errorMessagePrefix)
   {
      int argCount = replayArgs.length;
      Error nonVarargsError = assertEquals(values, replayArgs, argCount - 1, instanceMap, errorMessagePrefix);

      if (nonVarargsError != null) {
         return nonVarargsError;
      }

      VarargsComparison varargsComparison = new VarargsComparison(replayArgs);
      Object[] expectedValues = varargsComparison.getThisVarArgs();
      Object[] actualValues = varargsComparison.getOtherVarArgs();

      if (!varargsComparison.sameVarargArrayLength()) {
         return varargsComparison.errorForVarargsArraysOfDifferentLengths();
      }

      Error varargsError =
         assertEquals(expectedValues, actualValues, expectedValues.length, instanceMap, errorMessagePrefix);

      if (varargsError != null) {
         return new UnexpectedInvocation("Varargs " + varargsError);
      }

      return null;
   }

   @Override
   boolean hasEquivalentMatchers(@NotNull ArgumentValuesAndMatchers other)
   {
      int i = indexOfFirstValueAfterEquivalentMatchers(other);

      if (i < 0) {
         return false;
      }

      VarargsComparison varargsComparison = new VarargsComparison(other.values);
      int n = varargsComparison.getTotalArgumentCountWhenDifferent();

      if (n < 0) {
         return false;
      }

      while (i < n) {
         Object thisArg = varargsComparison.getThisArgument(i);
         Object otherArg = varargsComparison.getOtherArgument(i);

         if (!EqualityMatcher.areEqual(thisArg, otherArg)) {
            return false;
         }

         i++;
      }

      return true;
   }
   
   private static final Object[] NULL_VARARGS = new Object[0];

   private final class VarargsComparison
   {
      @NotNull private final Object[] otherValues;
      @Nullable private final Object[] thisVarArgs;
      @Nullable private final Object[] otherVarArgs;
      private final int regularArgCount;

      VarargsComparison(@NotNull Object[] otherValues)
      {
         this.otherValues = otherValues;
         thisVarArgs = getVarArgs(values);
         otherVarArgs = getVarArgs(otherValues);
         regularArgCount = values.length - 1;
      }

      @NotNull Object[] getThisVarArgs()  { return thisVarArgs  == null ? NULL_VARARGS : thisVarArgs; }
      @NotNull Object[] getOtherVarArgs() { return otherVarArgs == null ? NULL_VARARGS : otherVarArgs; }

      @Nullable private Object[] getVarArgs(@NotNull Object[] args)
      {
         Object lastArg = args[args.length - 1];

         if (lastArg == null) {
            return null;
         }
         else if (lastArg instanceof Object[]) {
            return (Object[]) lastArg;
         }

         int varArgsLength = Array.getLength(lastArg);
         Object[] results = new Object[varArgsLength];

         for (int i = 0; i < varArgsLength; i++) {
            results[i] = Array.get(lastArg, i);
         }

         return results;
      }

      int getTotalArgumentCountWhenDifferent()
      {
         if (thisVarArgs == null) {
            return regularArgCount + 1;
         }

         if (!sameVarargArrayLength()) {
            return -1;
         }

         return regularArgCount + thisVarArgs.length;
      }

      boolean sameVarargArrayLength() { return getThisVarArgs().length == getOtherVarArgs().length; }

      @Nullable Object getThisArgument(int parameter)
      {
         if (parameter < regularArgCount) return values[parameter];
         int p = parameter - regularArgCount;
         if (thisVarArgs == null || p >= thisVarArgs.length) return null;
         return thisVarArgs[p];
      }

      @Nullable Object getOtherArgument(int parameter)
      {
         if (parameter < regularArgCount) return otherValues[parameter];
         int p = parameter - regularArgCount;
         if (otherVarArgs == null || p >= otherVarArgs.length) return null;
         return otherVarArgs[p];
      }

      @NotNull Error errorForVarargsArraysOfDifferentLengths()
      {
         int n = getThisVarArgs().length;
         int m = getOtherVarArgs().length;
         return new UnexpectedInvocation("Expected " + n + " values for varargs parameter, got " + m);
      }
   }
}
