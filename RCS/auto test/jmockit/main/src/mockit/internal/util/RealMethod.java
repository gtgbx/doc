/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.util;

import java.lang.reflect.*;

import org.jetbrains.annotations.*;

public final class RealMethod
{
   @NotNull public final Method method;

   public RealMethod(@NotNull Class<?> realClass, @NotNull String methodName, @NotNull String methodDesc)
   {
      method = initialize(realClass, methodName, methodDesc);
   }

   public RealMethod(@NotNull String className, @NotNull String methodNameAndDesc)
   {
      this(ClassLoad.loadClass(className), methodNameAndDesc);
   }

   public RealMethod(@NotNull Class<?> realClass, @NotNull String methodNameAndDesc)
   {
      int p = methodNameAndDesc.indexOf('(');
      String methodName = methodNameAndDesc.substring(0, p);
      String methodDesc = methodNameAndDesc.substring(p);
      method = initialize(realClass, methodName, methodDesc);
   }

   @NotNull
   private Method initialize(@NotNull Class<?> realClass, @NotNull String methodName, @NotNull String methodDesc)
   {
      Class<?>[] parameterTypes = TypeDescriptor.getParameterTypes(methodDesc);
      Class<?> ownerClass = realClass;

      while (true) {
         try {
            return ownerClass.getDeclaredMethod(methodName, parameterTypes);
         }
         catch (NoSuchMethodException e) {
            if (ownerClass.isInterface()) {
               Method interfaceMethod = initialize(ownerClass, methodName, parameterTypes);

               if (interfaceMethod == null) {
                  throw new RuntimeException(e);
               }

               return interfaceMethod;
            }
            else {
               ownerClass = ownerClass.getSuperclass();
            }

            if (ownerClass == Object.class) {
               throw new RuntimeException(e);
            }
         }
      }
   }

   @Nullable
   private Method initialize(
      @NotNull Class<?> classOrInterface, @NotNull String methodName, @NotNull Class<?>[] parameterTypes)
   {
      for (Class<?> superInterface : classOrInterface.getInterfaces()) {
         try {
            return superInterface.getDeclaredMethod(methodName, parameterTypes);
         }
         catch (NoSuchMethodException ignore) {}
      }

      return null;
   }
}
