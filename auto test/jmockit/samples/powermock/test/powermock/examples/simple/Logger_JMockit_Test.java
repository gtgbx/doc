/*
 * Copyright (c) 2006-2013 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package powermock.examples.simple;

import java.io.*;

import org.junit.*;

import mockit.*;

import static mockit.Deencapsulation.*;

/**
 * <a href="http://code.google.com/p/powermock/source/browse/trunk/examples/simple/src/test/java/demo/org/powermock/examples/simple/LoggerTest.java">PowerMock version</a>
 */
public final class Logger_JMockit_Test
{
   @Test(expected = IllegalStateException.class)
   public void testException(@Mocked FileWriter fileWriter) throws Exception
   {
      new NonStrictExpectations() {{
         new FileWriter("target/logger.log"); result = new IOException();
      }};

      new Logger();
   }

   @Test
   public void testLogger(@Mocked FileWriter fileWriter, @Mocked final PrintWriter printWriter) throws Exception
   {
      new NonStrictExpectations() {{
         printWriter.println("qwe");
      }};

      Logger logger = new Logger();
      logger.log("qwe");
   }

   @Test
   public void testLogger2(@Mocked final Logger logger, @Mocked final PrintWriter printWriter)
   {
      setField(logger, printWriter);

      new NonStrictExpectations(logger) {{
         printWriter.println("qwe");
      }};

      logger.log("qwe");
   }
}
