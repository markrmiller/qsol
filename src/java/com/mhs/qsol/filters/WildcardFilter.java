package com.mhs.qsol.filters;

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
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.WildcardTermEnum;

import java.io.IOException;

import java.util.BitSet;

public class WildcardFilter extends Filter {
  private Term term;

  public WildcardFilter(Term term) {
    this.term = term;
  }

  @Override
  public BitSet bits(IndexReader reader) throws IOException {
    BitSet bits = new BitSet(reader.maxDoc());
    WildcardTermEnum enumerator = new WildcardTermEnum(reader, term);
    TermDocs termDocs = reader.termDocs();

    try {
      do {
        Term term = enumerator.term();

        if (term != null) {
          termDocs.seek(term);

          while (termDocs.next()) {
            bits.set(termDocs.doc());
          }
        } else {
          break;
        }
      } while (enumerator.next());
    } finally {
      termDocs.close();
      enumerator.close();
    }

    return bits;
  }
}
