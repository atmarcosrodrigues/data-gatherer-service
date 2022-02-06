package com.silibrina.tecnova.opendata.parsers;

import com.silibrina.tecnova.opendata.parsers.spreadsheet.MSOfficeParser;
import com.silibrina.tecnova.opendata.parsers.spreadsheet.XLSXParser;
import com.silibrina.tecnova.opendata.utils.ODFileUtils;
import org.json.JSONException;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class XLSXParserTest extends MSOfficeParserTests {

    @Override
    protected MSOfficeParser getParser() throws IOException {
        return new XLSXParser(new File(ODFileUtils.getCurrentDir("test-files/tmp.xlsx")));
    }

    @Test
    public void versionTest() throws JSONException {
        assertEquals("EXCEL2007", result.get("version").asText());
    }
}
