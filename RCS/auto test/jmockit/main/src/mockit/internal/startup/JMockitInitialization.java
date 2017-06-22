/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.startup;

import java.io.*;

import org.jetbrains.annotations.*;

import mockit.*;
import mockit.integration.junit3.internal.*;
import mockit.integration.junit4.internal.*;
import mockit.integration.robolectric.internal.*;
import mockit.integration.testng.internal.*;
import mockit.internal.*;
import mockit.internal.util.*;

final class JMockitInitialization
{
   @NotNull private final StartupConfiguration config;

   JMockitInitialization() throws IOException { config = new StartupConfiguration(); }

   void initialize()
   {
      MockingBridge.preventEventualClassLoadingConflicts();

      if (MockTestNG.hasDependenciesInClasspath()) {
         new MockTestNG();
      }

      if (MockFrameworkMethod.hasDependenciesInClasspath()) {
         loadInternalStartupMocksForJUnitIntegration();
      }

      try {
         new MockRobolectricSetup();
         new MockJREClass();
      }
      catch (ClassNotFoundException ignore) {}

      loadExternalToolsIfAny();
      setUpStartupMocksIfAny();
   }

   private void loadInternalStartupMocksForJUnitIntegration()
   {
      new TestSuiteDecorator();

      try { new MockTestCase(); }
      catch (VerifyError e) {
         // For some reason, this error occurs when running TestNG tests from Maven.
         e.printStackTrace();
      }

      new RunNotifierDecorator();
      new BlockJUnit4ClassRunnerDecorator();
      new MockFrameworkMethod();
   }

   private void loadExternalToolsIfAny()
   {
      for (String toolClassName : config.externalTools) {
         try {
            new ToolLoader(toolClassName).loadTool();
         }
         catch (Throwable unexpectedFailure) {
            StackTrace.filterStackTrace(unexpectedFailure);
            unexpectedFailure.printStackTrace();
         }
      }
   }

   private void setUpStartupMocksIfAny()
   {
      for (String mockClassName : config.mockClasses) {
         setUpStartupMock(mockClassName);
      }
   }

   private void setUpStartupMock(@NotNull String mockClassName)
   {
      try {
         Class<?> mockClass = ClassLoad.loadClassAtStartup(mockClassName);

         if (MockUp.class.isAssignableFrom(mockClass)) {
            ConstructorReflection.newInstanceUsingDefaultConstructor(mockClass);
         }
      }
      catch (UnsupportedOperationException ignored) {}
      catch (Throwable unexpectedFailure) {
         StackTrace.filterStackTrace(unexpectedFailure);
         unexpectedFailure.printStackTrace();
      }
   }
}
