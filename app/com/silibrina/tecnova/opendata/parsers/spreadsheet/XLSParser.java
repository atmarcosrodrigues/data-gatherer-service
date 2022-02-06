package com.silibrina.tecnova.opendata.parsers.spreadsheet;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Class that parses a .xls (microsoft office -2007 - binary) to a json representation.
 */
public class XLSParser extends MSOfficeParser {
    public static final String XLS_MIME_TYPE = "application/vnd.ms-excel";

    public XLSParser(File file) throws IOException {
        super(file);
    }

    protected Workbook getWorkbook(File file) throws IOException {
        return new HSSFWorkbook(new FileInputStream(file));
    }
}
