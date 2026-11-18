package com.stocktrading.simulator.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PdfReportBuilderTest {

    @Test
    void paginatesRepeatsHeaderAndNumbersPages() throws Exception {
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            rows.add(List.of("row-" + i, "value " + i));
        }

        byte[] pdf = PdfReportBuilder.build("Big report", "sub", List.of("Key", "Value"), rows);

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            int pages = doc.getNumberOfPages();
            assertThat(pages).isGreaterThan(1);
            String text = new PDFTextStripper().getText(doc);
            assertThat(text).contains("Page 1 of " + pages, "Page " + pages + " of " + pages, "row-119");
        }
    }

    @Test
    void unsupportedCharactersAndLongValuesDoNotBreakTheDocument() throws Exception {
        String longValue = "x".repeat(500);

        byte[] pdf = PdfReportBuilder.build("T", "s", List.of("A", "B"),
            List.of(List.of("₹ 名字 line\nbreak", longValue)));

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(new PDFTextStripper().getText(doc)).contains("...");
        }
    }
}
