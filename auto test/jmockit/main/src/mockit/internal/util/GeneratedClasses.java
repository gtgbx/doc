/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.util;

import java.lang.reflect.*;

import org.jetbrains.annotations.*;

public final class GeneratedClasses
{
   public static final String SUBCLASS_PREFIX = "$Subclass_";
   public static final String IMPLCLASS_PREFIX = "$Impl_";

   @NotNull public static String getNameForGeneratedClass(@NotNull Class<?> aClass)
   {
      return getNameForGeneratedClass(aClass, aClass.getSimpleName());
   }

   @NotNull public static String getNameForGeneratedClass(@NotNull Class<?> aClass, @NotNull String suffix)
   {
      String prefix = aClass.isInterface() ? IMPLCLASS_PREFIX : SUBCLASS_PREFIX;
      StringBuilder name = new StringBuilder(60).append(prefix).append(suffix);

      if (aClass.getClassLoader() != null) {
         Package targetPackage = aClass.getPackage();

         if (targetPackage != null && !targetPackage.isSealed()) {
            name.insert(0, '.').insert(0, targetPackage.getName());
         }
      }

      return name.toString();
   }

   public static boolean isGeneratedImplementationClass(@NotNull Class<?> mockedType)
   {
      return isGeneratedImplementationClass(mockedType.getName());
   }

   private static boolean isGeneratedSubclass(@NotNull String className)
   {
      return className.contains(SUBCLASS_PREFIX);
   }

   private static boolean isGeneratedImplementationClass(@NotNull String className)
   {
      return className.contains(IMPLCLASS_PREFIX);
   }

   public static boolean isGeneratedClass(@NotNull String className)
   {
      return isGeneratedSubclass(className) || isGeneratedImplementationClass(className);
   }

   @NotNull public static Class<?> getMockedClassOrInterfaceType(@NotNull Class<?> aClass)
   {
      if (Proxy.isProxyClass(aClass) || isGeneratedImplementationClass(aClass)) {
         // Assumes that the proxy class implements a single interface.
         return aClass.getInterfaces()[0];
      }
      else if (isGeneratedSubclass(aClass.getName())) {
         return aClass.getSuperclass();
      }

      return aClass;
   }

   @NotNull public static Class<?> getMockedClass(@NotNull Object mock)
   {
      return getMockedClassOrInterfaceType(mock.getClass());
   }
}
