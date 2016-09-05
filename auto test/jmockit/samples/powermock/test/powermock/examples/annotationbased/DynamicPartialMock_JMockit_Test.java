/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package powermock.examples.annotationbased;

import org.junit.*;

import mockit.*;

import static org.junit.Assert.*;
import powermock.examples.annotationbased.dao.*;

public final class DynamicPartialMock_JMockit_Test
{
   @Injectable SomeDao someDaoMock;
   @Tested SomeService someService;

   @Test
   public void useDynamicPartialMock()
   {
      // Only invocations recorded inside this block will stay mocked for the replay.
      new NonStrictExpectations(someDaoMock) {{ someDaoMock.getSomeData(); result = "test"; }};

      assertEquals("test", someService.getData());
      assertNotNull(someService.getMoreData());
   }
}
