/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.state;

import java.lang.instrument.*;
import java.security.*;
import java.util.*;

import org.jetbrains.annotations.*;

import mockit.internal.startup.*;

/**
 * Holds a map of internal class names to the corresponding class files (bytecode arrays), for the classes
 * that have already been loaded during the test run.
 * These classfiles are not necessarily the same as those stored in the corresponding ".class" files
 * available from the runtime classpath.
 * If any third-party {@link java.lang.instrument.ClassFileTransformer}s are active, those original classfiles
 * may have been modified before being loaded by the JVM.
 * JMockit installs a {@code ClassFileTransformer} of its own which saves all potentially modified classfiles
 * here.
 * <p/>
 * This bytecode cache allows classes to be mocked and un-mocked correctly, even in the presence of other
 * bytecode modification agents such as the AspectJ load-time weaver.
 */
public final class CachedClassfiles implements ClassFileTransformer
{
   @NotNull public static final CachedClassfiles INSTANCE = new CachedClassfiles();

   @NotNull private final Map<ClassLoader, Map<String, byte[]>> classLoadersAndClassfiles =
      new WeakHashMap<ClassLoader, Map<String, byte[]>>(2);
   @Nullable private Class<?> classBeingCached;

   private CachedClassfiles() {}

   @Override @Nullable
   public byte[] transform(
      @Nullable ClassLoader loader, String classDesc, @Nullable Class<?> classBeingRedefinedOrRetransformed,
      @Nullable ProtectionDomain protectionDomain, @NotNull byte[] classfileBuffer)
   {
      if (classDesc != null) { // can be null for Java 8 lambdas
         if (classBeingRedefinedOrRetransformed != null && classBeingRedefinedOrRetransformed == classBeingCached) {
            addClassfile(loader, classDesc, classfileBuffer);
            classBeingCached = null;
         }
      }

      return null;
   }

   private void addClassfile(@Nullable ClassLoader loader, @NotNull String classDesc, @NotNull byte[] classfile)
   {
      Map<String, byte[]> classfiles = getClassfiles(loader);
      classfiles.put(classDesc, classfile);
   }

   @NotNull private Map<String, byte[]> getClassfiles(@Nullable ClassLoader loader)
   {
      Map<String, byte[]> classfiles = classLoadersAndClassfiles.get(loader);

      if (classfiles == null) {
         classfiles = new HashMap<String, byte[]>(100);
         classLoadersAndClassfiles.put(loader, classfiles);
      }

      return classfiles;
   }

   @Nullable private byte[] findClassfile(@NotNull Class<?> aClass)
   {
      String className = aClass.getName();

      // Discards an invalid numerical suffix from a synthetic Java 8 class, if detected.
      int p = className.indexOf('/');
      if (p > 0) className = className.substring(0, p);

      Map<String, byte[]> classfiles = getClassfiles(aClass.getClassLoader());
      return classfiles.get(className.replace('.', '/'));
   }

   @Nullable private synchronized byte[] findClassfile(@Nullable ClassLoader loader, @NotNull String classDesc)
   {
      Map<String, byte[]> classfiles = getClassfiles(loader);
      return classfiles.get(classDesc);
   }

   @Nullable public static synchronized byte[] getClassfile(@NotNull Class<?> aClass)
   {
      byte[] cached = INSTANCE.findClassfile(aClass);
      if (cached != null) return cached;

      INSTANCE.classBeingCached = aClass;
      Startup.retransformClass(aClass);
      return INSTANCE.findClassfile(aClass);
   }

   @Nullable public static byte[] getClassfile(@Nullable ClassLoader loader, @NotNull String internalClassName)
   {
      return INSTANCE.findClassfile(loader, internalClassName);
   }

   public static void addClassfile(@NotNull Class<?> aClass, @NotNull byte[] classfile)
   {
      INSTANCE.addClassfile(aClass.getClassLoader(), aClass.getName().replace('.', '/'), classfile);
   }
}
