package com.railway.security.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.railway.security.persistence.entity.CheckTask;
import com.railway.security.persistence.entity.HiddenDanger;
import com.railway.security.persistence.entity.SysConfig;
import com.railway.security.persistence.entity.TaskPeriodSnapshot;
import com.railway.security.persistence.mapper.ConfigMapper;
import com.railway.security.shared.web.BusinessException;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SystemPeriodService {
    public static final String CONFIG_KEY = "system.period.start";
    public static final Period DEFAULT_START = new Period(2026, 3);
    private static final Pattern PERIOD_PATTERN = Pattern.compile("^(\\d{4})-Q([1-4])$");

    private final ConfigMapper configMapper;
    private volatile Period cachedStart;

    public Period startPeriod() {
        Period cached = cachedStart;
        if (cached != null) return cached;
        synchronized (this) {
            if (cachedStart != null) return cachedStart;
            SysConfig config =
                    configMapper.selectOne(
                            new LambdaQueryWrapper<SysConfig>()
                                    .eq(SysConfig::getConfigKey, CONFIG_KEY));
            cachedStart = parseOrDefault(config == null ? null : config.getConfigValue());
            return cachedStart;
        }
    }

    public Period currentPeriod() {
        LocalDate today = LocalDate.now();
        return new Period(today.getYear(), (today.getMonthValue() - 1) / 3 + 1);
    }

    public Period effectivePeriod() {
        Period current = currentPeriod();
        Period start = startPeriod();
        return current.compareTo(start) >= 0 ? current : start;
    }

    public PeriodInfo info() {
        Period start = startPeriod();
        Period current = currentPeriod();
        Period effective = effectivePeriod();
        return new PeriodInfo(
                start.year(),
                start.quarter(),
                current.year(),
                current.quarter(),
                effective.year(),
                effective.quarter());
    }

    public boolean includes(Integer year, Integer quarter) {
        if (year == null || quarter == null || quarter < 1 || quarter > 4) return false;
        return new Period(year, quarter).compareTo(startPeriod()) >= 0;
    }

    public void assertIncluded(int year, int quarter) {
        if (!includes(year, quarter)) {
            throw new BusinessException("所选季度早于系统时间边界");
        }
    }

    public List<Integer> includedQuarters(int year, List<Integer> quarters) {
        return quarters.stream().filter(quarter -> includes(year, quarter)).toList();
    }

    public void applyTaskBoundary(LambdaQueryWrapper<CheckTask> wrapper) {
        Period start = startPeriod();
        wrapper.and(
                period ->
                        period.gt(CheckTask::getTaskYear, start.year())
                                .or(
                                        sameYear ->
                                                sameYear.eq(CheckTask::getTaskYear, start.year())
                                                        .ge(
                                                                CheckTask::getQuarter,
                                                                start.quarter())));
    }

    public void applySnapshotBoundary(LambdaQueryWrapper<TaskPeriodSnapshot> wrapper) {
        Period start = startPeriod();
        wrapper.and(
                period ->
                        period.gt(TaskPeriodSnapshot::getTaskYear, start.year())
                                .or(
                                        sameYear ->
                                                sameYear.eq(
                                                                TaskPeriodSnapshot::getTaskYear,
                                                                start.year())
                                                        .ge(
                                                                TaskPeriodSnapshot::getQuarter,
                                                                start.quarter())));
    }

    public void applyDangerBoundary(LambdaQueryWrapper<HiddenDanger> wrapper) {
        Period start = startPeriod();
        wrapper.and(
                period ->
                        period.gt(HiddenDanger::getTaskYear, start.year())
                                .or(
                                        sameYear ->
                                                sameYear.eq(HiddenDanger::getTaskYear, start.year())
                                                        .ge(
                                                                HiddenDanger::getQuarter,
                                                                start.quarter())));
    }

    public void validateConfigValue(String value) {
        Period period = parse(value);
        if (period.compareTo(DEFAULT_START) < 0) {
            throw new BusinessException("系统时间边界不能早于2026年第三季度");
        }
        int maximumYear = LocalDate.now().getYear() + 2;
        if (period.year() > maximumYear) {
            throw new BusinessException("系统时间边界不能晚于" + maximumYear + "年第四季度");
        }
    }

    public void invalidate() {
        cachedStart = null;
    }

    private Period parseOrDefault(String value) {
        try {
            return parse(value);
        } catch (BusinessException ignored) {
            return DEFAULT_START;
        }
    }

    private Period parse(String value) {
        Matcher matcher = PERIOD_PATTERN.matcher(value == null ? "" : value.trim().toUpperCase());
        if (!matcher.matches()) throw new BusinessException("系统时间边界格式无效");
        return new Period(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
    }

    public record Period(int year, int quarter) implements Comparable<Period> {
        @Override
        public int compareTo(Period other) {
            int yearCompare = Integer.compare(year, other.year);
            return yearCompare != 0 ? yearCompare : Integer.compare(quarter, other.quarter);
        }
    }

    public record PeriodInfo(
            int startYear,
            int startQuarter,
            int currentYear,
            int currentQuarter,
            int effectiveYear,
            int effectiveQuarter) {}
}
