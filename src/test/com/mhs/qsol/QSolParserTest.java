package com.mhs.qsol;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import junit.framework.TestCase;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.WhitespaceAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.spans.SpanNearQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.lucene.search.spell.LuceneDictionary;
import org.apache.lucene.search.spell.SpellChecker;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;

import com.mhs.qsol.QsolParser.Operator;

/**
 * @author Mark Miller (markrmiller@gmail.com) Aug 26, 2006
 * 
 */
public class QSolParserTest extends TestCase {
  String example = null;
  String expected = null;
  QsolParser parser = ParserFactory.getInstance(new QsolConfiguration())
      .getParser(false);
  private Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_33);

  public String parse(String query) {
    return parse(query, analyzer);
  }
  
  public String parse(String query, Analyzer analyzer) {
    parser.markDateField("date");
    System.out.println("query:" + query);

    Query result = null;

    try {
      result = parser.parse("allFields", query, analyzer);
    } catch (QsolSyntaxException e) {
      throw new RuntimeException(e);
    } catch (EmptyQueryException e) {
      return "";
    }

    return result.toString();
  }

  public void testBadSearches() {
  }

  public void testConfig() {
    QsolConfiguration config = new QsolConfiguration();
    config.setParagraphMarker("PARA");
    config.setSentenceMarker("S");
    config.hideOperators(false, false, true, true);
    config.addFindReplace(new FindReplace("AND", "&", false, true));
    config.addFindReplace(new FindReplace("OR", "|", false, true));
    config.addFindReplace(new FindReplace("BUTNOT", "!", false, true));
    config.addFindReplace(new FindReplace("%", "!", false, true));

    Pattern pattern;

    pattern = Pattern.compile("(pre)?" + "/" + "(\\d*)([s,p])?",
        Pattern.CASE_INSENSITIVE);
    config.addFindReplaceRegEx(new FindReplaceRegEx(pattern, "$1~$2$3", true));

    ParserFactory parserFactory = ParserFactory.getInstance(config);
    QsolParser parser = parserFactory.getParser(true);

    example = "field or (boat)";
    expected = "allFields:field allFields:boat";
    assertEquals(expected, parse(parser, example).toString());

    Query result = null;
    String query = "mark /3p cat";
    result = parse(parser, query);

    expected = "spanWithin(spanNear([allFields:mark, allFields:cat], 99999, false), 3 ,allFields:PARA)";
    assertEquals(expected, result.toString());
  }

  private Query parse(QsolParser parser, String query) {
    Query result = null;

    try {
      result = parser.parse("allFields", query, analyzer);
    } catch (QsolSyntaxException e) {
      throw new RuntimeException(e);
    } catch (EmptyQueryException e) {
      throw new RuntimeException(e);
    }

    return result;
  }

  public void testModifiedParser() {
    parser.hideOperators(false, true, false, false);

    parser.addFindReplace(new FindReplace("AND", "&", true, true));

    example = "Mark miller AND & | me";
    expected = "(+allFields:mark +allFields:miller) allFields:me";
    assertEquals(expected, parse(example));

    example = "Mark AND Miller AND together";
    // expected = "+allFields:mark +allFields:miller +allFields:together";
    expected = "+allFields:mark +allFields:miller +allFields:together";
    assertEquals(expected, parse(example));

    example = "Mark AND cat | me";
    expected = "(+allFields:mark +allFields:cat) allFields:me";
    assertEquals(expected, parse(example));

    example = "Mark and cat | me";
    expected = "(+allFields:mark +allFields:cat) allFields:me";
    assertEquals(expected, parse(example));
  }

  private static final String FIELD_NAME = "allFields";

  public String loadFileToString(final String path)
      throws FileNotFoundException {
    final File file = new File(path);

    FileInputStream fis = null;

    fis = new FileInputStream(file);

    InputStreamReader isr = new InputStreamReader(fis);

    BufferedReader br = new BufferedReader(isr);
    final StringBuffer sb = new StringBuffer();

    try {
      String line = null;

      while ((line = br.readLine()) != null) {
        sb.append(line + "\n");
      }
    } catch (final Exception ex) {
      throw new RuntimeException(ex);
    } finally {
      try {
        fis.close();
      } catch (final Exception ex) {
      }
      try {
        br.close();
      } catch (IOException e) {

      }
      try {
        isr.close();
      } catch (Exception e) {

      }
    }

    return sb.toString();
  }

  public void testSentParaSearch() {
    parser.setParagraphMarker("para");
    parser.setSentenceMarker("sent");
    example = "mark ~4p cat";
    expected = "spanWithin(spanNear([allFields:mark, allFields:cat], 99999, false), 4 ,allFields:para)";
    assertEquals(expected, parse(example));
  }

  public void testAlternateOrderOfOps() {
    List<Operator> opsList = new ArrayList<Operator>(4);

    opsList.add(Operator.AND);
    opsList.add(Operator.OR);
    opsList.add(Operator.PROXIMITY);
    opsList.add(Operator.ANDNOT);
    parser.setOpsOrder(opsList);

    example = "mark & dog | cat";
    expected = "+allFields:mark +(allFields:dog allFields:cat)";
    assertEquals(expected, parse(example));
  }

  public void testRandomQueries() {
    example = "headline(basketball) & section(sports) & edition(cape & may)";
    expected = "+(headline:basketball) +(section:sports) +((+edition:cape +edition:may))";
    assertEquals(expected, parse(example));

  }

  public void testFindReplace() {
    parser.addOperator(Operator.PROXIMITY, "/", false);

    example = "mark /4 dog | cat";
    expected = "spanNear([allFields:mark, allFields:dog], 4, false) allFields:cat";
    assertEquals(expected, parse(example));

    parser.addFindReplace(new FindReplace("NEAR", "~10", true, true));

    example = "mark NEAR dog ! cat";
    expected = "+spanNear([allFields:mark, allFields:dog], 10, false) -spanNear([allFields:mark, allFields:cat], 10, false)";
    assertEquals(expected, parse(example));

    FindReplaceRegEx fr = new FindReplaceRegEx(
        Pattern.compile("([a-zA-Z]{1})"), "$1*", false);
    fr.setField("test");
    parser.addFindReplaceRegEx(fr);
    example = "test(a)";
    expected = "test:a*";
    assertEquals(expected, parse(example));
  }

  public void testFixThese() {
    // this should work, but currently does not
    example = "the ! mark";
    expected = "";
    assertEquals(expected, parse(example));
    
    // this should work, but currently does not
    example = "total(bob";
    expected = "allFields:bob";
    assertEquals(expected, parse(example));
  }

  public void testThesauraus() {
    Set<String> words = new HashSet<String>();
    words.add("test1");
    words.add("test2");
    words.add("test3");
    parser.addThesaurusEntry("test", words, false);

    example = "test ~4 dog | cat";
    expected = "(spanOr([spanNear([allFields:test1, allFields:dog], 4, false), spanNear([allFields:test2, allFields:dog], 4, false)]) spanNear([allFields:test3, allFields:dog], 4, false)) allFields:cat";
    assertEquals(expected, parse(example));
  }

  public void testFieldBreaker() throws IOException {
    analyzer = new WhitespaceAnalyzer();

    RAMDirectory directory;
    directory = new RAMDirectory();
    parser.setSentenceMarker("sent");

    IndexWriter writer = new IndexWriter(directory, analyzer, true, MaxFieldLength.UNLIMITED);

    Document doc = new Document();
    doc
        .add(new Field(
            "allFields",
            "only the lonely shal break make it to the great sent almighty the receiver be sent It is the way of the one and only sent break if you are wise and practice hard break good results may come sent for you",
            Field.Store.YES, Field.Index.ANALYZED));

    writer.addDocument(doc);

    writer.close();

    IndexReader ir = IndexReader.open(directory);

    IndexSearcher searcher = new IndexSearcher(ir);
    String query = "shal ~1s almighty";

    TopDocs hits = searcher.search(parse(parser, query), 1000);
    assertEquals(1, hits.totalHits);

    parser.setFieldBreakMarker("break");

    query = "shal ~1s almighty";
    hits = searcher.search(parse(parser, query), 1000);
    assertEquals(0, hits.totalHits);

    query = "only ~9s results";
    hits = searcher.search(parse(parser, query), 1000);
    assertEquals(0, hits.totalHits);

    query = "good ~9s lonely";
    hits = searcher.search(parse(parser, query), 1000);
    assertEquals(0, hits.totalHits);

    query = "great ~2s way";
    hits = searcher.search(parse(parser, query), 1000);
    assertEquals(1, hits.totalHits);

    query = "great ~1s way";
    hits = searcher.search(parse(parser, query), 1000);
    assertEquals(0, hits.totalHits);

    query = "great ~3s way";
    hits = searcher.search(parse(parser, query), 1000);
    assertEquals(1, hits.totalHits);

  }

  public void testGeneralOnIndex() throws Exception {

    RAMDirectory directory;
    directory = new RAMDirectory();

    IndexWriter writer = new IndexWriter(directory, analyzer, true, MaxFieldLength.UNLIMITED);

    Document doc = new Document();
    doc.add(new Field("allFields", "pH test block 7.334", Field.Store.YES,
        Field.Index.ANALYZED));

    writer.addDocument(doc);

    writer.close();

    IndexReader ir = IndexReader.open(directory);

    IndexSearcher searcher = new IndexSearcher(ir);
    String query = "pH ~30 7.33?";

    Query q = parse(parser, query);
    System.out.println("q:" + q);
    TopDocs hits = searcher.search(q, 1000);
    assertEquals(1, hits.totalHits);

  }

  public void testSpan() throws IOException {
    analyzer = new WhitespaceAnalyzer();

    RAMDirectory directory;
    directory = new RAMDirectory();
    parser.setSentenceMarker("sent");

    IndexWriter writer = new IndexWriter(directory, analyzer, true, MaxFieldLength.UNLIMITED);

    Document doc = new Document();
    doc
        .add(new Field(
            "allFields",
            "only the lonely shal break make it to the great sent almighty the receiver be sent It is the way of the one and only sent break if you are wise and practice hard break good results may come sent for you",
            Field.Store.YES, Field.Index.ANALYZED));

    writer.addDocument(doc);

    writer.close();

    IndexReader ir = IndexReader.open(directory);

    IndexSearcher searcher = new IndexSearcher(ir);
    String query = "shal ~1s almighty";
    Query q = new SpanNearQuery(new SpanQuery[] {
        new SpanTermQuery(new Term(FIELD_NAME, "shal")),
        new SpanTermQuery(new Term(FIELD_NAME, "make")),
        new SpanTermQuery(new Term(FIELD_NAME, "sent")) }, 6, false);
    TopDocs hits = searcher.search(q, 1000);

  }

  public void testWildCardSearch() throws IOException {
    RAMDirectory directory;
    directory = new RAMDirectory();

    IndexWriter writer = new IndexWriter(directory, analyzer, true, MaxFieldLength.UNLIMITED);

    Document doc = new Document();
    doc
        .add(new Field(
            "allFields",
            "the brown fox�jumps \"over the lazy horse horse dog\" jhjkh jhjkh�hjhjh�f�jumps horse horse his own quick horse ",
            Field.Store.YES, Field.Index.ANALYZED));

    writer.addDocument(doc);

    doc = new Document();
    doc
        .add(new Field(
            "allFields",
            "the brown fox </p> <p> jumps �����</p> fox ������</p> <p> jumps his own horse horse quick horse </p>",
            Field.Store.YES, Field.Index.ANALYZED));
    writer.addDocument(doc);
    doc
        .add(new Field(
            "allFields",
            "the brown fox </p> <p> jumps �����</p> fox ������</p> <p> jumps his own horse horse quick horse </p>",
            Field.Store.YES, Field.Index.ANALYZED));
    writer.addDocument(doc);
    doc
        .add(new Field(
            "allFields",
            "the brown fox </p> <p> jumps �����</p> fox ������</p> <p> jumps motherfracker his own horse horse quick horse </p>",
            Field.Store.YES, Field.Index.ANALYZED));
    writer.addDocument(doc);
    doc
        .add(new Field(
            "allFields",
            "the brown fox </p> <p> jumps �����</p> fox ������</p> <p> jumps his own horse horse jumper jumping jumbo quick horse </p>",
            Field.Store.YES, Field.Index.ANALYZED));
    writer.addDocument(doc);

    writer.close();

    IndexReader ir = IndexReader.open(directory);

    example = "ho?se";
    expected = "ConstantScore(allFields:ho?se)";

    assertEquals(expected, parse(parser, example).rewrite(ir).toString());

    example = "ju*";
    expected = "ConstantScore(allFields:ju*)";

    assertEquals(expected, parse(parser, example).rewrite(ir).toString());

    example = "ju\\*";
    expected = "allFields:ju";

    assertEquals(expected, parse(parser, example).rewrite(ir).toString());

    example = "ju* ~5 fox";
    expected = "spanNear([spanOr([allFields:jumbo, allFields:jumper, allFields:jumping, allFields:jumps]), allFields:fox], 5, false)";

    assertEquals(expected, parse(parser, example).rewrite(ir).toString());

    example = "*ox";
    expected = "ConstantScore(allFields:*ox)";

    assertEquals(expected, parse(parser, example).rewrite(ir).toString());
  }

  public void testSuggestedSearch() throws IOException {
    RAMDirectory directory;
    directory = new RAMDirectory();

    IndexWriter writer = new IndexWriter(directory, analyzer, true, MaxFieldLength.UNLIMITED);

    Document doc = new Document();
    doc
        .add(new Field(
            "allFields",
            "the brown fox�jumps \"over the lazy horse horse dog\" jhjkh jhjkh�hjhjh�f�jumps horse horse his own quick horse ",
            Field.Store.YES, Field.Index.ANALYZED));

    writer.addDocument(doc);

    doc = new Document();
    doc
        .add(new Field(
            "allFields",
            "the brown fox </p> <p> jumps �����</p> fox ������</p> <p> jumps his own horse horse quick horse dog </p>",
            Field.Store.YES, Field.Index.ANALYZED));
    writer.addDocument(doc);
    doc
        .add(new Field(
            "allFields",
            "the brown fox </p> <p> jumps �����</p> fox ������</p> <p> jumps his own horse horse quick horse </p>",
            Field.Store.YES, Field.Index.ANALYZED));
    writer.addDocument(doc);
    doc
        .add(new Field(
            "allFields",
            "the brown fox </p> <p> jumps �����</p> fox ������</p> <p> jumps motherfracker his own horse horse quick horse </p>",
            Field.Store.YES, Field.Index.ANALYZED));
    writer.addDocument(doc);
    doc
        .add(new Field(
            "allFields",
            "the brown fox </p> <p> jumps �����</p> fox ������</p> <p> jumps his own horse horse quick horse </p>",
            Field.Store.YES, Field.Index.ANALYZED));
    writer.addDocument(doc);

    writer.close();

    SpellChecker spellChecker = new SpellChecker(directory);
    IndexReader reader = IndexReader.open(directory);
    spellChecker.indexDictionary(new LuceneDictionary(reader, "allFields"));
    reader.close();

    parser.useSuggest(directory);
    example = "horke & motherfroker";
    expected = "horse & motherfracker";
    parse(example);
    assertEquals(expected, parser.getSuggestedSearch());

    example = "horke motherfroker (toad)";
    expected = "horse motherfracker (dog)";
    parse(example);
    assertEquals(expected, parser.getSuggestedSearch());

    example = "field(mars life) motherfroker field2(bear) | (toad)";
    expected = "field(horse his) motherfracker field2(over) | (dog)";
    parse(example);
    assertEquals(expected, parser.getSuggestedSearch());
    
    example = "field(lazy life) motherfroker field2(bear) | (dog)";
    expected = "field(lazy his) motherfracker field2(over) | (dog)";
    parse(example);
    assertEquals(expected, parser.getSuggestedSearch());
  }

  public void testModifiedProximity() throws IOException {
    parser.addOperator(Operator.PROXIMITY, "%", false);

    example = "(qsol parser %3 work fine) %3 hose";
    expected = "+spanNear([allFields:qsol, allFields:hose], 3, false) +spanNear([spanNear([allFields:parser, allFields:work], 3, false), allFields:hose], 3, false) +spanNear([allFields:fine, allFields:hose], 3, false)";
    assertEquals(expected, parse(example));
  }

  public void testBoost() {
    example = "\"the old heart\"^34.5 ~3 mark";
    expected = "spanNear([spanNear([allFields:old, allFields:heart], 0, true)^34.5, allFields:mark], 3, false)";
    Query query = parse(parser, example);
    assertEquals(expected, query.toString().trim());

    example = "the old heart^34.5";
    expected = "+allFields:old +allFields:heart^34.5";
    query = parse(parser, example);
    assertEquals(expected, query.toString().trim());

    example = "old ~3 heart^39.5";
    expected = "spanNear([allFields:old, allFields:heart^39.5], 3, false)";
    query = parse(parser, example);

    assertEquals(expected, query.toString().trim());

    example = "\"the old heart\"^34.5 giver";
    // expected = "+spanNear([allFields:old^34.5, allFields:heart^34.5], 1,
    // true) +allFields:giver";
    expected = "+spanNear([allFields:old^34.5, allFields:heart^34.5], 0, true) +allFields:giver";
    query = parse(parser, example);

    assertEquals(expected, query.toString().trim());
  }

  public void testModifiedProximity2() throws IOException {
    parser.addOperator(Operator.PROXIMITY, "%", false);
    parser.addOperator(Operator.AND, "and", false);

    example = "field1(test search) and field2({1980 TO 1881})";
    expected = "+((+field1:test +field1:search)) +(field2:{1980 TO 1881})";
    assertEquals(expected, parse(example));

    example = "field1(test search) & field2({1980 TO 1881})";
    expected = "+((+field1:test +field1:search)) +(field2:{1980 TO 1881})";
    assertEquals(expected, parse(example));

    example = "field1(test search) & field2(dfgdfg)";
    expected = "+((+field1:test +field1:search)) +(field2:dfgdfg)";
    assertEquals(expected, parse(example));

    example = "(qsol parser %3 work fine) %3 hose";
    expected = "+spanNear([allFields:qsol, allFields:hose], 3, false) +spanNear([spanNear([allFields:parser, allFields:work], 3, false), allFields:hose], 3, false) +spanNear([allFields:fine, allFields:hose], 3, false)";
    assertEquals(expected, parse(example));
  }

  public void testSameOpInARow() {
    example = "man | dog | cat";
    expected = "allFields:man allFields:dog allFields:cat";
    assertEquals(expected, parse(example));
  }

  public void testEscaping() throws IOException {
    example = "\"horse man of \\\\\"the dead\\\\\"\"";
    expected = "+spanNear([allFields:horse, allFields:man], 0, true) +allFields:dead";
    assertEquals(expected, parse(example));

    example = "\\(search";
    expected = "+spanNear([allFields:horse, allFields:man], 0, true) +allFields:dead";
    assertEquals(expected, parse(example));

    example = "badfield\\(search";
    expected = "+spanNear([allFields:horse, allFields:man], 0, true) +allFields:dead";
    assertEquals(expected, parse(example));
  }

  public void test0Padding() throws IOException {
    parser.add0PadField("wc", 6);

    example = "wc(45)";
    expected = "wc:000045";
    assertEquals(expected, parse(example));

    example = "wc(045)";
    expected = "wc:000045";
    assertEquals(expected, parse(example));

    example = "wc(6 rng 10)";
    expected = "wc:{000006 TO 000010}";
    assertEquals(expected, parse(example));

  }
  
  public void testGeneralQueries() throws IOException {
    example = "pH ~30 7.33?";
    expected = "spanNear([allFields:ph, spanWildcardQuery(allFields:7.33?)], 30, false)";
    assertEquals(expected, parse(example));

    example = "date(8/8/2008) & send(no)";
    expected = "date:20080808";
    assertEquals(expected, parse(example));

    example = "field(george - bush)";
    expected = "(+field:george +field:bush)";
    assertEquals(expected, parse(example));

    example = "(cow ~0p car) ~0p book";
    expected = "spanWithin(spanNear([spanWithin(spanNear([allFields:cow, allFields:car], 99999, false), 0 ,allFields:/p), allFields:book], 99999, false), 0 ,allFields:null)";
    assertEquals(expected, parse(example));

    example = "horse || fish || cow !! car !! book";
    expected = "allFields:horse allFields:fish allFields:cow -allFields:car -allFields:book";
    assertEquals(expected, parse(example));

    // TODO: inspect the leading +
    example = "cow !! car !! book";
    expected = "+allFields:cow -allFields:car -allFields:book";
    assertEquals(expected, parse(example));

    example = "horse | bush ~3 gardner & cat";
    expected = "allFields:horse (+spanNear([allFields:bush, allFields:gardner], 3, false) +allFields:cat)";
    assertEquals(expected, parse(example));

    // TODO: investigate +C
    example = "AB | B | C ! D ! E";
    expected = "allFields:ab allFields:b (+allFields:c -allFields:d -allFields:e)";
    assertEquals(expected, parse(example));

    example = "(george bush /3 white man) /3 horse";
    expected = "+(+allFields:george +allFields:bush +allFields:3 +allFields:white +allFields:man) +allFields:3 +allFields:horse";
    assertEquals(expected, parse(example));

    example = "(mark dog) cat";
    expected = "+(+allFields:mark +allFields:dog) +allFields:cat";
    assertEquals(expected, parse(example));

    example = "(mark | chad & hockey) ~4 (horse & body ~3 cow) & horse,cow(donkey)";
    expected = "+((+spanNear([allFields:mark, allFields:horse], 4, false) +spanNear([allFields:mark, spanNear([allFields:body, allFields:cow], 3, false)], 4, false)) (+(+spanNear([allFields:chad, allFields:horse], 4, false) +spanNear([allFields:chad, spanNear([allFields:body, allFields:cow], 3, false)], 4, false)) +(+spanNear([allFields:hockey, allFields:horse], 4, false) +spanNear([allFields:hockey, spanNear([allFields:body, allFields:cow], 3, false)], 4, false)))) +(horse:donkey cow:donkey)";
    assertEquals(expected, parse(example));

    // TODO: eventually this might be optimized so that the third OR span goes
    // to the spanOr and not a boolean
    example = "(test2 | test3 | test1) ~4 dog | cat";
    expected = "(spanOr([spanNear([allFields:test2, allFields:dog], 4, false), spanNear([allFields:test3, allFields:dog], 4, false)]) spanNear([allFields:test1, allFields:dog], 4, false)) allFields:cat";
    assertEquals(expected, parse(example));

    example = "mark & dog | cat";
    expected = "(+allFields:mark +allFields:dog) allFields:cat";
    assertEquals(expected, parse(example));

    example = "jh ! (cat & hat) ~4 horse";
    expected = "+spanNear([allFields:jh, allFields:horse], 4, false) -(+spanNear([allFields:cat, allFields:horse], 4, false) +spanNear([allFields:hat, allFields:horse], 4, false))";
    assertEquals(expected, parse(example));

    example = "the are is fat rabbit";
    expected = "+allFields:fat +allFields:rabbit";
    assertEquals(expected, parse(example));

    example = "the old man";
    expected = "+allFields:old +allFields:man";
    assertEquals(expected, parse(example));

    example = "date:1982";
    expected = "spanNear([allFields:date, allFields:1982], 0, true)";
    assertEquals(expected, parse(example));

    example = "(good | witch | basket) ~4 scary ! man";
    expected = "(+spanNear([allFields:good, allFields:scary], 4, false) -spanNear([allFields:good, allFields:man], 4, false)) (+spanNear([allFields:witch, allFields:scary], 4, false) -spanNear([allFields:witch, allFields:man], 4, false)) (+spanNear([allFields:basket, allFields:scary], 4, false) -spanNear([allFields:basket, allFields:man], 4, false))";
    assertEquals(expected, parse(example));

    example = "(good witch & basket) ~4 scary ! man";
    expected = "+(+spanNear([allFields:good, allFields:scary], 4, false) -spanNear([allFields:good, allFields:man], 4, false)) +(+spanNear([allFields:witch, allFields:scary], 4, false) -spanNear([allFields:witch, allFields:man], 4, false)) +(+spanNear([allFields:basket, allFields:scary], 4, false) -spanNear([allFields:basket, allFields:man], 4, false))";
    assertEquals(expected, parse(example));

    example = "(horse | me & cop) ! dog ~2 (parrot | cat)";
    expected = "+(spanOr([spanNear([allFields:horse, allFields:parrot], 2, false), spanNear([allFields:horse, allFields:cat], 2, false)]) (+spanOr([spanNear([allFields:me, allFields:parrot], 2, false), spanNear([allFields:me, allFields:cat], 2, false)]) +spanOr([spanNear([allFields:cop, allFields:parrot], 2, false), spanNear([allFields:cop, allFields:cat], 2, false)]))) -spanOr([spanNear([allFields:dog, allFields:parrot], 2, false), spanNear([allFields:dog, allFields:cat], 2, false)])";
    assertEquals(expected, parse(example));

    example = "(mark & monkey ~3 white) ~3 horse";
    expected = "+spanNear([allFields:mark, allFields:horse], 3, false) +spanNear([spanNear([allFields:monkey, allFields:white], 3, false), allFields:horse], 3, false)";
    assertEquals(expected, parse(example));

    example = "goat cheese ~2 valley girl";
    // expected = "+allFields:goat +spanNear([allFields:cheese,
    // allFields:valley], 2, false) +allFields:girl";
    expected = "+allFields:goat +spanNear([allFields:cheese, allFields:valley], 2, false) +allFields:girl";
    assertEquals(expected, parse(example));

    example = "the & but | the and";
    expected = "";
    assertEquals(expected, parse(example));

    // example = "(the oger) ~3 butter the";
    // expected = "+allFields:goat +spanNear([allFields:cheese,
    // allFields:valley], 2, false) +allFields:girl";
    // assertEquals(expected, parse(example));
    example = "(goat cheese) ~2 (valley girl)";
    expected = "+(+spanNear([allFields:goat, allFields:valley], 2, false) +spanNear([allFields:goat, allFields:girl], 2, false)) +(+spanNear([allFields:cheese, allFields:valley], 2, false) +spanNear([allFields:cheese, allFields:girl], 2, false))";
    assertEquals(expected, parse(example));

    // example = "goat-valley";
    // expected = "spanNear([allFields:goat, allFields:valley], 1, true)";
    // assertEquals(expected, parse(example));
    //
    example = "[goat TO valley]";
    expected = "allFields:[goat TO valley]";
    assertEquals(expected, parse(example));

    example = "goat \\-- valley";
    expected = "+allFields:goat +allFields:valley";
    assertEquals(expected, parse(example));

    example = "goat \\- valley";
    expected = "+allFields:goat +allFields:valley";
    assertEquals(expected, parse(example));

    example = "{goat TO valley}";
    expected = "allFields:{goat TO valley}";
    assertEquals(expected, parse(example));

    // Note: This example shows that qsol doesn't play especially well with StopFilter.
    // In particular, note that in the output query there is a 0-slop gap between "killa"
    // and "willaw", leaving no room for "the". This reflects two things:
    // - Position increments of 2 are generated by the StopFilter that's implicitly created
    //   by StandardAnalyzer, the analyzer for parse().
    // - Qsol doesn't really support position increments other than 0 or 1 between
    //   the different words in a phrase. (For why, see tokenToQuery, where all members of
    //   a phrase may be given the same slop.)
    example = "(good witch & \"killa the willaw\") ~4 scary ! man";
    expected = "+(+spanNear([allFields:good, allFields:scary], 4, false) -spanNear([allFields:good, allFields:man], 4, false)) +(+spanNear([allFields:witch, allFields:scary], 4, false) -spanNear([allFields:witch, allFields:man], 4, false)) +(+spanNear([spanNear([allFields:killa, allFields:willaw], 0, true), allFields:scary], 4, false) -spanNear([spanNear([allFields:killa, allFields:willaw], 0, true), allFields:man], 4, false))";
    assertEquals(expected, parse(example));
    
    // Here's a related example, but without stopwords:
    example = "(good witch & \"killa balderdash willaw\") ~4 scary ! man";
    expected = "+(+spanNear([allFields:good, allFields:scary], 4, false) -spanNear([allFields:good, allFields:man], 4, false)) +(+spanNear([allFields:witch, allFields:scary], 4, false) -spanNear([allFields:witch, allFields:man], 4, false)) +(+spanNear([spanNear([allFields:killa, allFields:balderdash, allFields:willaw], 0, true), allFields:scary], 4, false) -spanNear([spanNear([allFields:killa, allFields:balderdash, allFields:willaw], 0, true), allFields:man], 4, false))";
    assertEquals(expected, parse(example));
    
    //
    // example = "beat` old magpie`";
    // expected = "+allFields:beat~0.5 +allFields:old +allFields:magpie~0.5";
    // assertEquals(expected, parse(example));
    //
    example = "hor*se & homer ~4 ma*ge";
    expected = "+allFields:hor*se +spanNear([allFields:homer, spanWildcardQuery(allFields:ma*ge)], 4, false)";
    assertEquals(expected, parse(example));

    example = "hor*se & homer ~4 ma*ge";
    expected = "+allFields:hor*se +spanNear([allFields:homer, spanWildcardQuery(allFields:ma*ge)], 4, false)";
    assertEquals(expected, parse(example));

    example = "hor\\*se & homer ~4 ma\\*ge";
    expected = "+spanNear([allFields:hor, allFields:se], 0, true) +spanNear([allFields:homer, spanNear([allFields:ma, allFields:ge], 0, true)], 4, false)";
    assertEquals(expected, parse(example));
    //
    example = "(monkey ~3 white) ~3 horse";
    expected = "spanNear([spanNear([allFields:monkey, allFields:white], 3, false), allFields:horse], 3, false)";
    assertEquals(expected, parse(example));

    example = "(monkey ~3 white) ord~3 horse";
    expected = "spanNear([spanNear([allFields:monkey, allFields:white], 3, false), allFields:horse], 3, true)";
    assertEquals(expected, parse(example));

    example = "homer ~4 marge";
    expected = "spanNear([allFields:homer, allFields:marge], 4, false)";
    assertEquals(expected, parse(example));

    example = "homer and simp*le?n";
    expected = "+allFields:homer +allFields:simp*le?n";
    assertEquals(expected, parse(example));

    example = "homer and simp*";
    expected = "+allFields:homer +allFields:simp*";
    assertEquals(expected, parse(example));

    example = "monkey fowl ~2 (white man) ~3 horse ~6 tom";
    expected = "+allFields:monkey +(+(+spanNear([allFields:fowl, allFields:white], 2, false) +spanNear([allFields:fowl, allFields:man], 2, false)) +(+spanNear([allFields:fowl, allFields:horse], 3, false) +(+spanNear([allFields:white, allFields:horse], 3, false) +spanNear([allFields:man, allFields:horse], 3, false))) +(+spanNear([allFields:fowl, allFields:tom], 6, false) +(+spanNear([allFields:white, allFields:tom], 6, false) +spanNear([allFields:man, allFields:tom], 6, false)) +spanNear([allFields:horse, allFields:tom], 6, false)))";
    assertEquals(expected, parse(example));

    example = "monkey fowl ~2 (white man) ~3 horse ~6 tom ~5 old maid";
    // expected = "+allFields:monkey +(+(+spanNear([allFields:fowl,
    // allFields:white], 2, false) +spanNear([allFields:fowl, allFields:man], 2,
    // false)) +(+spanNear([allFields:fowl, allFields:horse], 3, false)
    // +(+spanNear([allFields:white, allFields:horse], 3, false)
    // +spanNear([allFields:man, allFields:horse], 3, false)))
    // +(+spanNear([allFields:fowl, allFields:tom], 6, false)
    // +(+spanNear([allFields:white, allFields:tom], 6, false)
    // +spanNear([allFields:man, allFields:tom], 6, false))
    // +spanNear([allFields:horse, allFields:tom], 6, false))
    // +(+spanNear([allFields:fowl, allFields:old], 5, false)
    // +(+spanNear([allFields:white, allFields:old], 5, false)
    // +spanNear([allFields:man, allFields:old], 5, false))
    // +spanNear([allFields:horse, allFields:old], 5, false)
    // +spanNear([allFields:tom, allFields:old], 5, false))) +allFields:maid";
    expected = "+allFields:monkey +(+(+spanNear([allFields:fowl, allFields:white], 2, false) +spanNear([allFields:fowl, allFields:man], 2, false)) +(+spanNear([allFields:fowl, allFields:horse], 3, false) +(+spanNear([allFields:white, allFields:horse], 3, false) +spanNear([allFields:man, allFields:horse], 3, false))) +(+spanNear([allFields:fowl, allFields:tom], 6, false) +(+spanNear([allFields:white, allFields:tom], 6, false) +spanNear([allFields:man, allFields:tom], 6, false)) +spanNear([allFields:horse, allFields:tom], 6, false)) +(+spanNear([allFields:fowl, allFields:old], 5, false) +(+spanNear([allFields:white, allFields:old], 5, false) +spanNear([allFields:man, allFields:old], 5, false)) +spanNear([allFields:horse, allFields:old], 5, false) +spanNear([allFields:tom, allFields:old], 5, false))) +allFields:maid";
    assertEquals(expected, parse(example));

    example = "\"test the big search\" & me";
    expected = "+spanNear([allFields:test, allFields:big, allFields:search], 0, true) +allFields:me";
    assertEquals(expected, parse(example));

    example = "\"test the big search\":30 & me";
    expected = "+spanNear([allFields:test, allFields:big, allFields:search], 30, true) +allFields:me";
    assertEquals(expected, parse(example));

    example = "me & fox & cop";
    expected = "+allFields:me +allFields:fox +allFields:cop";
    assertEquals(expected, parse(example));

    example = "me | fox | cop";
    expected = "allFields:me allFields:fox allFields:cop";
    assertEquals(expected, parse(example));

    example = "me ! fox ! cop";
    expected = "+allFields:me -allFields:fox -allFields:cop";
    assertEquals(expected, parse(example));

    example = "me \\| test & hole";
    expected = "+allFields:me +allFields:test +allFields:hole";
    assertEquals(expected, parse(example));

    example = "me \\| the & test & hole";
    expected = "+allFields:me +allFields:test +allFields:hole";
    assertEquals(expected, parse(example));

    example = "president";
    expected = "allFields:president";
    assertEquals(expected, parse(example));

    example = "(fowl & helicopter) ~8 hillary";
    expected = "+spanNear([allFields:fowl, allFields:hillary], 8, false) +spanNear([allFields:helicopter, allFields:hillary], 8, false)";
    assertEquals(expected, parse(example));

    example = "(fowl | helicopter) ~6 hillary";
    expected = "spanOr([spanNear([allFields:fowl, allFields:hillary], 6, false), spanNear([allFields:helicopter, allFields:hillary], 6, false)])";
    assertEquals(expected, parse(example));

    // butnot resolves before proximity search
    example = "cop & flea ~4 horse";
    expected = "+allFields:cop +spanNear([allFields:flea, allFields:horse], 4, false)";
    assertEquals(expected, parse(example));
    //
    example = "cop | flea ~4 horse";
    expected = "allFields:cop spanNear([allFields:flea, allFields:horse], 4, false)";
    assertEquals(expected, parse(example));

    example = "(cop | flea) ~4 horse";
    expected = "spanOr([spanNear([allFields:cop, allFields:horse], 4, false), spanNear([allFields:flea, allFields:horse], 4, false)])";
    assertEquals(expected, parse(example));

    example = "(me & cop | her | cow) ~2 (parrot | cat)";
    expected = "(+spanOr([spanNear([allFields:me, allFields:parrot], 2, false), spanNear([allFields:me, allFields:cat], 2, false)]) +spanOr([spanNear([allFields:cop, allFields:parrot], 2, false), spanNear([allFields:cop, allFields:cat], 2, false)])) spanOr([spanOr([spanNear([allFields:her, allFields:parrot], 2, false), spanNear([allFields:her, allFields:cat], 2, false)]), spanOr([spanNear([allFields:cow, allFields:parrot], 2, false), spanNear([allFields:cow, allFields:cat], 2, false)])])";
    assertEquals(expected, parse(example));

    example = "me hammer & (clinton | me)";
    expected = "+allFields:me +allFields:hammer +(allFields:clinton allFields:me)";
    assertEquals(expected, parse(example));
    //
    example = "me & (clinton | me) ~4 (spont & him)";
    expected = "+allFields:me +((+spanNear([allFields:clinton, allFields:spont], 4, false) +spanNear([allFields:clinton, allFields:him], 4, false)) (+spanNear([allFields:me, allFields:spont], 4, false) +spanNear([allFields:me, allFields:him], 4, false)))";
    assertEquals(expected, parse(example));
    //
    example = "(him | me) ~3 those open";
    expected = "+spanOr([spanNear([allFields:him, allFields:those], 3, false), spanNear([allFields:me, allFields:those], 3, false)]) +allFields:open";
    assertEquals(expected, parse(example));

    example = "(him | me) ~3 (those | open)";
    expected = "spanOr([spanOr([spanNear([allFields:him, allFields:those], 3, false), spanNear([allFields:him, allFields:open], 3, false)]), spanOr([spanNear([allFields:me, allFields:those], 3, false), spanNear([allFields:me, allFields:open], 3, false)])])";
    assertEquals(expected, parse(example));

    // example = "me more ~4 horse";
    // expected = "+allFields:me +spanNear([allFields:more, allFields:horse], 4,
    // false)";
    // assertEquals(expected, parse(example));
    //
    // example = "more ~4 horse";
    // expected = "spanNear([allFields:more, allFields:horse], 4, false)";
    // assertEquals(expected, parse(example));
    //
    example = "more ~4 him ~3 old";
    expected = "+spanNear([allFields:more, allFields:him], 4, false) +(+spanNear([allFields:more, allFields:old], 3, false) +spanNear([allFields:him, allFields:old], 3, false))";
    assertEquals(expected, parse(example));

    example = "more ! her ~4 (horse | him) ~3 old";
    expected = "+(+spanOr([spanNear([allFields:more, allFields:horse], 4, false), spanNear([allFields:more, allFields:him], 4, false)]) -spanOr([spanNear([allFields:her, allFields:horse], 4, false), spanNear([allFields:her, allFields:him], 4, false)])) +(+(+spanNear([allFields:more, allFields:old], 3, false) -spanNear([allFields:her, allFields:old], 3, false)) +spanOr([spanNear([allFields:horse, allFields:old], 3, false), spanNear([allFields:him, allFields:old], 3, false)]))";
    assertEquals(expected, parse(example));
    //
    example = "more ~4 horse ~3 old ~2 core";
    expected = "+spanNear([allFields:more, allFields:horse], 4, false) +(+spanNear([allFields:more, allFields:old], 3, false) +spanNear([allFields:horse, allFields:old], 3, false)) +(+spanNear([allFields:more, allFields:core], 2, false) +spanNear([allFields:horse, allFields:core], 2, false) +spanNear([allFields:old, allFields:core], 2, false))";
    assertEquals(expected, parse(example));

    example = "(more | cow) ~4 horse ~3 (old & (barber ~3 casket0)) ~2 core";
    expected = "+spanOr([spanNear([allFields:more, allFields:horse], 4, false), spanNear([allFields:cow, allFields:horse], 4, false)]) +(+((+spanNear([allFields:more, allFields:old], 3, false) +spanNear([allFields:more, spanNear([allFields:barber, allFields:casket0], 3, false)], 3, false)) (+spanNear([allFields:cow, allFields:old], 3, false) +spanNear([allFields:cow, spanNear([allFields:barber, allFields:casket0], 3, false)], 3, false))) +(+spanNear([allFields:horse, allFields:old], 3, false) +spanNear([allFields:horse, spanNear([allFields:barber, allFields:casket0], 3, false)], 3, false))) +(+spanOr([spanNear([allFields:more, allFields:core], 2, false), spanNear([allFields:cow, allFields:core], 2, false)]) +spanNear([allFields:horse, allFields:core], 2, false) +(+spanNear([allFields:old, allFields:core], 2, false) +spanNear([spanNear([allFields:barber, allFields:casket0], 3, false), allFields:core], 2, false)))";
    assertEquals(expected, parse(example));

    example = "man & the | cow";
    expected = "allFields:man allFields:cow";
    assertEquals(expected, parse(example));

    example = "(cop | me & him) ~3 (hose | him & me)";
    expected = "(spanNear([allFields:cop, allFields:hose], 3, false) (+spanNear([allFields:cop, allFields:him], 3, false) +spanNear([allFields:cop, allFields:me], 3, false))) (+(spanNear([allFields:me, allFields:hose], 3, false) (+spanNear([allFields:me, allFields:him], 3, false) +spanNear([allFields:me, allFields:me], 3, false))) +(spanNear([allFields:him, allFields:hose], 3, false) (+spanNear([allFields:him, allFields:him], 3, false) +spanNear([allFields:him, allFields:me], 3, false))))";
    assertEquals(expected, parse(example));
    //
    // example = "cop | fowl & (fowl | priest & man) ! helicopter ~8 hillary
    // | tom";
    // expected = "+(allFields:cop allFields:fowl)
    // +(+spanNear([allFields:fowl, allFields:hillary], 8, false)
    // +spanNear([allFields:priest, allFields:hillary], 8, false)
    // +spanNear([allFields:man, allFields:hillary], 8, false)
    // -spanNear([allFields:helicopter, allFields:hillary], 8, false))";
    // assertEquals(expected, parse(example));
    //
    // example = "(cop | fowl) & (fowl & priest man) ! helicopter ~8
    // hillary";
    // expected = "+(allFields:cop allFields:fowl)
    // +(+spanNear([allFields:fowl, allFields:hillary], 8, false)
    // +spanNear([allFields:priest, allFields:hillary], 8, false)
    // +spanNear([allFields:man, allFields:hillary], 8, false)
    // -spanNear([allFields:helicopter, allFields:hillary], 8, false))";
    // assertEquals(expected, parse(example));
    //
    example = "priest man ! helicopter ~8 hillary";
    expected = "+allFields:priest +(+spanNear([allFields:man, allFields:hillary], 8, false) -spanNear([allFields:helicopter, allFields:hillary], 8, false))";
    assertEquals(expected, parse(example));

    // AnalyzerUtils.displayTokensWithFullDetails(analyzer, "m\\&m's");
    example = "m\\&m's";
    expected = "spanNear([allFields:m, allFields:m's], 0, true)";
    assertEquals(expected, parse(example));
  }

  public void testPhraseQueries() throws Exception {    

    // Two examples with stopwords
    //
    // Note that there is a 0-slop gap between "mark" and "best", leaving no room for "is" and "the".
    // This demonstrates Qsol doesn't play too well with StopFilter.
    //
    // Note about these two:
    // - Position increments of 2 are generated by the StopFilter implicitly created
    //   by StandardAnalyzer, the analyzer for parse().
    // - Qsol requires all the words in a phrase to have the same slop, rather than
    //   addressing the fact that some word pairs in a phrase might need different
    //   slop between them than others. Here, the query requires that "mark" and
    //   "best" be adjacent, even though this is incorrect.
      
    example = "\"mark is the best man\"";
    expected = "spanNear([allFields:mark, allFields:best, allFields:man], 0, true)";
    assertEquals(expected, parse(example));

    example = "\"MARK IS THE BEST MAN\"";
    expected = "spanNear([allFields:mark, allFields:best, allFields:man], 0, true)";
    assertEquals(expected, parse(example));
    
    
    // Parallel examples without stopwords:
    
    example = "\"best man mark\"";
    expected = "spanNear([allFields:best, allFields:man, allFields:mark], 0, true)";
    assertEquals(expected, parse(example));

    example = "\"BEST MAN MARK\"";
    expected = "spanNear([allFields:best, allFields:man, allFields:mark], 0, true)";
    assertEquals(expected, parse(example));

    
    // Phrase queries should also work with WhitespaceAnalyzer:
    
    // Without boost:
    example = "\"mark is the best man\"";
    expected = "spanNear([allFields:mark, allFields:is, allFields:the, allFields:best, allFields:man], 0, true)";
    assertEquals(expected, parse(example, new WhitespaceAnalyzer()));

    // With boost:
    example = "\"mark is the best man\":5";
    expected = "spanNear([allFields:mark, allFields:is, allFields:the, allFields:best, allFields:man], 5, true)";
    assertEquals(expected, parse(example, new WhitespaceAnalyzer()));
  }
  
  public void testPhraseQueriesWithSlopAndProximity() {
      example = "\"big time\":2 ~5 cat";
      expected = "spanNear([spanNear([allFields:big, allFields:time], 2, true), allFields:cat], 5, false)";
      assertEquals(expected, parse(example));

      example = "\"big time\":2 ~10 \"small town\"";
      expected = "spanNear([spanNear([allFields:big, allFields:time], 2, true), spanNear([allFields:small, allFields:town], 0, true)], 10, false)";
      assertEquals(expected, parse(example));
    }  
  
  public void testThatPhraseSlopIsNotAffectedByContext() throws Exception {
    // All these phrases should have default slop (slop == 0):

    example = "\"big time\"";
    expected = "spanNear([allFields:big, allFields:time], 0, true)";
    assertEquals(expected, parse(example));
    
    example = "\"big time\" & \"small town\"";
    expected = "+spanNear([allFields:big, allFields:time], 0, true) +spanNear([allFields:small, allFields:town], 0, true)";
    assertEquals(expected, parse(example));

    example = "\"big time\" ~5 cat";
    expected = "spanNear([spanNear([allFields:big, allFields:time], 0, true), allFields:cat], 5, false)";
    assertEquals(expected, parse(example));

    example = "cat ~21 \"big time\"";
    expected = "spanNear([allFields:cat, spanNear([allFields:big, allFields:time], 0, true)], 21, false)";
    assertEquals("bad cat B", expected, parse(example));

    example = "\"big time\" ~15 \"small town\"";
    expected = "spanNear([spanNear([allFields:big, allFields:time], 0, true), spanNear([allFields:small, allFields:town], 0, true)], 15, false)";
    assertEquals(expected, parse(example));

    example = "\"big time\" ord~15 \"small town\"";
    expected = "spanNear([spanNear([allFields:big, allFields:time], 0, true), spanNear([allFields:small, allFields:town], 0, true)], 15, true)";
    assertEquals(expected, parse(example));
  }

  public void testDefaultOp() {
    parser.setDefaultOp("|");
    example = "john mccain";
    expected = "allFields:john allFields:mccain";
    assertEquals(expected, parse(example));

    example = "AB | B | C ! D ! E";
    expected = "allFields:ab allFields:b (+allFields:c -allFields:d -allFields:e)";
    assertEquals(expected, parse(example));

  }

  public void testDateSearch() {
    example = "date(8/5/82)";
    expected = "date:19820805";
    assertEquals(expected, parse(example));

    example = "date(> 12/31/02)";
    expected = "ConstantScore(date:[20021231 TO *})";
    assertEquals(expected, parse(example));

    example = "date(< 03/23/2004)";
    expected = "ConstantScore(date:{* TO 20040323])";
    assertEquals(expected, parse(example));

    example = "date(3/23/2004 - 6/34/02)";
    expected = "ConstantScore(date:[20040323 TO 20020704])";
    assertEquals(expected, parse(example));

    example = "date(6/34/02) | mark";
    expected = "(date:20020704) allFields:mark";
    assertEquals(expected, parse(example));

  }

  public void testU() {
    example = "aa && (b  || c) !! d !! e";
    // expected = "+allFields:aa +(+(allFields:b allFields:c) -allFields:d
    // -allFields:e)";
    expected = "+allFields:aa +(+(allFields:b allFields:c) -allFields:d -allFields:e)";
    assertEquals(expected, parse(example));
  }

  public void testFieldSearch() {
    example = "field1,field2((search & old) ~3 horse)";
    expected = "(+spanNear([field1:search, field1:horse], 3, false) +spanNear([field1:old, field1:horse], 3, false)) (+spanNear([field2:search, field2:horse], 3, false) +spanNear([field2:old, field2:horse], 3, false))";
    assertEquals(expected, parse(example));

    example = "field1(search | old ~3 horse)";
    expected = "(field1:search spanNear([field1:old, field1:horse], 3, false))";
    assertEquals(expected, parse(example));

    parser.markDateField("date");
    parser.markDateField("udate");

    example = "date,udate(3/23/2004)";
    expected = "date:20040323 udate:20040323";
    assertEquals(expected, parse(example));
  }

  public void testFieldMapping() {
    parser.addFieldMapping("test", "tester1");
    example = "test,field2((search & old) ~3 horse)";
    expected = "(+spanNear([tester1:search, tester1:horse], 3, false) +spanNear([tester1:old, tester1:horse], 3, false)) (+spanNear([field2:search, field2:horse], 3, false) +spanNear([field2:old, field2:horse], 3, false))";
    assertEquals(expected, parse(example));

    example = "test(horse)";
    expected = "tester1:horse";
    assertEquals(expected, parse(example));

  }

  public void testFieldMapping2() {
    QsolConfiguration config = new QsolConfiguration();
    config.addFieldMapping("test", "tester1");
    parser = ParserFactory.getInstance(config).getParser(true);

    example = "test(horse)";
    expected = "tester1:horse";
    assertEquals(expected, parse(example));

  }

  public void testRangeSearches() {
    example = "{83 TO 85}";
    expected = "allFields:{83 TO 85}";
    assertEquals(expected, parse(example));

    example = "85 RNG 86";
    expected = "allFields:{85 TO 86}";
    assertEquals(expected, parse(example));

  }

  public void testMatchAll() {
    example = "band ! mark";
    expected = "+allFields:band -allFields:mark";
    assertEquals(expected, parse(example));

    example = "*:* ! mark";
    expected = "+*:* -allFields:mark";
    assertEquals(expected, parse(example));

  }

}
