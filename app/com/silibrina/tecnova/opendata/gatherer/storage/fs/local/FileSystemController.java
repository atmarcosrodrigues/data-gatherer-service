package com.silibrina.tecnova.opendata.gatherer.storage.fs.local;

import com.fasterxml.jackson.databind.JsonNode;
import com.silibrina.tecnova.commons.model.EntryMetadata;
import com.silibrina.tecnova.commons.model.Response;
import com.silibrina.tecnova.commons.model.file.FileMetadata;
import com.silibrina.tecnova.commons.model.file.FileVersion;
import com.silibrina.tecnova.opendata.gatherer.searcher.SearchMechanism;
import com.silibrina.tecnova.opendata.gatherer.searcher.SimpleSearchMechanism;
import com.silibrina.tecnova.opendata.utils.FileResponse;
import play.Logger;

import java.io.File;
import java.io.IOException;


import java.util.Locale;
import java.util.concurrent.CompletionStage;

import static com.silibrina.tecnova.commons.model.EntryMetadata.Status.MISSING_FILE;
import static com.silibrina.tecnova.commons.utils.Preconditions.checkCondition;
import static com.silibrina.tecnova.commons.utils.Preconditions.checkNotNullCondition;
import static com.silibrina.tecnova.conversion.fs.parser.OriginalParser.TYPE;

/**
 * This is the controller to a file system, this controller enables the downloading
 * of files using this service api using this service api, this is the case of locally
 * stored files.
 */
public class FileSystemController {
    private static final Logger.ALogger logger = Logger.of(FileSystemController.class);
    private static final String DOWNLOAD_TYPE = "download_type";
    private static final String DOWNLOAD_TYPE_INLINE = "inline";

    private final SearchMechanism searchMechanism;

    public FileSystemController() throws IOException {
        searchMechanism = new SimpleSearchMechanism();
    }

    public CompletionStage<Response<?>> get(String type, String entryId, JsonNode body) {
        logger.debug("type: {}, entryId: {}, body: {}", type, entryId, body);

        CompletionStage<Response<?>> entry = searchMechanism.find(entryId);
        return entry.thenApply(response -> {
            if (response.isException()) {
                return response;
            }

            try {
                EntryMetadata entryMetadata = (EntryMetadata) response.getPayload();
                logger.debug("entryMetadata: {}", entryMetadata);

                checkNotNullCondition(String.format(Locale.getDefault(),
                        "Entry [%s] does not have a file yet.", entryId), entryMetadata.getFileMetadata());
                checkCondition("status of entry is MISSING_FILE", entryMetadata.getStatus() != MISSING_FILE);

                FileMetadata fileMetadata = entryMetadata.getFileMetadata();

                checkNotNullCondition("this entry does not have a file associated with it.", fileMetadata);

                return new Response<>(getContent(type, fileMetadata, body));
            } catch (Throwable e) {
                return new Response<>(e);
            }
        });
    }

    private boolean isInline(JsonNode body) {
        return body.has(DOWNLOAD_TYPE)
                && !body.get(DOWNLOAD_TYPE).isNull()
                && body.get(DOWNLOAD_TYPE).asText().toLowerCase().equals(DOWNLOAD_TYPE_INLINE);
    }

    private FileResponse getContent(String type, FileMetadata fileMetadata, JsonNode body) throws Exception {
        checkNotNullCondition("type can not be null", type);

        FileVersion fileVersion = fileMetadata.getVersion(type.toLowerCase());
        checkNotNullCondition("This file does not support this format.", fileVersion);

        return new FileResponse(new File(fileVersion.getPath()), getFileName(fileMetadata.getOriginalName(), type.toLowerCase()), isInline(body));
    }

    private String getFileName(String originalName, String format) {
        if (!format.equals(TYPE)) {
            return String.format(Locale.getDefault(), "%s.%s", originalName, format);
        } else {
            return originalName;
        }
    }

}
