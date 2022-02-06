package com.silibrina.tecnova.opendata.gatherer.searcher;

import com.silibrina.tecnova.commons.exceptions.InvalidConditionException;
import com.silibrina.tecnova.commons.model.EntryMetadata;
import com.silibrina.tecnova.opendata.gatherer.searcher.tasks.SearchEntryTask;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import play.Logger;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static com.silibrina.tecnova.commons.utils.Preconditions.checkValidString;

/**
 * This is the mechanism based on Lucene algorithm to a {@link EntryMetadata} id.
 * It look for similar terms in the document content and at the related metadata
 * (author, org, title etc).
 */
public class LuceneSearcher {
    private final IndexSearcher indexSearcher;
    private final QueryParser queryParser;

    public LuceneSearcher(String indexPath) throws IOException {
        Directory dir = FSDirectory.open(new File(indexPath).toPath());
        indexSearcher = new IndexSearcher(DirectoryReader.open(dir));

        queryParser = new MultiFieldQueryParser(getFields(), new StandardAnalyzer());
    }

    private static String[] getFields() {
        return new String[] { EntryMetadata.CONTENT, EntryMetadata.AUTHOR,
                EntryMetadata.ORG, EntryMetadata.TITLE, EntryMetadata.UPLOADER};
    }

    private TopDocs searchTopDocs(String rawQuery) throws ParseException, IOException {
        try {
            Query query = queryParser.parse(rawQuery);
            return indexSearcher.search(query, Integer.MAX_VALUE);
        } catch (ParseException e) {
            throw new InvalidConditionException("Invalid query content: " + rawQuery);
        }
    }

    /**
     * Executes a search for the given query. It not only matches exact terms, but
     * it searches for similarities as well (E.g.: two words in a phrase).
     *
     * @param rawQuery the query to search related entries.
     *
     * @return a list of the related entries.
     *
     * @throws IOException if an error happens while IO operations.
     * @throws ParseException  if the parsing the query fails.
     */
    public Set<String> search(String rawQuery) throws IOException, ParseException {
        checkValidString("query can not be null or empty", rawQuery);

        TopDocs topDocs = searchTopDocs(rawQuery);

        Set<String> ids = new HashSet<>(topDocs.totalHits);
        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
            Document document = indexSearcher.doc(scoreDoc.doc);
            ids.add(document.get(EntryMetadata.ID));
        }
        return ids;
    }
}
