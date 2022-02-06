package com.silibrina.tecnova.opendata.parsers;

import com.fasterxml.jackson.databind.JsonNode;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Specify a parser object to another format.
 */
public interface Parser {

    /**
     * Parsers the object to a given representation (e.g. json).
     * Basically, extracting the content and converting to it.
     *
     * @return the new representation of the object.
     */
    JsonNode parse() throws Exception ;
}
