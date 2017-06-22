/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit;

import java.io.*;
import java.nio.*;
import java.util.*;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.*;
import org.junit.runners.*;

public final class InstanceSpecificMockingTest
{
   static class Collaborator
   {
      protected final int value;

      Collaborator() { value = -1; }
      Collaborator(int value) { this.value = value; }

      int getValue() { return value; }

      @SuppressWarnings("UnusedDeclaration")
      final boolean simpleOperation(int a, String b, Date c) { return true; }

      @SuppressWarnings("UnusedDeclaration")
      static void doSomething(boolean b, String s) { throw new IllegalStateException(); }
   }

   final Collaborator previousInstance = new Collaborator();
   @Injectable Collaborator mock;

   @Test
   public void exerciseInjectedInstanceDuringReplayOnly()
   {
      assertThatPreviouslyCreatedInstanceIsNotMocked();

      assertEquals(0, mock.value);
      assertEquals(0, mock.getValue());
      assertFalse(mock.simpleOperation(1, "test", null));

      assertThatNewlyCreatedInstanceIsNotMocked();
   }

   private void assertThatPreviouslyCreatedInstanceIsNotMocked()
   {
      assertEquals(-1, previousInstance.value);
      assertEquals(-1, previousInstance.getValue());
      assertTrue(previousInstance.simpleOperation(1, "test", null));
   }

   private void assertThatNewlyCreatedInstanceIsNotMocked()
   {
      Collaborator newInstance = new Collaborator();
      assertEquals(-1, newInstance.value);
      assertEquals(-1, newInstance.getValue());
      assertTrue(newInstance.simpleOperation(1, "test", null));
   }

   @Test
   public void mockSingleInstanceStrictly()
   {
      assertThatPreviouslyCreatedInstanceIsNotMocked();

      new Expectations() {{ mock.getValue(); result = 123; }};

      assertEquals(123, mock.getValue());
      assertThatNewlyCreatedInstanceIsNotMocked();
   }

   @Test
   public void mockSpecificInstanceWithNonStrictExpectations()
   {
      new NonStrictExpectations() {{
         mock.simpleOperation(1, "", null); result = false;
         mock.getValue(); result = 123;
      }};

      assertFalse(mock.simpleOperation(1, "", null));
      assertEquals(123, mock.getValue());
      assertThatPreviouslyCreatedInstanceIsNotMocked();
      assertThatNewlyCreatedInstanceIsNotMocked();

      try {
         Collaborator.doSomething(false, null);
         fail();
      }
      catch (IllegalStateException ignore) {}

      new Verifications() {{ mock.getValue(); times = 1; }};
   }

   @Test
   public void useASecondMockInstanceOfTheSameType(@Injectable final Collaborator mock2)
   {
      assertThatPreviouslyCreatedInstanceIsNotMocked();

      new NonStrictExpectations() {{
         mock2.getValue(); result = 2;
         mock.getValue(); returns(1, 3);
      }};

      assertEquals(1, mock.getValue());
      assertEquals(2, mock2.getValue());
      assertEquals(3, mock.getValue());
      assertEquals(2, mock2.getValue());
      assertEquals(3, mock.getValue());

      new FullVerifications() {{
         mock.getValue(); times = 3;
         mock2.getValue(); times = 2;
      }};
      
      assertThatPreviouslyCreatedInstanceIsNotMocked();
      assertThatNewlyCreatedInstanceIsNotMocked();
   }

   // Injectable mocks of unusual types ///////////////////////////////////////////////////////////////////////////////

   @Test
   public void allowInjectableMockOfInterfaceType(@Injectable final Runnable runnable)
   {
      new NonStrictExpectations() {{ runnable.run(); minTimes = 1; }};
      
      runnable.run();
      runnable.run();

      new Verifications() {{ runnable.run(); maxTimes = 2; }};
   }

   @Test
   public void allowInjectableMockOfAnnotationType(@Injectable final RunWith runWith)
   {
      new Expectations() {{ runWith.value(); result = BlockJUnit4ClassRunner.class; }};
      
      assertSame(BlockJUnit4ClassRunner.class, runWith.value());
   }

   enum EnumWithMethod { Value1; int someMethod() { return 123; } }

   @Test
   public void allowInjectableMockOfEnumType(@Injectable final EnumWithMethod enumWithMethod)
   {
      new Expectations() {{ enumWithMethod.someMethod(); result = 45; }};

      assertEquals(45, enumWithMethod.someMethod());
   }

   // Mocking java.nio.ByteBuffer /////////////////////////////////////////////////////////////////////////////////////

   @Test
   public void mockByteBufferAsInjectable(@Injectable final ByteBuffer buf)
   {
      ByteBuffer realBuf = ByteBuffer.allocateDirect(10);
      assertNotNull(realBuf);
      assertEquals(10, realBuf.capacity());
      
      new NonStrictExpectations() {{
         buf.isDirect(); result = true;

         // Calling "getBytes()" here indirectly creates a new ByteBuffer, requiring use of @Injectable.
         buf.put("Test".getBytes()); times = 1;
      }};

      assertTrue(buf.isDirect());
      buf.put("Test".getBytes());
   }

   @Test
   public void mockByteBufferRegularly(@Mocked ByteBuffer unused)
   {
      assertNull(ByteBuffer.allocateDirect(10));

      new Verifications() {{ ByteBuffer.allocateDirect(anyInt); }};
   }

   @Test
   public void mockByteBufferAsCascading(@Cascading ByteBuffer unused)
   {
      ByteBuffer cascadedBuf = ByteBuffer.allocateDirect(10);
      assertNotNull(cascadedBuf);
      assertEquals(0, cascadedBuf.capacity());
   }

   static class BufferFactory
   {
      ByteBuffer createBuffer() { return null; }
   }

   @Test
   public void mockByteBufferAsCascadedMock(@Cascading BufferFactory cascadingMock)
   {
      ByteBuffer realBuf1 = ByteBuffer.allocateDirect(10);
      assertEquals(10, realBuf1.capacity());

      ByteBuffer cascadedBuf = cascadingMock.createBuffer();
      assertEquals(0, cascadedBuf.capacity());

      ByteBuffer realBuf2 = ByteBuffer.allocateDirect(20);
      assertEquals(20, realBuf2.capacity());
   }

   // Mocking java.io.InputStream /////////////////////////////////////////////////////////////////////////////////////

   public static final class ConcatenatingInputStream extends InputStream
   {
      private final Queue<InputStream> sequentialInputs;
      private InputStream currentInput;

      public ConcatenatingInputStream(InputStream... sequentialInputs)
      {
         this.sequentialInputs = new LinkedList<InputStream>(Arrays.asList(sequentialInputs));
         currentInput = this.sequentialInputs.poll();
      }

      @Override
      public int read() throws IOException
      {
         if (currentInput == null) return -1;

         int nextByte = currentInput.read();

         if (nextByte >= 0) {
            return nextByte;
         }

         currentInput = sequentialInputs.poll();
         return read();
      }
   }

   @Test
   public void concatenateInputStreams(@Injectable final InputStream input1, @Injectable final InputStream input2)
      throws Exception
   {
      new Expectations() {{
         input1.read(); returns(1, 2, -1);
         input2.read(); returns(3, -1);
      }};

      InputStream concatenatedInput = new ConcatenatingInputStream(input1, input2);
      byte[] buf = new byte[3];
      concatenatedInput.read(buf);

      assertArrayEquals(new byte[] {1, 2, 3}, buf);
   }
}
