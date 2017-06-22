/*
 * Copyright (c) 2006-2013 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.coverage.lines;

import java.io.*;
import java.util.*;

import org.jetbrains.annotations.*;

import mockit.coverage.*;

public class LineSegmentData implements Serializable
{
   private static final long serialVersionUID = -6233980722802474992L;
   private static final int MAX_CALL_POINTS = 10;

   // Static data:
   boolean unreachable;

   // Runtime data:
   int executionCount;
   @Nullable private List<CallPoint> callPoints;

   public final void markAsUnreachable() { unreachable = true; }

   boolean acceptsAdditionalCallPoints()
   {
      return callPoints == null || callPoints.size() < MAX_CALL_POINTS;
   }

   final void registerExecution(@NotNull CallPoint callPoint)
   {
      addCallPoint(callPoint);
      executionCount++;
   }

   private void addCallPoint(@NotNull CallPoint callPoint)
   {
      if (callPoints == null) {
         callPoints = new ArrayList<CallPoint>(MAX_CALL_POINTS);
      }

      callPoints.add(callPoint);
   }

   final void addCallPointIfAny(@Nullable CallPoint callPoint)
   {
      if (callPoint != null) {
         addCallPoint(callPoint);
      }
   }

   public final boolean containsCallPoints() { return callPoints != null; }
   @Nullable public final List<CallPoint> getCallPoints() { return callPoints; }

   public int getExecutionCount() { return executionCount; }
   public boolean isCovered() { return unreachable || executionCount > 0; }

   final void addExecutionCountAndCallPointsFromPreviousTestRun(@NotNull LineSegmentData previousData)
   {
      executionCount += previousData.executionCount;

      if (previousData.callPoints != null) {
         if (callPoints != null) {
            callPoints.addAll(0, previousData.callPoints);
         }
         else {
            callPoints = previousData.callPoints;
         }
      }
   }

   void reset() { executionCount = 0; }
}