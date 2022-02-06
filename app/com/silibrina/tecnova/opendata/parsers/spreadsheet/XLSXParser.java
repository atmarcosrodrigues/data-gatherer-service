package com.silibrina.tecnova.opendata.parsers.spreadsheet;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Class that parses a .xlsx (microsoft office 2007+ - xml) to a json representation.
 */
public class XLSXParser extends MSOfficeParser {
    public static final String XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    public XLSXParser(File file) throws IOException {
        super(file);
    }

    protected Workbook getWorkbook(File file) throws IOException {
        return new XSSFWorkbook(new FileInputStream(file));
    }
}
