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

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mhs.qsol.QsolParser.Operator;

/**
 * Creates <code>QsolParser</code>'s.
 * 
 * @author Mark Miller <markrmiller@gmail.com> Dec 2, 2006
 * @version %I%, %G%
 * @since 1.0
 */
public class ParserFactory {

  private QsolConfiguration config;

  /**
     *
     */
  private ParserFactory(QsolConfiguration config) {
    this.config = config;
  }

  /**
   * Get the <code>ParserFactory</code>.
   * 
   * @return a <code>ParserFactory</code> singleton
   */
  public static ParserFactory getInstance(QsolConfiguration config) {
    return new ParserFactory(config);
  }

  /**
   * @return <code>Set</code> of date fields.
   */
  public Set<String> getDateFields() {
    return config.getDateFields();
  }

  /**
   * @return the findReplace
   */
  Map<String, FindReplace> getFindReplace() {
    return config.findReplace;
  }

  /**
   * @return the opsList
   */
  List<Operator> getOpsList() {
    return config.opsList;
  }

  /**
   * @return the orderOfOpsMap
   */
  Map<Operator, Integer> getOrderOfOpsMap() {
    return config.orderOfOpsMap;
  }

  /**
   * Creates and returns a new <code>QsolParser</code>. If
   * <code>inheritSettings</code> is <code>true</code> the the returned parser
   * will inherit all of the settings that have been applied to this
   * ParserFactory.
   * 
   * @param inheritSettings
   * @return new <code>QsolParser</code>
   * @since 1.0
   */
  public QsolParser getParser(boolean inheritSettings) {
    QsolParser parser = null;

    if (inheritSettings) {
      parser = new QsolParser(config, true);

      if (config.useHide) {
        parser.hideOperators(config.hideOr, config.hideAnd, config.hideAndNot,
            config.hideProximity);
      }

      parser.setParagraphMarker(config.paragraphMarker);
      parser.setSentenceMarker(config.sentenceMarker);

      return parser;
    }

    parser = new QsolParser(config, false);

    return parser;
  }

  Map<String, FindReplace> getThesaurusFindReplace() {
    return config.thesaurusFindReplace;
  }

}
