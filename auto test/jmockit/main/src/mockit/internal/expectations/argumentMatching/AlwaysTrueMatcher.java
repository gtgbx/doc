/*
 * Copyright (c) 2006-2013 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.expectations.argumentMatching;

import org.jetbrains.annotations.*;

public final class AlwaysTrueMatcher implements ArgumentMatcher
{
   public static final ArgumentMatcher INSTANCE = new AlwaysTrueMatcher();

   private AlwaysTrueMatcher() {}
   public boolean matches(@Nullable Object argValue) { return true; }

   public void writeMismatchPhrase(@NotNull ArgumentMismatch argumentMismatch)
   {
      argumentMismatch.append("any ").append(argumentMismatch.getParameterType());
   }
}
