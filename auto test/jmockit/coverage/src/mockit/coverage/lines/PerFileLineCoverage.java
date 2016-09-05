/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.coverage.lines;

import java.io.*;
import java.util.*;

import org.jetbrains.annotations.*;

import mockit.coverage.*;
import mockit.coverage.data.*;
import mockit.external.asm4.*;

public final class PerFileLineCoverage implements PerFileCoverage
{
   private static final long serialVersionUID = 6318915843739466316L;
   private static final int[] NO_EXECUTIONS_YET = new int[0];

   @NotNull private final Map<Integer, LineCoverageData> lineToLineData = new HashMap<Integer, LineCoverageData>(128);
   @NotNull private int[] executionCounts = NO_EXECUTIONS_YET;
   @NotNull private transient LineCoverageData sharedLineData;

   // Computed on demand:
   private int lastLine;
   private transient int totalSegments;
   private transient int coveredSegments;

   public PerFileLineCoverage()
   {
      sharedLineData = new LineCoverageData();
      initializeCache();
   }

   private void initializeCache() { totalSegments = coveredSegments = -1; }

   private void readObject(@NotNull ObjectInputStream in) throws IOException, ClassNotFoundException
   {
      sharedLineData = new LineCoverageData();
      initializeCache();
      in.defaultReadObject();

      if (executionCounts.length == 0) {
         executionCounts = NO_EXECUTIONS_YET;
      }
   }

   public void addLine(int line)
   {
      if (!lineToLineData.containsKey(line)) {
         lineToLineData.put(line, null);
      }

      if (line > lastLine) {
         // Happens for source files with multiple types, where one is only loaded
         // after another has already executed some code.
         if (executionCounts != NO_EXECUTIONS_YET && line >= executionCounts.length) {
            int[] newCounts = new int[line + 30];

            synchronized (this) {
               System.arraycopy(executionCounts, 0, newCounts, 0, executionCounts.length);
               executionCounts = newCounts;
            }
         }

         lastLine = line;
      }
   }

   public int addBranch(int line, @NotNull Label jumpSource, @NotNull Label jumpTarget)
   {
      LineCoverageData lineData = getOrCreateLineData(line);
      return lineData.addBranch(jumpSource, jumpTarget);
   }

   @NotNull private LineCoverageData getOrCreateLineData(int line)
   {
      LineCoverageData lineData = lineToLineData.get(line);

      if (lineData == null) {
         lineData = new LineCoverageData();
         lineToLineData.put(line, lineData);
      }

      return lineData;
   }

   @NotNull public BranchCoverageData getBranchData(int line, int index)
   {
      LineCoverageData lineData = lineToLineData.get(line);
      return lineData.getBranchData(index);
   }

   public int getBranchCount(int line)
   {
      LineCoverageData lineData = lineToLineData.get(line);
      return lineData == null ? 0 : lineData.getBranches().size();
   }

   public boolean acceptsAdditionalCallPoints(int line)
   {
      LineCoverageData lineData = getOrCreateLineData(line);
      return lineData.acceptsAdditionalCallPoints();
   }

   public void registerExecution(int line, @Nullable CallPoint callPoint)
   {
      if (executionCounts == NO_EXECUTIONS_YET) {
         executionCounts = new int[lastLine + 1];
      }

      executionCounts[line]++;

      if (callPoint != null) {
         LineCoverageData lineData = getOrCreateLineData(line);
         lineData.registerExecution(callPoint);
      }
   }

   public boolean acceptsAdditionalCallPoints(int line, int segment)
   {
      LineCoverageData lineData = getOrCreateLineData(line);
      return lineData.acceptsAdditionalCallPoints(segment);
   }

   public void registerExecution(int line, int segment, boolean jumped, @Nullable CallPoint callPoint)
   {
      LineCoverageData lineData = getOrCreateLineData(line);
      lineData.registerExecution(segment, jumped, callPoint);
   }

   public int getLineCount() { return lastLine; }
   public int getExecutableLineCount() { return lineToLineData.size(); }

   public boolean hasLineData(int line)
   {
      return executionCounts != NO_EXECUTIONS_YET && lineToLineData.containsKey(line);
   }

   @NotNull public LineCoverageData getLineData(int line)
   {
      LineCoverageData data = lineToLineData.get(line);

      if (data == null) {
         data = sharedLineData;
      }

      data.executionCount = executionCounts[line];
      return data;
   }

   public int getExecutionCount(int line)
   {
      return line < executionCounts.length ? executionCounts[line] : -1;
   }

   public int getTotalItems()
   {
      computeValuesIfNeeded();
      return totalSegments;
   }

   public int getCoveredItems()
   {
      computeValuesIfNeeded();
      return coveredSegments;
   }

   public int getCoveragePercentage()
   {
      computeValuesIfNeeded();
      return CoveragePercentage.calculate(coveredSegments, totalSegments);
   }

   private void computeValuesIfNeeded()
   {
      if (totalSegments >= 0) return;

      totalSegments = coveredSegments = 0;

      for (int line = 1, n = lastLine; line <= n; line++) {
         if (lineToLineData.containsKey(line)) {
            LineCoverageData lineData = lineToLineData.get(line);

            if (lineData == null) {
               totalSegments++;

               if (executionCounts != NO_EXECUTIONS_YET && executionCounts[line] > 0) {
                  coveredSegments++;
               }
            }
            else {
               totalSegments += lineData.getNumberOfSegments();
               coveredSegments += lineData.getNumberOfCoveredSegments();
            }
         }
      }
   }

   public int getNumberOfSegments(int line)
   {
      if (!lineToLineData.containsKey(line)) {
         return 0;
      }

      LineCoverageData lineData = lineToLineData.get(line);
      return lineData == null ? 1 : lineData.getNumberOfSegments();
   }

   public void reset()
   {
      for (int i = 1; i < executionCounts.length; i++) {
         executionCounts[i] = 0;
      }

      for (LineCoverageData lineData : lineToLineData.values()) {
         if (lineData != null) {
            lineData.reset();
         }
      }

      initializeCache();
   }

   public void mergeInformation(@NotNull PerFileLineCoverage previousCoverage)
   {
      Map<Integer, LineCoverageData> previousInfo = previousCoverage.lineToLineData;
      boolean previousRunHadLinesExecuted = previousCoverage.executionCounts.length > 0;

      for (Map.Entry<Integer, LineCoverageData> lineAndInfo : lineToLineData.entrySet()) {
         Integer line = lineAndInfo.getKey();
         LineCoverageData previousLineInfo = previousInfo.get(line);

         if (previousLineInfo != null) {
            LineCoverageData lineInfo = lineAndInfo.getValue();

            if (lineInfo == null) {
               lineInfo = new LineCoverageData();
               lineAndInfo.setValue(lineInfo);
            }

            lineInfo.addCountsFromPreviousTestRun(previousLineInfo);

            if (previousRunHadLinesExecuted) {
               createExecutionCountsArrayIfNeeded(previousCoverage);
               executionCounts[line] += previousCoverage.executionCounts[line];
            }
         }
      }

      for (Map.Entry<Integer, LineCoverageData> lineAndInfo : previousInfo.entrySet()) {
         Integer line = lineAndInfo.getKey();

         if (!lineToLineData.containsKey(line)) {
            LineCoverageData previousLineInfo = lineAndInfo.getValue();
            lineToLineData.put(line, previousLineInfo);

            if (previousRunHadLinesExecuted) {
               createExecutionCountsArrayIfNeeded(previousCoverage);
               executionCounts[line] = previousCoverage.executionCounts[line];
            }
         }
      }
   }

   private void createExecutionCountsArrayIfNeeded(@NotNull PerFileLineCoverage previousCoverage)
   {
      if (executionCounts == NO_EXECUTIONS_YET) {
         executionCounts = new int[previousCoverage.executionCounts.length];
      }
   }

   public Collection<LineCoverageData> getAllLines() { return lineToLineData.values(); }
}
