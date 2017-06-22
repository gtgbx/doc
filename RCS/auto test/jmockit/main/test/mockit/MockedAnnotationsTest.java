/*
 * Copyright (c) 2006-2013 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit;

import org.junit.*;

import static org.junit.Assert.*;

public final class MockedAnnotationsTest
{
   public @interface MyAnnotation
   {
      String value();
      boolean flag() default true;
      String[] values() default {};
   }

   @Test
   public void specifyValuesForAnnotationAttributes(@Mocked final MyAnnotation a)
   {
      assertSame(MyAnnotation.class, a.annotationType());

      new Expectations() {{
         a.flag(); result = false;
         a.value(); result = "test";
         a.values(); returns("abc", "dEf");
      }};

      assertFalse(a.flag());
      assertEquals("test", a.value());
      assertArrayEquals(new String[] {"abc", "dEf"}, a.values());
   }

   @Test
   public void verifyUsesOfAnnotationAttributes(@Mocked final MyAnnotation a)
   {
      new NonStrictExpectations() {{
         a.value(); result = "test";
         a.values(); returns("abc", "dEf");
      }};

      // Same rule for regular methods applies (ie, if no return value was recorded, invocations
      // will get the default for the return type).
      assertFalse(a.flag());

      assertEquals("test", a.value());
      assertArrayEquals(new String[] {"abc", "dEf"}, a.values());
      a.value();

      new FullVerifications() {{
         // Mocked methods called here always return the default value according to return type.
         a.flag();
         a.value(); times = 2;
         a.values();
      }};
   }

   @Test
   public void mockASingleAnnotationAttribute(@Mocked("value") final MyAnnotation a)
   {
      new Expectations() {{
         a.value(); result = "test";
      }};

      assertFalse(a.flag());
      assertEquals("test", a.value());
      assertEquals(0, a.values().length);
   }
}