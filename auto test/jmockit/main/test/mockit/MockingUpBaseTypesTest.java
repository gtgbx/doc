/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit;

import org.junit.*;
import org.junit.rules.*;
import org.junit.runners.*;
import static org.junit.Assert.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public final class MockingUpBaseTypesTest
{
   static class BeforeClassBaseAction { int perform() { return -1; } }
   static class BeforeClassAction extends BeforeClassBaseAction { @Override int perform() { return 12; } }

   @BeforeClass
   public static <T extends BeforeClassBaseAction> void applyMockUpForAllTests()
   {
      new MockUp<T>() {
         @Mock int perform() { return 34; }
      };
   }

   @AfterClass
   public static void verifyMockUpForAllTestsIsInEffect()
   {
      int i1 = new BeforeClassAction().perform();
      int i2 = new BeforeClassBaseAction().perform();

      assertEquals(34, i1);
      assertEquals(34, i2);
   }

   public interface IBeforeAction { int perform(); }
   static class BeforeAction implements IBeforeAction { @Override public int perform() { return 123; } }

   @Before
   public <T extends IBeforeAction> void applyMockUpForEachTest()
   {
      new MockUp<T>() {
         @Mock int perform() { return 56; }
      };
   }

   @After
   public void verifyMockUpForEachTestIsInEffect()
   {
      int i = new BeforeAction().perform();
      assertEquals(56, i);
   }

   @Rule public final ExpectedException thrown = ExpectedException.none();

   @Test
   public <T> void test1_attemptToMockUpBaseTypeUsingTypeVariableWithNoBounds()
   {
      thrown.expect(IllegalArgumentException.class);
      thrown.expectMessage("base type");

      new MockUp<T>() {};
   }

   @Test
   public <T extends Runnable> void test2_attemptToMockUpBaseTypeHavingMockMethodWithNoRealMethod()
   {
      thrown.expect(IllegalArgumentException.class);
      thrown.expectMessage("mockWithNoRealMethod");

      new MockUp<T>() {
         @Mock void mockWithNoRealMethod() {}
      };
   }

   public interface IAction
   {
      int perform(int i);
      boolean notToBeMocked();
   }

   public static class ActionImpl1 implements IAction
   {
      @Override public int perform(int i) { return i - 1; }
      @Override public boolean notToBeMocked() { return true; }
   }

   static final class ActionImpl2 implements IAction
   {
      @Override public int perform(int i) { return i - 2; }
      @Override public boolean notToBeMocked() { return true; }
   }

   IAction actionI;

   @Test
   public <T extends IAction> void test3_mockUpInterfaceImplementationClassesUsingAnonymousMockUp()
   {
      actionI = new ActionImpl1();

      new MockUp<T>() {
         @Mock(minInvocations = 2)
         int perform(int i) { return i + 1; }
      };

      assertEquals(2, actionI.perform(1));
      assertTrue(actionI.notToBeMocked());

      ActionImpl2 impl2 = new ActionImpl2();
      assertEquals(3, impl2.perform(2));
      assertTrue(impl2.notToBeMocked());
   }

   abstract static class BaseAction
   {
      abstract int perform(int i);
      int toBeMockedAsWell() { return -1; }
      int notToBeMocked() { return 1; }
   }

   static final class ConcreteAction1 extends BaseAction
   {
      @Override public int perform(int i) { return i - 1; }
   }

   static class ConcreteAction2 extends BaseAction
   {
      @Override int perform(int i) { return i - 2; }
      @Override int toBeMockedAsWell() { return super.toBeMockedAsWell() - 1; }
      @Override int notToBeMocked() { return super.notToBeMocked() + 1; }
   }

   static class ConcreteAction3 extends ConcreteAction2
   {
      @Override public int perform(int i) { return i - 3; }
      @Override int toBeMockedAsWell() { return -3; }
      @Override final int notToBeMocked() { return 3; }
   }

   BaseAction actionB;

   @Test
   public <T extends BaseAction> void test4_mockUpConcreteSubclassesUsingAnonymousMockUp()
   {
      actionB = new ConcreteAction1();

      new MockUp<T>() {
         @Mock int perform(int i) { return i + 1; }
         @Mock int toBeMockedAsWell() { return 123; }
      };

      assertEquals(2, actionB.perform(1));
      assertEquals(123, actionB.toBeMockedAsWell());
      assertEquals(1, actionB.notToBeMocked());

      ConcreteAction2 action2 = new ConcreteAction2();
      assertEquals(3, action2.perform(2));
      assertEquals(123, action2.toBeMockedAsWell());
      assertEquals(2, action2.notToBeMocked());

      ConcreteAction3 action3 = new ConcreteAction3();
      assertEquals(4, action3.perform(3));
      assertEquals(123, action3.toBeMockedAsWell());
      assertEquals(3, action3.notToBeMocked());
   }

   @After
   public void checkImplementationClassesAreNoLongerMocked()
   {
      if (actionI != null) {
         assertEquals(-1, actionI.perform(0));
      }

      assertEquals(-2, new ActionImpl2().perform(0));

      if (actionB != null) {
         assertEquals(-1, actionB.perform(0));
      }

      assertEquals(-2, new ConcreteAction2().perform(0));
      assertEquals(-3, new ConcreteAction3().perform(0));
   }

   static final class InterfaceMockUp<T extends IAction> extends MockUp<T>
   {
      @Mock
      int perform(int i) { return i + 2; }
   }

   @Test
   public void test5_mockUpInterfaceImplementationClassesUsingNamedMockUp()
   {
      new InterfaceMockUp();

      actionI = new ActionImpl1();
      assertEquals(3, actionI.perform(1));
      assertEquals(4, new ActionImpl2().perform(2));
   }

   static final class BaseClassMockUp<T extends BaseAction> extends MockUp<T>
   {
      @Mock(invocations = 3)
      int perform(int i) { return i + 3; }
   }

   @Test
   public void test6_mockUpConcreteSubclassesUsingNamedMockUp()
   {
      new BaseClassMockUp();

      actionB = new ConcreteAction1();
      assertEquals(4, actionB.perform(1));
      assertEquals(5, new ConcreteAction2().perform(2));
      assertEquals(6, new ConcreteAction3().perform(3));
   }

   interface GenericIAction<N extends Number> { N perform(N n); }

   @Test
   public <M extends GenericIAction<Number>> void test7_mockUpImplementationsOfGenericInterface()
   {
      GenericIAction<Number> actionN = new GenericIAction<Number>() {
         @Override public Number perform(Number n) { return n.intValue() + 1; }
      };

      GenericIAction<Integer> actionI = new GenericIAction<Integer>() {
         @Override public Integer perform(Integer n) { return n + 1; }
      };

      GenericIAction<Long> actionL = new GenericIAction<Long>() {
         @Override public Long perform(Long n) { return n + 2; }
      };

      new MockUp<M>() {
         @Mock Number perform(Number n) { return n.intValue() - 1; }
      };

      Number n = actionN.perform(1);
      assertEquals(0, n); // mocked

      int i = actionI.perform(2);
      assertEquals(3, i); // not mocked

      long l = actionL.perform(3L);
      assertEquals(5, l); // not mocked
   }
}
