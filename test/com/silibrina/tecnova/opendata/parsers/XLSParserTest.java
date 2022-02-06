package com.silibrina.tecnova.opendata.parsers;

import com.silibrina.tecnova.opendata.parsers.spreadsheet.MSOfficeParser;
import com.silibrina.tecnova.opendata.parsers.spreadsheet.XLSParser;
import com.silibrina.tecnova.opendata.utils.ODFileUtils;
import org.json.JSONException;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class XLSParserTest extends MSOfficeParserTests {

    @Override
    protected MSOfficeParser getParser() throws IOException {
        return new XLSParser(new File(ODFileUtils.getCurrentDir("test-files/tmp.xls")));
    }

    @Test
    public void versionTest() throws JSONException {
        assertEquals("EXCEL97", result.get("version").asText());
    }
}
