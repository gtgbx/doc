/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit;

import java.security.cert.Certificate;
import java.util.*;

import org.hamcrest.*;
import org.hamcrest.core.*;
import static org.junit.Assert.*;
import org.junit.*;
import org.junit.rules.*;

import mockit.internal.*;

public final class ExpectationsWithArgMatchersTest
{
   @Rule public final ExpectedException thrown = ExpectedException.none();

   @SuppressWarnings("unused")
   static class Collaborator
   {
      private void setValue(int value) {}
      void setValue(double value) {}
      void setValue(float value) {}
      void setValue(String value) {}
      void setValues(char c, boolean b) {}
      void setValues(String[] values) {}
      private void doSomething(Integer i) {}
      boolean doSomething(String s) { return false; }

      List<?> complexOperation(Object input1, Object... otherInputs)
      {
         return input1 == null ? Collections.emptyList() : Arrays.asList(otherInputs);
      }

      final void simpleOperation(int a, String b, Date c) {}

      void setValue(Certificate cert) {}
      void setValue(Exception ex) {}
   }

   @Mocked Collaborator mock;

   @Test
   public void replayWithUnexpectedMethodArgument()
   {
      thrown.expect(UnexpectedInvocation.class);
      thrown.expectMessage(" expected \"test\", got \"other\"");

      new Expectations() {{ mock.simpleOperation(2, "test", null); }};

      mock.simpleOperation(2, "other", null);
   }

   @Test
   public void replayWithMultipleUnexpectedMethodArguments()
   {
      thrown.expect(UnexpectedInvocation.class);
      thrown.expectMessage(" expected \"a\", got \"b\"");

      new Expectations() {{ mock.setValues('a', true); }};

      mock.setValues('b', false);
   }

   @Test
   public void replayWithUnexpectedSecondMethodArgument()
   {
      thrown.expect(UnexpectedInvocation.class);
      thrown.expectMessage(" expected true, got false");

      new Expectations() {{ mock.setValues('a', true); }};

      mock.setValues('a', false);
   }

   @Test
   public void replayWithUnexpectedNullArgument()
   {
      thrown.expect(UnexpectedInvocation.class);
      thrown.expectMessage("expected \"test\", got null");

      new Expectations() {{ mock.simpleOperation(2, "test", null); }};

      mock.simpleOperation(2, null, null);
   }

   @Test
   public void replayWithUnexpectedMethodArgumentUsingMatcher()
   {
      thrown.expect(UnexpectedInvocation.class);
      thrown.expectMessage("expected -1, got 1");

      new Expectations() {{ mock.setValue(withEqual(-1)); }};

      mock.setValue(1);
   }

   @Test
   public void expectInvocationWithDifferentThanExpectedProxyArgument(@Mocked final Runnable mock2)
   {
      thrown.expect(UnexpectedInvocation.class);
      thrown.expectMessage("got null");

      new Expectations() {{ mock.complexOperation(mock2); }};

      mock.complexOperation(null);
   }

   @Test
   public void expectInvocationWithAnyArgumentUsingField()
   {
      new Expectations() {{ mock.setValue(anyInt); }};

      mock.setValue(3);
   }

   @Test
   public void expectInvocationToPrivateInstanceMethodUsingAnyFieldMatcher()
   {
      new Expectations() {{ mock.doSomething(anyInt); }};

      mock.doSomething(3);
   }

   @Test
   public void expectInvocationWithAnyArgumentUsingMethod()
   {
      new Expectations() {{ mock.setValue(withAny(1)); }};

      mock.setValue(3);
   }

   @Test
   public void expectInvocationWithEqualArgument()
   {
      new Expectations() {{ mock.setValue(withEqual(3)); }};

      mock.setValue(3);
   }

   @Test
   public void expectInvocationWithEqualArrayArgument()
   {
      new Expectations() {{ mock.setValues(withEqual(new String[] {"A", "bb", "cee"})); }};

      mock.setValues(new String[] {"A", "bb", "cee"});
   }

   @Test
   public void expectInvocationWithEqualDoubleArgument()
   {
      new Expectations() {{ mock.setValue(withEqual(3.0, 0.01)); times = 3; }};

      mock.setValue(3.0);
      mock.setValue(3.01);
      mock.setValue(2.99);
   }

   @Test
   public void expectInvocationWithEqualFloatArgument()
   {
      new Expectations() {{ mock.setValue(withEqual(3.0F, 0.01)); times = 3; }};

      mock.setValue(3.0F);
      mock.setValue(3.01F);
      mock.setValue(2.99F);
   }

   @Test
   public void expectInvocationWithEqualFloatArgumentButWithDifferentReplayValue()
   {
      thrown.expect(UnexpectedInvocation.class);
      thrown.expectMessage(" within 0.01 of 3.0, got 3.02F");

      new Expectations() {{ mock.setValue(withEqual(3.0F, 0.01)); }};

      mock.setValue(3.02F);
   }

   @Test
   public void expectInvocationWithNotEqualArgument()
   {
      new Expectations() {{ mock.setValue(withNotEqual(3)); }};

      mock.setValue(4);
   }

   @Test
   public void expectInvocationWithInstanceOfClassFromGivenObject()
   {
      new Expectations() {{
         mock.complexOperation("string");
         mock.complexOperation(withInstanceLike("string"));
      }};

      mock.complexOperation("string");
      mock.complexOperation("another string");
   }

   @Test
   public void expectInvocationWithInstanceOfGivenClass()
   {
      new Expectations() {{ mock.complexOperation(withInstanceOf(long.class)); }};

      mock.complexOperation(5L);
   }

   @Test
   public void expectInvocationWithNullArgument()
   {
      new Expectations() {{ mock.complexOperation(withNull()); }};

      mock.complexOperation(null);
   }

   @Test
   public void expectInvocationWithNotNullArgument()
   {
      new Expectations() {{ mock.complexOperation(withNotNull()); }};

      mock.complexOperation(true);
   }

   @Test
   public void expectInvocationWithSameInstance()
   {
      new Expectations() {{ mock.complexOperation(withSameInstance(45L)); }};

      mock.complexOperation(45L);
   }

   @Test
   public void expectInvocationWithSameMockInstanceButReplayWithNull(
      // This class defines an abstract "toString" override, which initially was erroneously
      // mocked, causing a non-strict expectation to be created during replay:
      @Mocked final Certificate cert)
   {
      thrown.expect(MissingInvocation.class);

      new NonStrictExpectations() {{
         mock.setValue(withSameInstance(cert)); times = 1;
      }};

      mock.setValue((Certificate) null);
   }

   @Test
   public void expectNonStrictInvocationWithMatcherWhichInvokesMockedMethod()
   {
      thrown.expect(MissingInvocation.class);

      new NonStrictExpectations() {{
         mock.setValue(with(0, new Object() {
            @Mock boolean validateAsPositive(int value)
            {
               // Invoking mocked method caused ConcurrentModificationException (bug fixed):
               mock.simpleOperation(1, "b", null);
               return value > 0;
            }
         }));
         minTimes = 1;
      }};

      mock.setValue(-3);
   }

   @Test
   public void expectStrictInvocationWithCustomMatcherButNeverReplay()
   {
      thrown.expect(MissingInvocation.class);

      new Expectations() {{
         mock.doSomething(with(new Delegate<Integer>() {
            @Mock boolean test(Integer i) { return true; }
         }));
      }};
   }

   @Test
   public void expectInvocationWithSubstring()
   {
      new Expectations() {{ mock.complexOperation(withSubstring("sub")); }};

      mock.complexOperation("abcsub\r\n123");
   }

   @Test
   public void expectInvocationWithPrefix()
   {
      new Expectations() {{ mock.complexOperation(withPrefix("abc")); }};

      mock.complexOperation("abc\tsub\"123\"");
   }

   @Test
   public void expectInvocationWithSuffix()
   {
      new Expectations() {{ mock.complexOperation(withSuffix("123")); }};

      mock.complexOperation("abcsub123");
   }

   @Test
   public void expectInvocationWithMatchForRegex()
   {
      new Expectations() {{
         mock.complexOperation(withMatch("[a-z]+[0-9]*"));
         mock.complexOperation(withMatch("(?i)[a-z]+sub[0-9]*"));
      }};

      mock.complexOperation("abcsub123");
      mock.complexOperation("abcSuB123");
   }

   @Test
   public void expectInvocationWithMatchForRegexButWithNonMatchingArgument()
   {
      thrown.expect(UnexpectedInvocation.class);

      new Expectations() {{ mock.complexOperation(withMatch("test")); }};

      mock.complexOperation("otherValue");
   }

   @Test
   public void expectInvocationWithUserProvidedMatcher()
   {
      new Expectations() {{ mock.setValue(with(1, new IsEqual<Integer>(3))); }};

      mock.setValue(3);
   }

   @Test
   public void expectInvocationWithUserImplementedMatcherUsingHamcrestAPI()
   {
      new Expectations() {{
         mock.complexOperation(with(new BaseMatcher<Integer>() {
            @Override
            public boolean matches(Object item)
            {
               Integer value = (Integer) item;
               return value >= 10 && value <= 100;
            }

            @Override
            public void describeTo(Description description)
            {
               description.appendText("between 10 and 100");
            }
         }));
      }};

      mock.complexOperation(28);
   }

   @Test
   public void expectInvocationsWithUserImplementedReflectionBasedMatchers()
   {
      new Expectations() {{
         mock.setValue(with(0, new Object() {
            @Mock boolean matches(int value)
            {
               return value >= 10 && value <= 100;
            }
         }));

         mock.setValue(with(0.0, new Object() {
            @Mock void validate(double value)
            {
               assertTrue("value outside of 20-80 range", value >= 20.0 && value <= 80.0);
            }
         }));
      }};

      mock.setValue(28);
      mock.setValue(20.0);
   }

   @Test
   public void expectInvocationsWithUserImplementedReflectionBasedMatcherUsingTypedDelegate()
   {
      new Expectations() {{
         mock.setValue(with(new Delegate<String>() {
            @Mock boolean validLength(String value)
            {
               return value.length() >= 10 && value.length() <= 100;
            }
         }));

         mock.setValue(with(new Delegate<Float>() {
            @Mock boolean positive(float value) { return value > 0.0F; }
         }));
      }};

      mock.setValue("Test 123 abc");
      mock.setValue(1.5F);
   }

   @Test
   public void expectInvocationWithMatcherContainingAnotherMatcher()
   {
      new Expectations() {{ mock.setValue((Integer) with(IsEqual.equalTo(3))); }};

      mock.setValue(3);
   }

   class ReusableMatcher implements Delegate<Integer> {
      @Mock final boolean isPositive(int i) { return i > 0; }
   }

   @Test
   public void extendingAReusableArgumentMatcher()
   {
      mock.setValue(5);
      mock.setValue(123);

      new Verifications() {{
         mock.setValue(with(new ReusableMatcher() {}));
         times = 2;
      }};
   }

   @Test
   public void useMockedMethodBeforeRecordingExpectationWithArgumentMatcher()
   {
      assertFalse(mock.doSomething("abc"));

      new NonStrictExpectations() {{
         mock.doSomething(anyString);
         result = true;
      }};

      assertTrue(mock.doSomething("xyz"));
      assertTrue(mock.doSomething("abc"));
   }

   @Test
   public void replayWithDifferentArgumentOfClassLackingEqualsMethod()
   {
      thrown.expect(UnexpectedInvocation.class);
      thrown.expectMessage("argument class java.lang.RuntimeException has no \"equals\" method");

      new Expectations() {{
         mock.setValue(new RuntimeException("Recorded"));
      }};

      mock.setValue(new RuntimeException("Replayed"));
   }
}
