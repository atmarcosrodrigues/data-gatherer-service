package com.silibrina.tecnova.opendata.utils;

import org.apache.commons.io.FileUtils;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

/**
 * Utils class to deal with file system generals.
 */
public class ODFileUtils {

    /**
     * This method reads a file splitting lines.
     *
     * @param file The File to parse
     *
     * @return A list of string by lines from the file.
     */
    @SuppressWarnings("unchecked")
    static List<String> readLines(File file) throws IOException {
        final String charset = String.valueOf(Charset.defaultCharset());
        return FileUtils.readLines(file, charset);
    }

    /**
     * This method is a workaround to permit running junit tests inside intellij.
     * The problem is that during execution, intellij changes the working dir to
     * .idea/modules and some files stays in the root of the project, so paths
     * considering relative path would have problems trying to access those files.
     *
     * @return a path to the root of the project
     */
    public static String getCurrentDir(String file) {
        File current = new File("");

        if (current.getAbsolutePath().endsWith(".idea/modules")) {
            current = new File("../../" + file);
        } else {
            current = new File(file);
        }

        return current.getAbsolutePath();
    }
}
