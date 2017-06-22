/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.coverage.reporting.sourceFiles;

import java.io.*;
import java.util.*;

import org.jetbrains.annotations.*;

public final class InputFile
{
   @NotNull final String filePath;
   @NotNull private final File sourceFile;
   @NotNull private final BufferedReader input;

   @Nullable
   public static InputFile createIfFileExists(@NotNull List<File> sourceDirs, @NotNull String filePath)
      throws FileNotFoundException
   {
      File sourceFile = findSourceFile(sourceDirs, filePath);
      return sourceFile == null ? null : new InputFile(filePath, sourceFile);
   }

   @Nullable
   private static File findSourceFile(@NotNull List<File> sourceDirs, @NotNull String filePath)
   {
      int p = filePath.indexOf('/');
      String topLevelPackage = p < 0 ? "" : filePath.substring(0, p);
      int n = sourceDirs.size();

      for (int i = 0; i < n; i++) {
         File sourceDir = sourceDirs.get(i);
         File sourceFile = getSourceFile(sourceDir, topLevelPackage, filePath);

         if (sourceFile != null) {
            giveCurrentSourceDirHighestPriority(sourceDirs, i);
            addRootSourceDirIfNew(sourceDirs, filePath, sourceFile);
            return sourceFile;
         }
      }

      return null;
   }

   @Nullable
   private static File getSourceFile(
      @NotNull File sourceDir, @NotNull final String topLevelPackage, @NotNull final String filePath)
   {
      File file = new File(sourceDir, filePath);

      if (file.exists()) {
         return file;
      }

      File[] subDirs = sourceDir.listFiles(new FileFilter() {
         @Override
         public boolean accept(File subDir)
         {
            return subDir.isDirectory() && !subDir.isHidden() && !subDir.getName().equals(topLevelPackage);
         }
      });

      if (subDirs != null && subDirs.length > 0) {
         for (File subDir : subDirs) {
            File sourceFile = getSourceFile(subDir, topLevelPackage, filePath);

            if (sourceFile != null) {
               return sourceFile;
            }
         }
      }

      return null;
   }

   private static void giveCurrentSourceDirHighestPriority(@NotNull List<File> sourceDirs, int currentSourceDirIndex)
   {
      if (currentSourceDirIndex > 0) {
         File firstSourceDir = sourceDirs.get(0);
         File currentSourceDir = sourceDirs.get(currentSourceDirIndex);

         if (!firstSourceDir.getPath().startsWith(currentSourceDir.getPath())) {
            sourceDirs.set(currentSourceDirIndex, firstSourceDir);
            sourceDirs.set(0, currentSourceDir);
         }
      }
   }

   private static void addRootSourceDirIfNew(
      @NotNull List<File> sourceDirs, @NotNull String filePath, @NotNull File sourceFile)
   {
      String sourceFilePath = sourceFile.getPath();
      String sourceRootDir = sourceFilePath.substring(0, sourceFilePath.length() - filePath.length());
      File newSourceDir = new File(sourceRootDir);

      if (!sourceDirs.contains(newSourceDir)) {
         sourceDirs.add(0, newSourceDir);
      }
   }

   private InputFile(@NotNull String filePath, @NotNull File sourceFile) throws FileNotFoundException
   {
      this.filePath = filePath;
      this.sourceFile = sourceFile;
      input = new BufferedReader(new FileReader(sourceFile));
   }

   @NotNull String getSourceFileName() { return sourceFile.getName(); }

   @NotNull String getSourceFilePath()
   {
      String path = sourceFile.getPath();
      return path.startsWith("..") ? path.substring(3) : path;
   }

   @Nullable String nextLine() throws IOException { return input.readLine(); }
   void close() throws IOException { input.close(); }
}
