/*
 * Copyright (c) 2006-2013 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.coverage.reporting;

import java.util.*;

import org.jetbrains.annotations.*;

import mockit.coverage.*;

public final class ListOfCallPoints
{
   @NotNull private static final String EOL = System.getProperty("line.separator");

   @NotNull private final StringBuilder content;
   private int n;

   public ListOfCallPoints() { content = new StringBuilder(100); }

   public void insertListOfCallPoints(@NotNull List<CallPoint> callPoints)
   {
      if (content.length() == 0) {
         content.append(EOL).append("      ");
      }

      content.append("  <ol style='display: none'>").append(EOL);
      n = 1;

      StackTraceElement previous = null;

      for (CallPoint callPoint : callPoints) {
         StackTraceElement current = callPoint.getStackTraceElement();

         if (previous == null) {
            appendTestMethod(current);
         }
         else if (!isSameTestMethod(current, previous)) {
            appendRepetitionCountIfAny();
            content.append("</li>").append(EOL);
            appendTestMethod(current);
         }
         else if (current.getLineNumber() == previous.getLineNumber()) {
            n++;
         }
         else {
            appendRepetitionCountIfAny();
            content.append(", ").append(current.getLineNumber());
         }

         previous = current;
      }

      content.append("</li>").append(EOL).append("        </ol>").append(EOL).append("      ");
   }

   private void appendTestMethod(@NotNull StackTraceElement current)
   {
      content.append("          <li>");
      content.append(current.getClassName()).append('#');
      content.append(current.getMethodName().replaceFirst("<", "&lt;")).append(": ");
      content.append(current.getLineNumber());
   }

   private void appendRepetitionCountIfAny()
   {
      if (n > 1) {
         content.append('x').append(n);
         n = 1;
      }
   }

   private boolean isSameTestMethod(@NotNull StackTraceElement ste1, @NotNull StackTraceElement ste2)
   {
      return
         ste1 == ste2 ||
         ste1.getClassName().equals(ste2.getClassName()) && ste1.getMethodName().equals(ste2.getMethodName());
   }

   @NotNull public String getContents()
   {
      String result = content.toString();
      content.setLength(0);
      return result;
   }
}
