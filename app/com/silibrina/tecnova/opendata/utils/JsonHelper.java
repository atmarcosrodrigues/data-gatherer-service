package com.silibrina.tecnova.opendata.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.libs.Json;

import java.util.Map;

public class JsonHelper {

    public static JsonNode mapToJson(Map<String, String[]> data) {
        ObjectNode body = Json.newObject();
        for (String key : data.keySet()) {
            String[] values = data.get(key);
            if (values != null) {
                if (values.length >= 1) {
                    body.put(key, values[0]);
                }
            }

        }
        return body;
    }
}
