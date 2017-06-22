/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.startup;

import java.io.*;
import java.lang.instrument.*;
import java.util.logging.*;

import org.jetbrains.annotations.*;

import mockit.*;
import mockit.internal.expectations.transformation.*;
import mockit.internal.state.*;
import mockit.internal.util.*;

/**
 * This is the "agent class" that initializes the JMockit "Java agent". It is not intended for use in client code.
 *
 * @see #premain(String, Instrumentation)
 * @see #agentmain(String, Instrumentation)
 */
public final class Startup
{
   public static boolean initializing;
   @Nullable private static Instrumentation instrumentation;
   private static boolean initializedOnDemand;

   private Startup() {}

   /**
    * This method must only be called by the JVM, to provide the instrumentation object.
    * In order for this to occur, the JVM must be started with "-javaagent:jmockit.jar" as a command line parameter
    * (assuming the jar file is in the current directory).
    * <p/>
    * It is also possible to load other <em>instrumentation tools</em> at this time, by having set the "jmockit-tools"
    * and/or "jmockit-mocks" system properties in the JVM command line.
    * There are two types of instrumentation tools:
    * <ol>
    * <li>A {@link ClassFileTransformer class file transformer}, which will be instantiated and added to the JVM
    * instrumentation service. Such a class must have a no-args constructor.</li>
    * <li>An <em>external mock</em>, which should be a {@code MockUp} subclass with a no-args constructor.
    * </ol>
    *
    * @param agentArgs not used
    * @param inst      the instrumentation service provided by the JVM
    */
   public static void premain(String agentArgs, @NotNull Instrumentation inst) throws IOException
   {
      initialize(true, inst);
   }

   private static void initialize(boolean applyStartupMocks, @NotNull Instrumentation inst) throws IOException
   {
      if (instrumentation == null) {
         instrumentation = inst;

         CachedClassfiles cachingTransformer = CachedClassfiles.INSTANCE;
         inst.addTransformer(cachingTransformer, true);

         if (applyStartupMocks) {
            applyStartupMocks();
         }

         inst.addTransformer(new ExpectationsTransformer(inst));
      }
   }

   private static void applyStartupMocks() throws IOException
   {
      initializing = true;

      try {
         new JMockitInitialization().initialize();
      }
      finally {
         initializing = false;
      }
   }

   /**
    * This method must only be called by the JVM, to provide the instrumentation object.
    * This occurs only when the JMockit Java agent gets loaded on demand, through the Attach API.
    * <p/>
    * For additional details, see the {@link #premain(String, Instrumentation)} method.
    *
    * @param agentArgs not used
    * @param inst      the instrumentation service provided by the JVM
    */
   public static void agentmain(@SuppressWarnings("unused") String agentArgs, @NotNull Instrumentation inst)
      throws IOException
   {
      initialize(false, inst);

      ClassLoader customCL = (ClassLoader) System.getProperties().remove("jmockit-customCL");

      if (customCL != null) {
         reinitializeJMockitUnderCustomClassLoader(customCL);
      }
   }

   private static void reinitializeJMockitUnderCustomClassLoader(@NotNull ClassLoader customLoader)
   {
      Class<?> startupClass;

      try {
         startupClass = customLoader.loadClass(Startup.class.getName());
      }
      catch (ClassNotFoundException ignore) {
         return;
      }

      replaceCustomLogManagerAsNeeded();

      System.out.println("JMockit: Reinitializing under custom class loader " + customLoader);
      FieldReflection.setField(startupClass, null, "instrumentation", instrumentation);
      MethodReflection.invoke(startupClass, (Object) null, "reapplyStartupMocks");
   }

   private static void replaceCustomLogManagerAsNeeded()
   {
      final LogManager logManager = LogManager.getLogManager();

      if (logManager.getClass() != LogManager.class) {
         LogManager newLogManager = new LogManager() {
            @Override
            public boolean addLogger(Logger logger)
            {
               logManager.addLogger(logger);
               return super.addLogger(logger);
            }

            @Override
            public Logger getLogger(String name)
            {
               Logger logger = super.getLogger(name);
               if (logger == null) logger = logManager.getLogger(name);
               return logger;
            }
         };

         FieldReflection.setField(LogManager.class, null, "manager", newLogManager);
      }
   }

   private static void reapplyStartupMocks()
   {
      try { applyStartupMocks(); } catch (IOException e) { throw new RuntimeException(e); }
   }

   @NotNull public static Instrumentation instrumentation()
   {
      verifyInitialization();
      assert instrumentation != null;
      return instrumentation;
   }

   /**
    * Only called from the coverage tool, when it is executed with {@code -javaagent:jmockit-coverage.jar} even though
    * JMockit is in the classpath.
    */
   public static void initialize(@NotNull Instrumentation inst, @NotNull Class<? extends MockUp<?>>... mockUpClasses)
      throws IOException
   {
      boolean fullJMockit = false;

      try {
         Class.forName("mockit.internal.expectations.transformation.ExpectationsTransformer");
         fullJMockit = true;
      }
      catch (ClassNotFoundException ignored) {}

      instrumentation = inst;
      initializing = true;

      try {
         if (fullJMockit) {
            new JMockitInitialization().initialize();
         }

         applyMockUpClasses(mockUpClasses);
      }
      finally {
         initializing = false;
      }

      if (fullJMockit) {
         inst.addTransformer(CachedClassfiles.INSTANCE);
         inst.addTransformer(new ExpectationsTransformer(inst));
      }
   }

   private static void applyMockUpClasses(@NotNull Class<? extends MockUp<?>>[] mockUpClasses)
   {
      for (Class<?> mockUpClass : mockUpClasses) {
         try {
            ConstructorReflection.newInstanceUsingDefaultConstructor(mockUpClass);
         }
         catch (UnsupportedOperationException ignored) {}
         catch (Throwable unexpectedFailure) {
            StackTrace.filterStackTrace(unexpectedFailure);
            unexpectedFailure.printStackTrace();
         }
      }
   }

   public static boolean wasInitializedOnDemand() { return initializedOnDemand; }

   public static void verifyInitialization()
   {
      if (getInstrumentation() == null) {
         initializedOnDemand = new AgentInitialization().loadAgentFromLocalJarFile();

         if (initializedOnDemand) {
            System.out.println(
               "WARNING: JMockit was initialized on demand, which may cause certain tests to fail;\n" +
               "please check the documentation for better ways to get it initialized.");
         }
      }
   }

   @Nullable private static Instrumentation getInstrumentation()
   {
      if (instrumentation == null) {
         Class<?> initialStartupClass =
            ClassLoad.loadClass(ClassLoader.getSystemClassLoader(), Startup.class.getName());

         if (initialStartupClass != null) {
            instrumentation = FieldReflection.getField(initialStartupClass, "instrumentation", null);

            if (instrumentation != null) {
               reapplyStartupMocks();
            }
         }
      }

      return instrumentation;
   }

   public static boolean initializeIfPossible()
   {
      if (getInstrumentation() == null) {
         ClassLoader currentCL = Startup.class.getClassLoader();
         boolean usingCustomCL = currentCL != ClassLoader.getSystemClassLoader();

         if (usingCustomCL) {
            System.getProperties().put("jmockit-customCL", currentCL);
         }

         try {
            boolean initialized = new AgentInitialization().loadAgentFromLocalJarFile();

            if (initialized && !usingCustomCL) {
               applyStartupMocks();
            }

            return initialized;
         }
         catch (RuntimeException e) {
            e.printStackTrace(); // makes sure the exception gets printed at least once
            throw e;
         }
         catch (IOException e) {
            e.printStackTrace(); // makes sure the exception gets printed at least once
            throw new RuntimeException(e);
         }
      }

      return false;
   }

   public static void retransformClass(@NotNull Class<?> aClass)
   {
      try { instrumentation().retransformClasses(aClass); } catch (UnmodifiableClassException ignore) {}
   }

   public static void redefineMethods(@NotNull Class<?> classToRedefine, @NotNull byte[] modifiedClassfile)
   {
      redefineMethods(new ClassDefinition(classToRedefine, modifiedClassfile));
   }

   public static void redefineMethods(@NotNull ClassDefinition... classDefs)
   {
      try {
         instrumentation().redefineClasses(classDefs);
      }
      catch (ClassNotFoundException e) {
         // should never happen
         throw new RuntimeException(e);
      }
      catch (UnmodifiableClassException e) {
         throw new RuntimeException(e);
      }
      catch (InternalError e) {
         // If a class to be redefined hasn't been loaded yet, the JVM may get a NoClassDefFoundError during
         // redefinition. Unfortunately, it then throws a plain InternalError instead.
         for (ClassDefinition classDef : classDefs) {
            detectMissingDependenciesIfAny(classDef.getDefinitionClass());
         }

         // If the above didn't throw upon detecting a NoClassDefFoundError, the original error is re-thrown.
         throw e;
      }
   }

   private static void detectMissingDependenciesIfAny(@NotNull Class<?> mockedClass)
   {
      try {
         Class.forName(mockedClass.getName(), true, mockedClass.getClassLoader());
      }
      catch (NoClassDefFoundError e) {
         throw new RuntimeException("Unable to mock " + mockedClass + " due to a missing dependency", e);
      }
      catch (ClassNotFoundException ignore) {
         // Shouldn't happen since the mocked class would already have been found in the classpath.
      }
   }
}
