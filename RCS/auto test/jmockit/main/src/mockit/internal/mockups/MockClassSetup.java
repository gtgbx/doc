/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.mockups;

import java.lang.reflect.*;
import java.lang.reflect.Type;
import java.util.*;

import org.jetbrains.annotations.*;

import mockit.*;
import mockit.external.asm4.*;
import mockit.internal.*;
import mockit.internal.startup.*;
import mockit.internal.state.*;
import mockit.internal.util.*;

public final class MockClassSetup
{
   @NotNull final Class<?> realClass;
   @Nullable private ClassReader rcReader;
   @NotNull private final MockMethods mockMethods;
   @NotNull final MockUp<?> mockUp;
   private final boolean forStartupMock;

   public MockClassSetup(
      @NotNull Class<?> realClass, @Nullable Type mockedType, @NotNull MockUp<?> mockUp, @Nullable byte[] realClassCode)
   {
      this.realClass = realClass;
      mockMethods = new MockMethods(realClass, mockedType);
      this.mockUp = mockUp;
      forStartupMock = Startup.initializing;
      rcReader = realClassCode == null ? null : new ClassReader(realClassCode);

      new MockMethodCollector(mockMethods).collectMockMethods(mockUp.getClass());
   }

   @NotNull
   public Set<Class<?>> redefineMethods()
   {
      Set<Class<?>> redefinedClasses = redefineMethodsInClassHierarchy();
      validateThatAllMockMethodsWereApplied();
      return redefinedClasses;
   }

   @NotNull
   private Set<Class<?>> redefineMethodsInClassHierarchy()
   {
      Set<Class<?>> redefinedClasses = new HashSet<Class<?>>();
      Class<?> classToModify = realClass;

      while (classToModify != null && mockMethods.hasUnusedMocks()) {
         byte[] modifiedClassFile = modifyRealClass(classToModify);

         if (modifiedClassFile != null) {
            applyClassModifications(classToModify, modifiedClassFile);
            redefinedClasses.add(classToModify);
         }

         Class<?> superClass = classToModify.getSuperclass();
         classToModify = superClass == Object.class || superClass == Proxy.class ? null : superClass;
         rcReader = null;
      }

      return redefinedClasses;
   }

   @Nullable
   private byte[] modifyRealClass(@NotNull Class<?> classToModify)
   {
      if (rcReader == null) {
         rcReader = createClassReaderForRealClass(classToModify);
      }

      MockupsModifier modifier = new MockupsModifier(rcReader, classToModify, mockUp, mockMethods, forStartupMock);
      rcReader.accept(modifier, ClassReader.SKIP_FRAMES);

      return modifier.wasModified() ? modifier.toByteArray() : null;
   }

   @NotNull
   ClassVisitor createClassModifier(@NotNull ClassReader cr)
   {
      return new MockupsModifier(cr, realClass, mockUp, mockMethods, forStartupMock);
   }

   @NotNull
   private ClassReader createClassReaderForRealClass(@NotNull Class<?> classToModify)
   {
      if (classToModify.isInterface() || classToModify.isArray()) {
         throw new IllegalArgumentException("Not a modifiable class: " + classToModify.getName());
      }

      return ClassFile.createReaderFromLastRedefinitionIfAny(classToModify);
   }

   void applyClassModifications(@NotNull Class<?> classToModify, @NotNull byte[] modifiedClassFile)
   {
      Startup.redefineMethods(classToModify, modifiedClassFile);
      mockMethods.registerMockStates(forStartupMock);

      if (forStartupMock) {
         CachedClassfiles.addClassfile(classToModify, modifiedClassFile);
      }
      else {
         String mockClassDesc = mockMethods.getMockClassInternalName();
         TestRun.mockFixture().addRedefinedClass(mockClassDesc, classToModify, modifiedClassFile);
      }
   }

   void validateThatAllMockMethodsWereApplied()
   {
      List<String> remainingMocks = mockMethods.getUnusedMockSignatures();

      if (!remainingMocks.isEmpty()) {
         String classDesc = mockMethods.getMockClassInternalName();
         String mockSignatures = new MethodFormatter(classDesc).friendlyMethodSignatures(remainingMocks);

         throw new IllegalArgumentException(
            "Matching real methods not found for the following mocks:\n" + mockSignatures);
      }
   }
}
