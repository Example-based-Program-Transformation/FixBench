package com.ldbc.driver.util;

import com.google.common.base.Charsets;

import java.io.*;
import java.util.Iterator;

public class CsvFileWriter {
    public static final String DEFAULT_COLUMN_SEPARATOR_STRING = "|";
    public static final String DEFAULT_COLUMN_SEPARATOR_REGEX_STRING = "\\|";

    private final BufferedWriter bufferedWriter;
    private final String columnSeparator;

    public CsvFileWriter(File file, String columnSeparator) throws IOException {
        this.bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), Charsets.UTF_8));

        this.columnSeparator = columnSeparator;
    }

    synchronized public void writeRows(Iterator<String[]> csvRows) throws IOException {
        while (csvRows.hasNext())
            writeRow(csvRows.next());
    }

    synchronized public void writeRow(String... columns) throws IOException {
        for (int i = 0; i < columns.length - 1; i++) {
            bufferedWriter.write(columns[i]);
            bufferedWriter.write(columnSeparator);
        }
        bufferedWriter.write(columns[columns.length - 1]);
        bufferedWriter.newLine();
    }

    public void close() throws IOException {
        bufferedWriter.flush();
        bufferedWriter.close();
    }
}
