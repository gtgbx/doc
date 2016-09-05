/*
 * Copyright (c) 2006-2013 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.coverage.reporting.pathCoverage;

import java.io.*;
import java.util.*;

import org.jetbrains.annotations.*;

import mockit.coverage.*;
import mockit.coverage.paths.*;

public final class PathCoverageOutput
{
   @NotNull private final PrintWriter output;
   @NotNull private final PathCoverageFormatter pathFormatter;
   @NotNull private final Iterator<MethodCoverageData> nextMethod;

   // Helper fields:
   @Nullable private MethodCoverageData currentMethod;

   public PathCoverageOutput(@NotNull PrintWriter output, @NotNull Collection<MethodCoverageData> methods)
   {
      this.output = output;
      pathFormatter = new PathCoverageFormatter(output);
      nextMethod = methods.iterator();
      moveToNextMethod();
   }

   private void moveToNextMethod()
   {
      currentMethod = nextMethod.hasNext() ? nextMethod.next() : null;
   }

   public void writePathCoverageInfoIfLineStartsANewMethodOrConstructor(int lineNumber)
   {
      if (currentMethod != null && lineNumber == currentMethod.getFirstLineInBody()) {
         writePathCoverageInformationForMethod(currentMethod);
         moveToNextMethod();
      }
   }

   private void writePathCoverageInformationForMethod(@NotNull MethodCoverageData methodData)
   {
      List<Path> paths = methodData.getPaths();

      if (paths.size() > 1) {
         writeHeaderForAllPaths(methodData);
         pathFormatter.writeInformationForEachPath(paths);
         writeFooterForAllPaths();
      }
   }

   private void writeHeaderForAllPaths(@NotNull MethodCoverageData methodData)
   {
      int coveredPaths = methodData.getCoveredPaths();
      int totalPaths = methodData.getTotalPaths();

      output.println("    <tr>");
      output.write("      <td></td><td class='count'>");
      output.print(methodData.getExecutionCount());
      output.println("</td>");
      output.println("      <td class='paths'>");
      output.write("        <span style='cursor:default; background-color:#");
      output.write(CoveragePercentage.percentageColor(coveredPaths, totalPaths));
      output.write("' onclick='hidePath()'>Path coverage: ");
      output.print(coveredPaths);
      output.print('/');
      output.print(totalPaths);
      output.println("</span>");
   }

   private void writeFooterForAllPaths()
   {
      output.println("      </td>");
      output.println("    </tr>");
   }
}
