/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.startup;

import java.lang.instrument.*;

import org.jetbrains.annotations.*;

import mockit.external.asm4.*;
import mockit.internal.*;
import mockit.internal.util.*;

final class ToolLoader extends ClassVisitor
{
   @NotNull private final String toolClassName;
   private boolean loadClassFileTransformer;

   ToolLoader(@NotNull String toolClassName) { this.toolClassName = toolClassName; }

   void loadTool()
   {
      String classDesc = toolClassName.replace('.', '/');
      ClassReader cr;

      try {
         cr = ClassFile.readFromFile(classDesc);
      }
      catch (RuntimeException ignore) {
         System.out.println("JMockit: external tool class \"" + toolClassName + "\" not available in classpath");
         return;
      }

      try {
         cr.accept(this, ClassReader.SKIP_DEBUG);
         System.out.println("JMockit: loaded external tool " + toolClassName);
      }
      catch (IllegalStateException ignore) {}
   }

   @Override
   public void visit(
      int version, int access, @NotNull String name, @Nullable String signature, @Nullable String superName,
      @Nullable String[] interfaces)
   {
      if (interfaces != null && containsClassFileTransformer(interfaces)) {
         loadClassFileTransformer = true;
      }
   }

   private boolean containsClassFileTransformer(@NotNull String[] interfaces)
   {
      for (String anInterface : interfaces) {
         if ("java/lang/instrument/ClassFileTransformer".equals(anInterface)) {
            return true;
         }
      }

      return false;
   }

   @Override
   public void visitEnd()
   {
      if (loadClassFileTransformer) {
         createAndInstallSpecifiedClassFileTransformer();
      }
      else {
         setUpStartupMock();
      }
   }

   private void createAndInstallSpecifiedClassFileTransformer()
   {
      Class<ClassFileTransformer> transformerClass = ClassLoad.loadClassAtStartup(toolClassName);
      ClassFileTransformer transformer = ConstructorReflection.newInstanceUsingDefaultConstructor(transformerClass);

      Startup.instrumentation().addTransformer(transformer);
   }

   private void setUpStartupMock()
   {
      Class<?> mockClass = ClassLoad.loadClassAtStartup(toolClassName);

      //noinspection UnnecessaryFullyQualifiedName
      if (mockit.MockUp.class.isAssignableFrom(mockClass)) {
         try {
            ConstructorReflection.newInstanceUsingDefaultConstructor(mockClass);
         }
         catch (TypeNotPresentException e) {
            // OK, ignores the startup mock if the necessary third-party class files are not in the classpath.
            System.out.println(e);
         }
      }
   }
}
