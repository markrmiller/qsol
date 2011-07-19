package com.mhs.qsol;

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
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.DateTools.Resolution;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeFilter;

import java.text.DateFormat;
import java.text.ParseException;

import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultDateParser implements DateParser {
  private static final Pattern DATE_TO_DATE = Pattern
      .compile("(.*)\\s?-\\s?(.*)");
  private static final Pattern BEFORE_DATE = Pattern.compile("<(.*)");
  private static final Pattern AFTER_DATE = Pattern.compile(">(.*)");

  public Query buildDateQuery(String field, String date, Locale locale) {
    DateFormat df = DateFormat.getDateInstance(DateFormat.SHORT, locale);
    df.setLenient(true);

    Matcher m;

    if ((m = BEFORE_DATE.matcher(date)).find()) {
      Date date1 = null;

      try {
        date1 = df.parse(m.group(1).trim());
      } catch (ParseException e) {
        throw new QsolParseException(e);
      }

      final Filter filter = TermRangeFilter.Less(field, DateTools.dateToString(
          date1, Resolution.DAY));

      return new ConstantScoreQuery(filter);
    } else if ((m = AFTER_DATE.matcher(date)).find()) {
      Date date1 = null;

      try {
        date1 = df.parse(m.group(1).trim());
      } catch (ParseException e) {
        throw new QsolParseException("Could not parse date", e);
      }

      final Filter filter = TermRangeFilter.More(field, DateTools.dateToString(
          date1, Resolution.DAY));

      return new ConstantScoreQuery(filter);
    } else if ((m = DATE_TO_DATE.matcher(date)).find()) {
      Date date1 = null;
      Date date2 = null;

      try {
        date1 = df.parse(m.group(1).trim());
        date2 = df.parse(m.group(2).trim());
      } catch (ParseException e) {
        throw new QsolParseException(e);
      }

      if ((date1 != null) && (date2 != null)) {
      }

      return new ConstantScoreQuery(new TermRangeFilter(field, DateTools
          .dateToString(date1, Resolution.DAY), DateTools.dateToString(date2,
          Resolution.DAY), true, true));
    } else {
      Date date1 = null;

      try {
        date1 = df.parse(date.toString());
      } catch (ParseException e) {
        throw new QsolParseException(e);
      }

      return new TermQuery(new Term(field, DateTools.dateToString(date1,
          Resolution.DAY)));
    }
  }
}
