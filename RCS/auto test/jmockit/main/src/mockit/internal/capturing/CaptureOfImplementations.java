/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.capturing;

import java.lang.reflect.*;

import org.jetbrains.annotations.*;

import mockit.external.asm4.*;
import mockit.external.asm4.Type;
import mockit.internal.*;
import mockit.internal.startup.*;
import mockit.internal.state.*;

public abstract class CaptureOfImplementations
{
   protected CaptureOfImplementations() {}

   public final void makeSureAllSubtypesAreModified(@NotNull Class<?> baseType)
   {
      makeSureAllSubtypesAreModified(baseType, false);
   }

   public final void makeSureAllSubtypesAreModified(@NotNull Class<?> baseType, boolean registerCapturedClasses)
   {
      if (baseType == TypeVariable.class) {
         throw new IllegalArgumentException("Capturing implementations of multiple base types is not supported");
      }

      String baseTypeDesc = Type.getInternalName(baseType);
      CapturedType captureMetadata = new CapturedType(baseType);

      redefineClassesAlreadyLoaded(captureMetadata, baseTypeDesc);
      createCaptureTransformer(captureMetadata, registerCapturedClasses);
   }

   private void redefineClassesAlreadyLoaded(@NotNull CapturedType captureMetadata, @NotNull String baseTypeDesc)
   {
      Class<?>[] classesLoaded = Startup.instrumentation().getAllLoadedClasses();

      for (Class<?> aClass : classesLoaded) {
         if (captureMetadata.isToBeCaptured(aClass)) {
            redefineClass(aClass, baseTypeDesc);
         }
      }
   }

   public void redefineClass(@NotNull Class<?> realClass, @NotNull String baseTypeDesc)
   {
      if (!TestRun.mockFixture().containsRedefinedClass(realClass)) {
         ClassReader classReader;

         try {
            classReader = ClassFile.createReaderOrGetFromCache(realClass);
         }
         catch (ClassFile.NotFoundException ignore) {
            return;
         }

         ClassVisitor modifier = createModifier(realClass.getClassLoader(), classReader, baseTypeDesc);
         classReader.accept(modifier, 0);
         byte[] modifiedClass = modifier.toByteArray();

         redefineClass(realClass, modifiedClass);
      }
   }

   @NotNull
   protected abstract ClassVisitor createModifier(
      @Nullable ClassLoader cl, @NotNull ClassReader cr, @NotNull String baseTypeDesc);

   protected abstract void redefineClass(@NotNull Class<?> realClass, @NotNull byte[] modifiedClass);

   private void createCaptureTransformer(@NotNull CapturedType captureMetadata, boolean registerCapturedClasses)
   {
      CaptureTransformer transformer = new CaptureTransformer(captureMetadata, this, registerCapturedClasses);
      Startup.instrumentation().addTransformer(transformer, true);
      TestRun.mockFixture().addCaptureTransformer(transformer);
   }
}
