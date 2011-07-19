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

import com.mhs.qsol.queryparser.QueryParser;
import com.mhs.qsol.queryparser.QueryParserConstants;
import com.mhs.qsol.syntaxtree.Node;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.LogManager;
import java.util.regex.Pattern;

/**
 * Converts any valid Qsol syntax into an Apache Lucene Query object.
 * <p>
 * Qsol requires Java 1.5
 * <p>
 * Create a new QsolParser using the <code>ParserFactory</code>. The
 * <code>ParserFactory</code> is a singleton that returns new query parsers. You
 * can create query parsers that inherit their settings from the factory or
 * query parsers with no starting settings.
 * <p>
 * Overview of the QsolQueryParser:
 * <p>
 * Basic Operators and Order of Operations:
 * <p>
 * Note: all of the operators are <i>binary</i> operators. There are no unary
 * operators in Qsol syntax.
 * <p>
 * <li>1.'( )' parenthesis�: me & (him | her)</li>
 * <li>2.'!' and not�: mill�! bucketloader</li>
 * <li>3.'~' within�: score ~5 lunch�: use ord to only find terms in order�:
 * score ord~5 lunch</li>
 * <li>4.'&' and�: beat & pony</li>
 * <li>5.'|' or�: him | her</li>
 * 
 * The Order of Operations is configurable using the setOpsOrder method on
 * QsolQueryParser. You can make the default space operator bind at the level of
 * parenthesis by using the setTightSpaceBind method. That way, even if the
 * default space operator is an &, it will bind tighter than an explicit &.
 * <p>
 * Escape - A '\' will escape an operator�: m\&m's
 * <p>
 * Quotes - an in-order phrase search with or without a specified slop�: "holy
 * war sick":3 | "gimme all my cake"
 * <p>
 * Range Queries - a query in the form: beginword - endword will perform a range
 * search. The default search is inclusive. For an exclusive search use '--'
 * instead of '-'�: creditcard[23907094 - 23094345] | creditcard[23907094 --
 * 23094345] You can also use rng instead of '-'.
 * <p>
 * Wildcards - * indicates zero or more unknowns and�? indicates a single
 * unknown�: old harr*t?n | kil?r A wildcard query cannot begin with an unknown.
 * <p>
 * Fuzzy Query�: a ` indicates the preceding term should be a fuzzy term�: old
 * carrot & devil` may cry
 * <p>
 * Paragraph/Sentence Proximity Searching If you have enabled sentence and
 * paragraph proximity searching then the '~' operator may also be used as '~3p'
 * or '~5s' to perform paragraph and sentence proximity searches. Paragraph and
 * sentence proximity searching is implemented using special tokens that must be
 * put into the index at appropriate positions. It is up to you to inject the
 * tokens into the index and then identify them to the QsolParser with
 * <code>setSentenceMarker(String marker)</code> and
 * <code>setParagraphMarker(String marker)</code>.
 * <p>
 * Thesaurus: Set<String> words = new HashSet<String>(); words.add("test1");
 * words.add("test2"); words.add("test3"); parser.addThesaurusEntry("test",
 * words, false);
 * <p>
 * Find Replace: parser.addFindReplace(new FindReplace("NEAR", "~10", true,
 * true));
 * 
 * @see ParserFactory
 * 
 * @author Mark Miller <markrmiller@gmail.com> Aug 26, 2006
 * @version %I%, %G%
 * @since 1.0
 */
@SuppressWarnings("unchecked")
public class QsolParser {
  static {
    LogManager manager = LogManager.getLogManager();
    InputStream is = Thread.currentThread().getContextClassLoader()
        .getResourceAsStream("qlogger.properties");

    if (is != null) {
      try {
        manager.readConfiguration(is);
      } catch (SecurityException e) {
        throw new RuntimeException(e);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    } else {
      System.out
          .println("could not locate qsol logging def file, using java defaults");
    }
  }

  private Locale locale = Locale.getDefault();
  private PreProcessVisitor preProcessVisitor = new PreProcessVisitor();
  private QsolToQueryVisitor buildQueryVisitor = new QsolToQueryVisitor();
  private Map<String, FindReplace> findReplace = new HashMap<String, FindReplace>();
  private Set<FindReplaceRegEx> findReplaceRegEx = new HashSet<FindReplaceRegEx>();
  private Map<Operator, Integer> orderOfOpsMap = new HashMap<Operator, Integer>(
      4);
  private List<Operator> opsList = new ArrayList<Operator>(4);
  private Set<String> dateFields = new HashSet<String>();
  private boolean hideOr;
  private boolean hideAnd;
  private boolean hideAndNot;
  private boolean hideProximity;
  private boolean useHide;

  public QsolParser(QsolConfiguration config, boolean inherit) {
    if (inherit) {
      this.dateFields.addAll(config.dateFields);
      this.findReplace.putAll(config.findReplace);
      this.findReplaceRegEx.addAll(config.findReplaceRegEx);
      this.buildQueryVisitor.setDateParser(config.dateParser);
      this.setParagraphMarker(config.paragraphMarker);
      this.setSentenceMarker(config.sentenceMarker);
      this.setFieldBreakMarker(config.fieldBreakMarker);
      this.buildQueryVisitor.addFieldMappings(config.fieldMapping);
      if (config.zeroPadFields != null) {
        preProcessVisitor.setZeroPadFields(config.zeroPadFields);
      }
    }

    this.opsList.addAll(config.opsList);
    this.orderOfOpsMap.putAll(config.orderOfOpsMap);
  }

  public static void main(String[] args) {
    QsolParser parser = ParserFactory.getInstance(new QsolConfiguration())
        .getParser(false);

    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    String query = null;

    while (true) {
      System.out.print("Enter query: ");

      try {
        query = br.readLine();
      } catch (IOException ioe) {
        System.exit(1);
      }

      parser.markDateField("date");

      Query result = null;

      try {
        result = parser.parse("allFields", query, new StandardAnalyzer());
      } catch (QsolSyntaxException e) {
        System.out.println("syntax exception:");
        e.printStackTrace();
      } catch (EmptyQueryException e) {
      }

      System.out.println("lucene query:" + result.toString());
    }
  }

  public void addFieldMapping(String field, String mapToField) {
    this.buildQueryVisitor.addFieldMapping(field, mapToField);
  }

  /**
   * Adds a <code>FindReplace</code> expander to the pre-processor. When a token
   * is matched, it will be replaced by the FindReplace replace value. Do not
   * use the default space operator in the replacement syntax- any other valid
   * Qsol syntax is fine i.e. 'donut' might map to '(coffee & donute | (dunken &
   * donut)'. Note that the replacement search must use <i>basic</i> Qsol
   * syntax.
   * 
   * @param findReplace
   *          find/replace map
   * @since 1.0
   */
  public void addFindReplace(FindReplace findReplace) {
    this.findReplace.put(findReplace.getFind().toLowerCase(), findReplace);
  }

  /**
   * Adds a <code>FindReplaceRegEx</code> expander to the pre-processor. When
   * the <code>FindReplaceRegEx</code> <code>Pattern</code> matches a token , it
   * will be replaced by the FindReplaceRegEx replace value. The replace value
   * can contain back references to captured groups using $1, $2, $3, etc. Do
   * not use the default space operator in the replacement syntax- any other
   * valid Qsol syntax is fine i.e. 'donut' might map to '(coffee & donute |
   * (dunken & donut)'. Note that the replacement search must use <i>basic</i>
   * Qsol syntax.
   * 
   * @param findReplaceRegEx
   * 
   * @since 1.0
   */
  public void addFindReplaceRegEx(FindReplaceRegEx findReplaceRegEx) {
    this.findReplaceRegEx.add(findReplaceRegEx);
  }

  public void add0PadField(String field, int pad) {
    this.preProcessVisitor.add0PadField(field, pad);
  }

  /**
   * Adds a new Operator to the search syntax.
   * 
   * @param op
   *          the basic operator that the new operator will be mapped to
   * @param newOp
   *          the new operator
   * @since 1.0
   */
  public void addOperator(Operator op, String newOp, boolean caseSensitive) {
    switch (op) {
    case AND:
      findReplace.put(newOp.toLowerCase(), new FindReplace(newOp, "&",
          caseSensitive, true));

      break;

    case OR:
      findReplace.put(newOp.toLowerCase(), new FindReplace(newOp, "|",
          caseSensitive, true));

      break;

    case ANDNOT:
      findReplace.put(newOp.toLowerCase(), new FindReplace(newOp, "!",
          caseSensitive, true));

      break;

    case PROXIMITY:

      Pattern pattern;

      if (caseSensitive) {
        pattern = Pattern.compile("(ord)?" + newOp + "(\\d*)([s,p])?");
      } else {
        pattern = Pattern.compile("(ord)?" + newOp + "(\\d*)([s,p])?",
            Pattern.CASE_INSENSITIVE);
      }

      findReplaceRegEx.add(new FindReplaceRegEx(pattern, "$1~$2$3", true));
    }
  }

  /**
   * When a search token of <code>word</code> is found it will be replaced with
   * <code>words</code>. This method is a convenience method that uses the
   * find/replace functionality.
   * 
   * @param word
   *          term to replace
   * @param words
   *          replacement terms
   * @since 1.0
   */
  public void addThesaurusEntry(String word, Set<String> words,
      boolean caseSensitive) {
    StringBuilder wordsExpansion = new StringBuilder();
    int count = 0;

    for (String singleWord : words) {
      wordsExpansion.append(singleWord);

      if (count < (words.size() - 1)) {
        wordsExpansion.append(" | ");
      }

      count++;
    }

    this.findReplace.put(word.toLowerCase(), new FindReplace(word, "("
        + wordsExpansion + ")", caseSensitive, false));
  }

  /**
   * Clears the parser's internal settings.
   * 
   * @since 1.0
   */
  public void clearSettings() {
    orderOfOpsMap.clear();
    findReplace.clear();
    findReplaceRegEx.clear();
    hideAnd = false;
    hideAndNot = false;
    hideOr = false;
    hideProximity = false;
    dateFields.clear();
    this.buildQueryVisitor.setParaMarker("");
    this.buildQueryVisitor.setSentMarker("");
  }

  public Map<String, FindReplace> getFindReplace() {
    return findReplace;
  }

  /**
   * Returns the locale used for date parsing.
   * 
   * @return current locale used for date parsing
   * @since 1.0
   */
  public Locale getLocale() {
    return locale;
  }

  /**
   * Returns the order of operations list.
   * 
   * @return the order of operations list
   * @since 1.0
   */
  public List<Operator> getOpsOrder() {
    return opsList;
  }

  /**
   * Returns a suggested search for the last Query. Will return an empty String
   * if no suitable suggestion is found. The majority of the work involved in
   * generating the suggested query is not done unless this method is called.
   * 
   * @return suggested search query for last submitted query
   * @since 1.0
   */
  public String getSuggestedSearch() {
    String suggestedSearch = this.preProcessVisitor.getSuggestedSearch();

    if (suggestedSearch == null) {
      throw new IllegalStateException("No Suggest index has been set");
    }

    return suggestedSearch;
  }

  /**
   * Disables/Enables the specified operators.
   * 
   * @param or
   * @param and
   * @param andnot
   * @param proximity
   * @since 1.0
   */
  public void hideOperators(boolean or, boolean and, boolean andnot,
      boolean proximity) {
    this.useHide = true;
    this.hideOr = or;
    this.hideAnd = and;
    this.hideAndNot = andnot;
    this.hideProximity = proximity;
  }

  /**
   * Marks <code>field</code> to be treated as a date field during parsing.
   * 
   * @param dateFields
   *          set of fields to be processed as date fields
   * @since 1.0
   */
  public void markDateField(String field) {
    dateFields.add(field);
  }

  /**
   * Converts Qsol query syntax into a Lucene Query object.
   * 
   * @param field
   *          default search field
   * @param query
   *          Qsol syntax query
   * @param analyzer
   *          lucene analyzer to use on terms
   * @return
   * @throws QsolSyntaxException
   * @throws EmptyQueryException
   * @since 1.0
   */
  public Query parse(String field, String query, Analyzer analyzer)
      throws QsolSyntaxException, EmptyQueryException {
    Node root = null;
    // System.out.println("query:" + query);
    preProcessVisitor.setAnalyzer(analyzer);

    buildQueryVisitor.setOrderOfOps(opsList);

    preProcessVisitor.setDateFields(dateFields);
    buildQueryVisitor.setDateFields(dateFields);

    // Two passes over the tree--the first handles the default space
    // operator,
    // performs find/replace expansion, and preps the suggested search if
    // enabled.
    // The second builds the lucene query from the Qsol syntax parse tree.
  
      QueryParser parser = new QueryParser(new StringReader(query));

      if (useHide) {
        parser.setHideOps(hideOr, hideAnd, hideAndNot, hideProximity);
      }

      parser.setOrderOfOps(orderOfOpsMap);
      assert (parser != null);
      // build parse tree
      try {
        root = parser.Search();
      } catch(com.mhs.qsol.queryparser.ParseException e) {
        throw new QsolParseException(e);
//        e.printStackTrace();
//        query = escape(query).toString();
//        System.out.println("new query:" + query);
//        return parse(field, query, analyzer);
      } 
  

    // run over parse tree

    // this could be done more efficiently I'm sure
    preProcessVisitor.setFindReplace(findReplace);
    preProcessVisitor.setFindReplaceRegEx(findReplaceRegEx);

    String processedQuery = root.accept(preProcessVisitor, null);

    try {
      parser = new QueryParser(new StringReader(processedQuery));

      parser.setOrderOfOps(orderOfOpsMap);
      // buildQueryVisitor.setQsolParser(parser);
      // build 2nd parse tree from the pre-processed query
      root = parser.Search();
    } catch (Exception e) {
      throw new QsolSyntaxException(e);
    }

    buildQueryVisitor.setAnalyzer(analyzer);
    buildQueryVisitor.setField(field);

    // run over parse tree
    Query luceneQuery = root.accept(buildQueryVisitor, null);

    if (luceneQuery == null) {
      throw new EmptyQueryException("Expanded query is empty");
    }

    return luceneQuery;
  }

  private static CharSequence escape(CharSequence s) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '\\' || c == '!' || c == '(' || c == ')' || c == ':' || c == '^'
          || c == '[' || c == ']' || c == '{' || c == '}' || c == '~'
          || c == '*' || c == '?' || c == '"' || c == '+' || c == '-') {
        sb.append('\\');
      }
      sb.append(c);
    }
    return sb;
  }

  /**
   * Sets a new <code>DateParser</code> for parsing date fields.
   * 
   * @param dateParser
   *          to use
   * @since 1.0
   */
  public void setDateParser(DateParser dateParser) {
    buildQueryVisitor.setDateParser(dateParser);
  }

  public void setDefaultOp(String op) {
    this.preProcessVisitor.setDefaultOp(op);
  }

  public void setFieldBreakMarker(String fieldBreakMarker) {
    this.buildQueryVisitor.setFieldBreakMarker(fieldBreakMarker);
  }

  /**
   * Replace the internal find / replace map
   * 
   * @param findReplace
   */
  public void setFindReplace(Map<String, FindReplace> findReplace) {
    this.findReplace = findReplace;
  }

  /**
   * Sets the locale for date parsing.
   * 
   * @param locale
   * @since 1.0
   */
  public void setLocale(Locale locale) {
    this.locale = locale;
  }

  /**
   * Whether terms of wildcard, prefix, fuzzy and range queries are to be
   * automatically lower-cased or not. Default is <code>true</code>.
   */
  public void setLowercaseExpandedTerms(boolean lowercaseExpandedTerms) {
    this.buildQueryVisitor.setLowercaseExpandedTerms(lowercaseExpandedTerms);
  }

  /**
   * Sets the order of operations for the basic operators. This list should be
   * the opposite order of how tight you want the operator to bind. |, &, !, ~
   * would mean ~ binds tightest.
   * 
   * Add the operations in the reverse order of binding.
   * <p>
   * Example: switch & and | <code>
   * order: opsList.add(Operator.AND);
   * opsList.add(Operator.OR);
   * opsList.add(Operator.PROXIMITY);
   * opsList.add(Operator.ANDNOT);
   * 
   * parser.setOpsOrder(opsList);
     * </code>
   * 
   * @param opsList
   *          the opsList to set
   * @since 1.0
   */
  public void setOpsOrder(List<Operator> opsList) {
    this.opsList = opsList;
    orderOfOpsMap = new HashMap<Operator, Integer>(4);
    orderOfOpsMap.put(opsList.get(0), QueryParserConstants.OP1);
    orderOfOpsMap.put(opsList.get(1), QueryParserConstants.OP2);
    orderOfOpsMap.put(opsList.get(2), QueryParserConstants.OP3);
    orderOfOpsMap.put(opsList.get(3), QueryParserConstants.OP4);
  }

  /**
   * Set the default space ' ' operator to be an | (OR) instead of & (AND).
   * 
   * @since 1.0
   */
  public void setOrAsDefaultOp() {
    preProcessVisitor.setOrAsDefaultOp();
  }

  /**
   * Sets which token will act as a paragraph separation marker.
   * 
   * @param marker
   * @since 1.0
   */
  public void setParagraphMarker(String marker) {
    this.buildQueryVisitor.setParaMarker(marker);
  }

  /**
   * Sets which token will act as a sentence separation marker.
   * 
   * @param marker
   * @since 1.0
   */
  public void setSentenceMarker(String marker) {
    this.buildQueryVisitor.setSentMarker(marker);
  }

  /**
   * Enables the suggested search feature. Use the <code>SpellChecker</code> in
   * contrib to make a spell index and pass it to this method.
   * 
   * @param dir
   *          <code>SpellChecker</code> index to get suggestions from
   * @since 1.0
   */
  public void useSuggest(Directory dir) {
    this.preProcessVisitor.setSuggestedInfo(dir);
  }

  public enum Operator {
    AND, OR, PROXIMITY, ANDNOT;
  }
}
