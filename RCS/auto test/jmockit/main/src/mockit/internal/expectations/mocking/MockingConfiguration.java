/*
 * Copyright (c) 2006-2013 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.expectations.mocking;

import java.util.*;
import java.util.regex.*;

import org.jetbrains.annotations.*;

import mockit.external.asm4.*;

final class MockingConfiguration
{
   @NotNull private final List<RegexMockFilter> filtersToApply;

   MockingConfiguration(@NotNull String[] filters)
   {
      filtersToApply = parseMockFilters(filters);
   }

   @NotNull private List<RegexMockFilter> parseMockFilters(@NotNull String[] mockFilters)
   {
      List<RegexMockFilter> filters = new ArrayList<RegexMockFilter>(mockFilters.length);

      for (String mockFilter : mockFilters) {
         filters.add(new RegexMockFilter(mockFilter));
      }

      return filters;
   }

   boolean matchesFilters(@NotNull String name, @NotNull String desc)
   {
      for (RegexMockFilter filter : filtersToApply) {
         if (filter.matches(name, desc)) {
            return true;
         }
      }

      return false;
   }

   private static final class RegexMockFilter
   {
      private static final Pattern CONSTRUCTOR_NAME_REGEX = Pattern.compile("<init>");
      private static final String[] ANY_PARAMS = {};

      @NotNull private final Pattern nameRegex;
      @Nullable private final String[] paramTypeNames;

      private RegexMockFilter(@NotNull String filter)
      {
         int lp = filter.indexOf('(');
         int rp = filter.indexOf(')');

         if (lp < 0 && rp >= 0 || lp >= 0 && lp >= rp) {
            throw new IllegalArgumentException("Invalid filter: " + filter);
         }

         if (lp == 0) {
            nameRegex = CONSTRUCTOR_NAME_REGEX;
         }
         else {
            nameRegex = Pattern.compile(lp < 0 ? filter : filter.substring(0, lp));
         }

         paramTypeNames = parseParameterTypes(filter, lp, rp);
      }

      @Nullable private String[] parseParameterTypes(@NotNull String filter, int lp, int rp)
      {
         if (lp < 0) {
            return ANY_PARAMS;
         }
         else if (lp == rp - 1) {
            return null;
         }

         String[] typeNames = filter.substring(lp + 1, rp).split(",");

         for (int i = 0; i < typeNames.length; i++) {
            typeNames[i] = typeNames[i].trim();
         }

         return typeNames;
      }

      boolean matches(@NotNull String name, @NotNull String desc)
      {
         if (!nameRegex.matcher(name).matches()) {
            return false;
         }

         if (paramTypeNames == ANY_PARAMS) {
            return true;
         }
         else if (paramTypeNames == null) {
            return desc.charAt(1) == ')';
         }

         Type[] argTypes = Type.getArgumentTypes(desc);

         if (argTypes.length != paramTypeNames.length) {
            return false;
         }

         for (int i = 0; i < paramTypeNames.length; i++) {
            Type argType = argTypes[i];
            String paramTypeName = argType.getClassName();
            assert paramTypeName != null;

            if (!paramTypeName.endsWith(paramTypeNames[i])) {
               return false;
            }
         }

         return true;
      }
   }
}
