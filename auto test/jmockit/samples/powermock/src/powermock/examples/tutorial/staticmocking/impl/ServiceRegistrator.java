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
package powermock.examples.tutorial.staticmocking.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import powermock.examples.tutorial.staticmocking.IServiceRegistrator;
import powermock.examples.tutorial.staticmocking.osgi.BundleContext;
import powermock.examples.tutorial.staticmocking.osgi.ServiceRegistration;

/**
 * An "OSGi"-ish implementation of the {@link IServiceRegistrator} interface.
 * The test for this class demonstrates static mocking as well as getting and
 * setting internal state.
 */
public final class ServiceRegistrator implements IServiceRegistrator
{
   private BundleContext bundleContext;

   /**
    * Holds all services registrations that has been registered by this service registrator.
    */
   private final Map<Long, ServiceRegistration> serviceRegistrations;

   /**
    * Default constructor, initializes internal state.
    */
   public ServiceRegistrator()
   {
      serviceRegistrations = new ConcurrentHashMap<>();
   }

   public long registerService(String name, Object serviceImplementation)
   {
      ServiceRegistration registerService =
         bundleContext.registerService(name, serviceImplementation, null);
      long id = IdGenerator.generateNewId();
      
      serviceRegistrations.put(id, registerService);

      return id;
   }

   public void unregisterService(long id)
   {
      ServiceRegistration registration = serviceRegistrations.remove(id);

      if (registration == null) {
         throw new IllegalStateException(
            "Registration with id " + id +
            " has already been removed or has never been registered");
      }

      registration.unregister();
   }
}
