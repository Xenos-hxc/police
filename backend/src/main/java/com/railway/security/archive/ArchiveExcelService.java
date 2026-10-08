package com.railway.security.archive;

import com.railway.security.persistence.entity.ImportantPart;
import com.railway.security.persistence.entity.KeyUnit;
import com.railway.security.persistence.entity.PoliceStation;
import com.railway.security.shared.web.BusinessException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ArchiveExcelService {
    private final ArchiveService archiveService;
    private final DataFormatter formatter = new DataFormatter();

    public byte[] exportStations(List<PoliceStation> rows) {
        return workbook(
                new String[] {"编号", "名称", "负责人", "联系电话", "地址", "管辖范围", "状态", "备注"},
                rows.stream()
                        .map(
                                x ->
                                        new Object[] {
                                            x.getStationCode(),
                                            x.getStationName(),
                                            x.getLeader(),
                                            x.getPhone(),
                                            x.getAddress(),
                                            x.getJurisdiction(),
                                            x.getStatus(),
                                            x.getRemark()
                                        })
                        .toList());
    }

    public byte[] exportUnits(List<KeyUnit> rows) {
        return workbook(
                new String[] {
                    "编号", "重点单位名称", "单位类型", "确定时间", "负责人", "联络员", "联系电话", "地址", "管辖公安处", "管辖派出所",
                    "派出所部门ID", "状态", "备注"
                },
                rows.stream()
                        .map(
                                x ->
                                        new Object[] {
                                            x.getUnitCode(),
                                            x.getUnitName(),
                                            x.getUnitType(),
                                            x.getEstablishedDate(),
                                            x.getLeader(),
                                            x.getContactPerson(),
                                            x.getPhone(),
                                            x.getAddress(),
                                            x.getBureauName(),
                                            x.getJurisdictionText(),
                                            join(x.getStationDeptIds()),
                                            x.getStatus(),
                                            x.getRemark()
                                        })
                        .toList());
    }

    public byte[] exportParts(List<ImportantPart> rows) {
        return workbook(
                new String[] {
                    "编号",
                    "重要部位名称",
                    "部位类型",
                    "确立时间",
                    "撤销时间",
                    "值守情况",
                    "桥隧全长",
                    "线别",
                    "公里数",
                    "所处地域",
                    "责任单位（站段）",
                    "责任单位（车间）",
                    "管辖公安处",
                    "管辖派出所",
                    "派出所部门ID",
                    "状态",
                    "备注"
                },
                rows.stream()
                        .map(
                                x ->
                                        new Object[] {
                                            x.getPartCode(),
                                            x.getPartName(),
                                            x.getPartType(),
                                            x.getEstablishedDate(),
                                            x.getRemovedDate(),
                                            x.getGuardStatus(),
                                            x.getLengthDescription(),
                                            x.getRailwayLine(),
                                            x.getKilometerMark(),
                                            x.getLocation(),
                                            x.getResponsibleUnit(),
                                            x.getWorkshop(),
                                            x.getBureauName(),
                                            x.getJurisdictionText(),
                                            join(x.getStationDeptIds()),
                                            x.getStatus(),
                                            x.getRemark()
                                        })
                        .toList());
    }

    @Transactional
    public int importFile(String type, MultipartFile file) {
        String filename =
                file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (file.isEmpty() || !filename.endsWith(".xlsx")) {
            throw new BusinessException("请选择有效的 XLSX 文件");
        }
        try (var workbook = new XSSFWorkbook(file.getInputStream())) {
            var sheet = workbook.getSheetAt(0);
            if (sheet.getLastRowNum() > 5000) throw new BusinessException("单次导入不能超过5000条");
            int count = 0;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                var row = sheet.getRow(i);
                if (row == null || text(row.getCell(0)).isBlank()) continue;
                switch (type) {
                    case "police-stations" -> {
                        var entity = new PoliceStation();
                        entity.setStationCode(text(row.getCell(0)));
                        entity.setStationName(text(row.getCell(1)));
                        entity.setLeader(text(row.getCell(2)));
                        entity.setPhone(text(row.getCell(3)));
                        entity.setAddress(text(row.getCell(4)));
                        entity.setJurisdiction(text(row.getCell(5)));
                        entity.setStatus(integer(row.getCell(6), 1));
                        entity.setRemark(text(row.getCell(7)));
                        archiveService.createStation(entity);
                    }
                    case "key-units" -> {
                        var entity = new KeyUnit();
                        entity.setUnitCode(text(row.getCell(0)));
                        entity.setUnitName(text(row.getCell(1)));
                        entity.setUnitType(text(row.getCell(2)));
                        entity.setEstablishedDate(date(row.getCell(3)));
                        entity.setLeader(text(row.getCell(4)));
                        entity.setContactPerson(text(row.getCell(5)));
                        entity.setPhone(text(row.getCell(6)));
                        entity.setAddress(text(row.getCell(7)));
                        entity.setBureauName(text(row.getCell(8)));
                        entity.setJurisdictionText(text(row.getCell(9)));
                        entity.setStationDeptIds(longList(row.getCell(10)));
                        entity.setStatus(integer(row.getCell(11), 1));
                        entity.setRemark(text(row.getCell(12)));
                        archiveService.createUnit(entity);
                    }
                    case "important-parts" -> {
                        var entity = new ImportantPart();
                        entity.setPartCode(text(row.getCell(0)));
                        entity.setPartName(text(row.getCell(1)));
                        entity.setPartType(text(row.getCell(2)));
                        entity.setEstablishedDate(date(row.getCell(3)));
                        entity.setRemovedDate(date(row.getCell(4)));
                        entity.setGuardStatus(text(row.getCell(5)));
                        entity.setLengthDescription(text(row.getCell(6)));
                        entity.setRailwayLine(text(row.getCell(7)));
                        entity.setKilometerMark(text(row.getCell(8)));
                        entity.setLocation(text(row.getCell(9)));
                        entity.setResponsibleUnit(text(row.getCell(10)));
                        entity.setWorkshop(text(row.getCell(11)));
                        entity.setBureauName(text(row.getCell(12)));
                        entity.setJurisdictionText(text(row.getCell(13)));
                        entity.setStationDeptIds(longList(row.getCell(14)));
                        entity.setStatus(integer(row.getCell(15), 1));
                        entity.setRemark(text(row.getCell(16)));
                        archiveService.createPart(entity);
                    }
                    default -> throw new BusinessException("不支持的档案类型");
                }
                count++;
            }
            return count;
        } catch (IOException | IllegalArgumentException ex) {
            throw new BusinessException("Excel 文件解析失败：" + ex.getMessage());
        }
    }

    private byte[] workbook(String[] headers, List<Object[]> data) {
        try (var workbook = new XSSFWorkbook();
                var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("档案数据");
            var header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            for (int i = 0; i < data.size(); i++) {
                var row = sheet.createRow(i + 1);
                Object[] values = data.get(i);
                for (int j = 0; j < values.length; j++) {
                    var cell = row.createCell(j);
                    Object value = values[j];
                    if (value instanceof Number number) cell.setCellValue(number.doubleValue());
                    else cell.setCellValue(value == null ? "" : String.valueOf(value));
                }
            }
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i), 12000));
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException("生成 Excel 失败");
        }
    }

    private String text(Cell cell) {
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private LocalDate date(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) return null;
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        String value = text(cell);
        if (value.isBlank()) return null;
        return LocalDate.parse(value);
    }

    private List<Long> longList(Cell cell) {
        String value = text(cell);
        if (value.isBlank()) return List.of();
        return Arrays.stream(value.split("[,，、]"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .map(item -> Long.parseLong(item.replace(".0", "")))
                .distinct()
                .toList();
    }

    private Integer integer(Cell cell, int fallback) {
        String value = text(cell);
        return value.isBlank() ? fallback : Integer.parseInt(value.replace(".0", ""));
    }

    private String join(List<Long> ids) {
        return ids == null
                ? ""
                : ids.stream()
                        .map(String::valueOf)
                        .collect(java.util.stream.Collectors.joining(","));
    }
}
