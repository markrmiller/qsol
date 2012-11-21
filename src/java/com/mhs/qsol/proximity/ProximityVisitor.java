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
package com.mhs.qsol.proximity;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.spans.SpanNearQuery;
import org.apache.lucene.search.spans.SpanOrQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;

import com.mhs.qsol.QsolParseException;
import com.mhs.qsol.QsolParser.Operator;
import com.mhs.qsol.QsolToQueryVisitor;
import com.mhs.qsol.abstractnode.VisitCheckOp;
import com.mhs.qsol.abstractnode.VisitCheckOp1;
import com.mhs.qsol.abstractnode.VisitCheckOp2;
import com.mhs.qsol.abstractnode.VisitCheckOp3;
import com.mhs.qsol.abstractnode.VisitCheckOp4;
import com.mhs.qsol.abstractnode.VisitOp;
import com.mhs.qsol.abstractnode.VisitOrd1;
import com.mhs.qsol.abstractnode.VisitOrd2;
import com.mhs.qsol.abstractnode.VisitOrd3;
import com.mhs.qsol.abstractnode.VisitOrd4;
import com.mhs.qsol.proximity.distribute.BasicDistributable;
import com.mhs.qsol.spans.SpanFuzzyQuery;
import com.mhs.qsol.spans.SpanWildcardQuery;
import com.mhs.qsol.syntaxtree.BasicSearchType;
import com.mhs.qsol.syntaxtree.CheckOrd1Search;
import com.mhs.qsol.syntaxtree.CheckOrd2Search;
import com.mhs.qsol.syntaxtree.CheckOrd3Search;
import com.mhs.qsol.syntaxtree.CheckOrd4Search;
import com.mhs.qsol.syntaxtree.FieldSearch;
import com.mhs.qsol.syntaxtree.NodeChoice;
import com.mhs.qsol.syntaxtree.Ord1Search;
import com.mhs.qsol.syntaxtree.Ord2Search;
import com.mhs.qsol.syntaxtree.Ord3Search;
import com.mhs.qsol.syntaxtree.Ord4Search;
import com.mhs.qsol.syntaxtree.ParenthesisSearch;
import com.mhs.qsol.syntaxtree.Search;
import com.mhs.qsol.syntaxtree.SearchToken;
import com.mhs.qsol.visitor.GJDepthFirst;

public class ProximityVisitor extends GJDepthFirst<Query, Query> {
  private static final Pattern BOOST_EXTRACTOR = Pattern
      .compile("^(.*?)\\^(\\d+(?:\\.\\d+)?)$");
  private final static Logger logger = Logger.getLogger(ProximityVisitor.class
      .getPackage().getName());
  private static final Map<Operator, Occur> opToOccur = new HashMap<Operator, Occur>();

  static {
    opToOccur.put(Operator.AND, Occur.MUST);
    opToOccur.put(Operator.ANDNOT, Occur.MUST_NOT);
    opToOccur.put(Operator.OR, Occur.SHOULD);
  }

  private boolean lowercaseExpandedTermsboolean = true;
  ProximityBuilder proxBuilder = new ProximityBuilder();
  private String field;
  private Analyzer analyzer;
  private int slop = 0; // 0 is the default slop for when phrases become SpanNearQuerys
  private List<Operator> orderOfOps = new ArrayList<Operator>();
  private float boost = 1;

  public ProximityVisitor(Analyzer analyzer, String field) {
    this.analyzer = analyzer;
    this.field = field;
  }

  public Query getQuery() {
    return proxBuilder.getQuery();
  }

  /**
   * f0 -> CheckOrd1Search() f1 -> <EOF>
   */
  public Query visit(Search n, Query query) {
    proxBuilder = new ProximityBuilder();

    n.f0.accept(this, null);

    return null;
  }

  /**
   * f0 -> CheckOrd2Search() f1 -> ( Ord1Search() )?
   */
  public Query visit(CheckOrd1Search n, Query query) {
    Operator op = orderOfOps.get(0);

    if (op != Operator.PROXIMITY) {
      visitCheckForBoolean(new VisitCheckOp1(n));

      return null;
    }

    visitCheckForProx(new VisitCheckOp1(n), query);

    return null;
  }

  /**
   * f0 -> <OR> f1 -> CheckOrd2Search() f2 -> ( Ord1Search() )?
   */
  public Query visit(Ord1Search n, Query query) {
    Operator op = orderOfOps.get(0);

    if (op != Operator.PROXIMITY) {
      visitBooleanOp(new VisitOrd1(n), opToOccur.get(op));

      return null;
    }

    visitProxOp(new VisitOrd1(n), query);

    return null;
  }

  /**
   * f0 -> CheckOrd3Search() f1 -> ( Ord2Search() )?
   */
  public Query visit(CheckOrd2Search n, Query query) {
    Operator op = orderOfOps.get(1);

    if (op != Operator.PROXIMITY) {
      visitCheckForBoolean(new VisitCheckOp2(n));

      return null;
    }

    visitCheckForProx(new VisitCheckOp2(n), query);

    return null;
  }

  /**
   * f0 -> <AND> f1 -> CheckOrd3Search() f2 -> ( Ord2Search() )?
   */
  public Query visit(Ord2Search n, Query query) {
    Operator op = orderOfOps.get(1);

    if (op != Operator.PROXIMITY) {
      visitBooleanOp(new VisitOrd2(n), opToOccur.get(op));

      return null;
    }

    visitProxOp(new VisitOrd2(n), query);

    return null;
  }

  /**
   * f0 -> CheckOrd4Search() f1 -> ( Ord3Search() )?
   */
  public Query visit(CheckOrd3Search n, Query query) {
    Operator op = orderOfOps.get(2);

    if (op != Operator.PROXIMITY) {
      visitCheckForBoolean(new VisitCheckOp3(n));

      return null;
    }

    visitCheckForProx(new VisitCheckOp3(n), query);

    return null;
  }

  /**
   * f0 -> <PROXIMITY> f1 -> CheckOrd4Search() f2 -> ( Ord3Search() )?
   */
  public Query visit(Ord3Search n, Query query) {
    Operator op = orderOfOps.get(2);

    if (op != Operator.PROXIMITY) {
      visitBooleanOp(new VisitOrd3(n), opToOccur.get(op));

      return null;
    }

    visitProxOp(new VisitOrd3(n), query);

    return null;
  }

  /**
   * f0 -> BasicSearchType() f1 -> ( Ord4Search() )?
   */
  public Query visit(CheckOrd4Search n, Query query) {
    Operator op = orderOfOps.get(3);

    if (op != Operator.PROXIMITY) {
      visitCheckForBoolean(new VisitCheckOp4(n));

      return null;
    }

    visitCheckForProx(new VisitCheckOp4(n), query);

    return null;
  }

  /**
   * f0 -> <BUTNOT> f1 -> BasicSearchType() f2 -> ( Ord4Search() )?
   */
  public Query visit(Ord4Search n, Query query) {
    Operator op = orderOfOps.get(3);

    if (op != Operator.PROXIMITY) {
      visitBooleanOp(new VisitOrd4(n), opToOccur.get(op));

      return null;
    }

    visitProxOp(new VisitOrd4(n), query);

    return null;
  }

  /**
   * f0 -> SearchToken() f1 -> "[" f2 -> CheckOrd1Search() f3 -> "]"
   */
  public Query visit(FieldSearch n, Query query) {
    throw new QsolParseException(
        "A field query cannot be nested in a proximity query");
  }

  /**
   * f0 -> <DATE> f1 -> "[" f2 -> SearchToken() f3 -> "]"
   */
  public void buildDateSearch(String fiel) {
    throw new QsolParseException(
        "A date query cannot be nested in a proximity query");
  }

  /**
   * f0 -> <MATCHALL> | <QUOTED> | <BOOSTEDQUOTED> | <RANGE> | <WILDCARD> | <FUZZY> | <BOOSTEDSEARCHTOKEN> | <SEARCHTOKEN>
   */
  public Query visit(SearchToken n, Query query) {
    String tokens = null;
    NodeChoice choice = (NodeChoice) n.f0;

    if (choice.which == 0) {
      // if <MATCHALL>
      throw new RuntimeException("MATCHALL not allowed in proximity search");
    } else if (choice.which == 1) {
      // if <QUOTED>
      Matcher m = QsolToQueryVisitor.GET_SLOP.matcher(choice.choice.toString());

      // check for slop
      if (m.matches()) {
        tokens = m.group(1);
        int holdSlop = slop;
        slop = Integer.parseInt(m.group(2));
        Query returnQuery = tokenToQuery(tokens);
        slop = holdSlop;

        proxBuilder.addDistrib(new BasicDistributable((SpanQuery) returnQuery));
        
        return null;
      } else {
        String tokensWithQuotes = choice.choice.toString();
        tokens = tokensWithQuotes.substring(1, tokensWithQuotes.length()-1);
        proxBuilder.addDistrib(new BasicDistributable(
            (SpanQuery) tokenToQuery(tokens)));

        return null;
      }
    } else if (choice.which == 2) {
      // if <BOOSTEDQUOTED>
      Matcher m = QsolToQueryVisitor.GET_SLOP_AND_BOOST.matcher(choice.choice
          .toString());

      // check for slop
      if (m.matches()) {
        tokens = m.group(1);
        int holdSlop = slop;

        String phraseSlop = m.group(2);

        if (phraseSlop != null) {
          slop = Integer.parseInt(m.group(2));
        }

        this.boost = Float.parseFloat(m.group(3));

        Query phraseQuery = tokenToQuery(tokens);
        phraseQuery.setBoost(this.boost);
        this.boost = 1;
        this.slop = holdSlop;

        proxBuilder.addDistrib(new BasicDistributable((SpanQuery) phraseQuery));

        return null;
      }

      throw new RuntimeException(
          "boosted quoted matched in javacc but not here");
    } else if (choice.which == 3) {
      // If <RANGE>
      throw new RuntimeException("RangeQuery found in proximity clause");
    } else if (choice.which == 4) {
      // If <WILDCARD>
      String term = choice.choice.toString();

      if (lowercaseExpandedTermsboolean) {
        term = term.toLowerCase();
      }

      SpanWildcardQuery wildQuery = new SpanWildcardQuery(new Term(field, term));

      proxBuilder.addDistrib(new BasicDistributable(wildQuery));

      return null;
    } else if (choice.which == 5) {
      // If <FUZZY>
      // logger.fine("fuzzy");
      String fuzzyString = choice.choice.toString();

      if (lowercaseExpandedTermsboolean) {
        fuzzyString = fuzzyString.toLowerCase();
      }

      // logger.fine(fuzzyString.substring(0, fuzzyString.length()-1));
      proxBuilder
          .addDistrib(new BasicDistributable(new SpanFuzzyQuery(new Term(field,
              fuzzyString.substring(0, fuzzyString.length() - 1)))));
      
      return null;
    } else if (choice.which == 6) {
      // If <BOOSTEDSEARCHTOKEN>
      System.out.println("boosted term:" +choice.choice.toString());
      // boosted term
      Matcher m = BOOST_EXTRACTOR.matcher(choice.choice.toString());

      if (m.matches()) {
        float boost = Float.parseFloat(m.group(2));
        this.boost = boost;
        System.out.println("match boost:" + boost);

        proxBuilder.addDistrib(new BasicDistributable(
            (SpanQuery) tokenToQuery(m.group(1))));
        this.boost = 1;

        return null;
      } else {
        throw new RuntimeException("Matched boosted in javacc but not here");
      }
    } else if (choice.which == 7) {
      // IF <SEARCHTOKEN>
      proxBuilder.addDistrib(new BasicDistributable(
            (SpanQuery) tokenToQuery(choice.choice.toString())));

      return null;
    } else {
      throw new RuntimeException(
              "Unexpected SearchToken node type in ProximityVisitor.visit: " +
              Integer.toString(choice.which)
              );
    }
  }

  /**
   * f0 -> "(" f1 -> CheckOrd1Search() f2 -> ")"
   */
  public Query visit(ParenthesisSearch n, Query query) {
    n.f1.accept(this, query);

    return null;
  }

  /**
   * f0 -> FieldSearch() | SearchToken() | ParenthesisSearch()
   */
  public Query visit(BasicSearchType n, Query query) {
    n.f0.accept(this, query);

    return null;
  }

  private void visitCheckForBoolean(VisitCheckOp op) {
    if (op.isF1Present()) {
      proxBuilder.startGroup();
    }

    op.visitf0(this, null);

    if (op.isF1Present()) {
      op.visitf1(this, null);
    }
  }

  /**
   * f1 -> CheckNextSearch() f2 -> ( CurrentSearch() )?
   */
  private void visitBooleanOp(VisitOp op, Occur occur) {
    op.visitf1(this, null);

    if (occur == Occur.MUST_NOT) {
      proxBuilder.addConnector(Occur.MUST);
    } else {
      proxBuilder.addConnector(occur);
    }

    proxBuilder.addConnector(occur);

    if (op.isF2Present()) {
      proxBuilder.addParentConnector(occur);
      op.visitf2(this, null);
    }

    proxBuilder.endGroup();
  }

  /**
   * Removes escape characters.
   */
  private String removeEscapeChars(String input) {
    char[] caSource = input.toCharArray();
    char[] caDest = new char[caSource.length];
    int j = 0;

    for (int i = 0; i < caSource.length; i++) {
      if ((caSource[i] != '\\') || ((i > 0) && (caSource[i - 1] == '\\'))) {
        caDest[j++] = caSource[i];
      }
    }

    return new String(caDest, 0, j);
  }

  /**
   * Converts a token, as defined in the qsol.jtb JavaCC file, into an
   * appropriate query.
   * 
   * @param token
   * @return
   */
  protected Query tokenToQuery(String token) {
    if (logger.isLoggable(Level.FINE)) {
      // logger.fine("Query tokenToQuery(String token) : token:" + token);
    }

    if (logger.isLoggable(Level.FINE)) {
      logger.fine("Query tokenToQuery(String token) : token:" + token);
    }

    token = removeEscapeChars(token);

    TokenStream source = analyzer.tokenStream(field, new StringReader(token));
    CharTermAttribute charTermAtrib = source.getAttribute(CharTermAttribute.class);
    OffsetAttribute offsetAtrib = source.getAttribute(OffsetAttribute.class);
    PositionIncrementAttribute posIncAtt = source.addAttribute(PositionIncrementAttribute.class);
    ArrayList<Token> v = new ArrayList<Token>();
    Token t;
    int positionCount = 0;
    boolean severalTokensAtSamePosition = false;

    while (true) {
      try {
        if (!source.incrementToken()) {
          break;
        }
        t = new Token(charTermAtrib.buffer(), 0, charTermAtrib.length(), offsetAtrib.startOffset(), offsetAtrib.endOffset());
        t.setPositionIncrement(posIncAtt.getPositionIncrement());
      } catch (IOException e) {
        t = null;
      }

      if (t == null) {
        break;
      }

      v.add(t);

      if (t.getPositionIncrement() != 0) {
        positionCount += t.getPositionIncrement();
      } else {
        severalTokensAtSamePosition = true;
      }
    }

    try {
      source.close();
    } catch (IOException e) {
      // ignore
    }

    if (v.size() == 0) {
      return null;
    } else if (v.size() == 1) {
      t = v.get(0);
      SpanTermQuery stq = new SpanTermQuery(new Term(field, new String(t.buffer(), 0, t.length())));
      stq.setBoost(this.boost);
      return stq;
    } else {
      if (severalTokensAtSamePosition) {
        if (positionCount == 1) {
          // no phrase query:
          SpanQuery[] spanQueries = new SpanQuery[v.size()];

          StringBuilder regex = new StringBuilder();

          for (int i = 0; i < v.size(); i++) {
            spanQueries[i] = new SpanTermQuery(
                new Term(field, regex.toString()));
          }

          return new SpanOrQuery(spanQueries);
        } else {
            // All the Tokens in each sub-list are positioned at the the same location.
            ArrayList<ArrayList<Token>> identicallyPositionedTokenLists =
                    new ArrayList<ArrayList<Token>>();
            for (int i = 0; i < v.size(); i++) {
              if ((i == 0) || (v.get(i).getPositionIncrement() > 0)) {
                identicallyPositionedTokenLists.add(new ArrayList<Token>());
              }
              ArrayList<Token> curList =
                      identicallyPositionedTokenLists.get(identicallyPositionedTokenLists.size()-1);
              curList.add(v.get(i));
            }

            ArrayList<SpanQuery> spanNearSubclauses = new ArrayList<SpanQuery>();
            for (int listNum = 0; listNum < identicallyPositionedTokenLists.size(); listNum++) {
              ArrayList<Token> curTokens = identicallyPositionedTokenLists.get(listNum);

              ArrayList<SpanTermQuery> curTermQueries = new ArrayList<SpanTermQuery>();
              for (int tokenNum = 0; tokenNum < curTokens.size(); tokenNum++) {
                SpanTermQuery termQuery = new SpanTermQuery(new Term(field, curTokens.get(tokenNum).term()));
                termQuery.setBoost(this.boost);
                curTermQueries.add(termQuery);
              }

              int size = curTermQueries.size();
              if (size <= 0)
                continue;
              else if (size == 1)
                spanNearSubclauses.add(curTermQueries.get(0));
              else
                spanNearSubclauses.add(new SpanOrQuery(curTermQueries.toArray(new SpanQuery[0])));
            }

            SpanNearQuery query = new SpanNearQuery((SpanQuery[]) spanNearSubclauses
                .toArray(new SpanQuery[0]), slop, true);

          return query;
        }
      } else {
        SpanTermQuery[] clauses = new SpanTermQuery[v.size()];
     
        for (int i = 0; i < v.size(); i++) {
          Token t2 = v.get(i);
          clauses[i] = new SpanTermQuery(new Term(field, new String(t2.buffer(), 0, t2.length())));
        }

        SpanNearQuery query = new SpanNearQuery(clauses, slop, true);

        return query;
      }
    }
  }

  private void visitProxOp(VisitOp op, Query query) {
    op.visitf1(this, query);

    proxBuilder.endGroup();
    proxBuilder.saveClause();

    // The proxHandler has collected the sub tree on both the left and right
    // side of
    // the proximity connector--now we distribute the left tree against the
    // right
    // tree to get the correct span queries to represent the complex proximity
    // query.
    proxBuilder.constructProximityQuery(op.getF0TokenImage(), field);

    if (op.isF2Present()) {
      // proxBuilder.clear();
      proxBuilder.startGroup();
      // proxBuilder.addDistrib(new
      // BasicDistributable((SpanQuery)proxBuilder.getQuery()));
      op.visitf2(this, query);
    }
  }

  private void visitCheckForProx(VisitCheckOp op, Query query) {
    ProximityBuilder storeBuilder = null;

    if (op.isF1Present()) {
      if (logger.isLoggable(Level.FINE)) {
        logger.fine("in nested prox ----------------------------->");
      }

      storeBuilder = proxBuilder;
      proxBuilder = new ProximityBuilder();

      proxBuilder.startGroup();
    }

    op.visitf0(this, query);

    if (op.isF1Present()) {
      proxBuilder.endGroup();
      proxBuilder.saveClause();
      proxBuilder.startGroup();

      op.visitf1(this, query);

      // proxBuilder.clearDistribs();
      SpanQuery spanQuery = (SpanQuery) proxBuilder.getQuery();

      if (logger.isLoggable(Level.FINE)) {
        logger.fine("spanquery:" + proxBuilder.getQuery());
      }

      proxBuilder = storeBuilder;
      proxBuilder.addDistrib(new BasicDistributable(spanQuery));

      if (logger.isLoggable(Level.FINE)) {
        logger.fine("out of nested prox <-----------------------------");
      }
    }
  }

  public void startGroup() {
    proxBuilder.startGroup();
  }

  public void endGroup() {
    proxBuilder.endGroup();
  }

  public void promote() {
    proxBuilder.saveClause();
  }

  public void setAnalyzer(Analyzer analyzer) {
    this.analyzer = analyzer;
  }

  /**
   * @return the orderOfOps
   */
  public List<Operator> getOrderOfOps() {
    return orderOfOps;
  }

  /**
   * @param orderOfOps
   *          the orderOfOps to set
   */
  public void setOrderOfOps(List<Operator> orderOfOps) {
    this.orderOfOps = orderOfOps;
  }

  public void setParaMarker(String para) {
    proxBuilder.setParaMarker(para);
  }

  public void setSentMarker(String sent) {
    proxBuilder.setSentMarker(sent);
  }

  public void setFieldBreakMarker(String fieldBreak) {
    proxBuilder.setFieldBreakMarker(fieldBreak);
  }

  public void setLowercaseExpandedTermsboolean(
      boolean lowercaseExpandedTermsboolean) {
    this.lowercaseExpandedTermsboolean = lowercaseExpandedTermsboolean;
  }
}
