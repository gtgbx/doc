/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.integration.junit4.internal;

import java.util.*;

import org.jetbrains.annotations.*;

import org.junit.runners.model.*;

import mockit.*;
import mockit.internal.mockups.*;

/**
 * Startup mock that modifies the JUnit 4.5+ test runner so that it calls back to JMockit immediately after every test
 * executes.
 * When that happens, JMockit will assert any expectations set during the test, including expectations specified through
 * {@link mockit.Mock} as well as in {@link mockit.Expectations} subclasses.
 * <p/>
 * This class is not supposed to be accessed from user code. JMockit will automatically load it at startup.
 */
public final class MockFrameworkMethod extends MockUp<FrameworkMethod>
{
   public static boolean hasDependenciesInClasspath()
   {
      try {
         Class.forName(FrameworkMethod.class.getName(), true, FrameworkMethod.class.getClassLoader());
         return true;
      }
      catch (NoClassDefFoundError ignore) { return false; }
      catch (ClassNotFoundException ignore) { return false; }
   }

   @NotNull private final JUnit4TestRunnerDecorator decorator = new JUnit4TestRunnerDecorator();

   @Mock
   @Nullable
   public Object invokeExplosively(@NotNull Invocation invocation, Object target, Object... params) throws Throwable
   {
      return decorator.invokeExplosively((MockInvocation) invocation, target, params);
   }

   @Mock
   public static void validatePublicVoidNoArg(@NotNull Invocation invocation, boolean isStatic, List<Throwable> errors)
   {
      FrameworkMethod it = invocation.getInvokedInstance();

      if (!isStatic && it.getMethod().getParameterTypes().length > 0) {
         it.validatePublicVoid(false, errors);
         return;
      }

      ((MockInvocation) invocation).prepareToProceed();
      it.validatePublicVoidNoArg(isStatic, errors);
   }
}
