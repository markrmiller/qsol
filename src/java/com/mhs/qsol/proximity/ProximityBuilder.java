package com.mhs.qsol.proximity;

import com.mhs.qsol.proximity.distribute.BasicDistributable;
import com.mhs.qsol.proximity.distribute.Distributable;
import com.mhs.qsol.proximity.distribute.GroupDistributable;
import com.mhs.qsol.proximity.distribute.ProxInfo;

import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;

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
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class collects terms and boolean connectors for distribution.
 * 
 * @author Mark Miller (markrmiller@gmail.com) Aug 26, 2006
 * 
 */
public class ProximityBuilder {
  // regex
  private static final Pattern PROXIMITY = Pattern.compile(
      "((?:ord|pre)?)~(\\d*)([ps]?)", Pattern.CASE_INSENSITIVE);
  private final static Logger logger = Logger.getLogger(ProximityBuilder.class
      .getPackage().getName());
  private List<Distributable> distribClauses = new ArrayList<Distributable>();
  private Distributable distribs;
  private boolean moreThanOne = false;
  private Query wholeQuery = null;
  ProxType proxType = null;
  private String sentMarker = "/s";
  private String paraMarker = "/p";
  private String fieldBreakMarker;

  public Query getQuery() {
    return wholeQuery;
  }

  public void addConnector(Occur occur) {
    if (logger.isLoggable(Level.FINE)) {
      logger.fine("Adding basic connector:" + occur);
      logger.fine("to:" + distribs);
    }

    distribs.addConnector(occur);
  }

  public void addParentConnector(Occur occur) {
    if (logger.isLoggable(Level.FINE)) {
      logger.fine("Adding basic connector:" + occur);
      logger.fine("to:" + distribs);
    }

    distribs.getParent().addConnector(occur);
  }

  public void addDistrib(BasicDistributable distributable) {
    if (logger.isLoggable(Level.FINE)) {
      logger.fine("Adding basic" + distributable);
    }

    distribs.add(distributable);
  }

  public void clearDistribs() {
    if (distribs != null) {
      distribs.clear();
    }
  }

  public void constructProximityQuery(String proximityToken, String field) {
    boolean ordered = false;

    Matcher m = PROXIMITY.matcher(proximityToken);
    m.matches();

    String inorder = m.group(1);

    if (inorder.length() > 0) {
      ordered = true;
    }

    String distance = m.group(2);
    String pType = m.group(3);

    if (distance.length() == 0) {
      distance = "1";
    }

    if (pType.length() == 0) {
      proxType = ProxType.WORD;
    } else if (pType.compareToIgnoreCase("p") == 0) {
      proxType = ProxType.PARAGRAPH;
    } else if (pType.compareToIgnoreCase("s") == 0) {
      proxType = ProxType.SENTENCE;
    }

    if (logger.isLoggable(Level.FINE)) {
      logger.fine("constructProximityQuery(String, String) - distance:"
          + distance);
    }

    Distributable newest = distribClauses.get(distribClauses.size() - 1);
    BooleanQuery boolQuery = null;

    ProxInfo proxInfo = new ProxInfo(distance, ordered, proxType, sentMarker,
        paraMarker);

    proxInfo.fieldBreakMarker = this.fieldBreakMarker;

    if (distribClauses.size() > 2) {
      boolQuery = new BooleanQuery();

      for (int i = 0; i < (distribClauses.size() - 1); i++) {
        boolQuery.add(distribClauses.get(i).distribute(newest, proxInfo),
            Occur.MUST);
      }

      if (wholeQuery == null) {
        wholeQuery = boolQuery;
        moreThanOne = true;
      } else {

        if (moreThanOne) {
          ((BooleanQuery) wholeQuery).add(boolQuery, Occur.MUST);
        } else {
          BooleanQuery newBoolQuery = new BooleanQuery();
          newBoolQuery.add(wholeQuery, Occur.MUST);
          newBoolQuery.add(boolQuery, Occur.MUST);

          wholeQuery = newBoolQuery;
          moreThanOne = true;
        }
      }

      return;
    }

    if (logger.isLoggable(Level.FINE)) {
      logger.fine("DISTRIBUTING: " + distribClauses.get(0).toString()
          + "\nWITH\n" + newest);
    }

    Query distribQuery = distribClauses.get(0).distribute(newest, proxInfo);

    if (logger.isLoggable(Level.FINE)) {
      logger.fine("distrib query: " + distribQuery);
    }

    if (wholeQuery == null) {
      wholeQuery = distribQuery;
      moreThanOne = false;
    } else {

      if (moreThanOne) {
        ((BooleanQuery) wholeQuery).add(distribQuery, Occur.MUST);
      } else {
        BooleanQuery newBoolQuery = new BooleanQuery();
        newBoolQuery.add(wholeQuery, Occur.MUST);
        newBoolQuery.add(distribQuery, Occur.MUST);

        wholeQuery = newBoolQuery;
        moreThanOne = true;
      }

    }
  }

  public void endGroup() {
    if (logger.isLoggable(Level.FINE)) {
      logger.fine("beforeEndGroup() - end group:\n" + distribs + "\n\n");
    }

    Distributable distrib = distribs.getParent();

    if (distrib != null) {
      distribs = distrib;
    }

    if (logger.isLoggable(Level.FINE)) {
      logger.fine("endGroup() - end group:\n" + distribs + "\n\n");
      logger.fine("<-------------------------->");
    }
  }

  public void saveClause() {
    if (logger.isLoggable(Level.FINE)) {
      logger.fine("\nsaving clause:" + distribs);
      logger.fine("\n^--------------------------^");
    }

    distribClauses.add(distribs);
    distribs = null;
  }

  public void startGroup() {
    if (logger.isLoggable(Level.FINE)) {
      logger.fine("\nStartGroup()  --------->");
    }

    // StackTraceElement[] elems =
    // java.lang.Thread.currentThread().getStackTrace();
    // for(StackTraceElement ele : elems) {
    // System.out.println(ele);
    // }
    GroupDistributable newGroup = new GroupDistributable();

    if (distribs == null) {
      distribs = newGroup;
    } else {
      distribs.add(newGroup);
      distribs = newGroup;
    }
  }

  public void clear() {
    // distribGroupings.clear();
    if (distribs != null) {
      distribs.clear();
    }
  }

  /**
   * @return the paraMarker
   */
  public String getParaMarker() {
    return paraMarker;
  }

  /**
   * @param paraMarker
   *          the paraMarker to set
   */
  public void setParaMarker(String paraMarker) {
    this.paraMarker = paraMarker;
  }

  /**
   * @return the sentMarker
   */
  public String getSentMarker() {
    return sentMarker;
  }

  /**
   * @param sentMarker
   *          the sentMarker to set
   */
  public void setSentMarker(String sentMarker) {
    this.sentMarker = sentMarker;
  }

  public String getFieldBreakMarker() {
    return fieldBreakMarker;
  }

  public void setFieldBreakMarker(String fieldBreakMarker) {
    this.fieldBreakMarker = fieldBreakMarker;
  }

  public enum ProxType {
    WORD, SENTENCE, PARAGRAPH;
  }
}
