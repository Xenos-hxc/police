package com.railway.security.ai;

import com.railway.security.shared.web.BusinessException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MaterialTextExtractor {
    @Value("${app.ai.max-text-chars:20000}")
    private int maxChars;

    @Value("${app.ai.ocr-executable:tesseract}")
    private String ocrExecutable = "tesseract";

    // 文档解析限制字节、页数、字符数和 OCR 时间；PDF 已有文本的页面不会因此覆盖所有图片文字，应解释混合页限制。
    public String extract(byte[] bytes, String extension) throws IOException {
        String type = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        String text;
        switch (type) {
            case "txt" -> text = new String(bytes, StandardCharsets.UTF_8);
            case "pdf" -> {
                try (var pdf = Loader.loadPDF(bytes)) {
                    if (pdf.getNumberOfPages() > 100) throw new BusinessException("材料页数超过质检上限");
                    var out = new StringBuilder();
                    var stripper = new PDFTextStripper();
                    int scannedPages = 0;
                    for (int page = 0; page < pdf.getNumberOfPages(); page++) {
                        stripper.setStartPage(page + 1);
                        stripper.setEndPage(page + 1);
                        String pageText = stripper.getText(pdf);
                        if (pageText.isBlank()) {
                            if (++scannedPages > 10) throw new BusinessException("扫描页面超过 10 页质检上限");
                            pageText = ocrPdfPage(pdf, page);
                        }
                        out.append(pageText).append('\n');
                        checkLength(out.length());
                    }
                    text = out.toString();
                }
            }
            case "docx" -> {
                try (var document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
                    try (var wordExtractor =
                            new org.apache.poi.xwpf.extractor.XWPFWordExtractor(document)) {
                        text = wordExtractor.getText();
                    }
                }
            }
            case "xlsx" -> {
                try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                    var out = new StringBuilder();
                    var formatter = new org.apache.poi.ss.usermodel.DataFormatter();
                    for (var sheet : workbook) {
                        for (var row : sheet) {
                            for (var cell : row) {
                                out.append(formatter.formatCellValue(cell)).append(' ');
                                if (out.length() > maxChars) break;
                            }
                            out.append('\n');
                            if (out.length() > maxChars) break;
                        }
                        if (out.length() > maxChars) break;
                    }
                    text = out.toString();
                }
            }
            case "pptx" -> {
                try (var slides = new XMLSlideShow(new ByteArrayInputStream(bytes))) {
                    var out = new StringBuilder();
                    for (var slide : slides.getSlides()) {
                        for (XSLFShape shape : slide.getShapes()) {
                            if (shape instanceof XSLFTextShape textShape) {
                                out.append(textShape.getText()).append('\n');
                            }
                        }
                        if (out.length() > maxChars) break;
                    }
                    text = out.toString();
                }
            }
            case "jpg", "jpeg", "png" -> text = ocr(bytes, "." + type);
            default -> throw new BusinessException("该文件类型暂不支持智能质检");
        }
        text = text.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " ").replaceAll("[ \\t]{2,}", " ").trim();
        if (text.isBlank()) throw new BusinessException("未能从材料中提取文字，请人工检查");
        checkLength(text.length());
        return text;
    }

    private void checkLength(int length) {
        if (length > maxChars) throw new BusinessException("材料文字超过质检上限，请拆分后重试，未进行截断质检");
    }

    private String ocr(byte[] bytes, String suffix) throws IOException {
        Path input = Files.createTempFile("railway-ocr-", suffix);
        Path output = Files.createTempFile("railway-ocr-output-", ".txt");
        Path errors = Files.createTempFile("railway-ocr-errors-", ".txt");
        Process process = null;
        try {
            Files.write(input, bytes);
            process =
                    new ProcessBuilder(
                                    ocrExecutable, input.toString(), "stdout", "-l", "chi_sim+eng")
                            .redirectError(errors.toFile())
                            .redirectOutput(output.toFile())
                            .start();
            if (!process.waitFor(Duration.ofSeconds(45).toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new BusinessException(503, "本地 OCR 超时");
            }
            if (process.exitValue() != 0) throw new BusinessException(503, "本地 OCR 不可用或识别失败");
            if (Files.size(output) > maxChars * 8L) throw new BusinessException("OCR 文字超过质检上限");
            return Files.readString(output, StandardCharsets.UTF_8);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("OCR 被中断", exception);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                try {
                    process.waitFor(5, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
            Files.deleteIfExists(input);
            Files.deleteIfExists(output);
            Files.deleteIfExists(errors);
        }
    }

    private String ocrPdfPage(org.apache.pdfbox.pdmodel.PDDocument pdf, int page)
            throws IOException {
        var renderer = new PDFRenderer(pdf);
        var box = pdf.getPage(page).getMediaBox();
        if (box.getWidth() > 2500 || box.getHeight() > 2500) {
            throw new BusinessException("扫描版 PDF 页面尺寸超过质检上限");
        }
        var image = renderer.renderImageWithDPI(page, 120);
        var png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        return ocr(png.toByteArray(), ".png");
    }
}
