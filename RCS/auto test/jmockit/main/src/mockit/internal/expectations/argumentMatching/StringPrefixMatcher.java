/*
 * Copyright (c) 2006-2013 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.expectations.argumentMatching;

import org.jetbrains.annotations.*;

public final class StringPrefixMatcher extends SubstringMatcher
{
   public StringPrefixMatcher(@NotNull CharSequence substring) { super(substring); }

   public boolean matches(@Nullable Object string)
   {
      return string instanceof CharSequence && string.toString().startsWith(substring);
   }

   public void writeMismatchPhrase(@NotNull ArgumentMismatch argumentMismatch)
   {
      argumentMismatch.append("a string starting with ").appendFormatted(substring);
   }
}