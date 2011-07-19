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
package com.mhs.qsol;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.ConstantScoreRangeQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.spans.SpanNearQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.lucene.store.Directory;

import com.mhs.qsol.QsolParser.Operator;
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
import com.mhs.qsol.proximity.ProximityVisitor;
import com.mhs.qsol.syntaxtree.BasicSearch;
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

/**
 * Translates QSol query language to a Lucene Query
 * 
 * @author Mark Miller <markrmiller@gmail.com> Aug 26, 2006
 * 
 */
public class QsolToQueryVisitor extends GJDepthFirst<Query, Query> {
  public static final Pattern GET_SLOP = Pattern.compile("^\"(.*)\":(\\d+)$");
  public static final Pattern GET_SLOP_AND_BOOST = Pattern
      .compile("^\"(.*)\"(?::(\\d+))?\\^(\\d+(?:\\.\\d+)?)$");

  // private static final Pattern RANGE_EXTRACTOR2 = Pattern.compile(
  // "^(.+)\\srng\\s(.+)$", Pattern.CASE_INSENSITIVE);
  static final Pattern RANGE_EXTRACTOR = Pattern
      .compile("^([\\[{])?(.*?) (TO|RNG|rng) (.*?)([}\\]])?$");
  private static final Pattern BOOST_EXTRACTOR = Pattern
      .compile("^(.*?)\\^(\\d+(?:\\.\\d+)?)$");
  private final static Logger logger = Logger
      .getLogger(QsolToQueryVisitor.class.getPackage().getName());
  private static final Map<Operator, List<Occur>> opToOccur = new HashMap<Operator, List<Occur>>(
      3);
  private static final Map<String, List<Occur>> rfOpToOccur = new HashMap<String, List<Occur>>(
      3);

  static {
    List<Occur> and = new ArrayList<Occur>(2);
    and.add(Occur.MUST);
    and.add(Occur.MUST);

    List<Occur> andNot = new ArrayList<Occur>(2);
    andNot.add(Occur.MUST);
    andNot.add(Occur.MUST_NOT);

    List<Occur> or = new ArrayList<Occur>(2);
    or.add(Occur.SHOULD);
    or.add(Occur.SHOULD);

    opToOccur.put(Operator.AND, and);
    opToOccur.put(Operator.ANDNOT, andNot);
    opToOccur.put(Operator.OR, or);

    List<Occur> rfAndNot = new ArrayList<Occur>(2);
    rfAndNot.add(Occur.MUST);
    rfAndNot.add(Occur.MUST_NOT);

    rfOpToOccur.put("&&", and);
    rfOpToOccur.put("||", or);
    rfOpToOccur.put("!!", rfAndNot);
  }

  private Set<String> dateFields = new HashSet<String>();
  private String field;
  private Analyzer analyzer;
  private Locale locale = Locale.getDefault();
  private boolean[] opChain = new boolean[] { false, false, false, false };
  private int slop = 0;
  private Directory didyoumeanIndex = null;
  private DateParser dateParser;
  private List<Operator> orderOfOps = new ArrayList<Operator>();
  private String paraMarker;
  private String sentMarker;
  private boolean lowercaseExpandedTerms = true;
  private String fieldBreakMarker;
  private float boost = 1;
  private Map<String, String> fieldMap = new HashMap<String, String>(4);

  public void setDateFields(Set<String> dateFields) {
    this.dateFields = dateFields;
  }

  public void setField(String field) {
    this.field = field;
  }

  public void setAnalyzer(Analyzer analyzer) {
    this.analyzer = analyzer;
  }

  /**
   * f0 -> CheckOrd1Search() f1 -> <EOF>
   */
  public Query visit(Search n, Query query) {
    if (analyzer == null) {
      throw new IllegalStateException("analyzer not set");
    }

    Query returnQuery = null;

    if (dateParser == null) {
      dateParser = new DefaultDateParser();
    }

    returnQuery = n.f0.accept(this, query);

    if (logger.isLoggable(Level.FINE)) {
      logger.fine("query:" + returnQuery);
    }

    return returnQuery;
  }

  /**
   * Check for first order operation f0 -> CheckOrd2Search() f1 -> (
   * Ord1Search() )?
   */
  public Query visit(CheckOrd1Search n, Query query) {
    Operator op = orderOfOps.get(0);

    if (op != Operator.PROXIMITY) {
      Query q = visitCheckForBoolean(new VisitCheckOp1(n), query);

      return q;
    }

    return visitCheckForProx(new VisitCheckOp1(n), query);
  }

  /**
   * f0 -> <1st order operator> f1 -> CheckOrd2Search() f2 -> ( Ord1Search() )?
   */
  public Query visit(Ord1Search n, Query query) {
    List<Occur> occurs = null;

    if (n.f0.tokenImage.length() == 2) {
      occurs = rfOpToOccur.get(n.f0.tokenImage.substring(0, 1));
    } else {
      occurs = opToOccur.get(orderOfOps.get(0));
    }

    return visitBooleanOp(new VisitOrd1(n), query, occurs);
  }

  /**
   * Check for second order operation f0 -> CheckOrd3Search() f1 -> (
   * Ord2Search() )?
   */
  public Query visit(CheckOrd2Search n, Query query) {
    Operator op = orderOfOps.get(1);

    if (op != Operator.PROXIMITY) {
      Query q = visitCheckForBoolean(new VisitCheckOp2(n), query);

      return q;
    }

    return visitCheckForProx(new VisitCheckOp2(n), query);
  }

  /**
   * f0 -> <2nd order operator> f1 -> CheckOrd3Search() f2 -> ( Ord2Search() )?
   */
  public Query visit(Ord2Search n, Query query) {
    List<Occur> occurs = null;

    if (n.f0.tokenImage.length() == 2) {
      occurs = rfOpToOccur.get(n.f0.tokenImage.substring(0, 1));
    } else {
      occurs = opToOccur.get(orderOfOps.get(1));
    }

    return visitBooleanOp(new VisitOrd2(n), query, occurs);
  }

  /**
   * Check for 3rd order operator f0 -> CheckOrd4Search() f1 -> ( Ord3Search()
   * )?
   */
  public Query visit(CheckOrd3Search n, Query query) {
    Operator op = orderOfOps.get(2);

    if (op != Operator.PROXIMITY) {
      return visitCheckForBoolean(new VisitCheckOp3(n), query);
    }

    return visitCheckForProx(new VisitCheckOp3(n), query);
  }

  /**
   * f0 -> <3rd order operator> f1 -> CheckOrd4Search() f2 -> ( Ord3Search() )?
   */
  public Query visit(Ord3Search n, Query query) {
    List<Occur> occurs = null;

    if (n.f0.tokenImage.length() == 2) {
      occurs = rfOpToOccur.get(n.f0.tokenImage.substring(0, 1));
    } else {
      occurs = opToOccur.get(orderOfOps.get(2));
    }

    return visitBooleanOp(new VisitOrd3(n), query, occurs);
  }

  /**
   * Check for 4th order operator f0 -> (BasicSearchType())+ f1 -> (
   * Ord4Search() )?
   */
  public Query visit(CheckOrd4Search n, Query query) {
    Operator op = orderOfOps.get(3);

    if (op != Operator.PROXIMITY) {
      return visitCheckForBoolean(new VisitCheckOp4(n), query);
    }

    return visitCheckForProx(new VisitCheckOp4(n), query);
  }

  /**
   * f0 -> <4th order operator> f1 -> BasicSearchType() f2 -> ( Ord4Search() )?
   */
  public Query visit(Ord4Search n, Query query) {
    List<Occur> occurs = null;

    if (n.f0.tokenImage.length() == 2) {
      occurs = rfOpToOccur.get(n.f0.tokenImage);
    } else {
      occurs = opToOccur.get(orderOfOps.get(3));
    }

    return visitBooleanOp(new VisitOrd4(n), query, occurs);
  }

  /**
   * f0 -> <FIELDSTART> f1 -> CheckOrd1Search() f2 -> ")"
   */
  public Query visit(FieldSearch n, Query query) {
    Query returnQuery = null;

    // get comma delimited field list
    String fieldList = n.f0.toString();

    String[] fields = fieldList.split(",");

    BooleanQuery fieldSearch = new BooleanQuery();
    // save the query field
    String oldField = field;
    // apply the search to each field
    for (int x = 0; x < fields.length; x++) {
      field = fields[x];

      String mapTo;

      if ((mapTo = fieldMap.get(field)) != null) {
        field = mapTo;
      }

      // if the field is a registered date field, build date
      if (dateFields.contains(field)) {
        StringBuilder date = new StringBuilder();

        BasicSearch tokens = n.f1.f0.f0.f0.f0;

        // (SearchToken) n.f2.f0.f0.f0.f0.f0.choice;
        int size = tokens.f0.size();

        for (int i = 0; i < size; i++) {
          // NodeChoice choice = (NodeChoice) tokens.f0.elementAt(i);
          // date.append(choice.choice.toString());
          SearchToken choice = (SearchToken) ((BasicSearchType) tokens.f0
              .elementAt(i)).f0.choice;
          date.append(choice.f0.choice);

          if (i != (size - 1)) {
            date.append(" ");
          }
        }

        returnQuery = dateParser.buildDateQuery(field, date.toString(), locale);
      } else {

        returnQuery = n.f1.accept(this, query);

      }

      if (returnQuery != null) {
        fieldSearch.add(returnQuery, BooleanClause.Occur.SHOULD);
      }
    }
    field = oldField;
    if (fieldSearch.getClauses().length == 0) {
      return null;
    }
    return fieldSearch;
  }

  /**
   * f0 -> <QUOTED> | <WILDCARD> | <FUZZY> | <SEARCHTOKEN>
   */
  public Query visit(SearchToken n, Query query) {
    Query returnQuery = null;

    String tokens = null;
    NodeChoice choice = (NodeChoice) n.f0;

    if (choice.which == 0) {
      return new MatchAllDocsQuery();

    } else if (choice.which == 1) {
      Matcher m = GET_SLOP.matcher(choice.choice.toString());
      int holdSlop = 1;

      // check for slop
      if (m.matches()) {
        tokens = m.group(1);
        holdSlop = slop;
        slop = Integer.parseInt(m.group(2));
      } else {
        return tokenToQuery(choice.choice.toString());
      }

      returnQuery = tokenToQuery(tokens);
      slop = holdSlop;

      return returnQuery;
    } else if (choice.which == 2) {
      Matcher m = GET_SLOP_AND_BOOST.matcher(choice.choice.toString());
      int holdSlop = 1;

      // check for slop
      if (m.matches()) {
        tokens = m.group(1);
        holdSlop = slop;

        String phraseSlop = m.group(2);

        if (phraseSlop != null) {
          slop = Integer.parseInt(m.group(2));
        }

        this.boost = Float.parseFloat(m.group(3));

        Query phraseQuery = tokenToQuery(tokens);
        this.boost = 1;
        this.slop = holdSlop;

        return phraseQuery;
      }

      throw new RuntimeException(
          "boosted quoted matched in javacc but not here");
    } else if (choice.which == 3) {
      Matcher m = RANGE_EXTRACTOR.matcher(choice.choice.toString());
      boolean inclusive1 = false;
      boolean inclusive2 = false;

      if (m.matches()) {
        if (m.group(1) != null && m.group(1).equals("[")) {
          inclusive1 = true;
        }

        if (m.group(1) != null && m.group(5).equals("]")) {
          inclusive2 = true;
        }

        String term1 = m.group(2);
        String term2 = m.group(4);

        if (lowercaseExpandedTerms) {
          term1 = term1.toLowerCase();
          term2 = term2.toLowerCase();
        }

        return new ConstantScoreRangeQuery(field, term1, term2, inclusive1,
            inclusive2);
      } else {
        throw new RuntimeException(
            "Range did not match in QsolToQueryVisitor but did with JavaCC parser");
      }
    } else if (choice.which == 4) {
      // Wildcard
      String term = choice.choice.toString();

      if (lowercaseExpandedTerms) {
        term = term.toLowerCase();
      }

      return new WildcardQuery(new Term(field, term));
    } else if (choice.which == 5) {
      // Fuzzyquery
      String fuzzyString = choice.choice.toString();

      if (lowercaseExpandedTerms) {
        fuzzyString = fuzzyString.toLowerCase();
      }

      return new FuzzyQuery(new Term(field, fuzzyString.substring(0,
          fuzzyString.length() - 1)));
    } else if (choice.which == 6) {
      // boosted term
      Matcher m = BOOST_EXTRACTOR.matcher(choice.choice.toString());

      if (m.matches()) {
        float boost = Float.parseFloat(m.group(2));
        this.boost = boost;

        Query boostQuery = tokenToQuery(m.group(1));
        this.boost = 1;

        return boostQuery;
      } else {
        throw new RuntimeException("Matched boosted in javacc but not here");
      }
    }

    // must be search token
    return tokenToQuery(choice.choice.toString());
  }

  /**
   * f0 -> "(" f1 -> CheckOrd1Search() f2 -> ")"
   */
  public Query visit(ParenthesisSearch n, Query query) {
    Query returnQuery = null;

    returnQuery = n.f1.accept(this, query);

    return returnQuery;
  }

  /**
   * f0 -> ( BasicSearchType() )+
   */
  public Query visit(BasicSearch n, Query query) {
    Query returnQuery = n.f0.elementAt(0).accept(this, query);

    return returnQuery;
  }

  /**
   * f0 -> FieldSearch() | SearchToken() | ParenthesisSearch()
   */
  public Query visit(BasicSearchType n, Query query) {
    Query returnQuery = null;
    returnQuery = n.f0.accept(this, query);

    return returnQuery;
  }

  /**
   * There is no need for a visitProx because check for prox creates a new prox
   * visitor.
   * 
   * @param op
   * @param query
   * @return
   */
  private Query visitCheckForProx(VisitCheckOp op, Query query) {
    Query returnQuery = null;

    ProximityVisitor proxVisitor = new ProximityVisitor(analyzer, field);
    proxVisitor.setOrderOfOps(this.orderOfOps);

    proxVisitor.setParaMarker(paraMarker);
    proxVisitor.setSentMarker(sentMarker);

    proxVisitor.setFieldBreakMarker(fieldBreakMarker);

    if (op.isF1Present()) {
      proxVisitor.startGroup();
      op.visitf0(proxVisitor, null);

      proxVisitor.endGroup();
      proxVisitor.promote();
    } else {
      returnQuery = op.visitf0(this, query);
    }

    if (op.isF1Present()) {
      proxVisitor.startGroup();

      op.visitf1(proxVisitor, null);

      returnQuery = proxVisitor.getQuery();
    }

    return returnQuery;
  }

  /**
   * f0 -> CheckNextOp() f1 -> ( CheckCurrentOp() )?
   */
  private Query visitCheckForBoolean(VisitCheckOp op, Query query) {
    Query returnQuery = null;

    returnQuery = op.visitf0(this, query);

    if (op.isF1Present()) {
      returnQuery = op.visitf1(this, returnQuery);
    }

    return returnQuery;
  }

  /**
   * f0 -> <operator> f1 -> CheckNextOp() f2 -> ( CurrentOp() )?
   */
  private Query visitBooleanOp(VisitOp op, Query query, List<Occur> occurs) {
    // run down right side of OPERATOR
    Query returnQuery = op.visitf1(this, query);

    // check for stop word removal
    if (returnQuery == null) {
      // if another of the same op follows
      if (op.isF2Present()) {

        returnQuery = op.visitf2(this, query);

        return returnQuery;
      } else {

        return query;
      }
    }

    if (query == null) {
      // if another of the same op follows
      if (op.isF2Present()) {
        query = op.visitf2(this, returnQuery);

        return query;
      } else {

        return returnQuery;
      }
    }

    // done stop word check
    BooleanQuery boolQuery = null;

    // if in an op chain i.e. mark & horse & beer : the second & is in an op
    // chain
    if (opChain[op.getOpNum() - 1] && query instanceof BooleanQuery) {
      boolQuery = (BooleanQuery) query;
      boolQuery.add(returnQuery, occurs.get(1));
    } else {
      boolQuery = new BooleanQuery();

      boolQuery.add(query, occurs.get(0));

      boolQuery.add(returnQuery, occurs.get(1));
    }

    // if another of the same op follows set that we are in an op chain
    if (op.isF2Present()) {
      opChain[op.getOpNum() - 1] = true;

      Query nextQuery = op.visitf2(this, boolQuery);
      opChain[op.getOpNum() - 1] = false;

      return nextQuery;
    }

    return boolQuery;
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

    token = removeEscapeChars(token);

    TokenStream source = analyzer.tokenStream(field, new StringReader(token));
    ArrayList<Token> v = new ArrayList<Token>();
    Token t;
    int positionCount = 0;
    boolean severalTokensAtSamePosition = false;

    while (true) {
      try {
        t = source.next();
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
      // null's will get cleaned up in visitBooleanOp
      return null;
    } else if (v.size() == 1) {

      t = v.get(0);

      TermQuery termQuery = new TermQuery(new Term(field, t.termText()));
      termQuery.setBoost(this.boost);

      return termQuery;
    } else {
      if (severalTokensAtSamePosition) {
        if (positionCount == 1) {
          // no phrase query:
          BooleanQuery q = new BooleanQuery(true);

          for (int i = 0; i < v.size(); i++) {
            t = v.get(i);

            TermQuery currentQuery = new TermQuery(
                new Term(field, t.termText()));
            currentQuery.setBoost(this.boost);

            q.add(currentQuery, BooleanClause.Occur.SHOULD);
          }

          return q;
        } else {
          List<SpanQuery> clauses = new ArrayList<SpanQuery>(v.size());

          for (int i = 0; i < v.size(); i++) {
            // TODO: handle this?
            // if (t.getPositionIncrement() == 0) {
            // }
            SpanQuery termQuery = new SpanTermQuery(new Term(field, v.get(i)
                .termText()));
            termQuery.setBoost(this.boost);
            clauses.set(i, termQuery);
          }

          SpanNearQuery query = new SpanNearQuery((SpanQuery[]) clauses
              .toArray(new SpanQuery[0]), slop, true);

          return query;
        }
      } else {
        SpanTermQuery[] clauses = new SpanTermQuery[v.size()];

        for (int i = 0; i < v.size(); i++) {
          SpanTermQuery spanQuery = new SpanTermQuery(new Term(field, v.get(i)
              .termText()));
          spanQuery.setBoost(boost);
          clauses[i] = spanQuery;
        }

        SpanNearQuery query = new SpanNearQuery(clauses, slop, true);

        return query;
      }
    }
  }

  public Locale getLocale() {
    return locale;
  }

  public void setLocale(Locale locale) {
    this.locale = locale;
  }

  /**
   * @return the didyoumeanIndex
   */
  public Directory getDidyoumeanIndex() {
    return didyoumeanIndex;
  }

  /**
   * @param didyoumeanIndex
   *          the didyoumeanIndex to set
   */
  public void setDidyoumeanIndex(Directory didyoumeanIndex) {
    this.didyoumeanIndex = didyoumeanIndex;
  }

  /**
   * @return the dateParser
   */
  public DateParser getDateParser() {
    return dateParser;
  }

  /**
   * @param dateParser
   *          the dateParser to set
   */
  public void setDateParser(DateParser dateParser) {
    this.dateParser = dateParser;
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

  /**
   * @return the paraMarker
   */
  public String getParaMarker() {
    return paraMarker;
  }

  /**
   * @param paraMarker
   *          the paraMarker to set
   */
  public void setParaMarker(String paraMarker) {
    this.paraMarker = paraMarker;
  }

  /**
   * @return the sentMarker
   */
  public String getSentMarker() {
    return sentMarker;
  }

  /**
   * @param sentMarker
   *          the sentMarker to set
   */
  public void setSentMarker(String sentMarker) {
    this.sentMarker = sentMarker;
  }

  public void setLowercaseExpandedTerms(boolean lowercaseExpandedTerms) {
    this.lowercaseExpandedTerms = lowercaseExpandedTerms;
  }

  public String getFieldBreakMarker() {
    return fieldBreakMarker;
  }

  public void setFieldBreakMarker(String fieldBreakMarker) {
    this.fieldBreakMarker = fieldBreakMarker;
  }

  public void addFieldMapping(String field2, String mapToField) {
    this.fieldMap.put(field2, mapToField);
  }

  public void addFieldMappings(Map<String, String> fieldMap) {
    this.fieldMap.putAll(fieldMap);
  }
}
