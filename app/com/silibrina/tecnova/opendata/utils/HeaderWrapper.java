package com.silibrina.tecnova.opendata.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.silibrina.tecnova.commons.model.Response;
import play.Logger;
import play.libs.Json;
import play.mvc.Result;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static play.mvc.Http.Status.BAD_REQUEST;
import static play.mvc.Results.ok;
import static play.mvc.Results.status;

/**
 * Formats the response of request encapsulating it in a {@link Result} with the
 * proper header including status, a message and body (payload).
 */
public class HeaderWrapper {
    public static final String DATA = "data";
    public static final String MESSAGE = "message";
    public static final String STATUS = "status";

    private static final Logger.ALogger logger = Logger.of(HeaderWrapper.class);

    public static CompletionStage<Result> result(CompletionStage<Response<?>> file) {
        return file.thenApply(response -> {
            logger.debug("response: {}", response);
            if (response.isException()) {
                return resultSync((Throwable) response.getPayload());
            }
            FileResponse fileResponse = (FileResponse) response.getPayload();
            if (fileResponse.inline()) {
                return ok(fileResponse.getFile(), fileResponse.inline());
            }
            return ok(fileResponse.getFile(), fileResponse.getFilename());
        });
    }

    public static Result result(File file) {
        return ok(file);
    }

    public static Result resultSync(Throwable exception) {
        return status(BAD_REQUEST, createHeader("Error", exception.getMessage(), Json.newObject())).as("application/json");
    }

    public static Result result(String message, JsonNode response) {
        return ok(createHeader("Ok", message, response)).as("application/json");
    }

    public static CompletionStage<Result> result(Throwable exception) {
        return CompletableFuture.supplyAsync(() -> {
            logger.debug("response: {}", exception.getMessage());
            JsonNode header = createHeader("Error", exception.getMessage(), Json.newObject());
            return status(BAD_REQUEST, header).as("application/json");
        });
    }

    public static CompletionStage<Result> result(String message, CompletionStage<Response<?>> response) {
        return response.thenApply(result -> {
            logger.debug("result: {}", result);
            if (result.isException()) {
                return resultSync((Throwable) result.getPayload());
            }
            return result(message, (JsonNode) result.getPayload());
        });
    }

    public static CompletionStage<Result> result(Result result) {
        return CompletableFuture.supplyAsync(() -> {
            logger.debug("result: {}", result);
            return result;
        });
    }

    private static JsonNode createHeader(String status, String message, JsonNode data) {
        ObjectNode node = Json.newObject();
        node.put(STATUS, status);
        node.put(MESSAGE, message);
        node.set(DATA, data);
        return node;
    }
}
