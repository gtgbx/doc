/*
 * Copyright (c) 2006-2013 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package unitils.wiki;

import org.junit.*;

import mockit.*;

import static org.junit.Assert.*;

public final class MockChaining_JMockit_Test
{
   @Tested MyService myService;

   @Test
   public void withoutChaining(@Mocked final User user, @Mocked final UserService userService)
   {
      new NonStrictExpectations() {{
         userService.getUser(); result = user; // returns the user mock
         user.getName(); result = "my name";   // define behavior of user mock
      }};

      assertEquals("my name", myService.outputUserName());
   }

   @Test
   public void sameTestButWithChaining(@Cascading final UserService userService)
   {
      new NonStrictExpectations() {{
         userService.getUser().getName(); result = "my name"; // automatically returns user mock
      }};

      assertEquals("my name", myService.outputUserName());
   }
}