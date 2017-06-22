/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.coverage.modification;

import java.net.*;
import java.security.*;
import java.util.regex.*;

import org.jetbrains.annotations.*;

import mockit.coverage.*;
import mockit.coverage.standalone.*;

final class ClassSelection
{
   private static final String THIS_CLASS_NAME = ClassSelection.class.getName();

   final boolean loadedOnly;
   @Nullable private final Matcher classesToInclude;
   @Nullable private final Matcher classesToExclude;
   @Nullable private final Matcher testCode;

   ClassSelection()
   {
      String classes = Configuration.getProperty("classes", "");
      loadedOnly = "loaded".equals(classes);
      classesToInclude = loadedOnly ? null : newMatcherForClassSelection(classes);

      String excludes = Configuration.getProperty("excludes", "");
      classesToExclude = newMatcherForClassSelection(excludes);

      testCode = Startup.isTestRun() ? Pattern.compile(".+Test(\\$.+)?").matcher("") : null;
   }

   @Nullable private Matcher newMatcherForClassSelection(@NotNull String specification)
   {
      if (specification.length() == 0) {
         return null;
      }

      String[] specs = specification.split(",");
      String finalRegex = "";
      String sep = "";

      for (String spec : specs) {
         String regex = null;

         if (spec.indexOf('\\') >= 0) {
            regex = spec;
         }
         else if (spec.length() > 0) {
            regex = spec.replace(".", "\\.").replace("*", ".*").replace('?', '.');
         }

         if (regex != null) {
            finalRegex += sep + regex;
            sep = "|";
         }
      }

      return finalRegex.length() == 0 ? null : Pattern.compile(finalRegex).matcher("");
   }

   boolean isSelected(@NotNull String className, @NotNull ProtectionDomain protectionDomain)
   {
      CodeSource codeSource = protectionDomain.getCodeSource();

      if (
         codeSource == null || className.charAt(0) == '[' || className.startsWith("mockit.") ||
         className.startsWith("org.junit.") || className.startsWith("junit.") || className.startsWith("org.testng.")
      ) {
         return false;
      }

      if (!canAccessJMockitFromClassToBeMeasured(protectionDomain.getClassLoader())) {
         return false;
      }

      if (classesToExclude != null && classesToExclude.reset(className).matches()) {
         return false;
      }
      else if (testCode != null && testCode.reset(className).matches()) {
         return false;
      }
      else if (classesToInclude != null) {
         return classesToInclude.reset(className).matches();
      }

      URL codeSourceLocation = codeSource.getLocation();

      return codeSourceLocation != null && !isClassFromExternalLibrary(codeSourceLocation.getPath());
   }

   private boolean canAccessJMockitFromClassToBeMeasured(@NotNull ClassLoader loaderOfClassToBeMeasured)
   {
      try {
         Class<?> thisClass = loaderOfClassToBeMeasured.loadClass(THIS_CLASS_NAME);
         return thisClass == getClass();
      }
      catch (ClassNotFoundException ignore) {
         return false;
      }
   }

   private boolean isClassFromExternalLibrary(String location)
   {
      return
         location.endsWith(".jar") || location.endsWith("/.cp/") ||
         testCode != null && (location.endsWith("/test-classes/") || location.endsWith("/jmockit/main/classes/"));
   }
}
