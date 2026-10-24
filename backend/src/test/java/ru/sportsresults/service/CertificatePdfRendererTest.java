package ru.sportsresults.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import ru.sportsresults.api.dto.CertificateTemplateLayoutDto;
import ru.sportsresults.domain.CertificateTextAlignment;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CertificatePdfRendererTest {
    private final CertificatePdfRenderer renderer = new CertificatePdfRenderer();

    @Test
    void rendersCyrillicLongNameAndOnlySelectedDynamicLines() throws Exception {
        String longName = "Иван Петров-Сидоров Очень Длинное Имя Участника Для Проверки Масштабирования";
        byte[] generated = renderer.render(blankPdf(), layout(), new CertificatePdfRenderer.CertificateContent(
                longName, List.of("Тестовый забег", "Москва", "10 км"),
                List.of("Официальное время: 00:42:15", "Место в категории: 5 место")
        ));
        assertThat(new String(generated, 0, 5)).isEqualTo("%PDF-");
        try (PDDocument document = Loader.loadPDF(generated)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Иван Петров-Сидоров", "Тестовый забег", "Официальное время", "Место в категории");
            assertThat(text).doesNotContain("Стартовый номер");
        }
    }

    @Test
    void rendersEverySelectableFieldWithoutDroppingTheLastLine() throws Exception {
        List<String> fields = List.of(
                "Официальное время: 00:42:15",
                "Время от выстрела: 00:42:15",
                "Чистое время: 00:41:58",
                "Официальное место: 25 место",
                "Абсолютное место: 25 место",
                "Место среди мужчин / женщин: 20 место",
                "Место в категории: 5 место",
                "Категория: М35–39",
                "Стартовый номер: TEST-101"
        );
        byte[] generated = renderer.render(blankPdf(), layout(), new CertificatePdfRenderer.CertificateContent(
                "ОченьДлинноеИмяБезПробеловКотороеДолжноБезопасноПереноситьсяВнутриОбласти",
                List.of("Очень длинное название тестового мероприятия", "Москва", "10 км"), fields));

        try (PDDocument document = Loader.loadPDF(generated)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Официальное время", "Место среди мужчин / женщин", "Стартовый номер: TEST-101");
        }
    }

    private static byte[] blankPdf() throws Exception {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            ByteArrayOutputStream output = new ByteArrayOutputStream(); document.save(output); return output.toByteArray();
        }
    }

    private static CertificateTemplateLayoutDto layout() {
        BigDecimal x = new BigDecimal("40"), width = new BigDecimal("520");
        return new CertificateTemplateLayoutDto(x, new BigDecimal("700"), width, new BigDecimal("28"), new BigDecimal("10"), CertificateTextAlignment.CENTER,
                x, new BigDecimal("640"), width, new BigDecimal("14"), new BigDecimal("9"), new BigDecimal("18"), CertificateTextAlignment.CENTER,
                x, new BigDecimal("540"), width, new BigDecimal("180"), new BigDecimal("13"), new BigDecimal("8"), new BigDecimal("18"), CertificateTextAlignment.LEFT);
    }
}
