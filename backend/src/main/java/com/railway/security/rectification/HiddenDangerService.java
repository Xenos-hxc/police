package com.railway.security.rectification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.railway.security.persistence.entity.CheckAttachment;
import com.railway.security.persistence.entity.CheckRecord;
import com.railway.security.persistence.entity.CheckTask;
import com.railway.security.persistence.entity.HiddenDanger;
import com.railway.security.persistence.entity.SysConfig;
import com.railway.security.persistence.entity.SysDept;
import com.railway.security.persistence.mapper.CheckAttachmentMapper;
import com.railway.security.persistence.mapper.ConfigMapper;
import com.railway.security.persistence.mapper.DeptMapper;
import com.railway.security.persistence.mapper.HiddenDangerMapper;
import com.railway.security.shared.security.DataScopeService;
import com.railway.security.shared.security.SecurityUtils;
import com.railway.security.shared.web.BusinessException;
import com.railway.security.shared.web.PageResult;
import com.railway.security.system.SystemPeriodService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HiddenDangerService {
    public static final String DEADLINE = "DEADLINE";
    public static final String ROUTINE = "ROUTINE";
    private static final Set<String> COMPLETED = Set.of("COMPLETED", "OVERDUE_COMPLETED");

    private final HiddenDangerMapper dangerMapper;
    private final CheckAttachmentMapper attachmentMapper;
    private final DeptMapper deptMapper;
    private final DataScopeService dataScopeService;
    private final ConfigMapper configMapper;
    private final SystemPeriodService systemPeriodService;

    @Transactional
    public void syncFromInspection(CheckTask task, CheckRecord record) {
        int hasDanger = Integer.valueOf(1).equals(record.getHasDanger()) ? 1 : 0;
        HiddenDanger danger =
                dangerMapper.selectOne(
                        new LambdaQueryWrapper<HiddenDanger>()
                                .eq(HiddenDanger::getTaskId, task.getId()));
        if (danger != null
                && COMPLETED.contains(danger.getStatus())
                && (!hasDangerEquals(record, danger))) {
            throw new BusinessException("该隐患已完成整改，不能再修改原检查结论");
        }
        if (hasDanger == 0) {
            record.setHasDanger(0);
            record.setRectificationType(null);
            record.setDangerDetail(null);
            record.setRectificationDeadline(null);
            if (danger != null && !COMPLETED.contains(danger.getStatus())) {
                danger.setStatus("CANCELLED");
                danger.setDangerDetail("检查结论已修改为无隐患");
                danger.setRectificationType(null);
                danger.setRectificationDeadline(null);
                dangerMapper.updateById(danger);
            }
            return;
        }

        String type = normalizeRectificationType(record.getRectificationType());
        if (!hasText(record.getDangerDetail())) throw new BusinessException("请填写隐患详情");
        requireAttachment(task.getId(), "HAZARD_MATERIAL", "请至少上传一份隐患材料");
        if (DEADLINE.equals(type)) {
            if (record.getRectificationDeadline() == null) throw new BusinessException("请选择整改期限");
            if (record.getRectificationDeadline().isBefore(LocalDate.now())) {
                throw new BusinessException("整改期限不能早于今天");
            }
            requireAttachment(task.getId(), "NOTICE", "请至少上传一份责令改正通知书");
        } else {
            record.setRectificationDeadline(null);
        }

        if (danger == null) {
            danger = new HiddenDanger();
            danger.setDangerNo(
                    "YH"
                            + task.getTaskYear()
                            + String.format("%02d", task.getQuarter())
                            + "-"
                            + task.getId());
            danger.setTaskId(task.getId());
        }
        danger.setTaskNo(task.getTaskNo());
        danger.setTaskYear(task.getTaskYear());
        danger.setQuarter(task.getQuarter());
        danger.setExecutorDeptId(task.getExecutorDeptId());
        danger.setTargetId(task.getTargetId());
        danger.setTargetType(task.getTargetType());
        danger.setTargetName(task.getTargetName());
        danger.setDangerDetail(record.getDangerDetail().trim());
        danger.setRectificationType(type);
        danger.setRectificationDeadline(record.getRectificationDeadline());
        if (danger.getStatus() == null || !COMPLETED.contains(danger.getStatus())) {
            danger.setStatus(
                    DEADLINE.equals(type)
                            ? deadlineStatus(record.getRectificationDeadline())
                            : "ROUTINE_RECORDED");
        }
        if (danger.getId() == null) dangerMapper.insert(danger);
        else dangerMapper.updateById(danger);
    }

    public PageResult<DangerView> list(DangerQuery query) {
        List<Long> deptIds = permittedExecutorDeptIds(query.executorDeptId());
        if (deptIds.isEmpty()) return new PageResult<>(0, List.of());
        String targetType = normalizeTargetType(query.targetType(), false);
        var wrapper =
                new LambdaQueryWrapper<HiddenDanger>()
                        .eq(HiddenDanger::getRectificationType, DEADLINE)
                        .in(HiddenDanger::getExecutorDeptId, deptIds)
                        .like(
                                hasText(query.targetName()),
                                HiddenDanger::getTargetName,
                                trim(query.targetName()))
                        .eq(targetType != null, HiddenDanger::getTargetType, targetType)
                        .eq(query.year() != null, HiddenDanger::getTaskYear, query.year())
                        .eq(query.quarter() != null, HiddenDanger::getQuarter, query.quarter())
                        .eq(hasText(query.status()), HiddenDanger::getStatus, trim(query.status()))
                        .ge(
                                query.deadlineStart() != null,
                                HiddenDanger::getRectificationDeadline,
                                query.deadlineStart())
                        .le(
                                query.deadlineEnd() != null,
                                HiddenDanger::getRectificationDeadline,
                                query.deadlineEnd());
        systemPeriodService.applyDangerBoundary(wrapper);
        Map<Long, String> deptNames = departmentNames(deptIds);
        List<DangerView> all =
                dangerMapper.selectList(wrapper).stream()
                        .sorted(
                                Comparator.comparingInt(
                                                (HiddenDanger item) ->
                                                        statusOrder(item.getStatus()))
                                        .thenComparing(
                                                HiddenDanger::getRectificationDeadline,
                                                Comparator.nullsLast(Comparator.naturalOrder()))
                                        .thenComparing(HiddenDanger::getId))
                        .map(item -> view(item, deptNames.get(item.getExecutorDeptId())))
                        .toList();
        int page = query.page() == null || query.page() < 1 ? 1 : query.page();
        int size = query.size() == null || query.size() < 1 ? 10 : Math.min(query.size(), 100);
        int from = Math.min((page - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        return new PageResult<>(all.size(), all.subList(from, to));
    }

    public Map<String, Object> detail(Long id) {
        HiddenDanger danger = required(id);
        assertAccess(danger);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("danger", danger);
        SysDept dept = deptMapper.selectById(danger.getExecutorDeptId());
        result.put("executorDeptName", dept == null ? "-" : dept.getDeptName());
        result.put(
                "attachments",
                attachmentMapper.selectList(
                        new LambdaQueryWrapper<CheckAttachment>()
                                .eq(CheckAttachment::getTaskId, danger.getTaskId())
                                .in(
                                        CheckAttachment::getAttachmentType,
                                        List.of(
                                                "HAZARD_MATERIAL",
                                                "NOTICE",
                                                "RECTIFICATION_VIDEO",
                                                "RECTIFICATION_ATTACHMENT"))
                                .orderByDesc(CheckAttachment::getCreateTime)));
        result.put("editable", canEdit(danger));
        return result;
    }

    public Map<String, Long> unfinishedCounts() {
        List<Long> deptIds = permittedExecutorDeptIds(null);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String targetType : accessibleTargetTypes()) {
            var wrapper =
                    new LambdaQueryWrapper<HiddenDanger>()
                            .eq(HiddenDanger::getRectificationType, DEADLINE)
                            .in(HiddenDanger::getExecutorDeptId, deptIds)
                            .eq(HiddenDanger::getTargetType, targetType)
                            .in(HiddenDanger::getStatus, List.of("PENDING", "OVERDUE"));
            systemPeriodService.applyDangerBoundary(wrapper);
            long count = deptIds.isEmpty() ? 0 : dangerMapper.selectCount(wrapper);
            counts.put(targetType, count);
        }
        return counts;
    }

    @Transactional
    public void saveRectificationDraft(Long id, RectificationRequest request) {
        HiddenDanger danger = required(id);
        assertEditable(danger);
        danger.setRectificationCheckTime(request.checkTime());
        danger.setRectificationInspectors(trim(request.inspectors()));
        danger.setRectificationRemark(trim(request.remark()));
        dangerMapper.updateById(danger);
    }

    @Transactional
    public void submitRectification(Long id, RectificationRequest request) {
        HiddenDanger danger = required(id);
        assertEditable(danger);
        if (request.checkTime() == null) throw new BusinessException("请选择检查时间");
        if (!hasText(request.inspectors())) throw new BusinessException("请填写检查人员");
        requireAttachment(danger.getTaskId(), "RECTIFICATION_VIDEO", "请至少上传一个整改检查视频");
        requireAttachment(danger.getTaskId(), "RECTIFICATION_ATTACHMENT", "请至少上传一份整改附件");
        boolean overdue =
                LocalDate.now().isAfter(danger.getRectificationDeadline())
                        || Set.of("OVERDUE", "OVERDUE_COMPLETED").contains(danger.getStatus());
        danger.setRectificationCheckTime(request.checkTime());
        danger.setRectificationInspectors(request.inspectors().trim());
        danger.setRectificationRemark(trim(request.remark()));
        danger.setRectificationSubmittedBy(SecurityUtils.currentUser().getUserId());
        danger.setRectificationSubmittedTime(LocalDateTime.now());
        danger.setStatus(overdue ? "OVERDUE_COMPLETED" : "COMPLETED");
        dangerMapper.updateById(danger);
    }

    public void assertUploadAllowed(Long taskId, String attachmentType) {
        HiddenDanger danger =
                dangerMapper.selectOne(
                        new LambdaQueryWrapper<HiddenDanger>()
                                .eq(HiddenDanger::getTaskId, taskId)
                                .eq(HiddenDanger::getRectificationType, DEADLINE));
        if (danger == null) throw new BusinessException("整改任务不存在，请先提交期限改隐患");
        if (!systemPeriodService.includes(danger.getTaskYear(), danger.getQuarter())) {
            throw new BusinessException("隐患整改任务早于系统时间边界");
        }
        assertEditable(danger);
        if (!List.of("RECTIFICATION_VIDEO", "RECTIFICATION_ATTACHMENT").contains(attachmentType)) {
            throw new BusinessException("整改材料类型无效");
        }
    }

    public void assertAttachmentDeleteAllowed(CheckAttachment attachment) {
        if (!List.of("RECTIFICATION_VIDEO", "RECTIFICATION_ATTACHMENT")
                .contains(attachment.getAttachmentType())) return;
        assertUploadAllowed(attachment.getTaskId(), attachment.getAttachmentType());
    }

    public List<String> accessibleTargetTypes() {
        Set<String> roles = SecurityUtils.currentUser().getRoles();
        if (roles.contains("STATION")) return List.of("KEY_UNIT", "IMPORTANT_PART");
        if (roles.contains("BUREAU")) {
            return List.of("STATION", "KEY_UNIT", "IMPORTANT_PART");
        }
        throw new BusinessException(403, "当前账号无权访问隐患整改");
    }

    public int defaultDeadlineDays() {
        SysConfig config =
                configMapper.selectOne(
                        new LambdaQueryWrapper<SysConfig>()
                                .eq(SysConfig::getConfigKey, "danger.default.deadline.days"));
        if (config == null) return 30;
        try {
            return Math.max(1, Math.min(3650, Integer.parseInt(config.getConfigValue())));
        } catch (NumberFormatException ignored) {
            return 30;
        }
    }

    public long countByStatus(String... statuses) {
        var wrapper =
                new LambdaQueryWrapper<HiddenDanger>()
                        .eq(HiddenDanger::getRectificationType, DEADLINE)
                        .in(HiddenDanger::getStatus, List.of(statuses));
        systemPeriodService.applyDangerBoundary(wrapper);
        return dangerMapper.selectCount(wrapper);
    }

    public void refreshOverdue() {
        var wrapper =
                new LambdaQueryWrapper<HiddenDanger>()
                        .eq(HiddenDanger::getRectificationType, DEADLINE)
                        .eq(HiddenDanger::getStatus, "PENDING")
                        .lt(HiddenDanger::getRectificationDeadline, LocalDate.now());
        systemPeriodService.applyDangerBoundary(wrapper);
        dangerMapper.selectList(wrapper).forEach(this::refreshOverdue);
    }

    private void refreshOverdue(HiddenDanger danger) {
        if (danger != null
                && "PENDING".equals(danger.getStatus())
                && danger.getRectificationDeadline() != null
                && LocalDate.now().isAfter(danger.getRectificationDeadline())) {
            danger.setStatus("OVERDUE");
            dangerMapper.updateById(danger);
        }
    }

    private void assertAccess(HiddenDanger danger) {
        if (!dataScopeService.permittedDeptIds().contains(danger.getExecutorDeptId())) {
            throw new BusinessException(403, "无权访问该隐患整改任务");
        }
    }

    private void assertEditable(HiddenDanger danger) {
        assertAccess(danger);
        if (!SecurityUtils.currentUser().getDeptId().equals(danger.getExecutorDeptId())) {
            throw new BusinessException(403, "仅隐患任务执行部门可以提交整改材料");
        }
        refreshOverdue(danger);
        if (!Set.of("PENDING", "OVERDUE", "COMPLETED", "OVERDUE_COMPLETED")
                .contains(danger.getStatus())) {
            throw new BusinessException("当前状态不能修改隐患整改资料");
        }
    }

    private boolean canEdit(HiddenDanger danger) {
        if (!Set.of("PENDING", "OVERDUE", "COMPLETED", "OVERDUE_COMPLETED")
                .contains(danger.getStatus())) return false;
        return SecurityUtils.currentUser().getDeptId().equals(danger.getExecutorDeptId());
    }

    private HiddenDanger required(Long id) {
        HiddenDanger danger = dangerMapper.selectById(id);
        if (danger == null || !DEADLINE.equals(danger.getRectificationType())) {
            throw new BusinessException("隐患整改任务不存在");
        }
        if (!systemPeriodService.includes(danger.getTaskYear(), danger.getQuarter())) {
            throw new BusinessException("隐患整改任务早于系统时间边界");
        }
        return danger;
    }

    private List<Long> permittedExecutorDeptIds(Long requestedDeptId) {
        List<Long> permitted = dataScopeService.permittedDeptIds();
        if (requestedDeptId == null) return permitted;
        if (!permitted.contains(requestedDeptId)) throw new BusinessException(403, "无权查询该部门隐患");
        return List.of(requestedDeptId);
    }

    private Map<Long, String> departmentNames(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return deptMapper.selectBatchIds(ids).stream()
                .collect(
                        java.util.stream.Collectors.toMap(
                                SysDept::getId, SysDept::getDeptName, (left, right) -> left));
    }

    private DangerView view(HiddenDanger item, String deptName) {
        return new DangerView(
                item.getId(),
                item.getDangerNo(),
                item.getTaskId(),
                item.getTaskNo(),
                item.getTaskYear(),
                item.getQuarter(),
                item.getExecutorDeptId(),
                deptName,
                item.getTargetId(),
                item.getTargetType(),
                item.getTargetName(),
                item.getDangerDetail(),
                item.getRectificationDeadline(),
                item.getStatus(),
                item.getRectificationSubmittedTime());
    }

    private void requireAttachment(Long taskId, String type, String message) {
        if (attachmentMapper.selectCount(
                        new LambdaQueryWrapper<CheckAttachment>()
                                .eq(CheckAttachment::getTaskId, taskId)
                                .eq(CheckAttachment::getAttachmentType, type)
                                .eq(CheckAttachment::getStorageStatus, "ACTIVE")
                                .in(CheckAttachment::getScanStatus, "CLEAN", "SKIPPED"))
                == 0) throw new BusinessException(message);
    }

    private boolean hasDangerEquals(CheckRecord record, HiddenDanger danger) {
        if (!Integer.valueOf(1).equals(record.getHasDanger())) return false;
        return normalizeRectificationType(record.getRectificationType())
                .equals(danger.getRectificationType());
    }

    private String normalizeRectificationType(String value) {
        String type = value == null ? "" : value.trim().toUpperCase();
        if (!Set.of(DEADLINE, ROUTINE).contains(type)) {
            throw new BusinessException("请选择期限改或例行改");
        }
        return type;
    }

    private String normalizeTargetType(String value, boolean required) {
        if (!hasText(value)) {
            if (required) throw new BusinessException("检查对象类型不能为空");
            return null;
        }
        String type = value.trim().toUpperCase();
        if (!accessibleTargetTypes().contains(type)) throw new BusinessException(403, "不能查询该对象类型");
        return type;
    }

    private String deadlineStatus(LocalDate deadline) {
        return LocalDate.now().isAfter(deadline) ? "OVERDUE" : "PENDING";
    }

    private int statusOrder(String status) {
        return switch (String.valueOf(status)) {
            case "OVERDUE" -> 0;
            case "PENDING" -> 1;
            case "OVERDUE_COMPLETED" -> 2;
            case "COMPLETED" -> 3;
            default -> 4;
        };
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record DangerQuery(
            Integer page,
            Integer size,
            String targetName,
            String targetType,
            Integer year,
            Integer quarter,
            String status,
            Long executorDeptId,
            LocalDate deadlineStart,
            LocalDate deadlineEnd) {}

    public record DangerView(
            Long id,
            String dangerNo,
            Long taskId,
            String taskNo,
            Integer taskYear,
            Integer quarter,
            Long executorDeptId,
            String executorDeptName,
            Long targetId,
            String targetType,
            String targetName,
            String dangerDetail,
            LocalDate rectificationDeadline,
            String status,
            LocalDateTime rectificationSubmittedTime) {}

    public record RectificationRequest(
            @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime checkTime,
            String inspectors,
            String remark) {}
}
