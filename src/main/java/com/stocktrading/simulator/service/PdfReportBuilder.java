package com.stocktrading.simulator.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a simple A4 table report: title, subtitle, header row repeated on every page, "Page x of y".
 * ponytail: uses the built-in Helvetica font (WinAnsi/Latin-1 only); upgrade to embedded TTF if non-Latin PDF text is needed.
 */
public final class PdfReportBuilder {

    private static final float MARGIN = 40f;
    private static final float ROW_HEIGHT = 16f;
    private static final float FONT_SIZE = 9f;

    private final PDDocument doc = new PDDocument();
    private final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final List<PDPage> pages = new ArrayList<>();
    private final List<String> columns;
    private PDPageContentStream content;
    private float y;

    private PdfReportBuilder(List<String> columns) {
        this.columns = columns;
    }

    public static byte[] build(String title, String subtitle, List<String> columns, List<List<String>> rows) throws IOException {
        PdfReportBuilder b = new PdfReportBuilder(columns);
        try {
            return b.render(title, subtitle, rows);
        } finally {
            b.doc.close();
        }
    }

    private byte[] render(String title, String subtitle, List<List<String>> rows) throws IOException {
        newPage();
        text(bold, 16, MARGIN, y - 16, title);
        y -= 24;
        text(regular, FONT_SIZE, MARGIN, y - 10, subtitle);
        y -= 26;
        drawRow(columns, bold);
        if (rows.isEmpty()) {
            text(regular, FONT_SIZE, MARGIN, y - 12, "No records to show.");
        }
        for (List<String> row : rows) {
            drawRow(row, regular);
        }
        content.close();

        for (int i = 0; i < pages.size(); i++) {
            try (PDPageContentStream footer = new PDPageContentStream(
                    doc, pages.get(i), PDPageContentStream.AppendMode.APPEND, true, true)) {
                String label = "Page " + (i + 1) + " of " + pages.size();
                float w = regular.getStringWidth(label) / 1000 * 8;
                footer.beginText();
                footer.setFont(regular, 8);
                footer.newLineAtOffset(PDRectangle.A4.getWidth() - MARGIN - w, 24);
                footer.showText(label);
                footer.endText();
                footer.beginText();
                footer.setFont(regular, 8);
                footer.newLineAtOffset(MARGIN, 24);
                footer.showText("StockSim - simulated trading, virtual money only");
                footer.endText();
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.save(out);
        return out.toByteArray();
    }

    private void newPage() throws IOException {
        if (content != null) content.close();
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        pages.add(page);
        content = new PDPageContentStream(doc, page);
        y = PDRectangle.A4.getHeight() - MARGIN;
    }

    private void drawRow(List<String> cells, PDFont font) throws IOException {
        if (y - ROW_HEIGHT < MARGIN + 20) {
            newPage();
            drawRow(columns, bold); // repeat header on the new page
        }
        float colWidth = (PDRectangle.A4.getWidth() - 2 * MARGIN) / columns.size();
        for (int i = 0; i < columns.size(); i++) {
            String value = i < cells.size() ? cells.get(i) : "";
            text(font, FONT_SIZE, MARGIN + i * colWidth, y - 11, fit(value, font, colWidth - 6));
        }
        y -= ROW_HEIGHT;
        if (font == bold) {
            content.moveTo(MARGIN, y + 2);
            content.lineTo(PDRectangle.A4.getWidth() - MARGIN, y + 2);
            content.stroke();
        }
    }

    private void text(PDFont font, float size, float x, float baseline, String s) throws IOException {
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, baseline);
        content.showText(sanitize(s, font));
        content.endText();
    }

    /** Truncates with "..." so long values never run into the next column. */
    private String fit(String value, PDFont font, float maxWidth) throws IOException {
        String s = sanitize(value, font);
        if (font.getStringWidth(s) / 1000 * FONT_SIZE <= maxWidth) return s;
        while (!s.isEmpty() && font.getStringWidth(s + "...") / 1000 * FONT_SIZE > maxWidth) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "...";
    }

    /** Replaces control characters and anything the font cannot encode. */
    private static String sanitize(String value, PDFont font) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder();
        value.codePoints().forEach(cp -> {
            if (cp < 32 || cp == 127) {
                sb.append(' ');
                return;
            }
            String ch = new String(Character.toChars(cp));
            try {
                font.encode(ch);
                sb.append(ch);
            } catch (IOException | IllegalArgumentException e) {
                sb.append('?');
            }
        });
        return sb.toString();
    }
}
