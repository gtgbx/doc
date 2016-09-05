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
package powermock.examples.staticmocking;

import java.util.HashMap;
import java.util.Map;

public class ServiceRegistrator
{
   /**
    * Holds all services registrations that has been registered by this service registrator.
    */
   private final Map<Long, Object> serviceRegistrations = new HashMap<Long, Object>();

   public long registerService(Object service)
   {
      long id = IdGenerator.generateNewId();
      serviceRegistrations.put(id, service);
      return id;
   }
}
