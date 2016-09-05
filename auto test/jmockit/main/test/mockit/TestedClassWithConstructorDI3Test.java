/*
 * Copyright (c) 2006-2012 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit;

import static org.junit.Assert.*;
import org.junit.*;

public final class TestedClassWithConstructorDI3Test
{
   public static final class TestedClass
   {
      private final Dependency[] dependencies;

      public TestedClass(Runnable r, Dependency... dependencies)
      {
         this.dependencies = dependencies;
         r.run();
      }

      public int doSomeOperation()
      {
         int sum = 0;

         for (Dependency dependency : dependencies) {
            sum += dependency.doSomething();
         }

         return sum;
      }
   }

   static class Dependency
   {
      int doSomething() { return -1; }
   }

   @Tested TestedClass tested;
   @Injectable Dependency mock1;
   @Injectable Runnable task;
   @Injectable Dependency mock2;

   @Test
   public void exerciseTestedObjectWithDependenciesOfSameTypeInjectedThroughVarargsConstructorParameter()
   {
      new Expectations() {{
         mock1.doSomething(); result = 23;
         mock2.doSomething(); result = 5;
      }};

      assertEquals(28, tested.doSomeOperation());
   }

   @Test
   public void exerciseTestedObjectWithDependenciesProvidedByMockFieldsAndMockParameter(
      @Injectable final Dependency mock3)
   {
      new Expectations() {{
         mock1.doSomething(); result = 2;
         mock2.doSomething(); result = 3;
         mock3.doSomething(); result = 5;
      }};

      assertEquals(10, tested.doSomeOperation());
   }
}
