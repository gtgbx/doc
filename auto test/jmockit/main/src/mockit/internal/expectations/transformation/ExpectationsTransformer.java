/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.expectations.transformation;

import java.lang.instrument.*;
import java.security.*;
import java.util.*;

import static java.lang.reflect.Modifier.*;

import org.jetbrains.annotations.*;

import mockit.external.asm4.*;
import mockit.internal.*;
import mockit.internal.startup.*;
import mockit.internal.util.*;

public final class ExpectationsTransformer implements ClassFileTransformer
{
   @NotNull private final List<String> baseSubclasses;

   public ExpectationsTransformer(@NotNull Instrumentation instrumentation)
   {
      baseSubclasses = new ArrayList<String>();
      baseSubclasses.add("mockit/Expectations");
      baseSubclasses.add("mockit/NonStrictExpectations");
      baseSubclasses.add("mockit/Verifications");
      baseSubclasses.add("mockit/FullVerifications");
      baseSubclasses.add("mockit/VerificationsInOrder");
      baseSubclasses.add("mockit/FullVerificationsInOrder");

      Class<?>[] alreadyLoaded = instrumentation.getAllLoadedClasses();
      findAndModifyOtherBaseSubclasses(alreadyLoaded);
      modifyFinalSubclasses(alreadyLoaded);
   }

   private void findAndModifyOtherBaseSubclasses(@NotNull Class<?>[] alreadyLoaded)
   {
      for (Class<?> aClass : alreadyLoaded) {
         if (
            aClass.getClassLoader() != null && !isFinalClass(aClass) &&
            isExpectationsOrVerificationsSubclassFromUserCode(aClass)
         ) {
            modifyInvocationsSubclass(aClass, false);
         }
      }
   }

   private boolean isFinalClass(@NotNull Class<?> aClass)
   {
      return isFinal(aClass.getModifiers()) || ClassNaming.isAnonymousClass(aClass);
   }

   private boolean isExpectationsOrVerificationsSubclassFromUserCode(@NotNull Class<?> aClass)
   {
      if (isExpectationsOrVerificationsAPIClass(aClass)) {
         return false;
      }

      Class<?> superclass = aClass.getSuperclass();

      while (superclass != null && superclass != Object.class && superclass.getClassLoader() != null) {
         if (isExpectationsOrVerificationsAPIClass(superclass)) {
            return true;
         }

         superclass = superclass.getSuperclass();
      }

      return false;
   }

   private boolean isExpectationsOrVerificationsAPIClass(@NotNull Class<?> aClass)
   {
      return
         ("mockit.Expectations mockit.NonStrictExpectations " +
          "mockit.Verifications mockit.FullVerifications " +
          "mockit.VerificationsInOrder mockit.FullVerificationsInOrder").contains(aClass.getName());
   }

   private void modifyFinalSubclasses(@NotNull Class<?>[] alreadyLoaded)
   {
      for (Class<?> aClass : alreadyLoaded) {
         if (
            aClass.getClassLoader() != null && isFinalClass(aClass) &&
            isExpectationsOrVerificationsSubclassFromUserCode(aClass)
         ) {
            modifyInvocationsSubclass(aClass, true);
         }
      }
   }

   private void modifyInvocationsSubclass(@NotNull Class<?> aClass, boolean isFinalClass)
   {
      ClassReader cr = ClassFile.createClassFileReader(aClass);
      EndOfBlockModifier modifier = new EndOfBlockModifier(cr, aClass.getClassLoader(), isFinalClass);

      try {
         cr.accept(modifier, 0);
      }
      catch (VisitInterruptedException ignore) {
         return;
      }

      byte[] modifiedClassfile = modifier.toByteArray();
      Startup.redefineMethods(aClass, modifiedClassfile);
   }

   @Override @Nullable
   public byte[] transform(
      @Nullable ClassLoader loader, @NotNull String className, @Nullable Class<?> classBeingRedefined,
      @Nullable ProtectionDomain protectionDomain, @NotNull byte[] classfileBuffer)
   {
      if (classBeingRedefined == null && protectionDomain != null) {
         ClassReader cr = new ClassReader(classfileBuffer);
         String superClassName = cr.getSuperName();

         if (
            !baseSubclasses.contains(superClassName) &&
            !superClassName.endsWith("Expectations") && !superClassName.endsWith("Verifications")
         ) {
            return null;
         }

         boolean isAnonymousClass = ClassNaming.isAnonymousClass(className);

         try {
            EndOfBlockModifier modifier = new EndOfBlockModifier(cr, loader, isAnonymousClass);
            cr.accept(modifier, 0);
            return modifier.toByteArray();
         }
         catch (VisitInterruptedException ignore) {}
         catch (Throwable e) { e.printStackTrace(); }
      }

      return null;
   }

   private final class EndOfBlockModifier extends ClassVisitor
   {
      @NotNull private final ClassWriter cw;
      @Nullable private final ClassLoader loader;
      private boolean isFinalClass;
      @NotNull private String classDesc;

      EndOfBlockModifier(@NotNull ClassReader cr, @Nullable ClassLoader loader, boolean isFinalClass)
      {
         super(new ClassWriter(cr, ClassWriter.COMPUTE_MAXS));
         assert cv != null;
         cw = (ClassWriter) cv;
         this.loader = loader;
         this.isFinalClass = isFinalClass;
         classDesc = "";
      }

      @Override
      public void visit(
         int version, int access, @NotNull String name, @Nullable String signature, @Nullable String superName,
         @Nullable String[] interfaces)
      {
         if (isFinal(access)) {
            isFinalClass = true;
         }

         if (isClassWhichShouldBeModified(name, superName)) {
            super.visit(version, access, name, signature, superName, interfaces);
            classDesc = name;
         }
         else {
            throw VisitInterruptedException.INSTANCE;
         }
      }

      private boolean isClassWhichShouldBeModified(@NotNull String name, @Nullable String superName)
      {
         if (baseSubclasses.contains(name)) {
            return false;
         }

         boolean superClassIsKnownInvocationsSubclass = baseSubclasses.contains(superName);

         if (isFinalClass) {
            if (superClassIsKnownInvocationsSubclass) {
               return true;
            }

            SuperClassAnalyser superClassAnalyser = new SuperClassAnalyser(loader);

            if (superClassAnalyser.classExtendsInvocationsClass(superName)) {
               return true;
            }
         }
         else if (superClassIsKnownInvocationsSubclass) {
            baseSubclasses.add(name);
            return true;
         }
         else {
            SuperClassAnalyser superClassAnalyser = new SuperClassAnalyser(loader);

            if (superClassAnalyser.classExtendsInvocationsClass(superName)) {
               baseSubclasses.add(name);
               return true;
            }
         }

         return false;
      }

      @Override
      public MethodVisitor visitMethod(
         int access, @NotNull String name, @NotNull String desc,
         @Nullable String signature, @Nullable String[] exceptions)
      {
         MethodWriter mw = cw.visitMethod(access, name, desc, signature, exceptions);
         return new InvocationBlockModifier(mw, classDesc, isFinalClass && "<init>".equals(name));
      }
   }

   private final class SuperClassAnalyser extends ClassVisitor
   {
      @Nullable private final ClassLoader loader;
      private boolean classExtendsBaseSubclass;

      private SuperClassAnalyser(@Nullable ClassLoader loader) { this.loader = loader; }

      boolean classExtendsInvocationsClass(@Nullable String classOfInterest)
      {
         if (classOfInterest == null || "java/lang/Object".equals(classOfInterest)) {
            return false;
         }

         ClassReader cr = ClassFile.createClassFileReader(loader, classOfInterest);

         try { cr.accept(this, ClassReader.SKIP_DEBUG); } catch (VisitInterruptedException ignore) {}

         return classExtendsBaseSubclass;
      }

      @Override
      public void visit(
         int version, int access, @NotNull String name, @Nullable String signature, @Nullable String superName,
         @Nullable String[] interfaces)
      {
         classExtendsBaseSubclass = baseSubclasses.contains(superName);

         if (!classExtendsBaseSubclass && !"java/lang/Object".equals(superName)) {
            classExtendsInvocationsClass(superName);
         }

         throw VisitInterruptedException.INSTANCE;
      }
   }
}
