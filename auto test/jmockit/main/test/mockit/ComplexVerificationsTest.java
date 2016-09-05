/*
 * Copyright (c) 2006-2013 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit;

import static org.junit.Assert.*;
import org.junit.*;

public class ComplexVerificationsTest
{
   public static class A
   {
      @SuppressWarnings("UnusedParameters")
      public void process(Object[] input) {}
      public int result() { return 1; }
   }

   public static class B { public int foo() { return -2; } }
   public static class C { public int bar() { return 3; } }

   final Object[] input = new Object[3];

   int testedMethod()
   {
      // Requirement 1: instantiations occur (a) first, (b) once per class.
      A a = new A();
      B b = new B();
      C c = new C();

      // Requirement 2: a.process is (a) called first, (b) only once.
      a.process(input);

      // Requirement 3: b.foo and c.bar are called (a) between the calls to A, and (b) input.length times each.
      //noinspection UnusedDeclaration
      for (Object in : input) {
         // Requirement 4: b.foo and c.bar are called in any order relative to each other.
         b.foo();
         c.bar();
      }

      // Requirement 5: a.result is (a) called last, (b) only once.
      return a.result();

      // Requirement 6: no other invocations occur on (a) A, (b) B, or (c) C.
   }

   @Test
   public void usingStrictExpectationsOnly(@Mocked final A a, @Mocked final B b, @Mocked final C c)
   {
      new Expectations() {{
         // Meets requirement 1:
         new A();
         new B();
         new C();

         // Meets requirement 2:
         a.process(input);
      }};

      new Expectations(input.length) {{
         // Meets requirement 3 but NOT 4:
         b.foo();
         c.bar();
      }};

      new Expectations() {{
         // Meets requirement 5:
         a.result(); result = 42;
      }};

      assertEquals(42, testedMethod());

      // Requirement 6 is met since all classes are mocked strictly.
   }

   @Test
   public void usingStrictAndNonStrictMockedTypes(@Mocked final A a, @Mocked final B b, @Mocked final C c)
   {
      new Expectations() {{
         // Meets requirements 1 and 2.
         new A();
         a.process(input);

         // Meets requirement 5.
         a.result(); result = 42;
      }};

      new NonStrictExpectations() {{
         // Meets requirements 1 and 2.
         new B(); times = 1;
         new C(); times = 1;

         // Meets requirement 3b and 4, but NOT 3a.
         b.foo(); times = input.length;
         c.bar(); times = input.length;
      }};

      assertEquals(42, testedMethod());

      // Meets requirement 6.
      new FullVerifications() {};
   }

   @Test
   public void usingNonStrictExpectationsOnly(@Mocked final A a, @Mocked final B b, @Mocked final C c)
   {
      new NonStrictExpectations() {{
         // Meets requirements 1b and 5b.
         new A(); times = 1;
         new B(); times = 1;
         new C(); times = 1;
         a.result(); result = 42; times = 1;
      }};

      assertEquals(42, testedMethod());

      new VerificationsInOrder() {{
         // Meets requirements 1a, 2, 3a, and 5a.
         unverifiedInvocations(); // accounts for the instantiations of A, B, and C
         a.process(input); times = 1;
         unverifiedInvocations(); // accounts for the calls to "foo" and "bar"
         a.result();
      }};

      new FullVerifications(a) {{
         // Meets requirement 6a.
         a.process(input);
         a.result();
      }};

      new FullVerifications(input.length, b, c) {{
         // Meets requirements 3b, 4, 6b and 6c.
         b.foo();
         c.bar();
      }};
   }

   @Test
   public void usingNonStrictExpectationsOnlyWithoutDuplicateInvocations(
      @Mocked final A a, @Mocked final B b, @Mocked final C c)
   {
      new NonStrictExpectations() {{
         // Meets requirements 1b and 5b.
         new A(); times = 1;
         new B(); times = 1;
         new C(); times = 1;
         a.result(); result = 42; times = 1;
      }};

      assertEquals(42, testedMethod());

      new VerificationsInOrder() {{
         // Meets requirements 1a, 2, 3a, and 5a.
         unverifiedInvocations(); // accounts for the instantiations of A, B, and C
         a.process(input); times = 1;
         unverifiedInvocations(); // accounts for the calls to "foo" and "bar"
         a.result(); // this duplication is inevitable with non-strict expectations, so it's ok
      }};

      new FullVerifications(input.length) {{
         // Meets requirements 3b, 4, and 6.
         b.foo();
         c.bar();
      }};
   }

   @Test
   public void testFewerRequirementsUsingNonStrictExpectationsOnly(
      @Mocked final A a, @Mocked final B b, @Mocked final C c)
   {
      // Requirements to meet: only 1b, 3b, 4, 6b and 6c.

      new NonStrictExpectations() {{
         // Meets requirement 1b.
         new A(); times = 1;
         new B(); times = 1;
         new C(); times = 1;
         a.result(); result = 42;

         // Meets requirements 3b and 4.
         b.foo(); times = input.length;
         c.bar(); times = input.length;
      }};

      assertEquals(42, testedMethod());

      // Meets requirements 6b and 6c.
      new FullVerifications(b, c) {};
   }
}
