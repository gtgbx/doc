/*
 * Copyright (c) 2006-2012 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.integration;

public final class TestedClass
{
   private final MockedClass dependency;

   public TestedClass(MockedClass dependency) { this.dependency = dependency; }
   public boolean doSomething(int i) { return dependency.doSomething(i); }
}
