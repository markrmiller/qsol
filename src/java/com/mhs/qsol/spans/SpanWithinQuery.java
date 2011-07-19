package com.mhs.qsol.spans;

/**
 * Copyright 2004 The Apache Software Foundation
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
import org.apache.lucene.search.Query;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.util.ToStringUtils;

import java.io.IOException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

/**
 * This is a modified Lucene SpanNotQuery search that will allow a specified
 * number of intersections between spans.
 * 
 * 
 * If only one document is in the index then this query will see the document as
 * wrapping from end to beginning and you may get spurious results.
 * 
 * @author SpanNotQuery slightly modified by mmiller (markrmiller@gmail.com)
 * 
 */
public class SpanWithinQuery extends SpanQuery {

  private SpanQuery include;
  private SpanQuery exclude;
  private int proximity;

  /**
   * Construct a SpanWithinQuery matching spans from <code>include</code> which
   * overlap with spans from <code>exclude</code> up to <code>proximity</code>
   * times.
   */
  public SpanWithinQuery(SpanQuery include, SpanQuery exclude, int proximity) {
    this.include = include;
    this.exclude = exclude;
    this.proximity = proximity;

    if (!include.getField().equals(exclude.getField())) {
      throw new IllegalArgumentException("Clauses must have same field.");
    }
  }

  /** Return the SpanQuery whose matches are filtered. */
  public SpanQuery getInclude() {
    return include;
  }

  /** Return the SpanQuery whose matches must not overlap those returned. */
  public SpanQuery getExclude() {
    return exclude;
  }

  public String getField() {
    return include.getField();
  }

  public void extractTerms(Set terms) {
    include.extractTerms(terms);
  }

  public String toString(String field) {
    StringBuffer buffer = new StringBuffer();
    buffer.append("spanWithin(");
    buffer.append(include.toString(field));
    buffer.append(", ");
    buffer.append(proximity + " ,");
    buffer.append(exclude.toString(field));
    buffer.append(")");
    buffer.append(ToStringUtils.boost(getBoost()));

    return buffer.toString();
  }

  public Spans getSpans(final IndexReader reader) throws IOException {
    return new Spans() {
      private Spans includeSpans = include.getSpans(reader);
      private boolean moreInclude = true;
      private Spans excludeSpans = exclude.getSpans(reader);
      private boolean moreExclude = true;

      public boolean next() throws IOException {
        if (moreInclude) { // move to next include
          moreInclude = includeSpans.next();
        }

        while (moreInclude && moreExclude) {
          if (includeSpans.doc() > excludeSpans.doc()) { // skip exclude
            moreExclude = excludeSpans.skipTo(includeSpans.doc());
          }

          int count = 0;

          while (moreExclude // while exclude is before
              && (includeSpans.doc() == excludeSpans.doc())) {
            if ((!(excludeSpans.end() <= includeSpans.start()))) {
              count += 1;

              if (count > proximity) {
                break;
              }
            }

            moreExclude = excludeSpans.next(); // increment exclude
          }

          if (!moreExclude // if no intersection
              || (includeSpans.doc() != excludeSpans.doc())
              || (includeSpans.end() <= excludeSpans.start())) {
            break; // we found a match
          }

          moreInclude = includeSpans.next(); // intersected: keep scanning
        }

        return moreInclude;
      }

      public boolean skipTo(int target) throws IOException {
        if (moreInclude) { // skip include
          moreInclude = includeSpans.skipTo(target);
        }

        if (!moreInclude) {
          return false;
        }

        if (moreExclude // skip exclude
            && (includeSpans.doc() > excludeSpans.doc())) {
          moreExclude = excludeSpans.skipTo(includeSpans.doc());
        }

        int count = 0;

        while (moreExclude // while exclude is before
            && (includeSpans.doc() == excludeSpans.doc())) {
          if ((!(excludeSpans.end() <= includeSpans.start()))) {
            count += 1;

            if (count > proximity) {
              break;
            }
          }

          moreExclude = excludeSpans.next(); // increment exclude
        }

        if (!moreExclude // if no intersection
            || (includeSpans.doc() != excludeSpans.doc())
            || (includeSpans.end() <= excludeSpans.start())) {
          return true; // we found a match
        }

        boolean returnboolean = next();

        return returnboolean; // scan to next match
      }

      public int doc() {
        return includeSpans.doc();
      }

      public int start() {
        return includeSpans.start();
      }

      public int end() {
        return includeSpans.end();
      }
      
      // TODO: Remove warning after API has been finalized
      @Override
      public Collection<byte[]> getPayload() throws IOException {
        ArrayList<byte[]> result = null;
        if (includeSpans.isPayloadAvailable()) {
          result = new ArrayList<byte[]>(includeSpans.getPayload());
        }
        return result;
      }

      // TODO: Remove warning after API has been finalized
      @Override
      public boolean isPayloadAvailable() {
        return includeSpans.isPayloadAvailable();
      }

      public String toString() {
        return "spans(" + SpanWithinQuery.this.toString() + ")";
      }
    };
  }

  public Query rewrite(IndexReader reader) throws IOException {
    SpanWithinQuery clone = null;

    SpanQuery rewrittenInclude = (SpanQuery) include.rewrite(reader);

    if (rewrittenInclude != include) {
      clone = (SpanWithinQuery) this.clone();
      clone.include = rewrittenInclude;
    }

    SpanQuery rewrittenExclude = (SpanQuery) exclude.rewrite(reader);

    if (rewrittenExclude != exclude) {
      if (clone == null) {
        clone = (SpanWithinQuery) this.clone();
      }

      clone.exclude = rewrittenExclude;
    }

    if (clone != null) {
      return clone; // some clauses rewrote
    } else {
      return this; // no clauses rewrote
    }
  }

  /** Returns true iff <code>o</code> is equal to this. */
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof SpanWithinQuery)) {
      return false;
    }

    SpanWithinQuery other = (SpanWithinQuery) o;

    return this.include.equals(other.include)
        && this.exclude.equals(other.exclude)
        && (this.getBoost() == other.getBoost())
        && (proximity == other.proximity);
  }

  public int hashCode() {
    int h = include.hashCode();
    h = (h << 1) | (h >>> 31); // rotate left
    h ^= exclude.hashCode();
    h = (h << 1) | (h >>> 31); // rotate left
    h ^= Float.floatToRawIntBits(getBoost());
    h ^= proximity;

    return h;
  }
}
