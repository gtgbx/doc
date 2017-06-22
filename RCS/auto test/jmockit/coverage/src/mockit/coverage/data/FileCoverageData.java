/*
 * Copyright (c) 2006-2013 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.coverage.data;

import java.io.*;
import java.util.*;

import org.jetbrains.annotations.*;

import mockit.coverage.*;
import mockit.coverage.dataItems.*;
import mockit.coverage.lines.*;
import mockit.coverage.paths.*;

/**
 * Coverage data gathered for the lines and branches of a single source file.
 */
public final class FileCoverageData implements Serializable
{
   private static final long serialVersionUID = 3508572808457541012L;

   @NotNull public final PerFileLineCoverage lineCoverageInfo = new PerFileLineCoverage();
   @NotNull public final PerFilePathCoverage pathCoverageInfo = new PerFilePathCoverage();
   @NotNull public final PerFileDataCoverage dataCoverageInfo = new PerFileDataCoverage();

   // Used for fast indexed access.
   public final int index;

   // Used for output styling in the HTML report:
   @Nullable public String kindOfTopLevelType;

   // Used to track the last time the ".class" file was modified, to decide if merging can be done:
   long lastModified;

   public FileCoverageData(int index, @Nullable String kindOfTopLevelType)
   {
      this.index = index;
      this.kindOfTopLevelType = kindOfTopLevelType;
   }

   @NotNull public PerFileLineCoverage getLineCoverageData() { return lineCoverageInfo; }

   public void addMethod(MethodCoverageData methodData) { pathCoverageInfo.addMethod(methodData); }

   @NotNull
   public Collection<MethodCoverageData> getMethods() { return pathCoverageInfo.firstLineToMethodData.values(); }

   public PerFileCoverage getPerFileCoverage(@NotNull Metrics metric)
   {
      switch (metric) {
         case LineCoverage: return lineCoverageInfo;
         case PathCoverage: return pathCoverageInfo;
         default: return dataCoverageInfo;
      }
   }

   void mergeWithDataFromPreviousTestRun(@NotNull FileCoverageData previousInfo)
   {
      lineCoverageInfo.mergeInformation(previousInfo.lineCoverageInfo);
      pathCoverageInfo.mergeInformation(previousInfo.pathCoverageInfo);
      dataCoverageInfo.mergeInformation(previousInfo.dataCoverageInfo);
   }

   void reset()
   {
      lineCoverageInfo.reset();
      pathCoverageInfo.reset();
   }
}
