/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.capturing;

import static java.lang.reflect.Proxy.*;

import org.jetbrains.annotations.*;

import static mockit.internal.util.GeneratedClasses.*;

final class CapturedType
{
   @NotNull final Class<?> baseType;

   CapturedType(@NotNull Class<?> baseType) { this.baseType = baseType; }

   boolean isToBeCaptured(@NotNull Class<?> aClass)
   {
      if (aClass == baseType || isProxyClass(aClass) || !baseType.isAssignableFrom(aClass)) {
         return false;
      }

      String className = aClass.getName();
      return !className.startsWith("mockit.internal.") && !isGeneratedClass(className);
   }
}
