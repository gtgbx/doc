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
package powermock.examples.bypassencapsulation.nontest;

/**
 * A marker domain object used to demonstrate some test features in PowerMock.
 */
public class Report
{
   private final String name;

   public Report(String name)
   {
      this.name = name;
   }

   public String getName()
   {
      return name;
   }

   @Override
   public int hashCode()
   {
      int prime = 31;
      int result = 1;
      result = prime * result + (name == null ? 0 : name.hashCode());
      return result;
   }

   @Override
   public boolean equals(Object obj)
   {
      if (this == obj) {
         return true;
      }
      if (obj == null) {
         return false;
      }
      if (getClass() != obj.getClass()) {
         return false;
      }

      Report other = (Report) obj;

      if (name == null) {
         if (other.name != null) {
            return false;
         }
      }
      else if (!name.equals(other.name)) {
         return false;
      }

      return true;
   }
}
