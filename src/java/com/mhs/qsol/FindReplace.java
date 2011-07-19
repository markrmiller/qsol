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

/**
 * Use to replace a token with qsol syntax.
 * 
 * @author Mark Miller <markrmiller@gmail.com> Dec 6, 2006
 * @version %I%, %G%
 * @since 1.0
 */
public class FindReplace {
  private String replace;
  private boolean operatorReplace;
  private boolean caseSensitive;
  private String find;
  private String field;

  /**
   * When a token matching the <code>find</code> parameter is found it will be
   * replaced by the <code>replace</code> parameter. The replacement String can
   * be any valid Qsol Syntax except that it cannot include the space operator
   * and must be only <i>basic</i> Qsol syntax. If you would like to map to a
   * custom operator than map to the basic Qsol syntax that the operator maps
   * to.
   * 
   * example: 'distilled' might map to '(distill | tiller) & bunker'
   * <p>
   * Set operator replace to true if the substitution will replace an operator.
   * This will allow the new operator to function correctly with the space
   * operator.
   * <p>
   * example: you could map AND to &, creating a new operator. Setting
   * operatorReplace to true will keep 'mark AND sue' from becoming 'mark & & &
   * sue' during the substitution.
   * 
   * @param find
   *          token to match
   * @param replace
   *          replacement query
   * @param caseSensitive
   *          is the token match case sensitive
   * @param operatorReplace
   *          is the replacement an operator
   */
  public FindReplace(String find, String replace, boolean caseSensitive,
      boolean operatorReplace) {
    this.replace = replace;
    this.operatorReplace = operatorReplace;
    this.caseSensitive = caseSensitive;
    this.find = find;
  }

  /**
   * @return the replacement String
   */
  public String getReplacement() {
    return replace;
  }

  /**
   * @return does this replace to an operator
   */
  public boolean isOperatorReplace() {
    return operatorReplace;
  }

  /**
   * @return caseSensitive
   */
  public boolean isCaseSensitive() {
    return caseSensitive;
  }

  /**
   * @return the find String
   */
  public String getFind() {
    return find;
  }

  public String getField() {
    return field;
  }

  public void setField(String field) {
    this.field = field;
  }
}
