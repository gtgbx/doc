/*
 * Copyright (c) 2006-2013 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.coverage;

import java.io.*;
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;

import org.jetbrains.annotations.*;

import mockit.internal.util.*;

public final class CallPoint implements Serializable
{
   private static final long serialVersionUID = 362727169057343840L;
   private static final Map<StackTraceElement, Boolean> steCache = new HashMap<StackTraceElement, Boolean>();
   private static final Class<? extends Annotation> testAnnotation;
   private static final boolean checkTestAnnotationOnClass;
   private static final boolean checkIfTestCaseSubclass;

   static
   {
      boolean checkOnClassAlso = true;
      Class<?> annotation;

      try {
         annotation = Class.forName("org.junit.Test");
         checkOnClassAlso = false;
      }
      catch (ClassNotFoundException ignore) {
         annotation = getTestNGAnnotationIfAvailable();
      }

      //noinspection unchecked
      testAnnotation = (Class<? extends Annotation>) annotation;
      checkTestAnnotationOnClass = checkOnClassAlso;
      checkIfTestCaseSubclass = checkForJUnit3Availability();
   }

   @Nullable private static Class<?> getTestNGAnnotationIfAvailable()
   {
      try {
         return Class.forName("org.testng.annotations.Test");
      }
      catch (ClassNotFoundException ignore) {
         // For older versions of TestNG:
         try {
            return Class.forName("org.testng.Test");
         }
         catch (ClassNotFoundException ignored) {
            return null;
         }
      }
   }

   private static boolean checkForJUnit3Availability()
   {
      try {
         Class.forName("junit.framework.TestCase");
         return true;
      }
      catch (ClassNotFoundException ignore) {
         return false;
      }
   }

   @NotNull private final StackTraceElement ste;

   private CallPoint(@NotNull StackTraceElement ste) { this.ste = ste; }

   @NotNull public StackTraceElement getStackTraceElement() { return ste; }

   @Nullable static CallPoint create(@NotNull Throwable newThrowable)
   {
      StackTrace st = new StackTrace(newThrowable);
      int n = st.getDepth();

      for (int i = 2; i < n; i++) {
         StackTraceElement ste = st.getElement(i);

         if (isTestMethod(ste)) {
            return new CallPoint(ste);
         }
      }

      return null;
   }

   private static boolean isTestMethod(@NotNull StackTraceElement ste)
   {
      if (steCache.containsKey(ste)){
         return steCache.get(ste);
      }

      if (ste.getFileName() == null || ste.getLineNumber() < 0) {
         steCache.put(ste, false);
         return false;
      }

      Class<?> aClass = loadClass(ste.getClassName());
      Method method = findMethod(aClass, ste.getMethodName());

      if (method == null) {
         steCache.put(ste, false);
         return false;
      }

      boolean isTestMethod =
         checkTestAnnotationOnClass && aClass.isAnnotationPresent(testAnnotation) ||
         containsATestFrameworkAnnotation(method.getDeclaredAnnotations()) ||
         checkIfTestCaseSubclass && isJUnit3xTestMethod(aClass, method);

      steCache.put(ste, isTestMethod);

      return isTestMethod;
   }

   @NotNull private static Class<?> loadClass(@NotNull String className)
   {
      try {
         return Class.forName(className);
      }
      catch (ClassNotFoundException e) {
         throw new RuntimeException(e);
      }
   }

   @Nullable private static Method findMethod(@NotNull Class<?> aClass, @NotNull String name)
   {
      try {
         for (Method method : aClass.getDeclaredMethods()) {
            if (
               Modifier.isPublic(method.getModifiers()) && method.getReturnType() == void.class &&
               name.equals(method.getName())
            ) {
               return method;
            }
         }
      }
      catch (NoClassDefFoundError e) {
         System.out.println(e + " when attempting to find method \"" + name + "\" in " + aClass);
      }

      return null;
   }

   private static boolean containsATestFrameworkAnnotation(@NotNull Annotation[] methodAnnotations)
   {
      for (Annotation annotation : methodAnnotations) {
         String annotationName = annotation.annotationType().getName();

         if (annotationName.startsWith("org.junit.") || annotationName.startsWith("org.testng.")) {
            return true;
         }
      }

      return false;
   }

   private static boolean isJUnit3xTestMethod(@NotNull Class<?> aClass, @NotNull Method method)
   {
      if (!method.getName().startsWith("test")) {
         return false;
      }

      Class<?> superClass = aClass.getSuperclass();

      while (superClass != Object.class) {
         if ("junit.framework.TestCase".equals(superClass.getName())) {
            return true;
         }

         superClass = superClass.getSuperclass();
      }

      return false;
   }
}
