/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.util;

import java.lang.reflect.*;
import java.util.*;

import org.jetbrains.annotations.*;

/**
 * Miscellaneous utility methods.
 */
public final class Utilities
{
   static void ensureThatMemberIsAccessible(@NotNull AccessibleObject classMember)
   {
      if (!classMember.isAccessible()) {
         classMember.setAccessible(true);
      }
   }

   @NotNull
   public static Class<?> getClassType(@NotNull Type declaredType)
   {
      if (declaredType instanceof ParameterizedType) {
         return (Class<?>) ((ParameterizedType) declaredType).getRawType();
      }

      return (Class<?>) declaredType;
   }

   public static boolean containsReference(@NotNull List<?> references, @Nullable Object toBeFound)
   {
      return indexOfReference(references, toBeFound) >= 0;
   }

   public static int indexOfReference(@NotNull List<?> references, @Nullable Object toBeFound)
   {
      for (int i = 0, n = references.size(); i < n; i++) {
         if (references.get(i) == toBeFound) {
            return i;
         }
      }

      return -1;
   }
}
