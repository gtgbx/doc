/*
 * Copyright 2008 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package powermock.examples.tutorial.domainmocking.impl;

import powermock.examples.tutorial.domainmocking.EventService;
import powermock.examples.tutorial.domainmocking.PersonService;
import powermock.examples.tutorial.domainmocking.SampleService;
import powermock.examples.tutorial.domainmocking.domain.BusinessMessages;
import powermock.examples.tutorial.domainmocking.domain.Person;
import powermock.examples.tutorial.domainmocking.domain.SampleServiceException;

/**
 * This is a simple service that delegates calls to two stub services. The purpose of this service
 * is to demonstrate the need to mock instantiation of domain objects.
 */
public final class SampleServiceImpl implements SampleService
{
   private final PersonService personService;
   private final EventService eventService;

   /**
    * Creates a new instance of the SampleServiceImpl with the following collaborators.
    *
    * @param personService The person service to use.
    * @param eventService  The event service to use.
    */
   public SampleServiceImpl(PersonService personService, EventService eventService)
   {
      this.personService = personService;
      this.eventService = eventService;
   }

   public boolean createPerson(String firstName, String lastName)
   {
      BusinessMessages messages = new BusinessMessages();
      Person person;

      try {
         person = new Person(firstName, lastName);
      }
      catch (IllegalArgumentException e) {
         throw new SampleServiceException(e.getMessage(), e);
      }

      personService.create(person, messages);

      boolean hasErrors = messages.hasErrors();

      if (hasErrors) {
         eventService.sendErrorEvent(person, messages);
      }

      return !hasErrors;
   }
}
