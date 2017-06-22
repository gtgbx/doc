/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.util;

import java.lang.reflect.*;

import org.jetbrains.annotations.*;

/**
 * Provides optimized utility methods to extract stack trace information.
 */
public final class StackTrace
{
   private static final Method getStackTraceDepth = getThrowableMethod("getStackTraceDepth");
   private static final Method getStackTraceElement = getThrowableMethod("getStackTraceElement", int.class);

   @Nullable private static Method getThrowableMethod(@NotNull String name, @NotNull Class<?>... parameterTypes)
   {
      Method m;
      try { m = Throwable.class.getDeclaredMethod(name, parameterTypes); }
      catch (NoSuchMethodException ignore) { return null; }
      m.setAccessible(true);
      return m;
   }

   @NotNull private final Throwable t;
   @Nullable private final StackTraceElement[] elements;

   public StackTrace() { this(new Throwable()); }

   public StackTrace(@NotNull Throwable t)
   {
      this.t = t;
      elements = getStackTraceDepth == null ? t.getStackTrace() : null;
   }

   public int getDepth()
   {
      if (elements != null) {
         return elements.length;
      }

      int depth = 0;

      try {
         depth = (Integer) getStackTraceDepth.invoke(t);
      }
      catch (IllegalAccessException ignore) {}
      catch (InvocationTargetException ignored) {}

      return depth;
   }

   @NotNull public StackTraceElement getElement(int index)
   {
      if (elements != null) {
         return elements[index];
      }

      StackTraceElement element;
      try { element = (StackTraceElement) getStackTraceElement.invoke(t, index); }
      catch (IllegalAccessException e) { throw new RuntimeException(e); }
      catch (InvocationTargetException e) { throw new RuntimeException(e); }

      return element;
   }

   public static void filterStackTrace(@NotNull Throwable t)
   {
      new StackTrace(t).filter();
   }

   public void filter()
   {
      int n = getDepth();
      StackTraceElement[] filteredST = new StackTraceElement[n];
      int j = 0;

      for (int i = 0; i < n; i++) {
         StackTraceElement ste = getElement(i);

         if (ste.getFileName() != null) {
            String where = ste.getClassName();

            if (!isSunMethod(ste) && !isTestFrameworkMethod(where) && !isJMockitMethod(where)) {
               filteredST[j] = ste;
               j++;
            }
         }
      }

      StackTraceElement[] newStackTrace = new StackTraceElement[j];
      System.arraycopy(filteredST, 0, newStackTrace, 0, j);
      t.setStackTrace(newStackTrace);

      Throwable cause = t.getCause();

      if (cause != null) {
         new StackTrace(cause).filter();
      }
   }

   private static boolean isSunMethod(@NotNull StackTraceElement ste)
   {
      return ste.getClassName().startsWith("sun.") && !ste.isNativeMethod();
   }

   private static boolean isTestFrameworkMethod(@NotNull String where)
   {
      return where.startsWith("org.junit.") || where.startsWith("junit.") || where.startsWith("org.testng.");
   }

   private static boolean isJMockitMethod(@NotNull String where)
   {
      return where.startsWith("mockit.") && (where.contains(".internal.") || !where.contains("Test"));
   }
}
