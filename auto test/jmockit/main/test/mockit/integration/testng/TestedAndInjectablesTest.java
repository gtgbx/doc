/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.integration.testng;

import org.testng.annotations.*;
import static org.testng.Assert.*;

import mockit.*;

public final class TestedAndInjectablesTest
{
   static class Collaborator { void doSomething() {} }

   static class SUT
   {
      final Collaborator collaborator1;
      Collaborator collaborator2;

      SUT(Collaborator collaborator1) { this.collaborator1 = collaborator1; }

      void useCollaborators()
      {
         collaborator1.doSomething();
         collaborator2.doSomething();
      }
   }

   @Tested SUT tested1;
   @Injectable Collaborator collaborator1;

   @Tested SUT tested2;
   @Tested final SUT tested3 = new SUT(new Collaborator());
   @Tested final SUT tested4 = null;

   SUT firstTestedObject;
   Collaborator firstMockedObject;
   Collaborator secondMockedObject;

   @BeforeMethod
   public void setUp()
   {
      tested2 = new SUT(new Collaborator());
   }

   @Test
   public void firstTest(@Injectable final Collaborator collaborator2)
   {
      assertNotNull(tested1);
      firstTestedObject = tested1;

      assertNotNull(collaborator1);
      firstMockedObject = collaborator1;

      assertNotNull(collaborator2);
      secondMockedObject = collaborator2;

      assertStatesOfTestedObjects(collaborator2);

      new Expectations() {{
         collaborator1.doSomething();
         collaborator2.doSomething();
      }};

      tested1.useCollaborators();
   }

   void assertStatesOfTestedObjects(Collaborator collaborator2)
   {
      assertSame(tested1.collaborator1, collaborator1);
      assertSame(tested1.collaborator2, collaborator2);

      assertNotSame(tested2.collaborator1, collaborator1);
      assertSame(tested2.collaborator2, collaborator2);

      assertNotSame(tested3.collaborator1, collaborator1);
      assertNotNull(tested3.collaborator2);

      assertNull(tested4);
   }

   @Test(dependsOnMethods = "firstTest")
   public void secondTest(@Injectable Collaborator collaborator2)
   {
      assertNotSame(collaborator2, secondMockedObject);
      assertSame(collaborator1, firstMockedObject);
      assertNotSame(tested1, firstTestedObject);

      assertStatesOfTestedObjects(collaborator2);
   }
}
