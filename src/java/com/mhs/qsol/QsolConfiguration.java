package com.mhs.qsol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.mhs.qsol.QsolParser.Operator;
import com.mhs.qsol.queryparser.QueryParserConstants;

public class QsolConfiguration {
  Set<String> dateFields = new HashSet<String>();
  Map<String, FindReplace> findReplace = new HashMap<String, FindReplace>();
  Set<FindReplaceRegEx> findReplaceRegEx = new HashSet<FindReplaceRegEx>();
  Map<String, FindReplace> thesaurusFindReplace = new HashMap<String, FindReplace>();
  Map<Operator, Integer> orderOfOpsMap = new HashMap<Operator, Integer>(4);
  List<Operator> opsList = new ArrayList<Operator>(4);
  boolean useHide;
  boolean hideOr;
  boolean hideAnd;
  boolean hideAndNot;
  boolean hideProximity;
  String paragraphMarker;
  String sentenceMarker;
  String fieldBreakMarker;
  DateParser dateParser = new DefaultDateParser();
  boolean tightSpaceBind;
  Map<String, String> fieldMapping = new HashMap<String, String>();
  Map<String, Integer> zeroPadFields;

  public QsolConfiguration() {
    opsList.add(Operator.OR);
    opsList.add(Operator.AND);
    opsList.add(Operator.PROXIMITY);
    opsList.add(Operator.ANDNOT);
    orderOfOpsMap.put(Operator.OR, QueryParserConstants.OP1);
    orderOfOpsMap.put(Operator.AND, QueryParserConstants.OP2);
    orderOfOpsMap.put(Operator.PROXIMITY, QueryParserConstants.OP3);
    orderOfOpsMap.put(Operator.ANDNOT, QueryParserConstants.OP4);
  }

  /**
   * Adds a <code>FindReplace</code> expander to the pre-processor. When a token
   * is matched, it will be replaced by the FindReplace replace value. Do not
   * use the default space operator in the replacement syntax- any other valid
   * Qsol syntax is fine i.e. 'donut' might map to '(coffee & donute | (dunken &
   * donut)'
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
   * (dunken & donut)'
   * 
   * @param findReplaceRegEx
   * 
   * @since 1.0
   */
  public void addFindReplaceRegEx(FindReplaceRegEx findReplaceRegEx) {
    this.findReplaceRegEx.add(findReplaceRegEx);
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
  public void addOperator(Operator op, String newOp) {
    switch (op) {
    case AND:
      findReplace.put(newOp.toLowerCase(), new FindReplace(newOp, "&", true,
          true));

      break;

    case OR:
      findReplace.put(newOp.toLowerCase(), new FindReplace(newOp, "|", true,
          true));

      break;

    case ANDNOT:
      findReplace.put(newOp.toLowerCase(), new FindReplace(newOp, "!", true,
          true));

      break;

    case PROXIMITY:
      findReplaceRegEx.add(new FindReplaceRegEx(Pattern.compile("(ord)?"
          + newOp + "(\\d*)([s,p])?"), "$1~$2$3", true));
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
    if (thesaurusFindReplace.isEmpty()) {
      thesaurusFindReplace = new HashMap<String, FindReplace>();
    }

    StringBuilder wordsExpansion = new StringBuilder();
    int count = 0;

    for (String singleWord : words) {
      wordsExpansion.append(singleWord);

      if (count < (words.size() - 1)) {
        wordsExpansion.append(" | ");
      }

      count++;
    }

    thesaurusFindReplace.put(word.toLowerCase(), new FindReplace(word, "("
        + wordsExpansion + ")", caseSensitive, false));
  }

  public Set<String> getDateFields() {
    return dateFields;
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
   * @param dateParser
   *          the dateParser to set
   */
  public void setDateParser(DateParser dateParser) {
    this.dateParser = dateParser;
  }

  /**
   * Sets the order of operations for the basic operators. This list should be
   * the opposite order of how tight you want the operator to bind. |, &, !, ~
   * would mean ~ binds tightest.
   * 
   * @param opsList
   *          the opsList to set
   * @since 1.0
   */
  public void setOpsOrder(List<Operator> opsList) {
    this.opsList = opsList;
    orderOfOpsMap.put(opsList.get(0), QueryParserConstants.OP1);
    orderOfOpsMap.put(opsList.get(1), QueryParserConstants.OP2);
    orderOfOpsMap.put(opsList.get(2), QueryParserConstants.OP3);
    orderOfOpsMap.put(opsList.get(3), QueryParserConstants.OP4);
  }

  /**
   * Sets which token will act as a paragraph separation marker.
   * 
   * @param marker
   * @since 1.0
   */
  public void setParagraphMarker(String marker) {

    this.paragraphMarker = marker;
  }

  /**
   * Sets which token will act as a sentence separation marker.
   * 
   * @param marker
   * @since 1.0
   */
  public void setSentenceMarker(String marker) {
    this.sentenceMarker = marker;
  }

  /**
   * When set to true the space operation will bind at the parenthesis level
   * (tightest bind). For example, if the space operator is set to '&' and the
   * setTitghtSpaceBind is set to true then a space connector will be an '&'
   * that binds tighter than an explicit '&'.
   * 
   * @param spaceBind
   * @since 1.0
   */
  public void setTightSpaceBind(boolean spaceBind) {
    this.tightSpaceBind = spaceBind;
  }

  public String getFieldBreakMarker() {
    return fieldBreakMarker;
  }

  public void setFieldBreakMarker(String fieldBreakMarker) {
    this.fieldBreakMarker = fieldBreakMarker;
  }

  public void addFieldMapping(String field, String mapToField) {
    this.fieldMapping.put(field, mapToField);
  }

  public void add0PadField(String field, int pad) {
    if (zeroPadFields == null) {
      zeroPadFields = new HashMap<String, Integer>();
    }
    zeroPadFields.put(field, pad);
  }
}
