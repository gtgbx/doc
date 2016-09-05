/*
 * Copyright (c) 2006-2012 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package jmockit.tutorial.domain;

import java.math.*;
import java.util.*;

import org.apache.commons.mail.*;

import static jmockit.tutorial.persistence.Database.*;

/**
 * This class makes use of several idioms which would prevent unit testing with more "conventional" mocking tools.
 * Its usage is as simple as it gets: {@code new MyBusinessService().doBusinessOperationXyz(data)}.
 * No need to make such classes stateless, or worse, <em>singletons</em>.
 * (Although not shown in this simple example, it is often a great idea to have stateful service objects with
 * operation-specific state passed in a constructor and assigned to {@code final} fields.)
 * <p/>
 * One of those "untestable" idioms is the use of a <em>static persistence facade</em> (the
 * {@linkplain jmockit.tutorial.persistence.Database Database} class) for high-level database operations in the context
 * of a thread-bound work unit.
 * Since all interaction with the facade is through {@code static} methods, client classes cannot be unit tested with a
 * tool which only supports <em>mock objects</em>.
 * With JMockit, though, writing such a test is just as easy as any other (even easier, in fact, given that
 * {@code static} methods don't require an instance of the mocked class at all).
 * <p/>
 * Another idiom which runs against limitations of other mocking tools is the direct instantiation and use of external
 * dependencies, such as the <a href="http://commons.apache.org/email">Apache Commons Email</a> API, used here to send
 * notification e-mails.
 * As demonstrated here, sending an e-mail is simply a matter of instantiating the appropriate {@code Email} subclass,
 * setting the necessary properties, and calling the {@code send()} method.
 * It is certainly not a good use case for <em>Dependency Injection</em> (DI).
 * <p/>
 * Finally, consider that application-specific classes like this one are inherently non-reusable in different
 * contexts/applications; as such, they can and should be made {@code final} to reflect the fact that they are not
 * supposed to be extended through inheritance.
 * In the case of reusable <em>base</em> classes, which are specifically designed to be extended through inheritance,
 * the judicious use of {@code final} for {@code public} and {@code protected} methods is important.
 * (The description of the <em>Template Method</em> pattern in the "GoF" book explains why a properly designed template
 * method should be non-overridable.)
 * Unfortunately, the practice of <em>designing for extension</em> conflicts with the particular implementation approach
 * employed by other mocking tools, which dynamically generate a subclass overriding all non-<code>final</code> methods
 * in the mocked class in order to provide mocked behavior.
 * For JMockit, on the other hand, whether a method or class to be mocked is {@code final} or not is irrelevant, as a
 * radically different mocking approach is employed: class <em>redefinition</em> as provided by
 * {@link java.lang.instrument.Instrumentation#redefineClasses(java.lang.instrument.ClassDefinition...)}.
 */
public final class MyBusinessService
{
   // This method can easily be made transactional, so that any exception thrown during its execution causes a rollback
   // somewhere up in the call stack (assuming a transaction gets started in the first place).
   public void doBusinessOperationXyz(EntityX data) throws EmailException
   {
      // Locate existing persistent entities of the same entity type (note that the query string is a DSL for querying
      // persistent domain entities, written in terms of the domain, not in terms of relational tables and columns):
      List<EntityX> items = find("select item from EntityX item where item.someProperty=?1", data.getSomeProperty());

      // Compute or obtain from another service a total value for the new persistent entity:
      BigDecimal total = new BigDecimal("12.30");
      data.setTotal(total);

      // Persist the entity (no DAO required for such a common, high-level, operation):
      persist(data);

      sendNotificationEmail(data, items);
   }

   private void sendNotificationEmail(EntityX data, List<EntityX> items) throws EmailException
   {
      Email email = new SimpleEmail();
      email.setSubject("Notification about processing of ...");
      email.addTo(data.getCustomerEmail());

      // Other e-mail parameters, such as the host name of the mail server, have defaults defined through external
      // configuration.

      String message = buildNotificationMessage(items);
      email.setMsg(message);

      email.send();
   }

   private String buildNotificationMessage(List<EntityX> items)
   {
      StringBuilder message = new StringBuilder();

      for (EntityX item : items) {
         message.append(item.getSomeProperty()).append(" Total: ").append(item.getTotal());
      }

      return message.toString();
   }
}
