/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.capturing;

import java.lang.instrument.*;
import java.security.*;
import java.util.*;

import org.jetbrains.annotations.*;

import mockit.external.asm4.*;
import mockit.internal.*;
import mockit.internal.state.*;
import mockit.internal.util.*;

import static mockit.internal.util.GeneratedClasses.*;

public final class CaptureTransformer implements ClassFileTransformer
{
   @NotNull private final Class<?> baseType;
   @NotNull private final String capturedType;
   @NotNull private final CaptureOfImplementations modifierFactory;
   @NotNull private final Map<ClassIdentification, byte[]> transformedClasses;
   private boolean inactive;

   CaptureTransformer(
      @NotNull CapturedType metadata, @NotNull CaptureOfImplementations modifierFactory,
      boolean registerTransformedClasses)
   {
      baseType = metadata.baseType;
      capturedType = Type.getInternalName(baseType);
      this.modifierFactory = modifierFactory;
      transformedClasses =
         registerTransformedClasses ?
            new HashMap<ClassIdentification, byte[]>(2) :
            Collections.<ClassIdentification, byte[]>emptyMap();
   }

   public void deactivate()
   {
      inactive = true;

      if (!transformedClasses.isEmpty()) {
         RedefinitionEngine redefinitionEngine = new RedefinitionEngine();

         for (Map.Entry<ClassIdentification, byte[]> classNameAndOriginalBytecode : transformedClasses.entrySet()) {
            ClassIdentification classId = classNameAndOriginalBytecode.getKey();
            byte[] originalBytecode = classNameAndOriginalBytecode.getValue();
            redefinitionEngine.restoreToDefinition(classId.getLoadedClass(), originalBytecode);
         }

         transformedClasses.clear();
      }
   }

   @Override @Nullable
   public byte[] transform(
      @Nullable ClassLoader loader, @NotNull String internalClassName, @Nullable Class<?> classBeingRedefined,
      @Nullable ProtectionDomain protectionDomain, @NotNull byte[] classfileBuffer)
   {
      if (inactive || classBeingRedefined != null || internalClassName.startsWith("mockit/internal/")) {
         return null;
      }

      ClassReader cr = new ClassReader(classfileBuffer);
      SuperTypeCollector superTypeCollector = new SuperTypeCollector(loader);

      try {
         cr.accept(superTypeCollector, ClassReader.SKIP_DEBUG);
      }
      catch (VisitInterruptedException ignore) {
         if (superTypeCollector.classExtendsCapturedType && !isGeneratedClass(internalClassName)) {
            String className = internalClassName.replace('/', '.');
            return modifyAndRegisterClass(loader, className, cr);
         }
      }

      return null;
   }

   @NotNull
   private byte[] modifyAndRegisterClass(
      @Nullable ClassLoader loader, @NotNull String className, @NotNull ClassReader cr)
   {
      ClassVisitor modifier = modifierFactory.createModifier(loader, cr, capturedType);
      cr.accept(modifier, 0);

      ClassIdentification classId = new ClassIdentification(loader, className);
      byte[] originalBytecode = cr.b;

      if (transformedClasses == Collections.<ClassIdentification, byte[]>emptyMap()) {
         TestRun.mockFixture().addTransformedClass(classId, originalBytecode);
      }
      else {
         transformedClasses.put(classId, originalBytecode);
      }

      TestRun.mockFixture().registerMockedClass(baseType);
      return modifier.toByteArray();
   }

   private final class SuperTypeCollector extends ClassVisitor
   {
      @Nullable private final ClassLoader loader;
      boolean classExtendsCapturedType;

      private SuperTypeCollector(@Nullable ClassLoader loader) { this.loader = loader; }

      @Override
      public void visit(
         int version, int access, @NotNull String name, @Nullable String signature, @Nullable String superName,
         @Nullable String[] interfaces)
      {
         classExtendsCapturedType = false;

         if (capturedType.equals(superName)) {
            classExtendsCapturedType = true;
         }
         else if (interfaces != null && interfaces.length > 0) {
            for (String implementedInterface : interfaces) {
               if (capturedType.equals(implementedInterface)) {
                  classExtendsCapturedType = true;
                  break;
               }
            }
         }

         if (superName != null && !classExtendsCapturedType && !"java/lang/Object".equals(superName)) {
            ClassReader cr = ClassFile.createClassFileReader(loader, superName);
            cr.accept(this, ClassReader.SKIP_DEBUG);
         }

         throw VisitInterruptedException.INSTANCE;
      }
   }
}
