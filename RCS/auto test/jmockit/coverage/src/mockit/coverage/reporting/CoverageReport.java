/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.coverage.reporting;

import java.io.*;
import java.util.*;
import java.util.Map.*;

import org.jetbrains.annotations.*;

import mockit.coverage.data.*;
import mockit.coverage.reporting.packages.*;
import mockit.coverage.reporting.sourceFiles.*;

class CoverageReport
{
   @NotNull private final String outputDir;
   private boolean outputDirCreated;
   @Nullable private final List<File> sourceDirs;
   @NotNull private final Map<String, FileCoverageData> fileToFileData;
   @NotNull private final Map<String, List<String>> packageToFiles;
   private final boolean withCallPoints;
   @Nullable private final Collection<String> sourceFilesNotFound;

   protected CoverageReport(
      @NotNull String outputDir, boolean outputDirCreated, @Nullable String[] srcDirs,
      @NotNull CoverageData coverageData, boolean withCallPoints)
   {
      this.outputDir = outputDir.isEmpty() ? "coverage-report" : outputDir;
      this.outputDirCreated = outputDirCreated;
      sourceDirs = srcDirs == null ? null : new SourceFiles().buildListOfSourceDirectories(srcDirs);
      fileToFileData = coverageData.getFileToFileDataMap();
      packageToFiles = new HashMap<String, List<String>>();
      this.withCallPoints = withCallPoints;
      sourceFilesNotFound = srcDirs == null ? null : new ArrayList<String>();
   }

   public final void generate() throws IOException
   {
      createReportOutputDirIfNotExists();

      File outputFile = createOutputFileForIndexPage();

      if (outputFile == null) {
         return;
      }

      boolean withSourceFilePages = sourceDirs != null;

      if (withSourceFilePages && sourceDirs.size() > 1) {
         System.out.println("JMockit: Coverage source dirs: " + sourceDirs);
      }

      generateFileCoverageReportsWhileBuildingPackageLists();

      new IndexPage(outputFile, sourceDirs, sourceFilesNotFound, packageToFiles, fileToFileData).generate();
      new StaticFiles(outputDir).copyToOutputDir(withSourceFilePages);

      System.out.println("JMockit: Coverage report written to " + outputFile.getParentFile().getCanonicalPath());
   }

   private void createReportOutputDirIfNotExists()
   {
      if (!outputDirCreated) {
         File outDir = new File(outputDir);
         outputDirCreated = outDir.mkdirs();
      }
   }

   @Nullable private File createOutputFileForIndexPage() throws IOException
   {
      File outputFile = new File(outputDir, "index.html");

      if (outputFile.exists() && !outputFile.canWrite()) {
         System.out.println("JMockit: " + outputFile.getCanonicalPath() + " is read-only; report generation canceled");
         return null;
      }

      return outputFile;
   }

   private void generateFileCoverageReportsWhileBuildingPackageLists() throws IOException
   {
      Set<Entry<String, FileCoverageData>> files = fileToFileData.entrySet();

      for (Entry<String, FileCoverageData> fileAndFileData : files) {
         generateFileCoverageReport(fileAndFileData.getKey(), fileAndFileData.getValue());
      }
   }

   private void generateFileCoverageReport(@NotNull String sourceFile, @NotNull FileCoverageData fileData)
      throws IOException
   {
      if (sourceDirs == null) {
         addFileToPackageFileList(sourceFile);
      }
      else {
         InputFile inputFile = InputFile.createIfFileExists(sourceDirs, sourceFile);

         if (inputFile != null) {
            new FileCoverageReport(outputDir, inputFile, fileData, withCallPoints).generate();
         }
         else {
            deleteOutdatedHTMLFileIfExists(sourceFile);

            if (sourceFilesNotFound != null) {
               sourceFilesNotFound.add(sourceFile);
            }
         }

         addFileToPackageFileList(sourceFile);
      }
   }

   private void addFileToPackageFileList(@NotNull String file)
   {
      int p = file.lastIndexOf('/');
      String filePackage = p < 0 ? "" : file.substring(0, p);
      List<String> filesInPackage = packageToFiles.get(filePackage);

      if (filesInPackage == null) {
         filesInPackage = new ArrayList<String>();
         packageToFiles.put(filePackage, filesInPackage);
      }

      filesInPackage.add(file.substring(p + 1));
   }

   private void deleteOutdatedHTMLFileIfExists(@NotNull String filePath) throws IOException
   {
      if (!outputDirCreated) {
         File outputFile = OutputFile.getOutputFile(outputDir, filePath);
         //noinspection ResultOfMethodCallIgnored
         outputFile.delete();
      }
   }
}
