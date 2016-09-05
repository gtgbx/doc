/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.coverage.data;

import java.io.*;
import java.security.*;
import java.util.*;

import org.jetbrains.annotations.*;

import mockit.coverage.*;

/**
 * Coverage data captured for all source files exercised during a test run.
 */
public final class CoverageData implements Serializable
{
   private static final long serialVersionUID = -4860004226098360259L;
   @NotNull private static final CoverageData instance = new CoverageData();

   @NotNull public static CoverageData instance() { return instance; }

   private boolean withCallPoints;

   @NotNull private final Map<String, FileCoverageData> fileToFileData = new LinkedHashMap<String, FileCoverageData>();
   @NotNull private final List<FileCoverageData> indexedFileData = new ArrayList<FileCoverageData>(100);

   public boolean isWithCallPoints() { return withCallPoints; }
   public void setWithCallPoints(boolean withCallPoints) { this.withCallPoints = withCallPoints; }

   /**
    * Returns an immutable map containing all source files with the corresponding coverage data gathered for each
    * file during a test run.
    */
   @NotNull public Map<String, FileCoverageData> getFileToFileDataMap()
   {
      return Collections.unmodifiableMap(new LinkedHashMap<String, FileCoverageData>(fileToFileData));
   }

   @NotNull public FileCoverageData getOrAddFile(@NotNull String file, @Nullable String kindOfTopLevelType)
   {
      FileCoverageData fileData = fileToFileData.get(file);

      // For a class with nested/inner classes, a previous class in the same source file may already have been added.
      if (fileData == null) {
         int fileIndex = indexedFileData.size();
         fileData = new FileCoverageData(fileIndex, kindOfTopLevelType);
         indexedFileData.add(fileData);
         fileToFileData.put(file, fileData);
      }
      else if (kindOfTopLevelType != null) {
         fileData.kindOfTopLevelType = kindOfTopLevelType;
      }

      return fileData;
   }

   @NotNull public FileCoverageData getFileData(@NotNull String file) { return fileToFileData.get(file); }
   @NotNull public FileCoverageData getFileData(int fileIndex) { return indexedFileData.get(fileIndex); }

   public boolean isEmpty() { return fileToFileData.isEmpty(); }
   public void clear() { fileToFileData.clear(); }

   /**
    * Computes the coverage percentage for a given metric, over a subset of the available source files.
    *
    * @param fileNamePrefix a regular expression for matching the names of the source files to be considered, or
    *                      {@code null} to consider <em>all</em> files
    *
    * @return the computed percentage from {@literal 0} to {@literal 100} (inclusive), or {@literal -1} if no
    * meaningful value could be computed for the metric
    */
   public int getPercentage(@NotNull Metrics metric, @Nullable String fileNamePrefix)
   {
      int coveredItems = 0;
      int totalItems = 0;

      for (Map.Entry<String, FileCoverageData> fileAndFileData : fileToFileData.entrySet()) {
         String sourceFile = fileAndFileData.getKey();

         if (fileNamePrefix == null || sourceFile.startsWith(fileNamePrefix)) {
            FileCoverageData fileData = fileAndFileData.getValue();
            PerFileCoverage coverageInfo = fileData.getPerFileCoverage(metric);
            coveredItems += coverageInfo.getCoveredItems();
            totalItems += coverageInfo.getTotalItems();
         }
      }

      return CoveragePercentage.calculate(coveredItems, totalItems);
   }

   /**
    * Finds the source file with the smallest coverage percentage for a given metric.
    *
    * @return the percentage value for the file found, or {@code Integer.MAX_VALUE} if no file is found with a
    * meaningful coverage percentage
    */
   public int getSmallestPerFilePercentage(@NotNull Metrics metric)
   {
      int minPercentage = Integer.MAX_VALUE;

      for (FileCoverageData fileData : fileToFileData.values()) {
         PerFileCoverage coverageInfo = fileData.getPerFileCoverage(metric);
         int percentage = coverageInfo.getCoveragePercentage();
         if (percentage >= 0 && percentage < minPercentage) minPercentage = percentage;
      }

      return minPercentage;
   }

   public void reset()
   {
      for (FileCoverageData fileCoverageData : fileToFileData.values()) {
         fileCoverageData.reset();
      }
   }

   public void fillLastModifiedTimesForAllClassFiles()
   {
      for (Iterator<Map.Entry<String, FileCoverageData>> itr = fileToFileData.entrySet().iterator(); itr.hasNext(); ) {
         Map.Entry<String, FileCoverageData> fileAndFileData = itr.next();
         File coveredClassFile = getClassFile(fileAndFileData.getKey());

         if (coveredClassFile != null) {
            fileAndFileData.getValue().lastModified = coveredClassFile.lastModified();
         }
         else {
            itr.remove();
         }
      }
   }

   @Nullable private File getClassFile(@NotNull String sourceFilePath)
   {
      String sourceFilePathNoExt = sourceFilePath.substring(0, sourceFilePath.lastIndexOf('.'));
      String className = sourceFilePathNoExt.replace('/', '.');

      Class<?> coveredClass = findCoveredClass(className);

      if (coveredClass == null) {
         return null;
      }

      CodeSource codeSource = coveredClass.getProtectionDomain().getCodeSource();
      String pathToClassFile = codeSource.getLocation().getPath() + sourceFilePathNoExt + ".class";

      return new File(pathToClassFile);
   }

   @Nullable private Class<?> findCoveredClass(@NotNull String className)
   {
      ClassLoader currentCL = getClass().getClassLoader();
      Class<?> coveredClass = loadClass(className, currentCL);

      if (coveredClass == null) {
         ClassLoader systemCL = ClassLoader.getSystemClassLoader();

         if (systemCL != currentCL) {
            coveredClass = loadClass(className, systemCL);
         }

         if (coveredClass == null) {
            ClassLoader contextCL = Thread.currentThread().getContextClassLoader();

            if (contextCL != null && contextCL != systemCL) {
               coveredClass = loadClass(className, contextCL);
            }
         }
      }

      return coveredClass;
   }

   @Nullable private Class<?> loadClass(@NotNull String className, @Nullable ClassLoader loader)
   {
      try {
         return Class.forName(className, false, loader);
      }
      catch (ClassNotFoundException ignore) { return null; }
      catch (NoClassDefFoundError ignored) { return null; }
   }

   /**
    * Reads a serialized {@code CoverageData} object from the given file (normally, a "<code>coverage.ser</code>" file
    * generated at the end of a previous test run).
    *
    * @param dataFile the ".ser" file containing a serialized {@code CoverageData} instance
    *
    * @return a new object containing all coverage data resulting from a previous test run
    */
   @NotNull public static CoverageData readDataFromFile(@NotNull File dataFile) throws IOException
   {
      ObjectInputStream input = new ObjectInputStream(new BufferedInputStream(new FileInputStream(dataFile)));

      try {
         return (CoverageData) input.readObject();
      }
      catch (ClassNotFoundException e) {
         throw new RuntimeException(
            "Serialized class in coverage data file \"" + dataFile + "\" not found in classpath", e);
      }
      finally {
         input.close();
      }
   }

   public void writeDataToFile(@NotNull File dataFile) throws IOException
   {
      ObjectOutputStream output = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(dataFile)));

      try {
         output.writeObject(this);
      }
      finally {
         output.close();
      }
   }

   public void merge(@NotNull CoverageData previousData)
   {
      withCallPoints |= previousData.withCallPoints;

      for (Map.Entry<String, FileCoverageData> previousFileAndFileData : previousData.fileToFileData.entrySet()) {
         String previousFile = previousFileAndFileData.getKey();
         FileCoverageData previousFileData = previousFileAndFileData.getValue();
         FileCoverageData fileData = fileToFileData.get(previousFile);

         if (fileData == null) {
            fileToFileData.put(previousFile, previousFileData);
         }
         else if (previousFileData.lastModified == fileData.lastModified) {
            fileData.mergeWithDataFromPreviousTestRun(previousFileData);
         }
      }
   }
}
