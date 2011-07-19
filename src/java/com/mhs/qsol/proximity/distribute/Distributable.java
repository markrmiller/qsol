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
import org.apache.lucene.search.Query;
import org.apache.lucene.search.spans.SpanQuery;

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

/**
 * @author Mark Miller (markrmiller@gmail.com) Aug 26, 2006
 * 
 */
public interface Distributable {
  void add(Distributable c);

  void addConnector(Occur occurType);

  void clear();

  Query distribute(Distributable distrib, ProxInfo proxInfo);

  Query distribute(SpanQuery query, ProxInfo proxInfo);

  ArrayList<Distributable> getChildren();

  ArrayList<Occur> getConnectors();

  Distributable getParent();

  void remove(Distributable c);

  void setChildren(ArrayList<Distributable> children);

  void setConnectors(ArrayList<Occur> connectors);

  void setParent(Distributable distrib);
}
