/*
 * Copyright (c) 2006-2012 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package jmockit.tutorial.persistence;

import java.util.*;
import javax.persistence.*;

/**
 * This class is a <em>static facade</em> for persistence operations.
 * All methods are {@code static}, so it can be statically imported in client classes for maximum usability.
 * <p/>
 * All of the persistence operations made available through this facade access a thread-bound <em>persistence
 * context</em>. In this particular implementation, the standard <strong>JPA</strong> API is used, where
 * {@code javax.persistence.EntityManager} represents a work unit. Typically, each work unit instance exists only long
 * enough to perform a single database transaction. Transaction demarcation is not a responsibility of this class,
 * however, which simply keeps the association between the current thread and a dedicated work unit object (an
 * {@code EntityManager} instance).
 * (In a web app, the persistence context can be tied to the HTTP request/response cycle, which normally runs entirely
 * in a single thread for each request/response pair; a central action-dispatch servlet can commit or rollback the
 * current transaction, while a custom {@code javax.servlet.Filter} can close the thread-bound {@code EntityManager}.)
 * <p/>
 * Compared to direct use of an ORM API such as JPA, or to the use of <em>Data Access Objects</em> (the "DAO" pattern),
 * this <em>static persistence facade</em> pattern has several advantages.
 * Mainly, client code which needs to perform high-level persistence operations (such as persisting a new entity
 * instance, deleting an already persisted entity, or finding and loading persistent entities) tends to be simpler and
 * shorter.
 * In the case of entity-specific DAO classes, potentially thousands of lines of code are saved, without any real loss
 * in portability (the facade implementation can adapt to new versions of the ORM API or even a different ORM API; and
 * consider that switching between an ORM API and plain use of JDBC is not viable anyway, since with JDBC the DAO
 * classes need to expose methods for the execution of "UPDATE" statements, which are not needed with modern ORM APIs).
 */
public final class Database
{
   private static final ThreadLocal<EntityManager> workUnit = new ThreadLocal<EntityManager>();
   private static final EntityManagerFactory entityManagerFactory;

   static
   {
      EntityManagerFactory factory = null;

      try {
         factory = Persistence.createEntityManagerFactory("AppPersistenceUnit");
      }
      catch (PersistenceException e) {
         e.printStackTrace();
      }

      entityManagerFactory = factory;
   }

   private Database() {}

   public static <E> E find(Class<E> entityClass, Object entityId)
   {
      E entity = workUnit().find(entityClass, entityId);
      return entity;
   }

   public static <E> List<E> find(String ql, Object... args)
   {
      Query query = workUnit().createQuery(ql);
      int position = 1;

      for (Object arg : args) {
         query.setParameter(position, arg);
         position++;
      }

      @SuppressWarnings("unchecked")
      List<E> result = query.getResultList();

      return result;
   }

   public static void persist(Object data)
   {
      workUnit().persist(data);
   }

   public static void remove(Object persistentEntity)
   {
      workUnit().remove(persistentEntity);
   }

   private static EntityManager workUnit()
   {
      EntityManager wu = workUnit.get();

      if (wu == null) {
         wu = entityManagerFactory.createEntityManager();
         workUnit.set(wu);
      }

      return wu;
   }
}
