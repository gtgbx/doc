/*
 * Copyright (c) 2006-2013 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.expectations.mocking;

import java.lang.reflect.Type;

import org.jetbrains.annotations.*;

import mockit.external.asm4.*;
import mockit.internal.util.*;

public final class CascadingTypeRedefinition extends BaseTypeRedefinition
{
   @NotNull private final Type mockedType;

   public CascadingTypeRedefinition(@NotNull Type mockedType)
   {
      super(Utilities.getClassType(mockedType));
      this.mockedType = mockedType;
      typeMetadata = new MockedType(mockedType);
   }

   @NotNull public InstanceFactory redefineType()
   {
      return redefineType(mockedType);
   }

   @Override
   @NotNull ExpectationsModifier createModifier(@NotNull Class<?> realClass, @NotNull ClassReader classReader)
   {
      ExpectationsModifier modifier = new ExpectationsModifier(realClass.getClassLoader(), classReader, null);
      modifier.useDynamicMockingForInstanceMethods(null);
      return modifier;
   }

   @Override
   @NotNull String getNameForConcreteSubclassToCreate()
   {
      return GeneratedClasses.SUBCLASS_PREFIX + targetClass.getSimpleName();
   }
}