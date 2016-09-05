/*
 * Copyright (c) 2006-2012 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit;

import java.util.*;

import static org.junit.Assert.*;
import org.junit.*;

public final class TestedClassWithConstructorDI1Test
{
   public static final class TestedClass
   {
      private final Dependency dependency;
      private final Runnable runnable;
      private final Observable observable;

      public TestedClass(Dependency dependency) { this(dependency, null, null); }
      public TestedClass(Dependency dependency, Runnable runnable) { this(dependency, runnable, null); }
      public TestedClass(Dependency dependency, Observable observable) { this(dependency, null, observable); }
      public TestedClass(Dependency dependency, Runnable runnable, Observable observable)
      {
         this.dependency = dependency;
         this.runnable = runnable;
         this.observable = observable;
      }

      public boolean doSomeOperation()
      {
         if (runnable != null) {
            runnable.run();
         }

         boolean b = dependency.doSomething() > 0;

         if (b && observable != null) {
            observable.notifyObservers();
         }

         return b;
      }
   }

   static class Dependency
   {
      int doSomething() { return -1; }
   }

   @Tested TestedClass tested;
   @Injectable Dependency mock;

   @Test
   public void exerciseTestedObjectWithSingleDependencyInjectedThroughConstructor()
   {
      new Expectations() {{ mock.doSomething(); result = 23; }};

      assertTrue(tested.doSomeOperation());
   }

   @Test
   public void exerciseTestedObjectWithTwoDependenciesInjectedThroughConstructor(@Injectable final Runnable mock2)
   {
      new Expectations() {{ mock.doSomething(); result = 23; }};

      assertTrue(tested.doSomeOperation());

      new Verifications() {{ mock2.run(); }};
   }

   @Test
   public void exerciseTestedObjectWithTwoOtherDependenciesInjectedThroughConstructor(@Injectable final Observable obs)
   {
      new Expectations() {{ mock.doSomething(); result = 123; }};

      assertTrue(tested.doSomeOperation());

      new FullVerifications() {{ obs.notifyObservers(); }};
   }

   @Test
   public void exerciseTestedObjectWithAllDependenciesInjectedThroughConstructor(
      @Injectable final Runnable mock2, @Injectable final Observable mock3)
   {
      new Expectations() {{ mock.doSomething(); result = 123; }};

      assertTrue(tested.doSomeOperation());

      new FullVerificationsInOrder() {{
         mock2.run();
         mock3.notifyObservers();
      }};
   }
}
