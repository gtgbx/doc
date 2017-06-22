/*
 * Copyright (c) 2006-2013 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.integration.junit4.internal;

import java.lang.reflect.*;

import org.jetbrains.annotations.*;

import org.junit.runners.*;
import org.junit.runners.model.*;

import mockit.*;
import mockit.integration.internal.*;
import mockit.internal.state.*;
import mockit.internal.util.*;

/**
 * Startup mock which works in conjunction with {@link JUnit4TestRunnerDecorator} to provide JUnit 4.5+ integration.
 * <p/>
 * This class is not supposed to be accessed from user code. JMockit will automatically load it at startup.
 */
public final class BlockJUnit4ClassRunnerDecorator extends MockUp<BlockJUnit4ClassRunner>
{
   private static final Method getTestClass;

   static
   {
      Method getTestClassMethod;

      try {
         getTestClassMethod = ParentRunner.class.getDeclaredMethod("getTestClass");
      }
      catch (NoSuchMethodException e) { throw new RuntimeException(e); }

      if (getTestClassMethod.isAccessible()) {
         getTestClass = null;
      }
      else {
         getTestClassMethod.setAccessible(true);
         getTestClass = getTestClassMethod;
      }
   }

   @Mock
   @Nullable public Object createTest(@NotNull Invocation invocation) throws Throwable
   {
      TestRun.enterNoMockingZone();

      try {
         ParentRunner<?> it = invocation.getInvokedInstance();
         assert it != null;

         TestClass junitTestClass = getTestClass == null ? it.getTestClass() : (TestClass) getTestClass.invoke(it);
         Class<?> newTestClass = junitTestClass.getJavaClass();
         Class<?> currentTestClass = TestRun.getCurrentTestClass();

         if (currentTestClass != null && !currentTestClass.isAssignableFrom(newTestClass)) {
            TestRunnerDecorator.cleanUpMocksFromPreviousTestClass();
         }

         return invocation.proceed();
      }
      catch (InvocationTargetException e) {
         Throwable cause = e.getCause();
         StackTrace.filterStackTrace(cause);
         throw cause;
      }
      finally {
         TestRun.exitNoMockingZone();
      }
   }
}
