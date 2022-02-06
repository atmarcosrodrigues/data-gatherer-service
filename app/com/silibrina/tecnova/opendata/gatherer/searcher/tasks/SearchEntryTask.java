package com.silibrina.tecnova.opendata.gatherer.searcher.tasks;

import com.fasterxml.jackson.databind.JsonNode;
import com.silibrina.tecnova.commons.exceptions.InvalidConditionException;
import com.silibrina.tecnova.commons.exceptions.UnrecoverableErrorException;
import com.silibrina.tecnova.commons.model.EntryMetadata;
import com.silibrina.tecnova.commons.model.Model;
import com.silibrina.tecnova.opendata.gatherer.searcher.LuceneSearcher;
import com.silibrina.tecnova.opendata.models.EntryMetadataPresenter;
import com.silibrina.tecnova.opendata.utils.Helper;
import play.Logger;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Callable;

import static com.silibrina.tecnova.commons.exceptions.ExitStatus.DEVELOPER_ERROR_STATUS;
import static com.silibrina.tecnova.commons.model.EntryMetadata.*;
import static com.silibrina.tecnova.commons.model.EntryMetadata.Status.*;
import static com.silibrina.tecnova.commons.utils.Preconditions.checkCondition;
import static com.silibrina.tecnova.commons.utils.Preconditions.checkNotNullCondition;
import static com.silibrina.tecnova.commons.utils.Preconditions.checkValidString;

/**
 * This is the task for executing a search in the metadata entries.
 * This task will search for similarities in the file content and
 * in other metadata fields.
 * It is also possible to restrict the search by metadata fields.
 */
public class SearchEntryTask implements Callable<SearchResult> {
    private static final Logger.ALogger logger = Logger.of(SearchEntryTask.class);

    public static final String PAGE = "page";
    public static final String LIMIT = "limit";
    public static final String ORDER_BY = "order_by";
    public static final String ORDER = "order";

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_LIMIT = 50;
    private static final int DEFAULT_MAX_LIMIT = 500;

    private final LuceneSearcher searcher;
    private final JsonNode params;
    private final List<Query> queries;
    private final String rawQuery;
    private final Object[] parameters;
    private final int page;
    private final int limit;
    private final String orderBy;

    public SearchEntryTask(String indexDir, JsonNode params)
            throws IOException, org.apache.lucene.queryparser.classic.ParseException, ParseException {
        checkValidString("index directory must be a valid string", indexDir);
        checkNotNullCondition("params can not be null", params);

        this.searcher = new LuceneSearcher(indexDir);
        this.params = params;

        Set<String> idsFound = search(params);
        queries = createQuery(idsFound, params);

        rawQuery = queriesToString(queries);
        parameters = queriesToObject(queries);

        page = getPage(params);
        limit = getLimit(params);
        orderBy = getOrderByField(params);
    }

    @Override
    public SearchResult call() throws Exception {
        try {
            if (isEmptyResult()) {
                return new SearchResult(new ArrayList<>(), 0L, false);
            }
            int skip = this.page * limit;
            List<EntryMetadata> entries = new EntryMetadata().find(rawQuery, parameters, skip, limit, orderBy);
            long count = new EntryMetadata().count(rawQuery, parameters);
            return new SearchResult(entries, count, true);
        } catch (InvalidConditionException e) {
            logger.warn("Bad parameter. params: {}, message: {}", params, e.getMessage(), e);
        }
        return new SearchResult(new ArrayList<>(), 0L, false);
    }

    /**
     * If an extra parameter is given, indexer query should give a list of ids
     * to {@code queries}, if this happens and the query is still empty, it means
     * no entry was found based on the given query.
     *
     * @return true if indexer found something and added to {@code queries},
     *          false otherwise.
     */
    private boolean isEmptyResult() {
        return params.has(CONTENT) && !(wasFoundByIndexer());
    }

    private boolean wasFoundByIndexer() {
        for (Query query : queries) {
            if (query.query.contains("_id")) {
                return true;
            }
        }
        return false;
    }

    private Set<String> search(JsonNode params) throws IOException, org.apache.lucene.queryparser.classic.ParseException {
        if (hasField(EntryMetadata.CONTENT, params)) {
            return searcher.search(params.get(EntryMetadata.CONTENT).asText());
        }
        return new HashSet<>();
    }

    private String queriesToString(List<Query> queries) {
        StringBuilder builder = new StringBuilder("{");
        for (Query query : queries) {
            builder.append(query.query).append(", ");
        }
        if (builder.length() >= 3) {
            builder.deleteCharAt(builder.length() - 2);
        }
        builder.append("}");
        return builder.toString();
    }

    private Object[] queriesToObject(List<Query> queries) {
        Object[] values = new Object[queries.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = queries.get(i).value;
        }
        return values;
    }

    private List<Query> createQuery(Set<String> ids, JsonNode params) throws ParseException {
        List<Query> queries = new LinkedList<>();
        addIdQuery(ids, params);
        addIdsQuery(queries, ids);

        addDatePeriod(queries, params);
        addField(queries, params, AUTHOR);
        addField(queries, params, ORG);
        addField(queries, params, TITLE);
        addField(queries, params, UPLOADER);
        addStatusQuery(queries, params);
        return queries;
    }

    private void addIdQuery(Set<String> ids, JsonNode params) {
        if (!hasField(ID, params)) {
            return;
        }
        JsonNode value = params.get(ID);
        if (value.isArray()) {
            for (JsonNode node : value) {
                ids.add(node.asText());
            }
        } else {
            ids.add(value.asText());
        }
    }

    private void addIdsQuery(List<Query> queries, Set<String> ids) {
        if (ids != null && !ids.isEmpty()) {
            queries.add(new Query("_id : {$in : #}", Model.toObjectIds(ids)));
        }
    }

    private void addStatusQuery(List<Query> queries, JsonNode params) {
        String query = String.format(Locale.getDefault(), "%s : #", STATUS);

        if (params.has(STATUS)) {
            checkCondition("status must be a valid string (READY, MISSING_FILE, DELETED, TO_INDEX or ALL)",
                    !params.get(STATUS).isNull() && params.get(STATUS).isTextual());

            Status status = getStatus(params);

            switch (status) {
                case READY:
                    queries.add(new Query(query, String.valueOf(READY)));
                    break;
                case MISSING_FILE:
                    queries.add(new Query(query, String.valueOf(MISSING_FILE)));
                    break;
                case DELETED:
                    queries.add(new Query(query, String.valueOf(DELETED)));
                    break;
                case TO_INDEX:
                    queries.add(new Query(query, String.valueOf(TO_INDEX)));
                    break;
                case ALL:
                    break;
                default:
                    throw new UnrecoverableErrorException("status not implemented: " + status, DEVELOPER_ERROR_STATUS);
            }
        } else {
            queries.add(new Query(query, READY));
        }
    }

    private Status getStatus(JsonNode params) {
        String rawStatus = params.get(STATUS).asText().toUpperCase();

        try {
            return Status.valueOf(rawStatus);
        } catch (IllegalArgumentException e) {
            throw new InvalidConditionException(
                    String.format(Locale.getDefault(),
                            "Invalid status: %s - status must be READY, MISSING_FILE, DELETED, TO_INDEX or ALL",
                            rawStatus));
        }
    }

    private void addField(List<Query> queries, JsonNode params, String field) {
        if (!hasField(field, params)) {
            return;
        }
        JsonNode value = params.get(field);
        if (value.isArray()) {
            Set<String> values = new HashSet<>();
            for (JsonNode node : value) {
                values.add(node.asText());
            }
            String rawQuery = String.format(Locale.getDefault(), "%s : { $in : #}", field);
            queries.add(new Query(rawQuery, values));
        } else {
            String rawQuery = String.format(Locale.getDefault(), "%s : { $regex : #, $options : 'i' }", field);
            queries.add(new Query(rawQuery, ".*" + value.asText() + ".*"));
        }
    }

    private void addDatePeriod(List<Query> queries, JsonNode params) throws ParseException {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
        if (hasField(PERIOD_START, params)) {
            Date start = sdf.parse(params.get(PERIOD_START).asText());
            queries.add(new Query(PERIOD_START + ": {$gte: #}", EntryMetadataPresenter.getCalMin(start)));
        }
        if (hasField(PERIOD_END, params)) {
            Date end = sdf.parse(params.get(PERIOD_END).asText());
            queries.add(new Query(PERIOD_END + ": {$lte: #}", EntryMetadataPresenter.getCalMax(end)));
        }
    }

    private boolean hasField(String field, JsonNode params) {
        return params.has(field)
                && params.get(field) != null
                && !params.get(field).isNull();
    }

    private int getPage(JsonNode params) {
        int page = DEFAULT_PAGE;
        if (params.has(PAGE)) {
            page = params.get(PAGE).asInt();
        }

        checkCondition("page number must be greater or equal than 0", page >= 0);

        return page;
    }

    private int getLimit(JsonNode params) {
        int limit = DEFAULT_LIMIT;
        if (params.has(LIMIT)) {
            limit = params.get(LIMIT).asInt();
        }

        checkCondition("limit of entries should be greater than 0", limit > 0);
        checkCondition("limit of entries should be less or equal than " + DEFAULT_MAX_LIMIT, limit <= DEFAULT_MAX_LIMIT);

        return limit;
    }

    private String getOrderByField(JsonNode params) {
        if (params.has(ORDER_BY)) {
            String orderBy = params.get(ORDER_BY).asText();
            List<String> fields = Arrays.asList(Helper.fields);

            if (!fields.contains(orderBy)) {
                throw new InvalidConditionException("invalid field to order_by: " + orderBy);
            }

            return String.format(Locale.getDefault(),
                    "{ %s : %s }",
                    orderBy, getOrderField(params));
        }
        return null;
    }

    private String getOrderField(JsonNode params) {
        if (params.has(ORDER)) {
            String order = params.get(ORDER).asText().trim().toLowerCase();
            switch (order) {
                case "asc":
                    return "1";
                case "desc":
                    return "-1";
            }
        }
        throw new InvalidConditionException("order must be ASC or DESC");
    }

    /**
     * Represents a query, the string the way Jongo expects
     * and the value to be applied to.
     */
    private class Query {
        private final String query;
        private final Object value;

        private Query(String query, Object value) {
            this.query = query;
            this.value = value;
        }

        @Override
        public String toString() {
            return String.format(Locale.getDefault(),
                    "[query: %s, value: %s]"
                    ,query, value);
        }
    }
}
