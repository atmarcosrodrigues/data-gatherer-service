package com.silibrina.tecnova.opendata.utils;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.Locale;

public class FileResponse {
    private final File file;
    private final String filename;
    private boolean inline;

    public FileResponse(@Nonnull File file, @Nonnull String filename, boolean inline) {
        this.file = file;
        this.filename = filename;
        this.inline = inline;
    }

    public File getFile() {
        return file;
    }

    public String getFilename() {
        return filename;
    }

    public boolean inline() {
        return inline;
    }

    @Override
    public String toString() {
        return String.format(Locale.getDefault(),
                "%s [filename: %s, file: %s, inline: %s]",
                this.getClass().getSimpleName(), filename, file, inline);
    }
}
