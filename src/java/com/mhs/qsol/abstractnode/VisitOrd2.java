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
package com.mhs.qsol.abstractnode;

import com.mhs.qsol.syntaxtree.Ord2Search;
import com.mhs.qsol.visitor.GJVisitor;

import org.apache.lucene.search.Query;

public class VisitOrd2 implements VisitOp {
  private Ord2Search n;

  public VisitOrd2(Ord2Search n) {
    this.n = n;
  }

  public boolean isF2Present() {
    return n.f2.present();
  }

  public Query visitf1(GJVisitor<Query, Query> visitor, Query query) {
    return n.f1.accept(visitor, query);
  }

  public Query visitf2(GJVisitor<Query, Query> visitor, Query query) {
    return n.f2.accept(visitor, query);
  }

  public int getOpNum() {
    return 2;
  }

  public String getF0TokenImage() {
    return n.f0.tokenImage;
  }
}
