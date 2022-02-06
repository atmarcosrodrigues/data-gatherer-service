package com.silibrina.tecnova.opendata.parsers;

import com.silibrina.tecnova.opendata.parsers.spreadsheet.MSOfficeParser;
import com.silibrina.tecnova.opendata.parsers.spreadsheet.XLSParser;
import com.silibrina.tecnova.opendata.parsers.spreadsheet.XLSXParser;
import com.silibrina.tecnova.opendata.utils.ODFileUtils;
import org.json.JSONException;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

public class ConvertersTest {
    private static final String DIR = ODFileUtils.getCurrentDir("test-files");
    private static final String XLS_FILE = DIR + "/tmp.xls";
    private static final String XLSX_FILE = DIR + "/tmp.xlsx";

    @Test
    public void readXLSFileTest() throws IOException, JSONException {
        MSOfficeParser xlsx = new XLSParser(new File(XLS_FILE));
        assertNotNull(xlsx);
    }

    @Test
    public void readXLSXFileTest() throws IOException, JSONException {
        MSOfficeParser xlsx = new XLSXParser(new File(XLSX_FILE));
        assertNotNull(xlsx);
    }
}
