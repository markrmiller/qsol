package com.mhs.qsol.spans;

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
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.search.spans.SpanOrQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.lucene.search.spans.Spans;

import java.io.IOException;

import java.util.ArrayList;
import java.util.Collection;

public class SpanWildcardQuery extends SpanQuery {
  private Term term;

  public SpanWildcardQuery(Term term) {
    this.term = term;
  }

  public Term getTerm() {
    return term;
  }

  public Query rewrite(IndexReader reader) throws IOException {
    System.out.println("term:" + term );
    WildcardQuery wildQuery = new WildcardQuery(term);

    BooleanQuery bq = null;
    bq = (BooleanQuery) wildQuery.rewrite(reader);
    
    BooleanClause[] clauses = bq.getClauses();
    SpanQuery[] sqs = new SpanQuery[clauses.length];

    for (int i = 0; i < clauses.length; i++) {
      BooleanClause clause = clauses[i];

      // Clauses from RegexQuery.rewrite are always TermQuery's
      TermQuery tq = (TermQuery) clause.getQuery();

      sqs[i] = new SpanTermQuery(tq.getTerm());
      sqs[i].setBoost(tq.getBoost());
    }

    SpanOrQuery query = new SpanOrQuery(sqs);
    query.setBoost(wildQuery.getBoost());

    return query;
  }

  public Spans getSpans(IndexReader reader) throws IOException {
    throw new UnsupportedOperationException("Query should have been rewritten");
  }

  public String getField() {
    return term.field();
  }

  public Collection getTerms() {
    Collection terms = new ArrayList();
    terms.add(term);

    return terms;
  }

  public String toString(String field) {
    StringBuffer buffer = new StringBuffer();
    buffer.append("spanWildcardQuery(");
    buffer.append(term);
    buffer.append(")");

    // buffer.append(ToStringUtils.boost(getBoost()));
    return buffer.toString();
  }
}
