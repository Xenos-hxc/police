package com.railway.security.archive;

import com.railway.security.persistence.entity.CheckAttachment;
import com.railway.security.persistence.entity.CheckRecord;
import com.railway.security.persistence.entity.CheckTask;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public final class ArchiveDtos {
    private ArchiveDtos() {}

    public record SearchCriteria(
            @Min(1) long page,
            @Min(1) @Max(100) long size,
            String name,
            String typeOrArea,
            String leader,
            String phone,
            Integer status,
            Long stationDeptId,
            Integer archived,
            LocalDate startDate,
            LocalDate endDate) {}

    public record HistoryCriteria(
            Integer year,
            @Min(1) @Max(4) Integer quarter,
            LocalDate startDate,
            LocalDate endDate) {}

    public record HistoryDetailResponse(
            Object archive,
            HistoryProgress progress,
            List<HistoryItem> items,
            boolean businessHistoryVisible) {}

    public record HistoryProgress(
            int year,
            int quarter,
            long total,
            long completed,
            long unfinished,
            long submitted,
            long overdue,
            long overdueCompleted,
            double rate) {}

    public record HistoryItem(
            CheckTask task,
            String executorDeptName,
            String executorDeptType,
            CheckRecord record,
            List<CheckAttachment> attachments) {}

    public record StatusUpdateRequest(@NotNull @Min(0) @Max(1) Integer status) {}

    public record ImportResponse(int imported) {}

    public record ExportFile(String filename, byte[] content) {}
}
