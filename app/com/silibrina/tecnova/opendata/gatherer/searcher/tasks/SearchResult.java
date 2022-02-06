package com.silibrina.tecnova.opendata.gatherer.searcher.tasks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.silibrina.tecnova.commons.model.EntryMetadata;
import com.silibrina.tecnova.commons.model.JsonParseable;
import com.silibrina.tecnova.commons.utils.Preconditions;

import java.util.List;
import java.util.Locale;

import static com.silibrina.tecnova.commons.utils.Preconditions.checkNotNullCondition;

public class SearchResult extends JsonParseable {
    public static final String ENTRIES = "entries";
    public static final String COUNT = "count";

    private final List<EntryMetadata> entries;
    private final long count;
    private final boolean hasContent;

    public SearchResult(List<EntryMetadata> entries, long count, boolean hasContent) {
        checkNotNullCondition("entries must not be null", entries);

        this.entries = entries;
        this.count = count;
        this.hasContent = hasContent;
    }

    public long count() {
        return count;
    }

    public List<EntryMetadata> getEntries() {
        return entries;
    }

    public boolean hasContent() {
        return hasContent;
    }

    @Override
    public String toString() {
        return String.format(Locale.getDefault(),
                "%s [count: %d, entries: %s]",
                this.getClass().getSimpleName(), count, entries);
    }

    @Override
    public JsonNode toJson(ObjectNode body) {
        body.put(COUNT, count);
        ArrayNode entriesJson = body.putArray(ENTRIES);
        for (EntryMetadata entry : entries) {
            entriesJson.add(entry.toJson());
        }
        return body;
    }
}
