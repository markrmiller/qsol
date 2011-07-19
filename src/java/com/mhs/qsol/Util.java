/**
 * Copyright 2006 Mark Miller (markrmiller@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mhs.qsol;

public class Util {
  /**
   * Utility method to dynamically load classes
   * 
   * @param lclass
   * @return
   * @throws LoadException
   */
  public static Object loadClass(final String lclass) {
    Object loadedClass = null;
    Class handlerClass = null;

    try {
      handlerClass = Class.forName(lclass);
    } catch (final NoClassDefFoundError e) {
      throw new RuntimeException("Cannot find class : " + lclass, e);
    } catch (final ClassNotFoundException e) {
      throw new RuntimeException("Cannot find class : " + lclass, e);
    }

    try {
      loadedClass = handlerClass.newInstance();
    } catch (final InstantiationException e) {
      throw new RuntimeException("Cannot create instance of : " + lclass, e);
    } catch (final IllegalAccessException e) {
      throw new RuntimeException("Cannot create instance of : " + lclass, e);
    }

    return loadedClass;
  }
}
