package com.mhs.qsol;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.search.spell.SpellChecker;
import org.apache.lucene.store.Directory;

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
import java.io.IOException;
import java.io.StringReader;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SuggestedSearch {
  private List<String> parts = new ArrayList<String>();
  private List<String> slots = new ArrayList<String>();
  private StringBuilder suggestedQuery;
  private Directory didYouMeanDirectory;
  private Analyzer analyzer;
  private boolean foundSuggestion;
  SpellChecker spellChecker;

  public SuggestedSearch(Directory didYouMeanDirectory, Analyzer anazlyer) {
    this.didYouMeanDirectory = didYouMeanDirectory;
    this.analyzer = anazlyer;
  }

  public void addPart(String part) {
    parts.add(part);
  }

  public String getInfo() {
    StringBuilder info = new StringBuilder();

    for (String part : parts) {
      info.append(" part: " + part);
    }

    for (String slot : slots) {
      info.append(" slot: " + slot);
    }

    return info.toString();
  }

  public void setSlot(int slot, String value) {

    slots.set(slot, value);
  }

  public void addSlot(String value) {

    slots.add(value);
  }

  /**
   * @return
   */
  public String getSuggestedSearch() {
    suggestedQuery = new StringBuilder();

    Iterator<String> partsIt = parts.iterator();
    Iterator<String> slotIt = slots.iterator();

    while (partsIt.hasNext()) {
      suggestedQuery.append(partsIt.next());

      if (slotIt.hasNext()) {
        suggestedQuery.append(getTerm(slotIt.next()));
      }
    }

    return suggestedQuery.toString();
  }

  private String getTerm(String term) {
    if (term.length() == 0) {
      return "";
    }
    TokenStream source = analyzer.tokenStream("", new StringReader(term));
    CharTermAttribute charTermAtrib = source.getAttribute(CharTermAttribute.class);
    String anaTerm = null;

    try {
      source.incrementToken();
      anaTerm = charTermAtrib.toString();
      if (source.incrementToken()) {
        return term;
      }
    } catch (IOException e2) {
      throw new RuntimeException(e2);
    }

    if (spellChecker == null) {
      try {
        spellChecker = new SpellChecker(didYouMeanDirectory);
      } catch (IOException e1) {
        throw new RuntimeException(e1);
      }
    }

    String returnTerm = null;

    try {
      if (spellChecker.exist(term)) {
        return term;
      }

      spellChecker.setAccuracy(.000f);

      String[] similarWords = spellChecker.suggestSimilar(anaTerm, 1);

      if (similarWords.length == 0) {
        return term;
      }

      // suggestedQuery = true;
      returnTerm = similarWords[0];
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    foundSuggestion = true;

    return returnTerm;
  }

  /**
   * @return the foundSuggestion
   */
  public boolean foundSuggestion() {
    return foundSuggestion;
  }
}
