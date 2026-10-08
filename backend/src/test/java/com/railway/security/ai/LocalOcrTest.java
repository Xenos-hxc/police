package com.railway.security.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;

/** Opt-in real OCR tests; no model, external network, or business data involved. */
@EnabledIfEnvironmentVariable(named = "AI_OCR_EXECUTABLE", matches = ".+")
class LocalOcrTest {
    private MaterialTextExtractor extractor() {
        var extractor = new MaterialTextExtractor();
        ReflectionTestUtils.setField(extractor, "maxChars", 20000);
        ReflectionTestUtils.setField(
                extractor, "ocrExecutable", System.getenv("AI_OCR_EXECUTABLE"));
        return extractor;
    }

    private BufferedImage scan() {
        var image = new BufferedImage(1400, 400, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 1400, 400);
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font("SimHei", Font.PLAIN, 52));
        graphics.drawString("检查笔录", 50, 85);
        graphics.drawString("2026年10月1日", 50, 170);
        graphics.drawString("受检单位：合成测试单位", 50, 255);
        graphics.dispose();
        return image;
    }

    @Test
    void recognizesChineseScan() throws Exception {
        var buffer = new ByteArrayOutputStream();
        ImageIO.write(scan(), "png", buffer);
        String text = extractor().extract(buffer.toByteArray(), "png");
        var facts = MaterialFacts.inspect(text, "RECORD", "合成测试单位");
        assertEquals("2026年10月1日", facts.date());
        assertEquals("合成测试单位", facts.unit());
        assertEquals("RECORD", facts.category());
    }

    @Test
    void readsTextPageAndScannedPageInSamePdf() throws Exception {
        try (var pdf = new PDDocument();
                var buffer = new ByteArrayOutputStream()) {
            var textPage = new PDPage();
            pdf.addPage(textPage);
            try (var stream = new PDPageContentStream(pdf, textPage)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 16);
                stream.newLineAtOffset(40, 700);
                stream.showText("Inspection first page");
                stream.endText();
            }
            var scannedPage = new PDPage();
            pdf.addPage(scannedPage);
            try (var stream = new PDPageContentStream(pdf, scannedPage)) {
                stream.drawImage(LosslessFactory.createFromImage(pdf, scan()), 20, 350, 560, 160);
            }
            pdf.save(buffer);
            String text = extractor().extract(buffer.toByteArray(), "pdf");
            assertTrue(text.contains("Inspection first page"));
            assertEquals("合成测试单位", MaterialFacts.inspect(text, "RECORD", "合成测试单位").unit());
        }
    }
}
