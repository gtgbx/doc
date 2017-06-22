/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal;

import java.lang.reflect.*;
import java.util.*;

import org.jetbrains.annotations.*;

import mockit.external.asm4.*;
import mockit.internal.util.*;

final class SuperConstructorCollector extends ClassVisitor
{
   @NotNull static final SuperConstructorCollector INSTANCE = new SuperConstructorCollector();

   @NotNull private final Map<String, String> cache = new HashMap<String, String>();
   @Nullable private String constructorDesc;
   private boolean samePackage;

   private SuperConstructorCollector() {}

   @NotNull synchronized String findConstructor(@NotNull String classDesc, @NotNull String superClassDesc)
   {
      constructorDesc = cache.get(superClassDesc);

      if (constructorDesc != null) {
         return constructorDesc;
      }

      findIfBothClassesAreInSamePackage(classDesc, superClassDesc);

      ClassReader cr = ClassFile.readFromFile(superClassDesc);
      try { cr.accept(this, ClassReader.SKIP_DEBUG); } catch (VisitInterruptedException ignore) {}

      cache.put(superClassDesc, constructorDesc);
      
      return constructorDesc;
   }

   private void findIfBothClassesAreInSamePackage(@NotNull String classDesc, @NotNull String superClassDesc)
   {
      int p1 = classDesc.lastIndexOf('/');
      int p2 = superClassDesc.lastIndexOf('/');
      samePackage = p1 == p2 && (p1 < 0 || classDesc.substring(0, p1).equals(superClassDesc.substring(0, p2)));
   }

   @Override @Nullable
   public MethodVisitor visitMethod(
      int access, @NotNull String name, @NotNull String desc, @Nullable String signature, @Nullable String[] exceptions)
   {
      if (isAccessible(access) && "<init>".equals(name)) {
         constructorDesc = desc;
         throw VisitInterruptedException.INSTANCE;
      }

      return null;
   }

   private boolean isAccessible(int access) { return access != Modifier.PRIVATE && (access != 0 || samePackage); }
}
