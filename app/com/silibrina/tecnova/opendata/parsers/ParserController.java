package com.silibrina.tecnova.opendata.parsers;

import com.silibrina.tecnova.opendata.exceptions.InvalidTypeException;
import com.silibrina.tecnova.opendata.parsers.spreadsheet.XLSParser;
import com.silibrina.tecnova.opendata.parsers.spreadsheet.XLSXParser;
import org.apache.tika.Tika;

import java.io.File;
import java.io.IOException;

import static com.silibrina.tecnova.opendata.parsers.spreadsheet.XLSParser.XLS_MIME_TYPE;
import static com.silibrina.tecnova.opendata.parsers.spreadsheet.XLSXParser.XLSX_MIME_TYPE;

public class ParserController {
    public static final String FORMAT = "format";

    public Parser parse(File file) throws IOException {
        ParserType type = extractFileType(file);
        switch (type) {
            case MS_XLS_TO_JSON:
                return new XLSParser(file);
            case MS_XLSX_TO_JSON:
                return new XLSXParser(file);
            default:
                throw new InvalidTypeException("Parser type not found: " + type);
        }
    }

    /**
     * Type of the src file. This are the supported types by now.
     */
    private enum ParserType {
        MS_XLS_TO_JSON("xls"),
        MS_XLSX_TO_JSON("xlsx"), ;

        private String type;

        ParserType(String type) {
            this.type = type;
        }

        @Override
        public String toString() {
            return type;
        }
    }

    private ParserType extractFileType(File file) throws IOException {
        Tika tika = new Tika();
        String fileType = tika.detect(file);
        switch (fileType) {
            case XLS_MIME_TYPE:
                return ParserType.MS_XLS_TO_JSON;
            case XLSX_MIME_TYPE:
                return ParserType.MS_XLSX_TO_JSON;
            default:
                throw new InvalidTypeException("Type not recognized: " + fileType);
        }
    }

    public enum FormatType {
        ORIGINAL,
        JSON,
        CSV,
        TXT
    }
}
