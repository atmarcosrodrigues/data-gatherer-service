package com.silibrina.tecnova.opendata.gatherer.searcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.silibrina.tecnova.commons.model.EntryMetadata;
import com.silibrina.tecnova.commons.model.Response;
import com.silibrina.tecnova.opendata.executors.ExecutorFactory;
import com.silibrina.tecnova.opendata.gatherer.searcher.tasks.SearchEntryTask;
import com.silibrina.tecnova.opendata.gatherer.searcher.tasks.SearchResult;
import com.typesafe.config.ConfigFactory;
import org.apache.lucene.queryparser.classic.ParseException;
import play.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.*;

import static com.silibrina.tecnova.commons.conf.ConfigConstants.Strings.GATHERER_INDEX_DIR;
import static com.silibrina.tecnova.commons.utils.Preconditions.checkNotNullCondition;
import static com.silibrina.tecnova.opendata.executors.ExecutorFactory.ExecutorType.SEARCHER;

/**
 * This is a simple mechanism to execute {@link EntryMetadata} searches.
 * This mechanism includes not only the metadata information (which are queried
 * in database) but the similarities in file content as well.
 */
public class SimpleSearchMechanism implements SearchMechanism {
    private final static Logger.ALogger logger = Logger.of(SimpleSearchMechanism.class);

    private final String indexDir;
    private final Executor executor;

    public SimpleSearchMechanism() throws IOException {
        indexDir = getIndexDirFromConfig();
        executor = ExecutorFactory.getExecutor(SEARCHER);
    }

    @Override
    public CompletionStage<SearchResult> search(JsonNode query) throws ParseException, java.text.ParseException, IOException {
        SearchEntryTask searchEntryTask = new SearchEntryTask(indexDir, query);

        return CompletableFuture.supplyAsync(() -> {
            try {
                return searchEntryTask.call();
            } catch (Throwable e) {
                logger.error("An error occurred while trying to search entry.", e);
            }
            return new SearchResult(new ArrayList<>(), 0L, false);
        }, executor);
    }

    @Override
    public CompletionStage<Response<?>> find(String id) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                EntryMetadata entry = new EntryMetadata().find(id);
                checkNotNullCondition(String.format(Locale.getDefault(), "Entry not found [%s]", id), entry);
                return new Response<>(entry);
            } catch (Throwable e) {
                return new Response<>(e);
            }
        });
    }

    private String getIndexDirFromConfig() {
        return ConfigFactory.load().getString(GATHERER_INDEX_DIR.field);
    }
}
