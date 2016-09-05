/*
 * Copyright (c) 2006-2013 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.expectations.mocking;

import java.lang.reflect.*;
import java.util.*;

import org.jetbrains.annotations.*;

import mockit.external.asm4.*;
import mockit.internal.*;
import mockit.internal.state.*;
import mockit.internal.util.*;

public final class DynamicPartialMocking
{
   @NotNull public final List<Object> targetInstances;
   @NotNull private final Map<Class<?>, byte[]> modifiedClassfiles;
   private final boolean nonStrict;

   public DynamicPartialMocking(boolean nonStrict)
   {
      targetInstances = new ArrayList<Object>(2);
      modifiedClassfiles = new HashMap<Class<?>, byte[]>();
      this.nonStrict = nonStrict;
   }

   public void redefineTypes(@NotNull Object[] classesOrInstancesToBePartiallyMocked)
   {
      for (Object classOrInstance : classesOrInstancesToBePartiallyMocked) {
         redefineTargetType(classOrInstance);
      }

      new RedefinitionEngine().redefineMethods(modifiedClassfiles);
      modifiedClassfiles.clear();
   }

   private void redefineTargetType(@NotNull Object classOrInstance)
   {
      Class<?> targetClass;

      if (classOrInstance instanceof Class) {
         targetClass = (Class<?>) classOrInstance;
         validateTargetClassType(targetClass);
         registerAsMocked(targetClass);
         redefineClassAndItsSuperClasses(targetClass, false);
      }
      else {
         targetClass = GeneratedClasses.getMockedClass(classOrInstance);
         validateTargetClassType(targetClass);
         registerAsMocked(classOrInstance);
         redefineClassAndItsSuperClasses(targetClass, true);
         targetInstances.add(classOrInstance);
      }

      TestRun.mockFixture().registerMockedClass(targetClass);
   }

   private void validateTargetClassType(@NotNull Class<?> targetClass)
   {
      if (
         targetClass.isInterface() || targetClass.isAnnotation() || targetClass.isArray() ||
         targetClass.isPrimitive() || AutoBoxing.isWrapperOfPrimitiveType(targetClass) ||
         GeneratedClasses.isGeneratedImplementationClass(targetClass)
      ) {
         throw new IllegalArgumentException("Invalid type for dynamic mocking: " + targetClass);
      }
   }

   private void registerAsMocked(@NotNull Class<?> mockedClass)
   {
      if (nonStrict) {
         ExecutingTest executingTest = TestRun.getExecutingTest();
         Class<?> classToRegister = mockedClass;

         do {
            executingTest.registerAsNonStrictlyMocked(classToRegister);
            classToRegister = classToRegister.getSuperclass();
         }
         while (classToRegister != null && classToRegister != Object.class && classToRegister != Proxy.class);
      }
   }

   private void registerAsMocked(@NotNull Object mock)
   {
      if (nonStrict) {
         TestRun.getExecutingTest().registerAsNonStrictlyMocked(mock);
      }
   }

   private void redefineClassAndItsSuperClasses(@NotNull Class<?> realClass, boolean methodsOnly)
   {
      redefineClass(realClass, methodsOnly);
      Class<?> superClass = realClass.getSuperclass();

      if (superClass != null && superClass != Object.class && superClass != Proxy.class) {
         redefineClassAndItsSuperClasses(superClass, methodsOnly);
      }
   }

   private void redefineClass(@NotNull Class<?> realClass, boolean methodsOnly)
   {
      ClassReader classReader = ClassFile.createReaderOrGetFromCache(realClass);

      ExpectationsModifier modifier = new ExpectationsModifier(realClass.getClassLoader(), classReader, null);
      modifier.useDynamicMocking(methodsOnly);

      classReader.accept(modifier, 0);
      byte[] modifiedClass = modifier.toByteArray();

      modifiedClassfiles.put(realClass, modifiedClass);
   }
}
