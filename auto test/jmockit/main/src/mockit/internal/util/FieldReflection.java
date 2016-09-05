/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.util;

import java.lang.reflect.*;
import static java.lang.reflect.Modifier.*;

import org.jetbrains.annotations.*;

public final class FieldReflection
{
   @Nullable
   public static <T> T getField(@NotNull Class<?> theClass, @NotNull String fieldName, @Nullable Object targetObject)
   {
      Field field = getDeclaredField(theClass, fieldName, targetObject != null);
      return getFieldValue(field, targetObject);
   }

   @NotNull
   private static Field getDeclaredField(@NotNull Class<?> theClass, @NotNull String fieldName, boolean instanceField)
   {
      try {
         return theClass.getDeclaredField(fieldName);
      }
      catch (NoSuchFieldException ignore) {
         Class<?> superClass = theClass.getSuperclass();

         if (superClass != null && superClass != Object.class) {
            return getDeclaredField(superClass, fieldName, instanceField);
         }

         String kind = instanceField ? "instance" : "static";
         throw new IllegalArgumentException("No " + kind + " field of name \"" + fieldName + "\" found in " + theClass);
      }
   }

   @Nullable
   public static <T> T getFieldValue(@NotNull Field field, @Nullable Object targetObject)
   {
      Utilities.ensureThatMemberIsAccessible(field);

      try {
         //noinspection unchecked
         return (T) field.get(targetObject);
      }
      catch (IllegalAccessException e) {
         throw new RuntimeException(e);
      }
   }

   @Nullable
   public static <T> T getField(@NotNull Class<?> theClass, @NotNull Class<T> fieldType, @Nullable Object targetObject)
   {
      Field field = getDeclaredField(theClass, fieldType, targetObject != null, false);
      return getFieldValue(field, targetObject);
   }

   @Nullable
   public static <T> T getField(@NotNull Class<?> theClass, @NotNull Type fieldType, @Nullable Object targetObject)
   {
      Field field = getDeclaredField(theClass, fieldType, targetObject != null, false);
      return getFieldValue(field, targetObject);
   }

   @NotNull
   public static Field setField(
      @NotNull Class<?> theClass, @Nullable Object targetObject, @Nullable String fieldName,
      @Nullable Object fieldValue)
   {
      boolean instanceField = targetObject != null;
      Field field;

      if (fieldName != null) {
         field = getDeclaredField(theClass, fieldName, instanceField);
      }
      else if (fieldValue != null) {
         field = getDeclaredField(theClass, fieldValue.getClass(), instanceField, true);
      }
      else {
         throw new IllegalArgumentException("Missing field value when setting field by type");
      }

      setFieldValue(field, targetObject, fieldValue);
      return field;
   }

   @NotNull
   private static Field getDeclaredField(
      @NotNull Class<?> theClass, @NotNull Type desiredType, boolean instanceField, boolean forAssignment)
   {
      Field found = getDeclaredFieldInSingleClass(theClass, desiredType, instanceField, forAssignment);

      if (found == null) {
         Class<?> superClass = theClass.getSuperclass();

         if (superClass != null && superClass != Object.class) {
            return getDeclaredField(superClass, desiredType, instanceField, forAssignment);
         }

         StringBuilder errorMsg = new StringBuilder(instanceField ? "Instance" : "Static");
         String typeName = getTypeName(desiredType);
         errorMsg.append(" field of type ").append(typeName).append(" not found in ").append(theClass);
         throw new IllegalArgumentException(errorMsg.toString());
      }

      return found;
   }

   @Nullable
   private static Field getDeclaredFieldInSingleClass(
      @NotNull Class<?> theClass, @NotNull Type desiredType, boolean instanceField, boolean forAssignment)
   {
      Field found = null;

      for (Field field : theClass.getDeclaredFields()) {
         if (!field.isSynthetic()) {
            Type fieldType = field.getGenericType();

            if (
               instanceField != isStatic(field.getModifiers()) &&
               isCompatibleFieldType(fieldType, desiredType, forAssignment)
            ) {
               if (found != null) {
                  String message =
                     errorMessageForMoreThanOneFieldFound(desiredType, instanceField, forAssignment, found, field);
                  throw new IllegalArgumentException(message);
               }

               found = field;
            }
         }
      }

      return found;
   }

   private static boolean isCompatibleFieldType(
      @NotNull Type fieldType, @NotNull Type desiredType, boolean forAssignment)
   {
      Class<?> fieldClass = Utilities.getClassType(fieldType);
      Class<?> desiredClass = Utilities.getClassType(desiredType);

      if (ParameterReflection.isSameTypeIgnoringAutoBoxing(desiredClass, fieldClass)) {
         return true;
      }
      else if (forAssignment) {
         return fieldClass.isAssignableFrom(desiredClass);
      }

      return desiredClass.isAssignableFrom(fieldClass) || fieldClass.isAssignableFrom(desiredClass);
   }

   private static String errorMessageForMoreThanOneFieldFound(
      @NotNull Type desiredFieldType, boolean instanceField, boolean forAssignment,
      @NotNull Field firstField, @NotNull Field secondField)
   {
      StringBuilder message = new StringBuilder("More than one ");
      message.append(instanceField ? "instance" : "static").append(" field ");

      message.append(forAssignment ? "to" : "from");
      message.append(" which a value of type ");
      message.append(getTypeName(desiredFieldType));
      message.append(forAssignment ? " can be assigned" : " can be read");

      message.append(" exists in ").append(secondField.getDeclaringClass()).append(": ");
      message.append(firstField.getName()).append(", ").append(secondField.getName());

      return message.toString();
   }

   @NotNull private static String getTypeName(@NotNull Type type)
   {
      Class<?> classType = Utilities.getClassType(type);
      Class<?> primitiveType = AutoBoxing.getPrimitiveType(classType);

      if (primitiveType != null) {
         return primitiveType + " or " + classType.getSimpleName();
      }

      String name = classType.getName();
      return name.startsWith("java.lang.") ? name.substring(10) : name;
   }

   public static void setFieldValue(@NotNull Field field, @Nullable Object targetObject, @Nullable Object value)
   {
      try {
         if (isStatic(field.getModifiers()) && isFinal(field.getModifiers())) {
            setStaticFinalField(field, value);
         }
         else {
            Utilities.ensureThatMemberIsAccessible(field);
            field.set(targetObject, value);
         }
      }
      catch (IllegalAccessException e) {
         throw new RuntimeException(e);
      }
   }

   private static void setStaticFinalField(@NotNull Field field, @Nullable Object value) throws IllegalAccessException
   {
      Field modifiersField;

      try {
         modifiersField = Field.class.getDeclaredField("modifiers");
      }
      catch (NoSuchFieldException e) {
         throw new RuntimeException(e);
      }

      modifiersField.setAccessible(true);
      int nonFinalModifiers = modifiersField.getInt(field) - FINAL;
      modifiersField.setInt(field, nonFinalModifiers);

      //noinspection UnnecessaryFullyQualifiedName,UseOfSunClasses
      sun.reflect.FieldAccessor accessor =
         sun.reflect.ReflectionFactory.getReflectionFactory().newFieldAccessor(field, false);
      accessor.set(null, value);
   }
}
