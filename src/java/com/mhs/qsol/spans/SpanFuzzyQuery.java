package com.mhs.qsol.spans;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.search.spans.SpanOrQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.lucene.search.spans.Spans;

import java.io.IOException;

import java.util.Collection;
import java.util.LinkedList;

/**
 * @author Karl Wettin <kalle@snigel.net>
 */
public class SpanFuzzyQuery extends SpanQuery {
  public final static float defaultMinSimilarity = 0.7f;
  public final static int defaultPrefixLength = 0;
  private final Term term;
  private final float minimumSimilarity;
  private final int prefixLength;
  private BooleanQuery rewrittenFuzzyQuery;

  public SpanFuzzyQuery(Term term) {
    this(term, defaultMinSimilarity, defaultPrefixLength);
  }

  public SpanFuzzyQuery(Term term, float minimumSimilarity, int prefixLength) {
    this.term = term;
    this.minimumSimilarity = minimumSimilarity;
    this.prefixLength = prefixLength;

    if (minimumSimilarity >= 1.0f) {
      throw new IllegalArgumentException("minimumSimilarity >= 1");
    } else if (minimumSimilarity < 0.0f) {
      throw new IllegalArgumentException("minimumSimilarity < 0");
    }

    if (prefixLength < 0) {
      throw new IllegalArgumentException("prefixLength < 0");
    }
  }

  public Query rewrite(IndexReader reader) throws IOException {
    FuzzyQuery fuzzyQuery = new FuzzyQuery(term, minimumSimilarity,
        prefixLength);

    rewrittenFuzzyQuery = (BooleanQuery) fuzzyQuery.rewrite(reader);

    BooleanClause[] clauses = rewrittenFuzzyQuery.getClauses();
    SpanQuery[] spanQueries = new SpanQuery[clauses.length];

    for (int i = 0; i < clauses.length; i++) {
      BooleanClause clause = clauses[i];

      TermQuery termQuery = (TermQuery) clause.getQuery();

      spanQueries[i] = new SpanTermQuery(termQuery.getTerm());
      spanQueries[i].setBoost(termQuery.getBoost());
    }

    SpanOrQuery query = new SpanOrQuery(spanQueries);
    query.setBoost(fuzzyQuery.getBoost());

    return query;
  }

  /**
   * Expert: Returns the matches for this query in an index. Used internally to
   * search for spans.
   */
  public Spans getSpans(IndexReader reader) throws IOException {
    throw new UnsupportedOperationException("Query should have been rewritten");
  }

  /** Returns the name of the field matched by this query. */
  public String getField() {
    return term.field();
  }

  /** Returns a collection of all terms matched by this query. */
  public Collection getTerms() {
    if (rewrittenFuzzyQuery == null) {
      throw new RuntimeException(
          "Query must be rewritten prior to calling getTerms()!");
    } else {
      LinkedList<Term> terms = new LinkedList<Term>();
      BooleanClause[] clauses = rewrittenFuzzyQuery.getClauses();

      for (int i = 0; i < clauses.length; i++) {
        BooleanClause clause = clauses[i];
        TermQuery termQuery = (TermQuery) clause.getQuery();
        terms.add(termQuery.getTerm());
      }

      return terms;
    }
  }

  /**
   * Prints a query to a string, with <code>field</code> as the default field
   * for terms.
   * <p>
   * The representation used is one that is supposed to be readable by
   * {@link org.apache.lucene.queryParser.QueryParser QueryParser}. However,
   * there are the following limitations:
   * <ul>
   * <li>If the query was created by the parser, the printed representation may
   * not be exactly what was parsed. For example, characters that need to be
   * escaped will be represented without the required backslash.</li>
   * <li>Some of the more complicated queries (e.g. span queries) don't have a
   * representation that can be parsed by QueryParser.</li>
   * </ul>
   */
  public String toString(String field) {
    return "fuzzy(" + term.toString() + ")";
  }
}
