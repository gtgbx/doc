/*
 * Copyright (c) 2006-2013 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.coverage.reporting.parsing;

import static java.util.Arrays.*;
import java.util.*;

import org.jetbrains.annotations.*;

public final class LineElement implements Iterable<LineElement>
{
   private static final List<String> CONDITIONAL_OPERATORS = asList("||", "&&", ":", "else");
   private static final List<String> CONDITIONAL_INSTRUCTIONS = asList("if", "for", "while");

   enum ElementType { CODE, COMMENT, SEPARATOR }

   @NotNull private final ElementType type;
   @NotNull private final String text;
   @Nullable private String openingTag;
   @Nullable private String closingTag;
   @Nullable private LineElement next;

   private boolean underConditionalStatement;
   private int parenthesesBalance;

   LineElement(@NotNull ElementType type, @NotNull String text)
   {
      this.type = type;
      this.text = text.replaceAll("<", "&lt;");
   }

   public boolean isCode() { return type == ElementType.CODE; }
   public boolean isComment() { return type == ElementType.COMMENT; }

   public boolean isKeyword(@NotNull String keyword)
   {
      return isCode() && text.equals(keyword);
   }

   public boolean isDotSeparator()
   {
      return type == ElementType.SEPARATOR && text.charAt(0) == '.';
   }

   @NotNull public String getText() { return text; }

   @Nullable public LineElement getNext() { return next; }
   void setNext(@Nullable LineElement next) { this.next = next; }

   @Nullable public LineElement getNextCodeElement()
   {
      if (next != null) {
         for (LineElement element : next) {
            if (element.isCode()) {
               return element;
            }
         }
      }

      return null;
   }

   public void wrapText(@NotNull String openingTag, @NotNull String closingTag)
   {
      this.openingTag = openingTag;
      this.closingTag = closingTag;
   }

   @Nullable public LineElement appendUntilNextCodeElement(@NotNull StringBuilder line)
   {
      LineElement element = this;

      while (!element.isCode()) {
         element.appendText(line);
         element = element.next;

         if (element == null) {
            break;
         }

         copyConditionalTrackingState(element);
      }

      return element;
   }

   private void copyConditionalTrackingState(@NotNull LineElement destination)
   {
      destination.underConditionalStatement = underConditionalStatement;
      destination.parenthesesBalance = parenthesesBalance;
   }

   private void appendText(@NotNull StringBuilder line)
   {
      if (openingTag == null) {
         line.append(text);
      }
      else {
         line.append(openingTag).append(text).append(closingTag);
      }
   }

   @Nullable public LineElement findNextBranchingPoint()
   {
      if (!underConditionalStatement) {
         underConditionalStatement = isConditionalStatement();
      }

      if (isBranchingElement()) {
         if (next != null) {
            copyConditionalTrackingState(next);
         }

         return this;
      }

      if (underConditionalStatement) {
         int balance = getParenthesisBalance();
         parenthesesBalance += balance;

         if (balance != 0 && parenthesesBalance == 0) {
            return next;
         }
      }

      if (next == null) {
         return null;
      }

      copyConditionalTrackingState(next);
      return next.findNextBranchingPoint();
   }

   private boolean isConditionalStatement() { return CONDITIONAL_INSTRUCTIONS.contains(text); }
   public boolean isBranchingElement() { return CONDITIONAL_OPERATORS.contains(text); }

   private int getParenthesisBalance()
   {
      if (text.indexOf('(') >= 0) {
         return 1;
      }
      else if (text.indexOf(')') >= 0) {
         return -1;
      }

      return 0;
   }

   @Nullable public LineElement findWord(@NotNull String word)
   {
      for (LineElement element : this) {
         if (element.isCode() && word.equals(element.text)) {
            return element;
         }
      }

      return null;
   }

   public int getBraceBalanceUntilEndOfLine()
   {
      int balance = 0;

      for (LineElement element : this) {
         balance += element.getBraceBalance();
      }

      return balance;
   }

   private int getBraceBalance()
   {
      if (isCode() && text.length() == 1) {
         char c = text.charAt(0);

         if (c == '{') {
            return 1;
         }
         else if (c == '}') {
            return -1;
         }
      }

      return 0;
   }

   public void appendAllBefore(@NotNull StringBuilder line, @Nullable LineElement elementToStopBefore)
   {
      LineElement elementToPrint = this;

      do {
         elementToPrint.appendText(line);
         elementToPrint = elementToPrint.next;
      }
      while (elementToPrint != null && elementToPrint != elementToStopBefore);
   }

   @NotNull public Iterator<LineElement> iterator()
   {
      return new Iterator<LineElement>()
      {
         @Nullable private LineElement current = LineElement.this;

         public boolean hasNext()
         {
            return current != null;
         }

         public LineElement next()
         {
            if (current == null) {
               throw new NoSuchElementException();
            }

            LineElement next = current;
            current = current.next;

            return next;
         }

         public void remove() {}
      };
   }

   @Override
   public String toString()
   {
      StringBuilder line = new StringBuilder(200);

      for (LineElement element : this) {
         element.appendText(line);
      }

      return line.toString();
   }
}
