package com.railway.security.file;

import com.railway.security.shared.web.BusinessException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
// 文件安全采用大小、扩展名、内容识别、路径隔离和病毒扫描的组合；任何单项检查都不能证明文件绝对安全。
public class FileSecurityService {
    @Value("${app.upload.antivirus.enabled:false}")
    private boolean antivirusEnabled;

    @Value("${app.upload.antivirus.host:127.0.0.1}")
    private String antivirusHost;

    @Value("${app.upload.antivirus.port:3310}")
    private int antivirusPort;

    @Value("${app.upload.antivirus.timeout-millis:300000}")
    private int antivirusTimeoutMillis;

    public String sha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input =
                    new DigestInputStream(
                            new BufferedInputStream(Files.newInputStream(file)), digest)) {
                input.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ex) {
            throw new BusinessException("文件完整性校验失败");
        }
    }

    public String scan(Path file) {
        if (!antivirusEnabled) return "SKIPPED";
        try (Socket socket = new Socket()) {
            socket.connect(
                    new InetSocketAddress(antivirusHost, antivirusPort), antivirusTimeoutMillis);
            socket.setSoTimeout(antivirusTimeoutMillis);
            try (var output = new BufferedOutputStream(socket.getOutputStream());
                    var input = new BufferedInputStream(socket.getInputStream());
                    var fileInput = new BufferedInputStream(Files.newInputStream(file))) {
                output.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
                byte[] buffer = new byte[1024 * 1024];
                int read;
                while ((read = fileInput.read(buffer)) >= 0) {
                    if (read == 0) continue;
                    output.write(ByteBuffer.allocate(4).putInt(read).array());
                    output.write(buffer, 0, read);
                }
                output.write(new byte[4]);
                output.flush();
                String response = new String(input.readNBytes(4096), StandardCharsets.UTF_8).trim();
                if (response.contains(" FOUND")) throw new MalwareDetectedException();
                if (!response.endsWith("OK") && !response.contains(" OK")) {
                    throw new BusinessException("文件安全检测服务返回异常");
                }
                return "CLEAN";
            }
        } catch (BusinessException | MalwareDetectedException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("文件安全检测服务不可用，已拒绝上传");
        }
    }

    public String trustedContentType(String extension) {
        return switch (String.valueOf(extension).toLowerCase()) {
            case "mp4" -> "video/mp4";
            case "mov" -> "video/quicktime";
            case "avi" -> "video/x-msvideo";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" ->
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" ->
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "txt" -> "text/plain";
            case "zip" -> "application/zip";
            case "rar" -> "application/vnd.rar";
            case "7z" -> "application/x-7z-compressed";
            default -> "application/octet-stream";
        };
    }
}
