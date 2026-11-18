package com.stocktrading.simulator.service;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Minimal RFC 4180 writer: UTF-8 with BOM (so Excel reads Unicode), CRLF rows, spreadsheet-formula guard. */
public final class CsvWriter {

    private CsvWriter() {}

    public static byte[] write(List<String> columns, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder("\uFEFF");
        appendRow(sb, columns);
        rows.forEach(row -> appendRow(sb, row));
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendRow(StringBuilder sb, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(cell(cells.get(i)));
        }
        sb.append("\r\n");
    }

    static String cell(String value) {
        if (value == null) return "";
        String s = value;
        // Formula injection: a leading = + - @ tab or CR makes Excel/Sheets run the cell as a formula.
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            s = '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }
}
