/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.expectations.mocking;

import java.util.*;

import static java.lang.reflect.Modifier.*;

import org.jetbrains.annotations.*;

import static mockit.external.asm4.Opcodes.*;

import mockit.external.asm4.*;
import mockit.internal.util.*;

final class ExpectationsModifier extends MockedTypeModifier
{
   private static final int METHOD_ACCESS_MASK = ACC_SYNTHETIC + ACC_ABSTRACT;

   private static final Map<String, String> DEFAULT_FILTERS = new HashMap<String, String>();
   static {
      DEFAULT_FILTERS.put("java/lang/Object", "<init> getClass hashCode");
      DEFAULT_FILTERS.put("java/lang/String", "");
      DEFAULT_FILTERS.put("java/lang/AbstractStringBuilder", "");
      DEFAULT_FILTERS.put("java/lang/StringBuilder", "");
      DEFAULT_FILTERS.put("java/lang/StringBuffer", "");
      DEFAULT_FILTERS.put("java/lang/System", "arraycopy getProperties getSecurityManager identityHashCode");
      DEFAULT_FILTERS.put("java/lang/Throwable", "<init> fillInStackTrace");
      DEFAULT_FILTERS.put("java/lang/Exception", "<init>");
      DEFAULT_FILTERS.put("java/lang/Thread", "currentThread isInterrupted interrupted");
      DEFAULT_FILTERS.put("java/util/Hashtable", "get hash");
      DEFAULT_FILTERS.put("java/util/ArrayList", "");
      DEFAULT_FILTERS.put("java/util/HashMap", "");
      DEFAULT_FILTERS.put("java/util/jar/JarEntry", "<init>");
      DEFAULT_FILTERS.put("java/util/logging/LogManager",
                          "getLogger getLogManager getUserContext readPrimordialConfiguration");
   }

   @Nullable private final MockingConfiguration mockingCfg;
   private String className;
   @Nullable private String baseClassNameForCapturedInstanceMethods;
   private boolean stubOutClassInitialization;
   private boolean ignoreConstructors;
   private int executionMode;
   private boolean isProxy;
   @Nullable private String defaultFilters;

   ExpectationsModifier(
      @Nullable ClassLoader classLoader, @NotNull ClassReader classReader, @Nullable MockedType typeMetadata)
   {
      super(classReader);

      if (typeMetadata == null) {
         mockingCfg = null;
      }
      else {
         mockingCfg = typeMetadata.mockingCfg;
         stubOutClassInitialization = typeMetadata.isClassInitializationToBeStubbedOut();
      }

      setUseMockingBridge(classLoader);
      executionMode = 3;
   }

   public void setClassNameForCapturedInstanceMethods(@NotNull String internalClassName)
   {
      baseClassNameForCapturedInstanceMethods = internalClassName;
   }

   public void useDynamicMocking(boolean methodsOnly)
   {
      ignoreConstructors = methodsOnly;
      executionMode = 1;
   }

   public void useDynamicMockingForInstanceMethods(@Nullable MockedType typeMetadata)
   {
      ignoreConstructors = typeMetadata == null || typeMetadata.getMaxInstancesToCapture() <= 0;
      executionMode = 2;
   }

   @Override
   public void visit(
      int version, int access, @NotNull String name, @Nullable String signature, @Nullable String superName,
      @Nullable String[] interfaces)
   {
      if ("java/lang/Class".equals(name) || "java/lang/ClassLoader".equals(name) ) {
         throw new IllegalArgumentException("Class " + name.replace('/', '.') + " is not mockable");
      }

      super.visit(version, access, name, signature, superName, interfaces);
      isProxy = "java/lang/reflect/Proxy".equals(superName);

      if (isProxy) {
         assert interfaces != null;
         className = interfaces[0];
         defaultFilters = null;
      }
      else {
         className = name;
         defaultFilters = DEFAULT_FILTERS.get(name);

         if (defaultFilters != null && defaultFilters.length() == 0) {
            throw VisitInterruptedException.INSTANCE;
         }
      }
   }

   @Override
   @Nullable public MethodVisitor visitMethod(
      int access, @NotNull String name, @NotNull String desc, @Nullable String signature, @Nullable String[] exceptions)
   {
      boolean syntheticOrAbstractMethod = (access & METHOD_ACCESS_MASK) != 0;

      if (syntheticOrAbstractMethod || isProxy && isConstructorOrSystemMethodNotToBeMocked(name, desc)) {
         return unmodifiedBytecode(access, name, desc, signature, exceptions);
      }

      boolean noFiltersToMatch = mockingCfg == null;
      boolean matchesFilters = noFiltersToMatch || mockingCfg.matchesFilters(name, desc);

      if ("<clinit>".equals(name)) {
         return stubOutClassInitializationIfApplicable(access, noFiltersToMatch, matchesFilters);
      }
      else if (stubOutFinalizeMethod(access, name, desc)) {
         return null;
      }

      if (
         !matchesFilters ||
         isMethodFromCapturedClassNotToBeMocked(access) ||
         noFiltersToMatch && isMethodOrConstructorNotToBeMocked(access, name)
      ) {
         return unmodifiedBytecode(access, name, desc, signature, exceptions);
      }

      // Otherwise, replace original implementation with redirect to JMockit.
      startModifiedMethodVersion(access, name, desc, signature, exceptions);

      boolean visitingConstructor = "<init>".equals(name);

      if (visitingConstructor && superClassName != null) {
         generateCallToSuperConstructor();
      }

      String internalClassName = className;

      if (!visitingConstructor && baseClassNameForCapturedInstanceMethods != null) {
         internalClassName = baseClassNameForCapturedInstanceMethods;
      }

      int actualExecutionMode = determineAppropriateExecutionMode(access, visitingConstructor);

      if (useMockingBridge) {
         return
            generateCallToHandlerThroughMockingBridge(
               access, signature, exceptions, internalClassName, actualExecutionMode);
      }

      generateDirectCallToHandler(internalClassName, access, name, desc, signature, exceptions, actualExecutionMode);
      generateDecisionBetweenReturningOrContinuingToRealImplementation();

      // Constructors of non-JRE classes can't be modified (unless running with "-noverify") in a way that
      // "super(...)/this(...)" get called twice, so we disregard such calls when copying the original bytecode.
      return visitingConstructor ? new DynamicConstructorModifier() : copyOriginalImplementationCode(access);
   }

   @Nullable
   private MethodVisitor unmodifiedBytecode(
      int access, @NotNull String name, @NotNull String desc, @Nullable String signature, @Nullable String[] exceptions)
   {
      return super.visitMethod(access, name, desc, signature, exceptions);
   }

   private boolean isConstructorOrSystemMethodNotToBeMocked(@NotNull String name, @NotNull String desc)
   {
      return
         "<init>".equals(name) || isMethodFromObject(name, desc) ||
         "annotationType".equals(name) && "()Ljava/lang/Class;".equals(desc);
   }

   @Nullable
   private MethodVisitor stubOutClassInitializationIfApplicable(int access, boolean noFilters, boolean matchesFilters)
   {
      mw = super.visitMethod(access, "<clinit>", "()V", null, null);

      if (!noFilters && matchesFilters || stubOutClassInitialization) {
         generateEmptyImplementation();
         return null;
      }

      return mw;
   }

   private boolean stubOutFinalizeMethod(int access, @NotNull String name, @NotNull String desc)
   {
      if ("finalize".equals(name) && "()V".equals(desc)) {
         mw = super.visitMethod(access, name, desc, null, null);
         generateEmptyImplementation();
         return true;
      }
      
      return false;
   }
   
   private boolean isMethodFromCapturedClassNotToBeMocked(int access)
   {
      return baseClassNameForCapturedInstanceMethods != null && (isStatic(access) || isPrivate(access));
   }

   private boolean isMethodOrConstructorNotToBeMocked(int access, @NotNull String name)
   {
      return
         isConstructorToBeIgnored(name) ||
         isStaticMethodToBeIgnored(access) ||
         isNativeMethodForDynamicMocking(access) ||
         useMockingBridge && isPrivate(access) && isNative(access) ||
         defaultFilters != null && defaultFilters.contains(name);
   }

   private boolean isConstructorToBeIgnored(@NotNull String name)
   {
      return ignoreConstructors && "<init>".equals(name);
   }

   private boolean isStaticMethodToBeIgnored(int access) { return executionMode == 2 && isStatic(access); }
   private boolean isNativeMethodForDynamicMocking(int access) { return executionMode < 3 && isNative(access); }

   private int determineAppropriateExecutionMode(int access, boolean visitingConstructor)
   {
      if (executionMode == 2) {
         if (visitingConstructor) {
            return ignoreConstructors ? 3 : 1;
         }
         else if (isStatic(access)) {
            return 3;
         }
      }

      return executionMode;
   }

   @NotNull
   private MethodVisitor generateCallToHandlerThroughMockingBridge(
      int access, @Nullable String genericSignature, @Nullable String[] exceptions, @NotNull String internalClassName,
      int actualExecutionMode)
   {
      generateCodeToObtainInstanceOfMockingBridge(MockedBridge.MB);

      // First and second "invoke" arguments:
      boolean isStatic = generateCodeToPassThisOrNullIfStaticMethod(access);
      mw.visitInsn(ACONST_NULL);

      // Create array for call arguments (third "invoke" argument):
      Type[] argTypes = Type.getArgumentTypes(methodDesc);
      generateCodeToCreateArrayOfObject(7 + argTypes.length);

      int i = 0;
      generateCodeToFillArrayElement(i++, access);
      generateCodeToFillArrayElement(i++, internalClassName);
      generateCodeToFillArrayElement(i++, methodName);
      generateCodeToFillArrayElement(i++, methodDesc);
      generateCodeToFillArrayElement(i++, genericSignature);
      generateCodeToFillArrayElement(i++, getListOfExceptionsAsSingleString(exceptions));
      generateCodeToFillArrayElement(i++, actualExecutionMode);

      generateCodeToPassMethodArgumentsAsVarargs(argTypes, i, isStatic ? 0 : 1);
      generateCallToInvocationHandler();

      generateDecisionBetweenReturningOrContinuingToRealImplementation();

      // Copies the entire original implementation even for a constructor, in which case the complete bytecode inside
      // the constructor fails the strict verification activated by "-Xfuture". However, this is necessary to allow the
      // full execution of a JRE constructor when the call was not meant to be mocked.
      return copyOriginalImplementationCode(access);
   }

   @NotNull private MethodVisitor copyOriginalImplementationCode(int access)
   {
      if (isNative(access)) {
         generateEmptyImplementation(methodDesc);
         return methodAnnotationsVisitor;
      }

      return new DynamicModifier();
   }

   private class DynamicModifier extends MethodVisitor
   {
      DynamicModifier() { super(mw); }

      @Override
      public final void visitLocalVariable(
         @NotNull String name, @NotNull String desc, @Nullable String signature,
         @NotNull Label start, @NotNull Label end, int index)
      {
         registerParameterName(name, index);

         // For some reason, the start position for "this" gets displaced by bytecode inserted at the beginning,
         // in a method modified by the EMMA tool. If not treated, this causes a ClassFormatError.
         if (end.position > 0 && start.position > end.position) {
            start.position = end.position;
         }

         // Ignores any local variable with required information missing, to avoid a VerifyError/ClassFormatError.
         if (start.position > 0 && end.position > 0) {
            super.visitLocalVariable(name, desc, signature, start, end, index);
         }
      }
   }

   private final class DynamicConstructorModifier extends DynamicModifier
   {
      @Override
      public void visitMethodInsn(int opcode, @NotNull String owner, @NotNull String name, @NotNull String desc)
      {
         disregardIfInvokingAnotherConstructor(opcode, owner, name, desc);
      }
   }
}
