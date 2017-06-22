/*
 * Copyright (c) 2006-2013 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.util;

import java.lang.reflect.*;
import java.util.*;

import org.jetbrains.annotations.*;

/**
 * This marker interface exists only to guarantee that JMockit can get the bytecode definition of
 * each Proxy class it creates through <code>java.lang.reflect.Proxy</code>.
 * If such a class is created before JMockit is initialized, its bytecode won't be stored in
 * JMockit's cache. And since the JDK uses an internal cache for proxy classes, it won't create a
 * new one, therefore not going through the ProxyRegistrationTransformer. So, by always implementing
 * this additional interface, we can guarantee a new proxy class will be created when JMockit first
 * requests it for a given interface.
 */
public interface EmptyProxy
{
   final class Impl
   {
      @NotNull
      public static <E> E newEmptyProxy(@Nullable ClassLoader loader, @NotNull Class<E> interfaceToBeProxied)
      {
         Class<?>[] interfaces = loader == null ?
            new Class<?>[] {interfaceToBeProxied} : new Class<?>[] {interfaceToBeProxied, EmptyProxy.class};

         //noinspection unchecked
         return (E) Proxy.newProxyInstance(loader, interfaces, MockInvocationHandler.INSTANCE);
      }

      @NotNull
      public static <E> E newEmptyProxy(@Nullable ClassLoader loader, @NotNull Type... interfacesToBeProxied)
      {
         List<Class<?>> interfaces = new ArrayList<Class<?>>();

         for (Type type : interfacesToBeProxied) {
            addInterface(interfaces, type);
         }

         if (loader == null) {
            //noinspection AssignmentToMethodParameter
            loader = interfaces.get(0).getClassLoader();
         }

         if (loader == EmptyProxy.class.getClassLoader()) {
            interfaces.add(EmptyProxy.class);
         }

         Class<?>[] interfacesArray = interfaces.toArray(new Class<?>[interfaces.size()]);

         //noinspection unchecked
         return (E) Proxy.newProxyInstance(loader, interfacesArray, MockInvocationHandler.INSTANCE);
      }

      private static void addInterface(@NotNull List<Class<?>> interfaces, @NotNull Type type)
      {
         if (type instanceof Class<?>) {
            interfaces.add((Class<?>) type);
         }
         else if (type instanceof ParameterizedType) {
            ParameterizedType paramType = (ParameterizedType) type;
            interfaces.add((Class<?>) paramType.getRawType());
         }
         else if (type instanceof TypeVariable) {
            TypeVariable<?> typeVar = (TypeVariable<?>) type;
            addBoundInterfaces(interfaces, typeVar.getBounds());
         }
      }

      private static void addBoundInterfaces(@NotNull List<Class<?>> interfaces, @NotNull Type[] bounds)
      {
         for (Type bound : bounds) {
            addInterface(interfaces, bound);
         }
      }
   }
}
