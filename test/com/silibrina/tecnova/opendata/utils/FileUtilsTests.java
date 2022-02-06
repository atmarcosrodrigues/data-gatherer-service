package com.silibrina.tecnova.opendata.utils;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.*;

public class FileUtilsTests {
    private static final String TARGET_DIR = "/tmp/opendata-test";
    private static final String FILE_TARGET = TARGET_DIR + "/somefile";

    @Before
    public void createFiles() throws IOException {
        FileUtils.forceMkdir(new File(TARGET_DIR));
        FileUtils.writeLines(new File(FILE_TARGET), populateFileContent());
    }

    @After
    public void cleanUp() throws IOException {
        FileUtils.cleanDirectory(new File(TARGET_DIR));
        FileUtils.forceDelete(new File(TARGET_DIR));
    }

    private static List<String> populateFileContent() {
        List<String> fileContent = new LinkedList<>();
        fileContent.add("GET    /   some.teste()");
        fileContent.add("POST    /   some.teste2()");
        return fileContent;
    }

    @Test
    public void readFileTest() throws IOException {
        List<String> thisFileContent = ODFileUtils.readLines(new File(FILE_TARGET));
        assertArrayEquals(thisFileContent.toArray(), populateFileContent().toArray());
    }
    @Test
    public void currentDirectoryTest() {
        File current = new File(ODFileUtils.getCurrentDir(""));
        assertTrue(current.exists());
    }
}
