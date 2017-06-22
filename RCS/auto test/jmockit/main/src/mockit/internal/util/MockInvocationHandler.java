/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.util;

import java.lang.reflect.*;
import java.lang.annotation.*;

import org.jetbrains.annotations.*;

/**
 * Handles invocations to all kinds of mock implementations created for interfaces and annotation types through any of
 * the mocking APIs.
 * <p/>
 * The {@code java.lang.Object} methods {@code equals}, {@code hashCode}, and {@code toString} are handled in a
 * meaningful way, returning a value that makes sense for the proxy instance.
 * The special {@linkplain Annotation} contracts for these three methods is <em>not</em> observed, though, since it
 * would require making dynamic calls to the mocked annotation attributes.
 * <p/>
 * Any other method invocation is handled by simply returning the default value according to the method's return type
 * (as defined in {@linkplain mockit.internal.util.DefaultValues}).
 */
public final class MockInvocationHandler implements InvocationHandler
{
   public static final InvocationHandler INSTANCE = new MockInvocationHandler();

   @Override @Nullable
   public Object invoke(@NotNull Object proxy, @NotNull Method method, Object[] args)
   {
      Class<?> declaringClass = method.getDeclaringClass();
      String methodName = method.getName();

      if (declaringClass == Object.class) {
         if ("equals".equals(methodName)) {
            return proxy == args[0];
         }
         else if ("hashCode".equals(methodName)) {
            return System.identityHashCode(proxy);
         }
         else if ("toString".equals(methodName)) {
            return ObjectMethods.objectIdentity(proxy);
         }
      }
      else if (declaringClass == Annotation.class) {
         return proxy.getClass().getInterfaces()[0];
      }

      Class<?> retType = method.getReturnType();

      return DefaultValues.computeForType(retType);
   }
}
