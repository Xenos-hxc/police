package com.railway.security.inspection;

import com.railway.security.persistence.entity.CheckTask;
import com.railway.security.shared.web.BusinessException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class TaskExcelService {
    public byte[] export(List<CheckTask> tasks) {
        try (var workbook = new XSSFWorkbook();
                var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("检查任务");
            String[] headers = {
                "任务编号", "检查类型", "检查对象", "年度", "季度", "半年度", "开始日期", "截止日期", "状态", "是否逾期", "生成方式",
                "计入覆盖率", "备注"
            };
            var header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            for (int i = 0; i < tasks.size(); i++) {
                var task = tasks.get(i);
                var row = sheet.createRow(i + 1);
                Object[] values = {
                    task.getTaskNo(),
                    task.getCheckType(),
                    task.getTargetName(),
                    task.getTaskYear(),
                    task.getQuarter(),
                    task.getHalfYear(),
                    task.getStartDate(),
                    task.getDeadline(),
                    task.getStatus(),
                    task.getOverdue(),
                    task.getCreationMode(),
                    task.getCountCoverage(),
                    task.getRemark()
                };
                for (int j = 0; j < values.length; j++) {
                    row.createCell(j)
                            .setCellValue(values[j] == null ? "" : String.valueOf(values[j]));
                }
            }
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException("任务报表生成失败");
        }
    }
}
