/*
 * Copyright (c) 2006-2013 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.expectations.mocking;

import java.lang.reflect.*;

import org.jetbrains.annotations.*;
import sun.reflect.*;

import mockit.internal.state.*;
import mockit.internal.util.*;

public abstract class InstanceFactory
{
   @Nullable protected Object lastInstance;

   @NotNull public abstract Object create();

   @Nullable public final Object getLastInstance() { return lastInstance; }
   public final void clearLastInstance() { lastInstance = null; }

   static final class InterfaceInstanceFactory extends InstanceFactory
   {
      @NotNull private final Object emptyProxy;

      InterfaceInstanceFactory(@NotNull Object emptyProxy) { this.emptyProxy = emptyProxy; }

      @Override
      @NotNull public Object create() { lastInstance = emptyProxy; return emptyProxy; }
   }

   @SuppressWarnings("UseOfSunClasses")
   static final class ClassInstanceFactory extends InstanceFactory
   {
      private static final ReflectionFactory REFLECTION_FACTORY = ReflectionFactory.getReflectionFactory();
      private static final Constructor<?> OBJECT_CONSTRUCTOR;
      static
      {
         try { OBJECT_CONSTRUCTOR = Object.class.getConstructor(); }
         catch (NoSuchMethodException e) { throw new RuntimeException(e); }
      }

      @NotNull private final Constructor<?> fakeConstructor;

      ClassInstanceFactory(@NotNull Class<?> concreteClass)
      {
         fakeConstructor = REFLECTION_FACTORY.newConstructorForSerialization(concreteClass, OBJECT_CONSTRUCTOR);
      }

      @Override
      @NotNull public Object create()
      {
         TestRun.exitNoMockingZone();

         try {
            lastInstance = fakeConstructor.newInstance();
            return lastInstance;
         }
         catch (NoClassDefFoundError e) {
            StackTrace.filterStackTrace(e);
            e.printStackTrace();
            throw e;
         }
         catch (ExceptionInInitializerError e) {
            StackTrace.filterStackTrace(e);
            e.printStackTrace();
            throw e;
         }
         catch (InstantiationException e) { throw new RuntimeException(e); }
         catch (IllegalAccessException e) { throw new RuntimeException(e); }
         catch (InvocationTargetException e) { throw new RuntimeException(e.getCause()); }
         finally {
            TestRun.enterNoMockingZone();
         }
      }
   }

   static final class EnumInstanceFactory extends InstanceFactory
   {
      @NotNull private final Object anEnumValue;

      EnumInstanceFactory(@NotNull Class<?> enumClass) { anEnumValue = enumClass.getEnumConstants()[0]; }

      @Override
      @NotNull public Object create() { lastInstance = anEnumValue; return anEnumValue; }
   }
}
