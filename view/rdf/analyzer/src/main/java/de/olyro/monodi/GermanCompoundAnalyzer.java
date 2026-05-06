package de.olyro.monodi;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.compound.DictionaryCompoundWordTokenFilter;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Lucene Analyzer for German text that splits compound words using a dictionary,
 * so that e.g. "Warenhäuser" also produces the token "häuser" (→ "hauser" after
 * ASCII folding) and matches a query for "Häuser".
 *
 * Pipeline: StandardTokenizer → LowerCaseFilter → DictionaryCompoundWordTokenFilter
 *           → ASCIIFoldingFilter
 *
 * ASCIIFolding is intentionally placed after compound splitting so that umlaut
 * forms such as "häuser" still match dictionary entries before being folded.
 *
 * The dictionary is loaded from the classpath resource /german-words.txt (one
 * lowercase word per line).  An alternative path can be supplied via the system
 * property {@code de.olyro.compound.dictionary}.
 */
public class GermanCompoundAnalyzer extends Analyzer {

    // Tokens shorter than this are not considered candidate compounds.
    private static final int MIN_WORD_SIZE = 5;
    // Subwords shorter than this are not emitted.
    private static final int MIN_SUBWORD_SIZE = 4;
    private static final int MAX_SUBWORD_SIZE = 15;

    private final CharArraySet dictionary;

    public GermanCompoundAnalyzer() {
        this.dictionary = loadDictionary();
    }

    private static CharArraySet loadDictionary() {
        String path = System.getProperty("de.olyro.compound.dictionary");
        try (InputStream in = path != null
                ? new FileInputStream(path)
                : GermanCompoundAnalyzer.class.getResourceAsStream("/german-words.txt")) {

            if (in == null) {
                throw new IllegalStateException(
                    "German compound dictionary not found on classpath (/german-words.txt). " +
                    "Override with -Dde.olyro.compound.dictionary=/path/to/file");
            }

            // CharArraySet with ignoreCase=true so the lowercased tokens from the
            // pipeline match dictionary entries regardless of original capitalisation.
            CharArraySet dict = new CharArraySet(200_000, true);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        dict.add(line);
                    }
                }
            }
            return dict;

        } catch (IOException e) {
            throw new RuntimeException("Failed to load compound dictionary", e);
        }
    }

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new StandardTokenizer();
        TokenStream stream = new LowerCaseFilter(tokenizer);
        stream = new DictionaryCompoundWordTokenFilter(
                stream, dictionary, MIN_WORD_SIZE, MIN_SUBWORD_SIZE, MAX_SUBWORD_SIZE,
                false /* onlyLongestMatch: emit all subwords, not just the longest */);
        stream = new ASCIIFoldingFilter(stream);
        return new TokenStreamComponents(tokenizer, stream);
    }
}
