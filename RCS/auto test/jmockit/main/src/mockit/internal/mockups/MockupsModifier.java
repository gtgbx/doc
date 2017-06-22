/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.mockups;

import static java.lang.reflect.Modifier.*;

import org.jetbrains.annotations.*;

import static mockit.external.asm4.Opcodes.*;

import mockit.*;
import mockit.external.asm4.*;
import mockit.internal.*;
import mockit.internal.state.*;
import mockit.internal.mockups.MockMethods.MockMethod;

/**
 * Responsible for generating all necessary bytecode in the redefined (real) class.
 * Such code will redirect calls made on "real" methods to equivalent calls on the corresponding "mock" methods.
 * The original code won't be executed by the running JVM until the class redefinition is undone.
 * <p/>
 * Methods in the real class which have no corresponding mock method are unaffected.
 * <p/>
 * Any fields (static or not) in the real class remain untouched.
 */
final class MockupsModifier extends BaseClassModifier
{
   private final int mockInstanceIndex;
   private final boolean forStartupMock;
   @NotNull private final MockMethods mockMethods;

   private final boolean useMockingBridgeForUpdatingMockState;
   @NotNull private final Class<?> mockedClass;

   // Helper fields:
   private MockMethod mockMethod;

   // Output data:
   private boolean classWasModified;

   MockupsModifier(
      @NotNull ClassReader cr, @NotNull Class<?> realClass, @NotNull MockUp<?> mock, @NotNull MockMethods mockMethods,
      boolean forStartupMock)
   {
      this(cr, realClass, mock, mockMethods, forStartupMock, true);
   }

   /**
    * Initializes the modifier for a given real/mock class pair.
    * <p/>
    * The mock instance provided will receive calls for any instance methods defined in the mock class.
    * Therefore, it needs to be later recovered by the modified bytecode inside the real method.
    * To enable this, the mock instance is added to the end of a global list made available through the
    * {@link mockit.internal.state.TestRun#getMock(int)} method.
    *
    * @param cr the class file reader for the real class
    * @param mock an instance of the mock class, never null
    * @param mockMethods contains the set of mock methods collected from the mock class; each mock method is identified
    * by a pair composed of "name" and "desc", where "name" is the method name, and "desc" is the JVM internal
    * description of the parameters; once the real class modification is complete this set will be empty, unless no
    * corresponding real method was found for any of its method identifiers
    */
   MockupsModifier(
      @NotNull ClassReader cr, @NotNull Class<?> realClass, @NotNull MockUp<?> mock, @NotNull MockMethods mockMethods,
      boolean forStartupMock, boolean computeFrames)
   {
      super(cr, computeFrames);
      mockedClass = realClass;
      mockInstanceIndex = TestRun.getMockClasses().getMocks(forStartupMock).addMock(mock);
      this.mockMethods = mockMethods;
      this.forStartupMock = forStartupMock;

      ClassLoader classLoaderOfRealClass = realClass.getClassLoader();
      useMockingBridgeForUpdatingMockState = classLoaderOfRealClass == null;
      inferUseOfMockingBridge(classLoaderOfRealClass, mock);
   }

   private void inferUseOfMockingBridge(@Nullable ClassLoader classLoaderOfRealClass, @NotNull Object mock)
   {
      setUseMockingBridge(classLoaderOfRealClass);

      if (!useMockingBridge && !isPublic(mock.getClass().getModifiers())) {
         useMockingBridge = true;
      }
   }

   /**
    * If the specified method has a mock definition, then generates bytecode to redirect calls made to it to the mock
    * method. If it has no mock, does nothing.
    *
    * @param access not relevant
    * @param name together with desc, used to identity the method in given set of mock methods
    * @param signature not relevant
    * @param exceptions not relevant
    *
    * @return null if the method was redefined, otherwise a MethodWriter that writes out the visited method code without
    * changes
    */
   @Override
   public MethodVisitor visitMethod(
      int access, @NotNull String name, @NotNull String desc, @Nullable String signature, @Nullable String[] exceptions)
   {
      if ((access & ACC_SYNTHETIC) != 0 || isAbstract(access)) {
         if (isAbstract(access)) {
            // Marks a matching mock method (if any) as having the corresponding mocked method.
            mockMethods.containsMethod(name, desc, signature);
         }

         return super.visitMethod(access, name, desc, signature, exceptions);
      }

      if ("<init>".equals(name) && isMockedSuperclass() || !hasMock(name, desc, signature)) {
         return super.visitMethod(access, name, desc, signature, exceptions);
      }

      if (isNative(access)) {
         mockMethod.markAsNativeRealMethod();
      }

      startModifiedMethodVersion(access, name, desc, signature, exceptions);
      classWasModified = true;

      if (mockMethod.isForConstructor()) {
         generateCallToSuperConstructor();
      }

      if (isToPreserveRealImplementation(access)) {
         return getAlternativeMethodWriter(access);
      }

      generateCallToUpdateMockStateIfAny(access);
      generateCallToMockMethod(access);
      generateMethodReturn();
      mw.visitMaxs(1, 0); // dummy values, real ones are calculated by ASM
      return methodAnnotationsVisitor;
   }

   private boolean hasMock(@NotNull String name, @NotNull String desc, @Nullable String signature)
   {
      String mockName = getCorrespondingMockName(name);
      mockMethod = mockMethods.containsMethod(mockName, desc, signature);
      return mockMethod != null;
   }

   @NotNull private String getCorrespondingMockName(@NotNull String name)
   {
      if ("<init>".equals(name)) {
         return "$init";
      }
      else if ("<clinit>".equals(name)) {
         return "$clinit";
      }

      return name;
   }

   @NotNull private MethodVisitor getAlternativeMethodWriter(int mockedAccess)
   {
      generateDynamicCallToMock(mockedAccess);

      final boolean forConstructor = methodName.charAt(0) == '<';

      return new MethodVisitor(mw) {
         @Override
         public void visitLocalVariable(
            @NotNull String name, @NotNull String desc, @Nullable String signature,
            @NotNull Label start, @NotNull Label end, int index)
         {
            // Discards debug info with missing information, to avoid a ClassFormatError (happens with EMMA).
            if (end.position > 0) {
               mw.visitLocalVariable(name, desc, signature, start, end, index);
            }
         }

         @Override
         public void visitMethodInsn(int opcode, @NotNull String owner, @NotNull String name, @NotNull String desc)
         {
            if (forConstructor) {
               disregardIfInvokingAnotherConstructor(opcode, owner, name, desc);
            }
            else {
               mw.visitMethodInsn(opcode, owner, name, desc);
            }
         }
      };
   }

   private boolean isToPreserveRealImplementation(int mockedAccess)
   {
      return !isNative(mockedAccess) && (isMockedSuperclass() || mockMethod.isDynamic());
   }

   private boolean isMockedSuperclass()
   {
      return mockedClass != mockMethods.getRealClass();
   }

   private void generateDynamicCallToMock(int mockedAccess)
   {
      if (!isStatic(mockedAccess) && !mockMethod.isForConstructor() && isMockedSuperclass()) {
         startOfRealImplementation = new Label();
         mw.visitVarInsn(ALOAD, 0);
         mw.visitTypeInsn(INSTANCEOF, Type.getInternalName(mockMethods.getRealClass()));
         mw.visitJumpInsn(IFEQ, startOfRealImplementation);
      }

      generateCallToUpdateMockStateIfAny(mockedAccess);

      if (mockMethod.isReentrant()) {
         generateCallToReentrantMockMethod(mockedAccess);
      }
      else if (mockMethod.isDynamic()) {
         generateCallToMockMethod(mockedAccess);
         generateDecisionBetweenReturningOrContinuingToRealImplementation();
      }
      else if (startOfRealImplementation != null) {
         generateCallToMockMethod(mockedAccess);
         generateMethodReturn();
         mw.visitLabel(startOfRealImplementation);
      }

      startOfRealImplementation = null;
   }

   private void generateCallToUpdateMockStateIfAny(int mockedAccess)
   {
      int mockStateIndex = mockMethod.getIndexForMockState();

      if (mockStateIndex >= 0) {
         if (useMockingBridgeForUpdatingMockState) {
            generateCallToControlMethodThroughMockingBridge(true, mockedAccess);
            mw.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
            mw.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z");
         }
         else {
            mw.visitLdcInsn(mockMethods.getMockClassInternalName());
            mw.visitIntInsn(SIPUSH, mockStateIndex);
            mw.visitMethodInsn(
               INVOKESTATIC, "mockit/internal/state/TestRun", "updateMockState", "(Ljava/lang/String;I)Z");
         }
      }
   }

   private void generateCallToReentrantMockMethod(int mockedAccess)
   {
      if (startOfRealImplementation == null) {
         startOfRealImplementation = new Label();
      }

      mw.visitJumpInsn(IFEQ, startOfRealImplementation);
      generateCallToMockMethod(mockedAccess);
      generateMethodReturn();
      mw.visitLabel(startOfRealImplementation);
   }

   private void generateCallToControlMethodThroughMockingBridge(boolean enteringMethod, int mockAccess)
   {
      generateCodeToObtainInstanceOfMockingBridge(MockupBridge.MB);

      // First and second "invoke" arguments:
      generateCodeToPassThisOrNullIfStaticMethod(mockAccess);
      mw.visitInsn(ACONST_NULL);

      // Create array for call arguments (third "invoke" argument):
      generateCodeToCreateArrayOfObject(3);

      int i = 0;
      generateCodeToFillArrayElement(i++, enteringMethod);
      generateCodeToFillArrayElement(i++, mockMethods.getMockClassInternalName());
      generateCodeToFillArrayElement(i, mockMethod.getIndexForMockState());

      generateCallToInvocationHandler();
   }

   private void generateCallToMockMethod(int access)
   {
      if (mockMethod.isStatic) {
         generateStaticMethodCall(access);
      }
      else {
         generateInstanceMethodCall(access);
      }
   }

   private void generateStaticMethodCall(int access)
   {
      if (shouldUseMockingBridge()) {
         generateCallToMockMethodThroughMockingBridge(false, access);
      }
      else {
         generateMethodOrConstructorArguments(access);
         mw.visitMethodInsn(INVOKESTATIC, mockMethods.getMockClassInternalName(), mockMethod.name, mockMethod.desc);
      }
   }

   private boolean shouldUseMockingBridge() { return useMockingBridge || mockMethod.hasInvocationParameter; }

   private void generateCallToMockMethodThroughMockingBridge(boolean callingInstanceMethod, int access)
   {
      generateCodeToObtainInstanceOfMockingBridge(MockMethodBridge.MB);

      // First and second "invoke" arguments:
      boolean isStatic = generateCodeToPassThisOrNullIfStaticMethod(access);
      mw.visitInsn(ACONST_NULL);

      // Create array for call arguments (third "invoke" argument):
      Type[] argTypes = Type.getArgumentTypes(methodDesc);
      generateCodeToCreateArrayOfObject(7 + argTypes.length);

      int i = 0;
      generateCodeToFillArrayElement(i++, callingInstanceMethod);
      generateCodeToFillArrayElement(i++, mockMethods.getMockClassInternalName());
      generateCodeToFillArrayElement(i++, mockMethod.name);
      generateCodeToFillArrayElement(i++, mockMethod.desc);
      generateCodeToFillArrayElement(i++, mockMethod.getIndexForMockState());
      generateCodeToFillArrayElement(i++, mockInstanceIndex);
      generateCodeToFillArrayElement(i++, forStartupMock);

      generateCodeToPassMethodArgumentsAsVarargs(argTypes, i, isStatic ? 0 : 1);
      generateCallToInvocationHandler();
   }

   private void generateInstanceMethodCall(int access)
   {
      if (shouldUseMockingBridge()) {
         generateCallToMockMethodThroughMockingBridge(true, access);
         return;
      }

      generateGetMockCallWithMockInstanceIndex();
      generateMockInstanceMethodInvocationWithRealMethodArgs(access);
   }

   private void generateGetMockCallWithMockInstanceIndex()
   {
      mw.visitIntInsn(SIPUSH, mockInstanceIndex);
      String getterName = forStartupMock ? "getStartupMock" : "getMock";
      mw.visitMethodInsn(INVOKESTATIC, "mockit/internal/state/TestRun", getterName, "(I)Ljava/lang/Object;");
      mw.visitTypeInsn(CHECKCAST, mockMethods.getMockClassInternalName());
   }

   private void generateMockInstanceMethodInvocationWithRealMethodArgs(int access)
   {
      generateMethodOrConstructorArguments(access);
      mw.visitMethodInsn(INVOKEVIRTUAL, mockMethods.getMockClassInternalName(), mockMethod.name, mockMethod.desc);
   }

   private void generateMethodOrConstructorArguments(int access)
   {
      boolean hasInvokedInstance = (access & ACC_STATIC) == 0;
      int varIndex = hasInvokedInstance ? 1 : 0;

      Type[] argTypes = Type.getArgumentTypes(mockMethod.desc);
      boolean forGenericMethod = mockMethod.isForGenericMethod();

      for (Type argType : argTypes) {
         int opcode = argType.getOpcode(ILOAD);
         mw.visitVarInsn(opcode, varIndex);

         if (forGenericMethod && argType.getSort() >= Type.ARRAY) {
            mw.visitTypeInsn(CHECKCAST, argType.getInternalName());
         }

         varIndex += argType.getSize();
      }
   }

   private void generateMethodReturn()
   {
      if (shouldUseMockingBridge()) {
         generateReturnWithObjectAtTopOfTheStack(methodDesc);
      }
      else {
         Type returnType = Type.getReturnType(methodDesc);
         mw.visitInsn(returnType.getOpcode(IRETURN));
      }
   }

   boolean wasModified() { return classWasModified; }
}
