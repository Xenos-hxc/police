package com.railway.security.file;

import com.railway.security.file.FileApplicationService.DownloadTicketResponse;
import com.railway.security.persistence.entity.CheckAttachment;
import com.railway.security.shared.web.ApiResponse;
import com.railway.security.shared.web.BusinessException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Slf4j
public class FileController {
    private final FileApplicationService fileService;
    private final FileScanEvents scanEvents;

    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('BUREAU','STATION')")
    public ApiResponse<CheckAttachment> upload(
            @RequestParam Long taskId,
            @RequestParam(defaultValue = "OTHER") String attachmentType,
            @RequestPart MultipartFile file)
            throws IOException {
        try {
            return ApiResponse.ok(fileService.upload(taskId, attachmentType, file));
        } catch (IOException exception) {
            // OS protection and storage failures must stay fail-closed, without leaking local
            // paths.
            log.warn("附件未能安全写入存储", exception);
            throw new BusinessException(503, "文件未能安全保存，可能被系统安全软件拦截或存储暂不可用，请检查文件并联系管理员；请勿关闭安全防护");
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','BUREAU','STATION')")
    public ApiResponse<List<CheckAttachment>> list(@RequestParam Long taskId) {
        return ApiResponse.ok(fileService.list(taskId));
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','BUREAU','STATION')")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter events(
            @RequestParam Long taskId) {
        return scanEvents.subscribe(taskId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','BUREAU','STATION')")
    public ApiResponse<CheckAttachment> detail(@PathVariable Long id) {
        return ApiResponse.ok(fileService.detail(id));
    }

    @GetMapping("/{id}/preview")
    @PreAuthorize("hasAnyRole('ADMIN','BUREAU','STATION')")
    public ResponseEntity<InputStreamResource> preview(@PathVariable Long id) {
        return fileResponse(fileService.preview(id), true);
    }

    @PostMapping("/{id}/download-ticket")
    @PreAuthorize("hasAnyRole('ADMIN','BUREAU','STATION')")
    public ApiResponse<DownloadTicketResponse> downloadTicket(@PathVariable Long id) {
        return ApiResponse.ok(fileService.issueDownloadTicket(id));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(
            @PathVariable Long id, @RequestParam String ticket) {
        return fileResponse(fileService.download(id, ticket), false);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('BUREAU','STATION')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        fileService.delete(id);
        return ApiResponse.ok();
    }

    private ResponseEntity<InputStreamResource> fileResponse(
            CheckAttachment attachment, boolean preview) {
        InputStreamResource resource;
        try {
            resource = new InputStreamResource(fileService.open(attachment));
        } catch (IOException exception) {
            throw new BusinessException("文件不存在或暂时无法读取");
        }
        var disposition =
                (preview ? ContentDisposition.inline() : ContentDisposition.attachment())
                        .filename(attachment.getOriginalName(), StandardCharsets.UTF_8)
                        .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(fileService.contentType(attachment)))
                .contentLength(attachment.getFileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(
                        "Content-Security-Policy",
                        preview
                                ? "sandbox; default-src 'none'; img-src 'self' data:"
                                : "default-src 'none'")
                .body(resource);
    }
}
