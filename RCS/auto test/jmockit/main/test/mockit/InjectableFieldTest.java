/*
 * Copyright (c) 2006-2013 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit;

import java.util.*;

import static org.junit.Assert.*;
import org.junit.*;

public final class InjectableFieldTest
{
   static class Base
   {
      protected int getValue() { return 1; }
   }

   static class Foo extends Base
   {
      void doSomething(String s) { throw new RuntimeException(s); }
      int getAnotherValue() { return 2; }
      private Boolean getBooleanValue() { return true; }
      final List<Integer> getList() { return null; }
   }

   @Injectable Foo foo;

   @Before
   public void recordCommonExpectations()
   {
      new NonStrictExpectations() {{
         foo.getValue(); result = 12;
         foo.getAnotherValue(); result = 123;
      }};

      assertEquals(123, foo.getAnotherValue());
      assertEquals(12, foo.getValue());
      assertEquals(1, new Base().getValue());
      assertEquals(2, new Foo().getAnotherValue());
   }

   @After
   public void verifyExpectedInvocation()
   {
      new Verifications() {{ foo.doSomething(anyString); times = 1; }};
   }

   @Test
   public void cascadeOneLevel()
   {
      try {
         new Foo().doSomething("");
         fail();
      }
      catch (RuntimeException ignore) {}

      new NonStrictExpectations() {{ foo.doSomething("test"); times = 1; }};

      assertEquals(123, foo.getAnotherValue());
      assertFalse(foo.getBooleanValue());
      assertTrue(foo.getList().isEmpty());

      foo.doSomething("test");
   }

   @Test
   public void overrideExpectationRecordedInBeforeMethod()
   {
      new NonStrictExpectations() {{ foo.getAnotherValue(); result = 45; }};

      assertEquals(45, foo.getAnotherValue());
      foo.doSomething("sdf");
   }
}
