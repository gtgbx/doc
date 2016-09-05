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
package powermock.examples.domain;

/**
 * A simple domain object.
 */
public class Person
{
   private String firstName;
   private String lastName;
   private String phoneNumber;

   public String getFirstName()
   {
      return firstName;
   }

   public void setFirstName(String firstName)
   {
      this.firstName = firstName;
   }

   public String getLastName()
   {
      return lastName;
   }

   public void setLastName(String lastName)
   {
      this.lastName = lastName;
   }

   public String getPhoneNumber()
   {
      return phoneNumber;
   }

   public void setPhoneNumber(String phoneNumber)
   {
      this.phoneNumber = phoneNumber;
   }

   public Person(String firstName, String lastName, String phoneNumber)
   {
      super();
      this.firstName = firstName;
      this.lastName = lastName;
      this.phoneNumber = phoneNumber;
   }

   @Override
   public int hashCode()
   {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((firstName == null) ? 0 : firstName.hashCode());
      result = prime * result + ((lastName == null) ? 0 : lastName.hashCode());
      result = prime * result + ((phoneNumber == null) ? 0 : phoneNumber.hashCode());
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
      final Person other = (Person) obj;
      if (firstName == null) {
         if (other.firstName != null) {
            return false;
         }
      }
      else if (!firstName.equals(other.firstName)) {
         return false;
      }
      if (lastName == null) {
         if (other.lastName != null) {
            return false;
         }
      }
      else if (!lastName.equals(other.lastName)) {
         return false;
      }
      if (phoneNumber == null) {
         if (other.phoneNumber != null) {
            return false;
         }
      }
      else if (!phoneNumber.equals(other.phoneNumber)) {
         return false;
      }
      return true;
   }
}
