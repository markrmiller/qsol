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
package com.mhs.qsol.proximity.distribute;

import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.spans.SpanOrQuery;
import org.apache.lucene.search.spans.SpanQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Mark Miller (markrmiller@gmail.com) Aug 26, 2006
 * 
 */
public class GroupDistributable implements Distributable {
  private final static Logger logger = Logger
      .getLogger(GroupDistributable.class.getPackage().getName());
  private ArrayList<Distributable> distribs = new ArrayList<Distributable>();
  private ArrayList<Occur> connector = new ArrayList<Occur>();
  private Distributable parent = null;

  // static {
  // Handler console = new ConsoleHandler();
  // console.setFormatter(new ShortFormatter());
  // logger.setUseParentHandlers(false);
  // logger.addHandler(console);
  // }
  public GroupDistributable() {
  }

  public GroupDistributable(Distributable distrib) {
    distribs.add(distrib);
  }

  public GroupDistributable(Distributable distrib1, Distributable distrib2) {
    distribs.add(distrib1);
    distribs.add(distrib2);
  }

  public void addConnector(Occur occurType) {
    connector.add(occurType);
  }

  public void add(Distributable distrib) {
    distrib.setParent(this);
    distribs.add(distrib);
  }

  public ArrayList<Distributable> getChildren() {
    return distribs;
  }

  public void remove(Distributable distrib) {
    distribs.remove(distrib);
  }

  public void clear() {
    distribs.clear();
    connector.clear();
  }

  public String toString() {
    StringBuilder string = new StringBuilder();

    int numParents = 0;
    Distributable distrib = this;

    do {
      distrib = distrib.getParent();

      if (distrib != null) {
        numParents++;
      }
    } while (distrib != null);

    StringBuilder tabs = new StringBuilder();

    for (int i = 0; i < numParents; i++) {
      tabs.append("\t");
    }

    string.append("\n" + tabs.toString() + "cdistrib(");
    string.append(tabs.toString());

    for (int i = 0; i < distribs.size(); i++) {
      if (i < connector.size()) {
        string.append("\n" + tabs.toString());
        string.append("'" + connector.get(i) + "'");
      }

      string.append(distribs.get(i).toString());
    }

    string.append(")");

    return string.toString();
  }

  public void setParent(Distributable distrib) {
    parent = distrib;
  }

  public Distributable getParent() {
    return parent;
  }

  public void setChildren(ArrayList<Distributable> children) {
    distribs = children;
  }

  public ArrayList<Occur> getConnectors() {
    return connector;
  }

  public void setConnectors(ArrayList<Occur> connectors) {
    connector = connectors;
  }

  public Query distribute(Distributable distrib, ProxInfo proxInfo) {
    if (distrib == null) {
      throw new IllegalArgumentException("distrib is null");
    }

    // compacts distrib.
    List<Distributable> children = distrib.getChildren();

    if ((children != null) && (children.size() == 1)) {
      distrib = children.get(0);
      distrib.setParent(null);
    }

    // compacts distribs.
    if (distribs.size() == 1) {
      if (logger.isLoggable(Level.FINE)) {
        logger.fine("compacting distribs");
      }

      Distributable returnDistrib = distribs.get(0);

      returnDistrib.setParent(null);

      if (logger.isLoggable(Level.FINE)) {
        logger.fine("compacting distribs:" + returnDistrib);
        logger.fine("other distrib:" + "sec" + distrib.toString());
      }

      return returnDistrib.distribute(distrib, proxInfo);
    }

    BooleanQuery boolQuery = new BooleanQuery();

    Query query;
    Query cacheQuery2 = null;

    for (int i = 0; i < distribs.size(); i++) {
      Occur con = connector.get(i);

      // if we have already computed query2 looking for a possible SpanOr use
      if (cacheQuery2 != null) {
        query = cacheQuery2;
      } else {
        if (logger.isLoggable(Level.FINE)) {
          logger.fine("distrib: " + distribs.get(i) + " WITH " + distrib);
        }

        query = distribs.get(i).distribute(distrib, proxInfo);

        if (logger.isLoggable(Level.FINE)) {
          logger.fine("thequery: " + query);
        }

        // logger.fine("query: " + query);
      }

      // must make sure both clauses are spans and connector is | if you want to
      // optimize to SpanOr
      if (!((i + 1) == distribs.size()) && (con == Occur.SHOULD)
          && query instanceof SpanQuery) {
        cacheQuery2 = distribs.get(i + 1).distribute(distrib, proxInfo);

        if (cacheQuery2 instanceof SpanQuery) {
          // if just a single 'or' set in this group
          if (distribs.size() == 2) {
            return new SpanOrQuery(new SpanQuery[] { (SpanQuery) query,
                (SpanQuery) cacheQuery2 });
          } else {
            query = new SpanOrQuery(new SpanQuery[] { (SpanQuery) query,
                (SpanQuery) cacheQuery2 });
            cacheQuery2 = null;
            i++;
          }
        } else {
          cacheQuery2 = null;
        }
      }

      // TODO: update this
      if (logger.isLoggable(Level.FINE)) {
        logger.fine("distribute(Distributable) - adding connector:" + con);
      }

      if (logger.isLoggable(Level.FINE)) {
        logger.fine("distribute(Distributable) - to distrib:" + distribs
            + " and :" + distrib);
        logger.fine("query:" + query);
      }

      boolQuery.add(query, con);
    }

    return boolQuery;
  }

  public Query distribute(SpanQuery spanQuery, ProxInfo proxInfo) {
    if (logger.isLoggable(Level.FINE)) {
      logger.fine("distributing term: " + spanQuery + " with:" + distribs);
    }

    BooleanQuery boolQuery = new BooleanQuery();
    int size = distribs.size();

    for (int i = 0; i < size; i++) {
      Query query = distribs.get(i).distribute(spanQuery, proxInfo);
      boolQuery.add(query, connector.get(i));
    }

    // we are not returning a span
    return boolQuery;
  }
}
