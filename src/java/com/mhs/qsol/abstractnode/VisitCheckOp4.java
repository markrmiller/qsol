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

import com.mhs.qsol.syntaxtree.CheckOrd4Search;
import com.mhs.qsol.visitor.GJVisitor;

import org.apache.lucene.search.Query;

public class VisitCheckOp4 implements VisitCheckOp {
  private CheckOrd4Search n;

  public VisitCheckOp4(CheckOrd4Search n) {
    this.n = n;
  }

  public boolean isF1Present() {
    return n.f1.present();
  }

  public Query visitf0(GJVisitor<Query, Query> visitor, Query query) {
    return n.f0.f0.nodes.elementAt(0).accept(visitor, query);
  }

  public Query visitf1(GJVisitor<Query, Query> visitor, Query query) {
    return n.f1.accept(visitor, query);
  }
}
