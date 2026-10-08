package com.railway.security.archive;

import com.railway.security.archive.ArchiveDtos.ImportResponse;
import com.railway.security.archive.ArchiveDtos.StatusUpdateRequest;
import com.railway.security.shared.web.ApiResponse;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class ArchiveTransferController {
    private static final MediaType XLSX =
            MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ArchiveTransferService transferService;
    private final ArchiveService archiveService;

    @PutMapping("/api/{type:police-stations|key-units|important-parts}/{id:\\d+}/status")
    public ApiResponse<Void> status(
            @PathVariable String type,
            @PathVariable Long id,
            @Valid @RequestBody StatusUpdateRequest request) {
        transferService.assertArchivePermission(type);
        archiveService.changeStatus(type, id, request.status());
        return ApiResponse.ok();
    }

    @PutMapping("/api/{type:police-stations|key-units|important-parts}/{id:\\d+}/archive")
    public ApiResponse<Void> archive(@PathVariable String type, @PathVariable Long id) {
        transferService.assertArchivePermission(type);
        archiveService.archive(type, id);
        return ApiResponse.ok();
    }

    @GetMapping("/api/{type:police-stations|key-units|important-parts}/export")
    public ResponseEntity<byte[]> export(@PathVariable String type) {
        var file = transferService.export(type);
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.filename(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(file.content());
    }

    @PostMapping("/api/{type:police-stations|key-units|important-parts}/import")
    public ApiResponse<ImportResponse> importFile(
            @PathVariable String type, @RequestPart MultipartFile file) {
        return ApiResponse.ok(transferService.importFile(type, file));
    }
}
