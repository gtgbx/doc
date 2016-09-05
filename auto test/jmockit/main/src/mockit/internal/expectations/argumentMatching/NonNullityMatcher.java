/*
 * Copyright (c) 2006-2013 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.expectations.argumentMatching;

import org.jetbrains.annotations.*;

public final class NonNullityMatcher implements ArgumentMatcher
{
   public static final ArgumentMatcher INSTANCE = new NonNullityMatcher();

   private NonNullityMatcher() {}
   public boolean matches(@Nullable Object argValue) { return argValue != null; }
   public void writeMismatchPhrase(@NotNull ArgumentMismatch argumentMismatch) { argumentMismatch.append("not null"); }
}

