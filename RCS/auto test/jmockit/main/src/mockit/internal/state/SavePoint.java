/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.state;

import java.util.*;

import org.jetbrains.annotations.*;

import mockit.internal.*;

public final class SavePoint
{
   @NotNull private final Set<ClassIdentification> previousTransformedClasses;
   @NotNull private final Map<Class<?>, byte[]> previousRedefinedClasses;
   private final int previousMockInstancesCount;
   private final int previousCaptureTransformerCount;

   public SavePoint()
   {
      MockFixture mockFixture = TestRun.mockFixture();
      previousTransformedClasses = mockFixture.getTransformedClasses();
      previousRedefinedClasses = mockFixture.getRedefinedClasses();
      previousMockInstancesCount = TestRun.getMockClasses().getRegularMocks().getInstanceCount();
      previousCaptureTransformerCount = mockFixture.getCaptureTransformerCount();
   }

   public synchronized void rollback()
   {
      MockFixture mockFixture = TestRun.mockFixture();
      mockFixture.removeCaptureTransformers(previousCaptureTransformerCount);
      mockFixture.restoreTransformedClasses(previousTransformedClasses);
      mockFixture.restoreRedefinedClasses(previousRedefinedClasses);
      TestRun.getMockClasses().getRegularMocks().removeInstances(previousMockInstancesCount);
   }

   public static void registerNewActiveSavePoint()
   {
      TestRun.setSavePointForTestClass(new SavePoint());
   }

   public static void rollbackForTestClass()
   {
      SavePoint savePoint = TestRun.getSavePointForTestClass();

      if (savePoint != null) {
         savePoint.rollback();
         TestRun.setSavePointForTestClass(null);
      }
   }
}
