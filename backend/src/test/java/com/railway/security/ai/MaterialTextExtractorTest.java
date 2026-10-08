package com.railway.security.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.railway.security.shared.web.BusinessException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MaterialTextExtractorTest {
    private final MaterialTextExtractor extractor = new MaterialTextExtractor();

    @Test
    void readsWordTableCells() throws Exception {
        ReflectionTestUtils.setField(extractor, "maxChars", 2000);
        try (var word = new org.apache.poi.xwpf.usermodel.XWPFDocument();
                var bytes = new java.io.ByteArrayOutputStream()) {
            word.createTable(1, 1).getRow(0).getCell(0).setText("检查笔录 2026年10月1日 示例单位");
            word.write(bytes);
            org.junit.jupiter.api.Assertions.assertTrue(
                    extractor.extract(bytes.toByteArray(), "docx").contains("示例单位"));
        }
    }

    @Test
    void refusesOversizedTextInsteadOfSilentlyTruncating() {
        ReflectionTestUtils.setField(extractor, "maxChars", 3);
        assertThrows(
                BusinessException.class,
                () -> extractor.extract("1234".getBytes(StandardCharsets.UTF_8), "txt"));
    }

    @Test
    void invalidCalendarDateIsReportedMissing() {
        var facts = MaterialFacts.inspect("检查笔录 2026年2月31日 示例单位", "RECORD", "示例单位");
        assertEquals(null, facts.date());
        org.junit.jupiter.api.Assertions.assertTrue(facts.missingItems().contains("材料日期"));
    }

    @Test
    void extractsPlainTextWithoutControlCharacters() throws Exception {
        ReflectionTestUtils.setField(extractor, "maxChars", 200);
        assertEquals(
                "2026年9月30日 示例派出所 检查笔录",
                extractor.extract(
                        "2026年9月30日\u0000 示例派出所 检查笔录".getBytes(StandardCharsets.UTF_8), "txt"));
    }

    @Test
    void refusesArchiveInsteadOfPretendingItWasParsed() {
        ReflectionTestUtils.setField(extractor, "maxChars", 200);
        assertThrows(BusinessException.class, () -> extractor.extract(new byte[] {1, 2}, "zip"));
    }
}
