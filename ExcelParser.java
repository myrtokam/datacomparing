package org.example.democolauam;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;

@Service
public class ExcelParser {

    public List<EntitlementRecord> parseAllSheets(MultipartFile file) {
        List<EntitlementRecord> out = new ArrayList<>();

        try (InputStream in = file.getInputStream();
             Workbook wb = new XSSFWorkbook(in)) {

            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                if (sheet == null) continue;

                // Assume first row is headers
                Row header = sheet.getRow(0);
                if (header == null) continue;

                Map<String, Integer> idx = headerIndex(header);

                // Expected columns (flexible names)
                Integer cUser = pick(idx, "userid", "user id", "user", "id");
                Integer cName = pick(idx, "name", "fullname", "displayname", "display name");
                Integer cApp  = pick(idx, "application", "app", "system");
                Integer cRole = pick(idx, "role", "entitlement", "permission", "group");

                // If we don’t have at least user + name, still try
                for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;

                    String userId = cellToString(row.getCell(nvl(cUser)));
                    String name   = cellToString(row.getCell(nvl(cName)));
                    String app    = cellToString(row.getCell(nvl(cApp)));
                    String role   = cellToString(row.getCell(nvl(cRole)));

                    if (isBlank(userId) && isBlank(name) && isBlank(app) && isBlank(role)) continue;
                    if (isBlank(userId)) continue; // userId is mandatory

                    out.add(new EntitlementRecord(norm(userId), normName(name), norm(app), norm(role)));
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Excel parsing failed: " + e.getMessage(), e);
        }

        return dedupe(out);
    }

    private Map<String, Integer> headerIndex(Row header) {
        Map<String, Integer> m = new HashMap<>();
        for (Cell c : header) {
            String k = normKey(cellToString(c));
            if (!k.isBlank()) m.put(k, c.getColumnIndex());
        }
        return m;
    }

    private Integer pick(Map<String, Integer> idx, String... names) {
        for (String n : names) {
            Integer i = idx.get(normKey(n));
            if (i != null) return i;
        }
        return null;
    }

    private int nvl(Integer i) {
        return i == null ? -1 : i;
    }

    private String cellToString(Cell cell) {
        if (cell == null) return "";
        try {
            return switch (cell.getCellType()) {
                case STRING -> cell.getStringCellValue();
                case NUMERIC -> {
                    double d = cell.getNumericCellValue();
                    if (Math.abs(d - Math.rint(d)) < 0.0000001) yield Long.toString(Math.round(d));
                    yield Double.toString(d);
                }
                case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
                case FORMULA -> {
                    try { yield cell.getStringCellValue(); }
                    catch (Exception ex) { yield Double.toString(cell.getNumericCellValue()); }
                }
                case BLANK, _NONE, ERROR -> "";
            };
        } catch (Exception e) {
            return "";
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String norm(String s) {
        if (s == null) return "";
        return s.trim().replaceAll("\\s+", " ");
    }

    private String normName(String s) {
        // keep spaces, just clean
        return norm(s);
    }

    private String normKey(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    private List<EntitlementRecord> dedupe(List<EntitlementRecord> in) {
        // Remove duplicates by (userId|app|role) but keep the best name
        Map<String, EntitlementRecord> m = new LinkedHashMap<>();
        for (EntitlementRecord r : in) {
            String key = (safe(r.userId) + "|" + safe(r.app) + "|" + safe(r.role)).toLowerCase(Locale.ROOT);
            if (!m.containsKey(key)) {
                m.put(key, r);
            } else {
                EntitlementRecord existing = m.get(key);
                String name = safe(existing.name);
                String newName = safe(r.name);
                if (name.isBlank() && !newName.isBlank()) {
                    m.put(key, new EntitlementRecord(existing.userId, newName, existing.app, existing.role));
                }
            }
        }
        return new ArrayList<>(m.values());
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
