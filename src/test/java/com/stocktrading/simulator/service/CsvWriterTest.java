package com.stocktrading.simulator.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvWriterTest {

    @Test
    void plainValuesAreUntouched() {
        assertThat(CsvWriter.cell("hello")).isEqualTo("hello");
        assertThat(CsvWriter.cell(null)).isEmpty();
    }

    @Test
    void quotesCommasAndNewlines() {
        assertThat(CsvWriter.cell("a,b")).isEqualTo("\"a,b\"");
        assertThat(CsvWriter.cell("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(CsvWriter.cell("line1\nline2")).isEqualTo("\"line1\nline2\"");
    }

    @Test
    void neutralisesSpreadsheetFormulas() {
        for (String start : List.of("=", "+", "-", "@")) {
            assertThat(CsvWriter.cell(start + "1+1")).isEqualTo("'" + start + "1+1");
        }
    }

    @Test
    void writesBomHeaderAndCrlfRows() {
        byte[] out = CsvWriter.write(List.of("A", "B"), List.of(List.of("1", "2")));
        assertThat(new String(out, StandardCharsets.UTF_8)).isEqualTo("\uFEFFA,B\r\n1,2\r\n");
    }
}
