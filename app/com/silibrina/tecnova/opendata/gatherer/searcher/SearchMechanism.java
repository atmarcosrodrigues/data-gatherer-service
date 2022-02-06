package com.silibrina.tecnova.opendata.gatherer.searcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.silibrina.tecnova.commons.model.EntryMetadata;
import com.silibrina.tecnova.commons.model.Response;
import com.silibrina.tecnova.opendata.gatherer.searcher.tasks.SearchResult;
import org.apache.lucene.queryparser.classic.ParseException;

import java.io.IOException;
import java.util.concurrent.CompletionStage;

/**
 * Describes a search mechanism for indexed entries.
 */
public interface SearchMechanism {


    /**
     * Search by metadata and content of the file for the given query.
     *
     * @param query term to search in files.
     *
     * @return list of ids for {@link EntryMetadata}.
     */
    CompletionStage<SearchResult> search(JsonNode query) throws IOException, ParseException, java.text.ParseException;

    /**
     * Retrieves an {@link EntryMetadata} from the database.
     *
     * @param id the id of the entry.
     *
     * @return an entry or null, if not found.
     */
    CompletionStage<Response<?>> find(String id);
}
