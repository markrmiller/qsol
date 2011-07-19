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

import java.util.regex.Pattern;

/**
 * @author Mark Miller <markrmiller@gmail.com> Dec 7, 2006
 * @version %I%, %G%
 * @since 1.0
 */
public class FindReplaceRegEx {
  private Pattern pattern;
  private String replacement;
  private boolean operatorReplace;
  private String field;

  public String getField() {
    return field;
  }

  public void setField(String field) {
    this.field = field;
  }

  /**
   * @param pattern
   * @param replacement
   * @param operatorReplace
   * @since 1.0
   */
  public FindReplaceRegEx(Pattern pattern, String replacement,
      boolean operatorReplace) {
    this.pattern = pattern;
    this.replacement = replacement;
    this.operatorReplace = operatorReplace;
  }

  /**
   * @return the pattern
   */
  public Pattern getPattern() {
    return pattern;
  }

  /**
   * @return the replacement
   */
  public String getReplacement() {
    return replacement;
  }

  /**
   * Returns true if the replacement will act as an operator.
   * 
   * @return
   */
  public boolean isOperatorReplace() {
    return operatorReplace;
  }
}
