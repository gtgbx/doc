/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.mockups;

import java.lang.reflect.Type;
import org.jetbrains.annotations.*;

import mockit.*;
import mockit.external.asm4.*;
import mockit.internal.capturing.*;
import mockit.internal.util.*;

public final class CaptureOfMockedUpImplementations extends CaptureOfImplementations
{
   private final MockClassSetup mockClassSetup;

   public CaptureOfMockedUpImplementations(MockUp<?> mockUp, Type baseType)
   {
      if (baseType == Object.class) {
         throw new IllegalArgumentException("Missing base type whose implementation classes should be mocked");
      }

      Class<?> baseClassType = Utilities.getClassType(baseType);
      mockClassSetup = new MockClassSetup(baseClassType, baseType, mockUp, null);
   }

   @NotNull
   @Override
   protected ClassVisitor createModifier(
      @Nullable ClassLoader cl, @NotNull ClassReader cr, @NotNull String baseTypeDesc)
   {
      return mockClassSetup.createClassModifier(cr);
   }

   @Override
   protected void redefineClass(@NotNull Class<?> realClass, @NotNull byte[] modifiedClass)
   {
      mockClassSetup.applyClassModifications(realClass, modifiedClass);
   }

   @Nullable
   public <T> T apply()
   {
      @SuppressWarnings("unchecked") Class<T> baseType = (Class<T>) mockClassSetup.realClass;
      T mockInstance = null;
      Class<T> baseClassType = baseType;

      if (baseType.isInterface()) {
         MockedImplementationClass<T> mockedImplementation = new MockedImplementationClass<T>(mockClassSetup.mockUp);
         mockInstance = mockedImplementation.newProxyInstance(baseType);
         baseClassType = mockedImplementation.generatedClass;
      }

      redefineClass(baseClassType, mockit.external.asm4.Type.getInternalName(baseType));
      mockClassSetup.validateThatAllMockMethodsWereApplied();
      makeSureAllSubtypesAreModified(baseType, false);
      return mockInstance;
   }
}
