/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.mockups;

import java.lang.reflect.Type;
import static java.lang.reflect.Modifier.*;

import org.jetbrains.annotations.*;

import mockit.*;
import mockit.external.asm4.*;
import mockit.internal.*;
import mockit.internal.util.*;

public final class MockedImplementationClass<T>
{
   @NotNull private final MockUp<?> mockUpInstance;
   @Nullable private ImplementationClass<T> implementationClass;
   @NotNull Class<T> generatedClass;

   public MockedImplementationClass(@NotNull MockUp<?> mockUpInstance) { this.mockUpInstance = mockUpInstance; }

   @NotNull T newProxyInstance(@NotNull Class<T> interfaceToBeMocked)
   {
      if (!isPublic(interfaceToBeMocked.getModifiers())) {
         T proxy = EmptyProxy.Impl.newEmptyProxy(interfaceToBeMocked.getClassLoader(), interfaceToBeMocked);
         //noinspection unchecked
         generatedClass = (Class<T>) proxy.getClass();
         return proxy;
      }

      implementationClass = new ImplementationClass<T>(interfaceToBeMocked) {
         @Override @NotNull
         protected ClassVisitor createMethodBodyGenerator(@NotNull ClassReader typeReader, @NotNull String className)
         {
            return new InterfaceImplementationGenerator(typeReader, className);
         }
      };

      generatedClass = implementationClass.generateNewMockImplementationClassForInterface();

      T proxy = ConstructorReflection.newInstanceUsingDefaultConstructor(generatedClass);
      return proxy;
   }

   @NotNull public T generate(@NotNull Class<T> interfaceToBeMocked, @Nullable Type typeToMock)
   {
      T proxy = newProxyInstance(interfaceToBeMocked);
      byte[] generatedBytecode = implementationClass == null ? null : implementationClass.getGeneratedBytecode();
      new MockClassSetup(generatedClass, typeToMock, mockUpInstance, generatedBytecode).redefineMethods();
      return proxy;
   }
}
