package com.silibrina.tecnova.opendata.parsers.spreadsheet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.silibrina.tecnova.opendata.parsers.Parser;
import org.apache.poi.ss.usermodel.*;
import play.libs.Json;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Locale;

import static com.silibrina.tecnova.commons.utils.Preconditions.checkCondition;
import static com.silibrina.tecnova.commons.utils.Preconditions.checkNotNullCondition;

public abstract class MSOfficeParser implements Parser {
    private final Workbook workbook;

    MSOfficeParser(File file) throws IOException {
        checkNotNullCondition("file can not be null", file);
        checkCondition(String.format(Locale.getDefault(), "file (%s) must exist.",
                file.getAbsolutePath()), file.exists());

        workbook = getWorkbook(file);
    }

    @Override
    public JsonNode parse() {
        ObjectNode jsonWorkbook = Json.newObject();

        jsonWorkbook.put("version", workbook.getSpreadsheetVersion().toString());
        jsonWorkbook.put("number_of_sheets", workbook.getNumberOfSheets());
        jsonWorkbook.putArray("sheets_names").addAll(getSheetNames(workbook));
        putSheets(workbook, jsonWorkbook.putObject("sheets"));

        return jsonWorkbook;
    }

    private ArrayNode getSheetNames(Workbook workbook) {
        ArrayNode array = new ObjectMapper().valueToTree(new LinkedList<>());
        for (Sheet sheet : workbook) {
            array.add(sheet.getSheetName());
        }
        return array;
    }

    private void putSheets(Workbook workbook, ObjectNode node) {
        for (Sheet sheet : workbook) {
            putRows(sheet, node.putArray(sheet.getSheetName()));
        }
    }

    private void putRows(Sheet sheet, ArrayNode node) {
        for (Row row : sheet) {
            node.add(extractCells(row));
        }
    }

    private ArrayNode extractCells(Row row) {
        ArrayNode node = Json.newArray();
        for (Cell cell : row) {
            node.add(extractCell(cell));
        }
        return node;
    }

    private ObjectNode extractCell(Cell cell) {
        ObjectNode node = Json.newObject();

        node.put("content", cell.toString());
        Comment comment = cell.getCellComment();

        if (comment != null) {
            node.put("comment", cell.getCellComment().toString());
        }
        return node;
    }

    protected abstract Workbook getWorkbook(File file) throws IOException;
}
