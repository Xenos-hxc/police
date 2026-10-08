package com.railway.security.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative facts only; missing information is reported rather than inferred. */
public final class MaterialFacts {
    private static final Pattern DATE =
            Pattern.compile(
                    "(?<!\\d)(20\\d{2})[年./-](0?[1-9]|1[0-2])[月./-](0?[1-9]|[12]\\d|3[01])日?(?!\\d)");

    private MaterialFacts() {}

    // 类别、日期和单位检查先用可解释规则；首个有效日期不等于业务事件日期，单位字符串匹配也不等于通用实体识别。
    public static Facts inspect(String text, String uploadedType, String expectedUnit) {
        // Chinese OCR often inserts spaces between characters and date components.
        text = text.replaceAll("(?<=[\\p{IsHan}\\d])\\h+(?=[\\p{IsHan}\\d])", "");
        String category = "UNKNOWN";
        if (text.contains("责令改正") && text.contains("通知")) category = "NOTICE";
        else if (text.contains("检查笔录") || text.contains("检查记录")) category = "RECORD";
        else if (text.contains("隐患") || text.contains("问题清单")) category = "HAZARD_MATERIAL";
        Matcher match = DATE.matcher(text);
        String date = null;
        while (match.find()) {
            try {
                java.time.LocalDate.of(
                        Integer.parseInt(match.group(1)),
                        Integer.parseInt(match.group(2)),
                        Integer.parseInt(match.group(3)));
                date = match.group();
                break;
            } catch (java.time.DateTimeException ignored) {
                // Invalid calendar dates are not accepted as documentary evidence.
            }
        }
        String unit =
                expectedUnit != null && !expectedUnit.isBlank() && text.contains(expectedUnit)
                        ? expectedUnit
                        : null;
        List<String> missing = new ArrayList<>();
        if (date == null) missing.add("材料日期");
        if (unit == null) missing.add("受检单位");
        if ("UNKNOWN".equals(category)) missing.add("材料类别无法可靠识别，请人工确认");
        else if (!category.equals(uploadedType)) missing.add("材料类别与上传选项不一致，请人工核对");
        return new Facts(category, date, unit, List.copyOf(missing));
    }

    public record Facts(String category, String date, String unit, List<String> missingItems) {}
}
