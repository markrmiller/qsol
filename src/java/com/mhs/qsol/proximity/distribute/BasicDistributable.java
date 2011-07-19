package com.mhs.qsol.proximity.distribute;

import com.mhs.qsol.QsolParseException;
import com.mhs.qsol.spans.SpanWithinQuery;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.spans.SpanNearQuery;
import org.apache.lucene.search.spans.SpanNotQuery;
import org.apache.lucene.search.spans.SpanOrQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;

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

/**
 * @author Mark Miller <markrmiller@gmail.com> Aug 26, 2006
 * 
 */
public class BasicDistributable implements Distributable {
  private final static Logger logger = Logger
      .getLogger(BasicDistributable.class.getPackage().getName());
  private SpanQuery query;
  private Distributable parent;

  private BasicDistributable() {
  }

  public BasicDistributable(SpanQuery query) {
    this.query = query;
  }

  public void addConnector(Occur occurType) {
    // no-op
  }

  public void add(Distributable c) {
    // no-op
  }

  public ArrayList<Distributable> getChildren() {
    return null;
  }

  public void remove(Distributable c) {
    // no-op
  }

  public void clear() {
    // no-op
  }

  public String toString() {
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

    if (query == null) {
      System.out.println("null term found in distrib in toString()");
    }

    return ("\n" + tabs.toString() + "distrib(" + query.toString() + ")");
  }

  public void setParent(Distributable distrib) {
    parent = distrib;
  }

  public Distributable getParent() {
    return parent;
  }

  public void setChildren(ArrayList<Distributable> children) {
    // no-op
  }

  public ArrayList<Occur> getConnectors() {
    // no-op
    return null;
  }

  public void setConnectors(ArrayList<Occur> connectors) {
    // no-op
  }

  public Query distribute(Distributable distrib, ProxInfo proxInfo) {
    BooleanQuery boolQuery = new BooleanQuery();
    List<Distributable> children = distrib.getChildren();

    if (children == null) {
      Query query = distrib.distribute(this.query, proxInfo);

      if (logger.isLoggable(Level.FINE)) {
        logger.fine("return query:" + query);
      }

      return query;
    }

    Query query;
    Query cacheQuery2 = null;
    int size = children.size();
    List<Occur> connectors = distrib.getConnectors();

    for (int i = 0; i < size; i++) {
      Occur con = connectors.get(i);

      // if we have already computed query2 looking for a possible SpanOr
      // use
      if (cacheQuery2 != null) {
        query = cacheQuery2;
      } else {
        query = children.get(i).distribute(this.query, proxInfo);
      }

      // must make sure both clauses are spans and connector is | if you
      // want to optimize to SpanOr
      if ((con == Occur.SHOULD) && query instanceof SpanQuery) {
        cacheQuery2 = children.get(i + 1).distribute(this.query, proxInfo);

        if (cacheQuery2 instanceof SpanQuery) {
          if (children.size() == 2) {
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

      boolQuery.add(query, con);
    }

    if (logger.isLoggable(Level.FINE)) {
      logger.fine("distribute(Distributable) - to distrib:" + distrib
          + " and :" + this.query);
      logger.fine("boolquery:" + boolQuery.toString());
    }

    return boolQuery;
  }

  public Query distribute(SpanQuery query, ProxInfo proxInfo) {
    if (logger.isLoggable(Level.FINE)) {
      logger.fine("distribute(Distributable) - to distrib:" + query + " and :"
          + this.query);
    }

    if ((this.query == null) || (query == null)) {
      throw new QsolParseException(
          "A proximity search cannot contain stop words: " + query + " ~"
              + proxInfo.distance + proxInfo.proxType + " " + this.query);
    }

    switch (proxInfo.proxType) {
    case WORD:

      if (logger.isLoggable(Level.FINE)) {
        logger.fine("return:" + "spanQuery(" + query + "," + this.query + ")");
      }

      SpanQuery spanQuery = new SpanNearQuery(new SpanQuery[] { query,
          this.query }, Integer.parseInt(proxInfo.distance), proxInfo.ordered);

      if (proxInfo.fieldBreakMarker != null) {
        SpanTermQuery fieldBreakMarker = new SpanTermQuery(new Term(this.query
            .getField(), proxInfo.fieldBreakMarker));
        spanQuery = new SpanNotQuery(spanQuery, fieldBreakMarker);
      }

      return spanQuery;

    case PARAGRAPH:

      SpanTermQuery paraMarker = new SpanTermQuery(new Term(this.query
          .getField(), proxInfo.paraMarker));
      SpanQuery querySpan = new SpanNearQuery(new SpanQuery[] { query,
          this.query }, 99999, false);

      if (proxInfo.fieldBreakMarker != null) {
        SpanTermQuery fieldBreakMarker = new SpanTermQuery(new Term(this.query
            .getField(), proxInfo.fieldBreakMarker));
        querySpan = new SpanNotQuery(querySpan, fieldBreakMarker);
      }

      return new SpanWithinQuery(querySpan, paraMarker, Integer
          .parseInt(proxInfo.distance));

    case SENTENCE:

      SpanTermQuery sentMarker = new SpanTermQuery(new Term(this.query
          .getField(), proxInfo.sentMarker));
      querySpan = new SpanNearQuery(new SpanQuery[] { query, this.query },
          99999, false);

      if (proxInfo.fieldBreakMarker != null) {
        SpanTermQuery fieldBreakMarker = new SpanTermQuery(new Term(this.query
            .getField(), proxInfo.fieldBreakMarker));
        querySpan = new SpanNotQuery(querySpan, fieldBreakMarker);
      }

      return new SpanWithinQuery(querySpan, sentMarker, Integer
          .parseInt(proxInfo.distance));
    }

    throw new RuntimeException(
        "proximity search was not of type sent/para/word");
  }
}
