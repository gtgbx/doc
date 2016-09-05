/*
 * Copyright (c) 2006-2013 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.expectations.mocking;

import java.lang.reflect.*;
import java.lang.reflect.Type;

import org.jetbrains.annotations.*;

import mockit.internal.util.*;

final class MockedTypeInfo
{
   @NotNull final Class<?> mockedClass;
   @NotNull final GenericTypeReflection genericTypeMap;
   @NotNull final String implementationSignature;

   MockedTypeInfo(@NotNull Type mockedType, @NotNull String typeDescPrefix)
   {
      mockedClass = Utilities.getClassType(mockedType);
      genericTypeMap = new GenericTypeReflection(mockedClass, mockedType);

      String signature = getGenericClassSignature(mockedType);
      String classDesc = mockedClass.getName().replace('.', '/');
      implementationSignature = typeDescPrefix + 'L' + classDesc + signature;
   }

   @NotNull private String getGenericClassSignature(@NotNull Type mockedType)
   {
      StringBuilder signature = new StringBuilder(100);

      if (mockedType instanceof ParameterizedType) {
         ParameterizedType parameterizedType = (ParameterizedType) mockedType;
         Type[] typeArguments = parameterizedType.getActualTypeArguments();

         if (typeArguments.length > 0) {
            signature.append('<');

            for (Type typeArg : typeArguments) {
               if (typeArg instanceof Class<?>) {
                  Class<?> classArg = (Class<?>) typeArg;
                  signature.append('L').append(classArg.getName().replace('.', '/')).append(';');
               }
               else {
                  signature.append('*');
               }
            }

            signature.append('>');
         }
      }

      signature.append(';');
      return signature.toString();
   }
}
