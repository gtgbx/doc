/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.util;

import java.lang.reflect.*;
import java.util.*;
import java.util.Map.*;

import org.jetbrains.annotations.*;

public final class GenericTypeReflection
{
   @NotNull private final Map<String, Type> typeParametersToTypeArguments;
   @NotNull private final Map<String, String> typeParametersToTypeArgumentNames;

   public GenericTypeReflection()
   {
      typeParametersToTypeArguments = Collections.emptyMap();
      typeParametersToTypeArgumentNames = new HashMap<String, String>(4);
   }

   public GenericTypeReflection(@NotNull Class<?> realClass, @Nullable Type mockedType)
   {
      typeParametersToTypeArguments = new HashMap<String, Type>(4);
      typeParametersToTypeArgumentNames = new HashMap<String, String>(4);

      if (mockedType instanceof ParameterizedType) {
         addMappingsFromTypeParametersToTypeArguments(realClass, (ParameterizedType) mockedType);
      }

      addGenericTypeMappingsForSuperTypes(realClass);
   }

   public boolean hasTypeParameters() { return !typeParametersToTypeArgumentNames.isEmpty(); }

   private void addGenericTypeMappingsForSuperTypes(@NotNull Class<?> realClass)
   {
      Type superType = realClass;

      while (superType instanceof Class<?> && superType != Object.class) {
         Class<?> superClass = (Class<?>) superType;
         superType = superClass.getGenericSuperclass();

         if (superType != null) {
            superType = addGenericTypeMappingsIfParameterized(superType);
         }

         for (Type implementedInterface : superClass.getGenericInterfaces()) {
            addGenericTypeMappingsIfParameterized(implementedInterface);
         }
      }
   }

   private Type addGenericTypeMappingsIfParameterized(@NotNull Type superType)
   {
      if (superType instanceof ParameterizedType) {
         ParameterizedType mockedSuperType = (ParameterizedType) superType;
         Type rawType = mockedSuperType.getRawType();
         addMappingsFromTypeParametersToTypeArguments((Class<?>) rawType, mockedSuperType);
         return rawType;
      }

      return superType;
   }

   private void addMappingsFromTypeParametersToTypeArguments(
      @NotNull Class<?> mockedClass, @NotNull ParameterizedType mockedType)
   {
      TypeVariable<?>[] typeParameters = mockedClass.getTypeParameters();
      Type[] typeArguments = mockedType.getActualTypeArguments();
      int n = typeParameters.length;

      for (int i = 0; i < n; i++) {
         Type typeArg = typeArguments[i];
         String typeArgName = null;
         String typeVarName = typeParameters[i].getName();

         if (typeArg instanceof Class<?>) {
            typeArgName = 'L' + ((Class<?>) typeArg).getName().replace('.', '/');
         }
         else if (typeArg instanceof TypeVariable<?>) {
            String intermediateTypeArg = 'T' + ((TypeVariable<?>) typeArg).getName();
            typeArgName = typeParametersToTypeArgumentNames.get(intermediateTypeArg);
         }

         Type mappedTypeArg = typeArgName == null ? Object.class : typeArg;
         String mappedTypeArgName = typeArgName == null ? "Ljava/lang/Object" : typeArgName;
         addTypeMapping(typeVarName, mappedTypeArg, mappedTypeArgName);
      }
   }

   private void addTypeMapping(
      @NotNull String typeVarName, @NotNull Type mappedTypeArg, @NotNull String mappedTypeArgName)
   {
      typeParametersToTypeArguments.put(typeVarName, mappedTypeArg);
      addTypeMapping(typeVarName, mappedTypeArgName);
   }

   private void addTypeMapping(@NotNull String typeVarName, @NotNull String mappedTypeArgName)
   {
      typeParametersToTypeArgumentNames.put('T' + typeVarName, mappedTypeArgName);
   }

   public final class GenericSignature
   {
      private final List<String> parameters;
      private final String parameterTypeDescs;
      private final int lengthOfParameterTypeDescs;
      private int currentPos;

      GenericSignature(@NotNull String signature)
      {
         int p = signature.indexOf('(');
         int q = signature.lastIndexOf(')');
         parameterTypeDescs = signature.substring(p + 1, q);
         lengthOfParameterTypeDescs = parameterTypeDescs.length();
         parameters = new ArrayList<String>();
         addTypeDescsToList();
      }

      private void addTypeDescsToList()
      {
         while (currentPos < lengthOfParameterTypeDescs) {
            addNextParameter();
         }
      }

      private void addNextParameter()
      {
         int startPos = currentPos;
         int endPos;
         char c = parameterTypeDescs.charAt(startPos);

         if (c == 'T') {
            endPos = parameterTypeDescs.indexOf(';', startPos);
            currentPos = endPos;
         }
         else if (c == 'L') {
            endPos = advanceToEndOfTypeDesc();
         }
         else if (c == '[') {
            char elemTypeStart = firstCharacterOfArrayElementType();

            if (elemTypeStart == 'T') {
               endPos = parameterTypeDescs.indexOf(';', startPos);
               currentPos = endPos;
            }
            else if (elemTypeStart == 'L') {
               endPos = advanceToEndOfTypeDesc();
            }
            else {
               endPos = currentPos + 1;
            }
         }
         else {
            endPos = currentPos + 1;
         }

         currentPos++;
         String parameter = parameterTypeDescs.substring(startPos, endPos);
         parameters.add(parameter);
      }

      private int advanceToEndOfTypeDesc()
      {
         char c = '\0';

         do {
            currentPos++;
            if (currentPos == lengthOfParameterTypeDescs) break;
            c = parameterTypeDescs.charAt(currentPos);
         } while (c != ';' && c != '<');

         int endPos = currentPos;

         if (c == '<') {
            advancePastTypeArguments();
         }

         return endPos;
      }

      private char firstCharacterOfArrayElementType()
      {
         char c;

         do {
            currentPos++;
            c = parameterTypeDescs.charAt(currentPos);
         } while (c == '[');

         return c;
      }

      private void advancePastTypeArguments()
      {
         int angleBracketDepth = 1;

         do {
            currentPos++;
            char c = parameterTypeDescs.charAt(currentPos);
            if (c == '>') angleBracketDepth--; else if (c == '<') angleBracketDepth++;
         } while (angleBracketDepth > 0);
      }

      public boolean satisfiesGenericSignature(@NotNull String otherSignature)
      {
         GenericSignature other = new GenericSignature(otherSignature);
         int n = parameters.size();

         if (n != other.parameters.size()) {
            return false;
         }

         for (int i = 0; i < n; i++) {
            String p1 = other.parameters.get(i);
            String p2 = parameters.get(i);

            if (!areParametersOfSameType(p1, p2)) {
               return false;
            }
         }

         return true;
      }

      private boolean areParametersOfSameType(@NotNull String param1, @NotNull String param2)
      {
         if (param1.equals(param2)) return true;

         int i = -1;
         char c;
         do { i++; c = param1.charAt(i); } while (c == '[');
         if (c != 'T') return false;

         String typeArg1 = typeParametersToTypeArgumentNames.get(param1.substring(i));
         return param2.substring(i).equals(typeArg1);
      }
   }

   @NotNull public GenericSignature parseSignature(@NotNull String signature)
   {
      return new GenericSignature(signature);
   }

   @NotNull public String resolveReturnType(@NotNull String signature)
   {
      addTypeArgumentsIfAvailable(signature);

      int p = signature.lastIndexOf(')') + 1;
      int q = signature.length();
      String returnType = signature.substring(p, q);
      String resolvedReturnType = replaceTypeParametersWithActualTypes(returnType);

      StringBuilder finalSignature = new StringBuilder(signature);
      finalSignature.replace(p, q, resolvedReturnType);
      return finalSignature.toString();
   }

   private void addTypeArgumentsIfAvailable(String signature)
   {
      int firstParen = signature.indexOf('(');
      if (firstParen == 0) return;

      int p = 1;
      boolean lastMappingFound = false;

      while (!lastMappingFound) {
         int q = signature.indexOf(':', p);
         String typeVar = signature.substring(p, q);

         q++;

         if (signature.charAt(q) == ':') {
            q++; // an unbounded type argument uses ":" as separator, while a bounded one uses "::"
         }

         int r = signature.indexOf(':', q);

         if (r < 0) {
            r = firstParen - 2;
            lastMappingFound = true;
         }
         else {
            r = signature.lastIndexOf(';', r);
            p = r + 1;
         }

         String typeArg = signature.substring(q, r);
         addTypeMapping(typeVar, typeArg);
      }
   }

   @NotNull private String replaceTypeParametersWithActualTypes(@NotNull String typeDesc)
   {
      if (typeDesc.charAt(0) == 'T') {
         String typeParameter = typeDesc.substring(0, typeDesc.length() - 1);
         String typeArg = typeParametersToTypeArgumentNames.get(typeParameter);
         return typeArg == null ? typeDesc : typeArg + ';';
      }

      int p = typeDesc.indexOf('<');

      if (p < 0) {
         return typeDesc;
      }

      String resolvedTypeDesc = typeDesc;

      for (Entry<String, String> paramAndArg : typeParametersToTypeArgumentNames.entrySet()) {
         String typeParam = paramAndArg.getKey() + ';';
         String typeArg = paramAndArg.getValue() + ';';
         resolvedTypeDesc = resolvedTypeDesc.replace(typeParam, typeArg);
      }

      return resolvedTypeDesc;
   }

   @NotNull public Type resolveReturnType(@NotNull TypeVariable<?> genericReturnType)
   {
      Type typeArgument = typeParametersToTypeArguments.get(genericReturnType.getName());

      if (typeArgument == null) {
         typeArgument = genericReturnType.getBounds()[0];
      }

      return typeArgument;
   }
}
