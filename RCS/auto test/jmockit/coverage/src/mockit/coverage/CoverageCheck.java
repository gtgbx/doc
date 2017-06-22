/*
 * Copyright (c) 2006-2013 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.coverage;

import java.io.*;
import java.util.*;

import org.jetbrains.annotations.*;

import mockit.coverage.data.*;

final class CoverageCheck
{
   private static final class Threshold
   {
      @Nullable private final String sourceFilePrefix;
      @NotNull private final String scopeDescription;
      @NotNull private final int[] minPercentages;

      Threshold(@NotNull String configurationParameter)
      {
         String[] sourceFilePrefixAndMinPercentages = configurationParameter.split(":");
         String csvPercentages;

         if (sourceFilePrefixAndMinPercentages.length == 1) {
            sourceFilePrefix = null;
            scopeDescription = "";
            csvPercentages = sourceFilePrefixAndMinPercentages[0];
         }
         else {
            String scope = sourceFilePrefixAndMinPercentages[0].trim();

            if ("perFile".equals(scope)) {
               sourceFilePrefix = scope;
               scopeDescription = " for some source files";
            }
            else {
               sourceFilePrefix = scope.replace('.', '/');
               scopeDescription = " for " + scope;
            }

            csvPercentages = sourceFilePrefixAndMinPercentages[1];
         }

         minPercentages = new int[Metrics.values().length];
         parseMinimumPercentages(csvPercentages);
      }

      private void parseMinimumPercentages(@NotNull String csvPercentages)
      {
         String[] textualPercentages = csvPercentages.split(",");
         int n = Math.min(textualPercentages.length, minPercentages.length);

         for (int i = 0; i < n; i++) {
            String textualValue = textualPercentages[i].trim();
            try { minPercentages[i] = Integer.parseInt(textualValue); } catch (NumberFormatException ignore) {}
         }
      }

      boolean verifyMinimum(@NotNull Metrics metric)
      {
         CoverageData coverageData = CoverageData.instance();
         int percentage;

         if ("perFile".equals(sourceFilePrefix)) {
            percentage = coverageData.getSmallestPerFilePercentage(metric);
         }
         else {
            percentage = coverageData.getPercentage(metric, sourceFilePrefix);
         }

         return percentage < 0 || verifyMinimum(metric, percentage);
      }

      private boolean verifyMinimum(@NotNull Metrics metric, int percentage)
      {
         int minPercentage = minPercentages[metric.ordinal()];

         if (percentage < minPercentage) {
            System.out.println(
               "JMockit: " + metric + " coverage too low" + scopeDescription + ": " +
               percentage + "% < " + minPercentage + '%');
            return false;
         }

         return true;
      }
   }

   @Nullable private final List<Threshold> thresholds;
   private boolean allThresholdsSatisfied;

   CoverageCheck()
   {
      String configuration = Configuration.getProperty("check", "");

      if (configuration.length() == 0) {
         thresholds = null;
         return;
      }

      String[] configurationParameters = configuration.split(";");
      int n = configurationParameters.length;
      thresholds = new ArrayList<Threshold>(n);

      for (String configurationParameter : configurationParameters) {
         thresholds.add(new Threshold(configurationParameter));
      }
   }

   void verifyThresholds()
   {
      if (thresholds == null) return;
      allThresholdsSatisfied = true;

      for (final Threshold threshold : thresholds) {
         Metrics.performAction(new Metrics.Action() {
            public void perform(@NotNull Metrics metric)
            {
               allThresholdsSatisfied &= threshold.verifyMinimum(metric);
            }
         });
      }

      createOrDeleteIndicatorFile();

      if (!allThresholdsSatisfied) {
         throw new AssertionError("JMockit: minimum coverage percentages not reached; see previous messages.");
      }
   }

   @SuppressWarnings("ResultOfMethodCallIgnored")
   private void createOrDeleteIndicatorFile()
   {
      File indicatorFile = new File("coverage.check.failed");

      if (indicatorFile.exists()) {
         if (allThresholdsSatisfied) {
            indicatorFile.delete();
         }
         else {
            indicatorFile.setLastModified(System.currentTimeMillis());
         }
      }
      else if (!allThresholdsSatisfied) {
         try { indicatorFile.createNewFile(); } catch (IOException e) { throw new RuntimeException(e); }
      }
   }
}
