package com.silibrina.tecnova.opendata.parsers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.POJONode;
import com.google.gson.JsonArray;
import com.silibrina.tecnova.opendata.parsers.spreadsheet.MSOfficeParser;
import jdk.nashorn.internal.ir.ObjectNode;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public abstract class MSOfficeParserTests {
    protected JsonNode result;

    @Before
    public void setUp() throws JSONException, IOException {
        result = getParser().parse();
    }

    @Test
    public void createdWithSuccess() {
        assertNotNull(result);
    }

    @Test
    public void parseToJsonTest() throws JSONException {
        assertTrue(result.has("version"));
        assertEquals(1, result.get("number_of_sheets").asInt());
        assertEquals(1, result.get("sheets_names").size());
        assertEquals("Sheet1", result.get("sheets_names").get(0).asText());
        assertNotNull(result.get("sheets"));
    }

    @Test
    public void sheetsTest() throws JSONException {
        JsonNode sheets = result.get("sheets");
        assertNotNull(sheets);
        JsonNode sheet1 = sheets.get("Sheet1");
        assertNotNull(sheet1);
        assertEquals(241, sheet1.size());
        for (JsonNode row : sheet1) {
            assertNotNull("row must not be null", row);
            for (JsonNode cell : row) {
                assertNotNull("cell must not be null", cell);
                assertTrue(cell.has("content"));
            }
        }
    }

    protected abstract MSOfficeParser getParser() throws IOException;
}
