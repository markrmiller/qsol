package com.mhs.qsol;

import java.text.DecimalFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.store.Directory;

import com.mhs.qsol.syntaxtree.BasicSearch;
import com.mhs.qsol.syntaxtree.BasicSearchType;
import com.mhs.qsol.syntaxtree.CheckOrd1Search;
import com.mhs.qsol.syntaxtree.CheckOrd2Search;
import com.mhs.qsol.syntaxtree.CheckOrd3Search;
import com.mhs.qsol.syntaxtree.CheckOrd4Search;
import com.mhs.qsol.syntaxtree.FieldSearch;
import com.mhs.qsol.syntaxtree.NodeChoice;
import com.mhs.qsol.syntaxtree.NodeList;
import com.mhs.qsol.syntaxtree.Ord1Search;
import com.mhs.qsol.syntaxtree.Ord2Search;
import com.mhs.qsol.syntaxtree.Ord3Search;
import com.mhs.qsol.syntaxtree.Ord4Search;
import com.mhs.qsol.syntaxtree.ParenthesisSearch;
import com.mhs.qsol.syntaxtree.Search;
import com.mhs.qsol.syntaxtree.SearchToken;
import com.mhs.qsol.visitor.GJDepthFirst;

/**
 * PreProcesses a Qsol String
 * 
 * @author Mark Miller <markrmiller@gmail.com> Aug 26, 2006
 * 
 */
@SuppressWarnings("unchecked")
public class PreProcessVisitor extends GJDepthFirst<String, String> {
  private final static Logger logger = Logger.getLogger(PreProcessVisitor.class
      .getPackage().getName());

  // private static final Pattern REPLACER = Pattern.compile("[^\\]");
  private Set<String> dateFields;
  private String defaultOp = "&";
  private Map<String, FindReplace> findReplace = Collections.EMPTY_MAP;
  private Set<FindReplaceRegEx> findReplaceRegEx = Collections.EMPTY_SET;
  private boolean buildSuggestedSearch = false;
  private StringBuilder suggestedSearchPart;
  private SuggestedSearch suggestedSearch;
  private Directory suggestedSearchDir;
  private Analyzer analyzer;
  private boolean useFindReplaceRegEx;
  private boolean isDefaultOpOn = true;
  private Set<String> fields = new HashSet<String>();

  private Map<String, Integer> zeroPadFields;

  public void add0PadField(String field, int pad) {
    if (zeroPadFields == null) {
      zeroPadFields = new HashMap<String, Integer>();
    }
    zeroPadFields.put(field, pad);

  }

  public void setZeroPadFields(Map<String, Integer> zeroPadFields) {
    this.zeroPadFields = zeroPadFields;
  }

  /**
   * @return the dateFields
   */
  public Set<String> getDateFields() {
    return dateFields;
  }

  public String getSuggestedSearch() {
    if (suggestedSearch.foundSuggestion()) {
      return suggestedSearch.getSuggestedSearch();
    } else {
      return "";
    }
  }

  public void setAnalyzer(Analyzer analyzer) {
    this.analyzer = analyzer;
  }

  public void setDateFields(Set<String> dateFields) {
    this.dateFields = dateFields;
  }

  /**
   * Sets the default space operation to the specified operator. This can be any
   * valid Qsol syntax.
   * 
   * @param op
   *          valid boolean operator
   */
  public void setDefaultOp(String op) {
    defaultOp = op;
  }

  public void setFindReplace(Map<String, FindReplace> findReplace) {
    this.findReplace = findReplace;
  }

  /**
   * @param findReplaceRegEx
   *          the findReplaceRegEx to set
   */
  public void setFindReplaceRegEx(Set<FindReplaceRegEx> findReplaceRegEx) {
    useFindReplaceRegEx = true;

    this.findReplaceRegEx = findReplaceRegEx;
  }

  /**
   * Sets the Qsol OR operator as the default space operator i.e. "mark
   * miller" becomes "mark | miller"
   */
  public void setOrAsDefaultOp() {
    defaultOp = "|";
  }

  public void setSuggestedInfo(Directory dir) {
    this.buildSuggestedSearch = true;
    this.suggestedSearchDir = dir;
  }

  /**
   * f0 -> ( BasicSearchType() )+
   */
  public String visit(BasicSearch n, String query) {
    StringBuilder returnString = new StringBuilder();

    // defaultOpOn is true if the default operator should be output between
    // these two basic
    // search types. Because a token might expand to an operator, defaultOpOn is
    // needed to
    // avoid connecting operators with the default operator.
    NodeList basicSearchTypeNodes = n.f0;
    int size = basicSearchTypeNodes.size();
    String lastToken = null;
    boolean lastWasOp = false;

    for (int i = 0; i < size; i++) {
      // I hate to repeat this control statement, but somehow it became
      // necessary to
      // build the suggest piece before descending down the parse tree.
      if (buildSuggestedSearch) {
        if (lastToken != null) {
          if (isDefaultOpOn && !lastWasOp) {
            suggestedSearchPart.append(" ");
          }
        }
      }

      String token = basicSearchTypeNodes.elementAt(i).accept(this, query);

      boolean useDefaultOp = isDefaultOpOn && !lastWasOp;

      if (lastToken != null) {
        if (useDefaultOp) {
          returnString.append(" " + defaultOp + " ");
        } else {
          returnString.append(" ");
        }
      }

      returnString.append(token);

      if ((i != (size - 1)) && isDefaultOpOn) {
        lastToken = token;
      }

      lastWasOp = !isDefaultOpOn;
      isDefaultOpOn = true;
    }

    return returnString.toString();
  }

  /**
   * f0 -> FieldSearch() | SearchToken() | ParenthesisSearch()
   */
  public String visit(BasicSearchType n, String query) {
    String returnString = null;
    returnString = n.f0.accept(this, query);

    return returnString;
  }

  /**
   * f0 -> CheckOrd2Search() f1 -> ( Ord1Search() )?
   */
  public String visit(CheckOrd1Search n, String query) {
    StringBuilder returnString = new StringBuilder();

    returnString.append(n.f0.accept(this, query));

    if (n.f1.present()) {
      returnString.append(n.f1.accept(this, query));
    }

    return returnString.toString();
  }

  /**
   * f0 -> CheckOrd3Search() f1 -> ( Ord2Search() )?
   */
  public String visit(CheckOrd2Search n, String query) {
    StringBuilder returnString = new StringBuilder();

    returnString.append(n.f0.accept(this, query));

    if (n.f1.present()) {
      returnString.append(n.f1.accept(this, query));
    }

    return returnString.toString();
  }

  /**
   * f0 -> CheckOrd4Search() f1 -> ( Ord3Search() )?
   */
  public String visit(CheckOrd3Search n, String query) {
    StringBuilder returnString = new StringBuilder();

    returnString.append(n.f0.accept(this, query));

    if (n.f1.present()) {
      returnString.append(n.f1.accept(this, query));
    }

    return returnString.toString();
  }

  /**
   * f0 -> BasicSearch() f1 -> ( Ord4Search() )?
   */
  public String visit(CheckOrd4Search n, String query) {
    StringBuilder returnString = new StringBuilder();

    returnString.append(n.f0.accept(this, query));

    if (n.f1.present()) {
      returnString.append(n.f1.accept(this, query));
    }

    return returnString.toString();
  }

  /**
   * f0 -> <FIELDSTART> f1 -> CheckOrd1Search() f2 -> ")"
   */
  public String visit(FieldSearch n, String query) {
    String returnString = null;
    String fieldList = n.f0.toString();
    String[] fields = fieldList.split(",");

    for (String field : fields) {
      this.fields.add(field);
    }

    if (dateFields.contains(fieldList)) {
      StringBuilder date = new StringBuilder();

      BasicSearch tokens = n.f1.f0.f0.f0.f0;

      int size = tokens.f0.size();

      for (int i = 0; i < size; i++) {
        SearchToken choice = (SearchToken) ((BasicSearchType) tokens.f0
            .elementAt(i)).f0.choice;
        date.append(choice.f0.choice);

        if (i != (size - 1)) {
          date.append(" ");
        }
      }

      if (buildSuggestedSearch) {
        suggestedSearchPart.append(fieldList + "(" + date.toString() + ")");
      }

      return fieldList + "(" + date.toString() + ")";
    }

    // get comma delimited field list
    if (buildSuggestedSearch) {
      suggestedSearchPart.append(fieldList + "(");
    }

    String fieldSearch = n.f1.accept(this, query);

    if (buildSuggestedSearch) {
      suggestedSearchPart.append(")");
    }

    returnString = fieldList + "(" + fieldSearch + ")";
    this.fields.clear();

    return returnString;
  }

  /**
   * f0 -> <OPERATOR1> f1 -> CheckOrd2Search() f2 -> ( Ord1Search() )?
   */
  public String visit(Ord1Search n, String query) {
    StringBuilder returnString = new StringBuilder();

    if (buildSuggestedSearch) {
      suggestedSearchPart.append(" " + n.f0.tokenImage + " ");
    }

    String clause = n.f1.accept(this, query);

    returnString.append(" " + n.f0.tokenImage + " " + clause);

    // if another <OPERATOR1> follows
    if (n.f2.present()) {
      returnString.append(n.f2.accept(this, query));
    }

    return returnString.toString();
  }

  /**
   * f0 -> <OPERATOR2> f1 -> CheckOrd3Search() f2 -> ( Ord2Search() )?
   */
  public String visit(Ord2Search n, String query) {
    StringBuilder returnString = new StringBuilder();

    if (buildSuggestedSearch) {
      suggestedSearchPart.append(" " + n.f0.tokenImage + " ");
    }

    String clause = n.f1.accept(this, query);

    returnString.append(" " + n.f0.tokenImage + " " + clause);

    // if another <OPERATOR2> follows
    if (n.f2.present()) {
      returnString.append(n.f2.accept(this, query));
    }

    return returnString.toString();
  }

  /**
   * f0 -> <OPERATOR3> f1 -> CheckOrd4Search() f2 -> ( Ord3Search() )?
   */
  public String visit(Ord3Search n, String query) {
    StringBuilder returnString = new StringBuilder();

    if (buildSuggestedSearch) {
      suggestedSearchPart.append(" " + n.f0.tokenImage + " ");
    }

    String clause = n.f1.accept(this, query);

    returnString.append(" " + n.f0.tokenImage + " " + clause);

    // if another <OPERATOR3> follows
    if (n.f2.present()) {
      returnString.append(n.f2.accept(this, query));
    }

    return returnString.toString();
  }

  /**
   * f0 -> <OPERATOR4> f1 -> BasicSearchType() f2 -> ( Ord4Search() )?
   */
  public String visit(Ord4Search n, String query) {
    StringBuilder returnString = new StringBuilder();

    if (buildSuggestedSearch) {
      suggestedSearchPart.append(" " + n.f0.tokenImage + " ");
    }

    String clause = n.f1.accept(this, query);

    returnString.append(" " + n.f0.tokenImage + " " + clause);

    // if another <OPERATOR4> follows
    if (n.f2.present()) {
      returnString.append(n.f2.accept(this, query));
    }

    return returnString.toString();
  }

  /**
   * f0 -> "(" f1 -> CheckOrd1Search() f2 -> ")"
   */
  public String visit(ParenthesisSearch n, String query) {
    String returnString = null;

    if (buildSuggestedSearch) {
      suggestedSearchPart.append("(");
    }

    returnString = "(" + n.f1.accept(this, query) + ")";

    if (buildSuggestedSearch) {
      suggestedSearch.addPart(")");
      suggestedSearch.addSlot("");
    }

    return returnString;
  }

  /**
   * f0 -> CheckOrd1Search() f1 -> <EOF>
   */
  public String visit(Search n, String query) {
    String returnString = null;

    if (buildSuggestedSearch) {
      suggestedSearchPart = new StringBuilder();
      suggestedSearch = new SuggestedSearch(suggestedSearchDir, analyzer);
    }

    // process replacement file
    returnString = n.f0.accept(this, query);

    if (buildSuggestedSearch) {

      suggestedSearch.getSuggestedSearch();

    }
    if (logger.isLoggable(Level.FINE)) {
      logger.info("processed query:" + returnString);

      if (buildSuggestedSearch) {
        logger.fine("suggested search:" + suggestedSearch.getSuggestedSearch());
        logger.fine(suggestedSearch.getInfo());
      }
    }

    return returnString;
  }

  /**
   * f0 -> ( <QUOTED> | <WILDCARD> | <FUZZY> | <SEARCHTOKEN> )+
   */
  public String visit(SearchToken n, String query) {
    // choice.which is the order of f0 -> i.e. choice 1 is quoted, choice 2
    // is wildcard etc.
    StringBuilder returnString = new StringBuilder();

    String token = n.f0.choice.toString();
    FindReplace replacment;

    if (useFindReplaceRegEx) {
      for (FindReplaceRegEx fr : findReplaceRegEx) {
        if ((fr.getField() == null) || fields.contains(fr.getField())) {
          Matcher m = fr.getPattern().matcher(token.trim());

          if (m.matches()) {
            token = m.replaceFirst(fr.getReplacement());

            if (fr.isOperatorReplace()) {
              isDefaultOpOn = false;
            }
          }
        }
      }
    }

    if ((replacment = findReplace.get(token.toLowerCase())) != null) {
      if ((replacment.getField() == null)
          || fields.contains(replacment.getField())) {
        if (replacment.isCaseSensitive()) {
          if (token.equals(replacment.getFind())) {
            token = replacment.getReplacement();

            if (replacment.isOperatorReplace()) {
              isDefaultOpOn = false;
            }
          }
        } else {
          token = replacment.getReplacement();

          if (replacment.isOperatorReplace()) {
            isDefaultOpOn = false;
          }
        }
      }
    }

    if (buildSuggestedSearch) {
      if (isDefaultOpOn == false) {
        suggestedSearchPart.append(" " + token + " ");
      } else {
        suggestedSearch.addSlot(token);

        suggestedSearch.addPart(suggestedSearchPart.toString());

        suggestedSearchPart.setLength(0);
      }
    }

    NodeChoice choice = (NodeChoice) n.f0;

    if (choice.which == 3) {
      Matcher m = QsolToQueryVisitor.RANGE_EXTRACTOR.matcher(choice.choice
          .toString());

      StringBuilder sb = new StringBuilder();

      if (m.matches()) {
        if (m.group(1) != null && m.group(1).equals("[")) {
          sb.append(m.group(1));
        }
        String term1 = m.group(2);
        String op = m.group(3);
        String term2 = m.group(4);
        sb.append(checkZeroPad(term1));
        sb.append(" ");
        sb.append(op);
        sb.append(" ");
        sb.append(checkZeroPad(term2));
        if (m.group(1) != null && m.group(5).equals("]")) {
          sb.append(m.group(5));
        }
        return sb.toString();
      } else {

        throw new RuntimeException(
            "Range did not match in QsolToQueryVisitor but did with JavaCC parser");
      }
    }

    token = checkZeroPad(token);

    returnString.append(token);

    return returnString.toString();
  }

  private String checkZeroPad(String token) {
    if (zeroPadFields == null) {
      return token;
    }

    for (String field : fields) {
      Integer pad = zeroPadFields.get(field);
      if (pad != null) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pad; i++) {
          sb.append("0");
        }

        DecimalFormat df = new DecimalFormat(sb.toString());
        token = df.format(Double.parseDouble(token));
      }
    }
    return token;
  }
}
