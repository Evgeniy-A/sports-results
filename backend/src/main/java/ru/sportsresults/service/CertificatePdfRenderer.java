package ru.sportsresults.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import ru.sportsresults.api.dto.CertificateTemplateLayoutDto;
import ru.sportsresults.domain.CertificateTextAlignment;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
public class CertificatePdfRenderer {
    public byte[] render(byte[] templatePdf, CertificateTemplateLayoutDto layout, CertificateContent content) {
        try (PDDocument document = Loader.loadPDF(templatePdf)) {
            if (document.getNumberOfPages() == 0) throw new InvalidRequestException("INVALID_CERTIFICATE_TEMPLATE", "PDF template has no pages");
            PDFont font;
            try (InputStream stream = new ClassPathResource("fonts/NotoSans-Regular.ttf").getInputStream()) {
                font = PDType0Font.load(document, stream);
            }
            PDPage page = document.getPage(0);
            try (PDPageContentStream output = new PDPageContentStream(
                    document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                drawFitLine(output, font, content.participantName(), layout.participantX().floatValue(),
                        layout.participantY().floatValue(), layout.participantWidth().floatValue(),
                        layout.participantFontSize().floatValue(), layout.participantMinFontSize().floatValue(),
                        layout.participantAlignment());
                drawWrapped(output, font, content.baseInformation(), layout.eventX().floatValue(),
                        layout.eventY().floatValue(), layout.eventWidth().floatValue(), Float.MAX_VALUE,
                        layout.eventFontSize().floatValue(), layout.eventMinFontSize().floatValue(),
                        layout.eventLineSpacing().floatValue(), layout.eventAlignment());
                drawWrapped(output, font, content.dynamicLines(), layout.dynamicX().floatValue(),
                        layout.dynamicY().floatValue(), layout.dynamicWidth().floatValue(),
                        layout.dynamicMaxHeight().floatValue(), layout.dynamicFontSize().floatValue(),
                        layout.dynamicMinFontSize().floatValue(), layout.dynamicLineSpacing().floatValue(),
                        layout.dynamicAlignment());
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(Math.max(templatePdf.length, 4096));
            document.save(bytes);
            return bytes.toByteArray();
        } catch (InvalidRequestException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new InvalidRequestException("CERTIFICATE_GENERATION_FAILED", "Certificate PDF could not be generated");
        }
    }

    private static void drawFitLine(PDPageContentStream output, PDFont font, String text, float x, float y,
                                    float width, float preferredSize, float minimumSize,
                                    CertificateTextAlignment alignment) throws IOException {
        float size = fitSize(font, text, width, preferredSize, minimumSize);
        List<String> lines = wrap(font, List.of(text), width, size);
        float spacing = size * 1.2f;
        for (int index = 0; index < lines.size(); index++) {
            drawLine(output, font, lines.get(index), x, y - index * spacing, width, size, alignment);
        }
    }

    private static void drawWrapped(PDPageContentStream output, PDFont font, List<String> paragraphs,
                                    float x, float y, float width, float maxHeight, float preferredSize,
                                    float minimumSize, float lineSpacing, CertificateTextAlignment alignment) throws IOException {
        float size = preferredSize;
        List<String> lines = wrap(font, paragraphs, width, size);
        float effectiveSpacing = lineSpacing;
        while (size > minimumSize && lines.size() * effectiveSpacing > maxHeight) {
            size = Math.max(minimumSize, size - 1);
            lines = wrap(font, paragraphs, width, size);
            effectiveSpacing = Math.min(lineSpacing, Math.max(size * 1.15f, maxHeight / lines.size()));
        }
        effectiveSpacing = Math.min(effectiveSpacing, maxHeight / Math.max(1, lines.size()));
        if (effectiveSpacing < size) {
            throw new InvalidRequestException("CERTIFICATE_LAYOUT_OVERFLOW", "Selected certificate fields do not fit the configured layout");
        }
        for (int index = 0; index < lines.size(); index++) {
            drawLine(output, font, lines.get(index), x, y - index * effectiveSpacing, width, size, alignment);
        }
    }

    private static List<String> wrap(PDFont font, List<String> paragraphs, float width, float size) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String paragraph : paragraphs) {
            if (paragraph == null || paragraph.isBlank()) continue;
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.strip().split("\\s+")) {
                if (textWidth(font, word, size) > width) {
                    if (!line.isEmpty()) {
                        lines.add(line.toString());
                        line = new StringBuilder();
                    }
                    List<String> chunks = splitToWidth(font, word, width, size);
                    lines.addAll(chunks.subList(0, chunks.size() - 1));
                    line = new StringBuilder(chunks.getLast());
                    continue;
                }
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && textWidth(font, candidate, size) > width) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            if (!line.isEmpty()) lines.add(line.toString());
        }
        return lines;
    }

    private static List<String> splitToWidth(PDFont font, String value, float width, float size) throws IOException {
        List<String> chunks = new ArrayList<>();
        StringBuilder chunk = new StringBuilder();
        for (int index = 0; index < value.length();) {
            int codePoint = value.codePointAt(index);
            String symbol = new String(Character.toChars(codePoint));
            if (!chunk.isEmpty() && textWidth(font, chunk + symbol, size) > width) {
                chunks.add(chunk.toString());
                chunk = new StringBuilder();
            }
            chunk.append(symbol);
            index += Character.charCount(codePoint);
        }
        if (!chunk.isEmpty()) chunks.add(chunk.toString());
        return chunks;
    }

    private static float fitSize(PDFont font, String text, float width, float preferred, float minimum) throws IOException {
        float size = preferred;
        while (size > minimum && textWidth(font, text, size) > width) size = Math.max(minimum, size - 1);
        return size;
    }

    private static float textWidth(PDFont font, String text, float size) throws IOException {
        return font.getStringWidth(text) / 1000f * size;
    }

    private static void drawLine(PDPageContentStream output, PDFont font, String text, float x, float y,
                                 float width, float size, CertificateTextAlignment alignment) throws IOException {
        float lineWidth = textWidth(font, text, size);
        float offset = switch (alignment) {
            case LEFT -> 0;
            case CENTER -> Math.max(0, (width - lineWidth) / 2);
            case RIGHT -> Math.max(0, width - lineWidth);
        };
        output.beginText();
        output.setFont(font, size);
        output.newLineAtOffset(x + offset, y);
        output.showText(text);
        output.endText();
    }

    public record CertificateContent(String participantName, List<String> baseInformation, List<String> dynamicLines) {
        public CertificateContent {
            baseInformation = List.copyOf(baseInformation);
            dynamicLines = List.copyOf(dynamicLines);
        }
    }
}
