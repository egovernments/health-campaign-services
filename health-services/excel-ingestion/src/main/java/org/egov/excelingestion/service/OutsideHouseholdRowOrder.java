package org.egov.excelingestion.service;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps the Outside Household rows of a generated template in the order the planner gave them in an earlier file. A template
 * lists the campaign's stored sites and the area's carried sites (by site type), and an earlier file's typed rows are added
 * after them, so without this the rows come back grouped by type instead of as they were filled.
 */
final class OutsideHouseholdRowOrder {

    private static final String SITE_ID = "HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID";
    private static final String NAME = "HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_NAME";
    private static final String TYPE = "HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_TYPE";
    private static final String BOUNDARY_CODE = "HCM_ADMIN_CONSOLE_BOUNDARY_CODE";
    private static final int FIRST_DATA_ROW = 2;   // row 0 = column keys, row 1 = localized headers
    // A cell reference (A1, $A$1, A$1, $A1) not part of a longer name or a function call
    private static final Pattern CELL_REFERENCE = Pattern.compile("(?<![A-Za-z0-9_.$])(\\$?[A-Z]{1,3}\\$?)(\\d+)(?![\\d(A-Za-z_])");

    private OutsideHouseholdRowOrder() {
    }

    /**
     * A site row: its site id tells it apart, else its name with its type and boundary. A row typed in a sheet often has no
     * boundary code (the planner picks the boundary names), so the name with the type, or the name alone, also count when
     * only one reference row has them.
     */
    record Site(String id, String name, String type, String boundary) {
        boolean isRow() {
            return id != null || name != null;
        }

        String fullKey() {
            return name == null || boundary == null ? null : norm(name) + "|" + norm(type) + "|" + norm(boundary);
        }

        String typeKey() {
            return name == null ? null : norm(name) + "|" + norm(type);
        }

        /** Without the type, whose label follows the template's language. */
        String nameKey() {
            return name == null ? null : norm(name);
        }
    }

    /** The site rows of an .xlsx file's Outside Household sheet in sheet order (empty without that sheet), read without loading the workbook. */
    static List<Site> readSites(byte[] xlsx) throws Exception {
        try (OPCPackage pkg = OPCPackage.open(new ByteArrayInputStream(xlsx))) {
            XSSFReader reader = new XSSFReader(pkg);
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(pkg);
            StylesTable styles = reader.getStylesTable();
            XSSFReader.SheetIterator sheets = (XSSFReader.SheetIterator) reader.getSheetsData();
            while (sheets.hasNext()) {
                try (InputStream sheet = sheets.next()) {
                    if (sheets.getSheetName().startsWith("_h_")) {
                        continue;
                    }
                    SiteCollector collector = new SiteCollector();
                    XMLReader parser = XMLHelper.newXMLReader();
                    parser.setContentHandler(new XSSFSheetXMLHandler(styles, null, strings, collector, new DataFormatter(), false));
                    try {
                        parser.parse(new InputSource(sheet));
                    } catch (RuntimeException e) {
                        if (!stoppedEarly(e)) {
                            throw e;
                        }
                        continue;
                    }
                    if (collector.isOutsideHouseholdSheet()) {
                        return collector.sites;
                    }
                }
            }
        }
        return List.of();
    }

    /**
     * Positions of target's sites in the order of reference: a site reference has takes its place there (same site id, else
     * same name, type and boundary, else same name and type, else same name, the last two only when one reference row has
     * them); the others keep their order after them. The identity order when nothing moves.
     */
    static List<Integer> order(List<Site> reference, List<Site> target) {
        Map<String, Integer> byId = new HashMap<>();
        Map<String, Integer> byFull = new HashMap<>();
        Map<String, Integer> byType = new HashMap<>();
        Map<String, Integer> byName = new HashMap<>();
        for (int i = 0; i < reference.size(); i++) {
            Site site = reference.get(i);
            if (site.id() != null) {
                byId.putIfAbsent(site.id(), i);
            }
            if (site.fullKey() != null) {
                byFull.putIfAbsent(site.fullKey(), i);
            }
            if (site.name() != null) {
                byType.merge(site.typeKey(), i, (a, b) -> -1);   // -1: more than one row, so not used
                byName.merge(site.nameKey(), i, (a, b) -> -1);
            }
        }
        List<Integer> positions = new ArrayList<>();
        Map<Integer, Integer> rank = new HashMap<>();
        for (int p = 0; p < target.size(); p++) {
            Site site = target.get(p);
            Integer at = site.id() == null ? null : byId.get(site.id());
            if (at == null && site.fullKey() != null) {
                at = byFull.get(site.fullKey());
            }
            if (at == null && site.name() != null) {
                at = unique(byType.get(site.typeKey()));
                if (at == null) {
                    at = unique(byName.get(site.nameKey()));
                }
            }
            rank.put(p, at == null ? Integer.MAX_VALUE : at);
            positions.add(p);
        }
        positions.sort(Comparator.comparing((Integer p) -> rank.get(p)).thenComparing(p -> p));
        return positions;
    }

    private static Integer unique(Integer index) {
        return index != null && index >= 0 ? index : null;
    }

    /**
     * Puts the template's site rows in the order of reference (see {@link #order}). A row moves whole, with its values,
     * styles (locked cells) and row id, and a formula's references to its own row follow it. Returns the number of rows that
     * moved; 0 when the template has no Outside Household sheet or is already in that order.
     */
    static int orderLike(List<Site> reference, Workbook template) {
        Sheet sheet = CloneSourceDataMerger.outsideHouseholdSheet(template);
        if (sheet == null || reference.isEmpty()) {
            return 0;
        }
        Map<String, Integer> columns = CloneSourceDataMerger.columns(sheet);
        List<Integer> rows = new ArrayList<>();
        List<Site> sites = new ArrayList<>();
        for (int r = FIRST_DATA_ROW; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            Site site = new Site(CloneSourceDataMerger.valueAt(row, columns, SITE_ID), CloneSourceDataMerger.valueAt(row, columns, NAME),
                    CloneSourceDataMerger.valueAt(row, columns, TYPE), CloneSourceDataMerger.valueAt(row, columns, BOUNDARY_CODE));
            if (site.isRow()) {
                rows.add(r);
                sites.add(site);
            }
        }
        List<Integer> order = order(reference, sites);
        List<List<CellCopy>> copies = new ArrayList<>();
        List<Short> heights = new ArrayList<>();
        for (int r : rows) {
            Row row = sheet.getRow(r);
            heights.add(row.getHeight());
            List<CellCopy> cells = new ArrayList<>();
            for (Cell cell : row) {
                cells.add(CellCopy.of(cell));
            }
            copies.add(cells);
        }
        int moved = 0;
        for (int k = 0; k < rows.size(); k++) {
            int from = order.get(k);
            if (from == k) {
                continue;
            }
            moved++;
            Row row = sheet.getRow(rows.get(k));
            for (Cell cell : new ArrayList<Cell>(toList(row))) {
                row.removeCell(cell);
            }
            row.setHeight(heights.get(from));
            for (CellCopy copy : copies.get(from)) {
                copy.writeTo(row, rows.get(from), rows.get(k));
            }
        }
        return moved;
    }

    private static List<Cell> toList(Row row) {
        List<Cell> cells = new ArrayList<>();
        row.forEach(cells::add);
        return cells;
    }

    /** One cell's content and style, to write into another row. */
    private record CellCopy(int column, CellStyle style, CellType type, String text, double number, boolean bool, String formula,
                            CellType cachedType) {

        static CellCopy of(Cell cell) {
            CellType type = cell.getCellType();
            String formula = type == CellType.FORMULA ? cell.getCellFormula() : null;
            CellType valueType = type == CellType.FORMULA ? cell.getCachedFormulaResultType() : type;
            String text = valueType == CellType.STRING ? cell.getStringCellValue() : null;
            double number = valueType == CellType.NUMERIC ? cell.getNumericCellValue() : 0;
            boolean bool = valueType == CellType.BOOLEAN && cell.getBooleanCellValue();
            return new CellCopy(cell.getColumnIndex(), cell.getCellStyle(), type, text, number, bool, formula, valueType);
        }

        /** Writes this cell into row; references in a formula to the row it came from (0-based fromRow) now name toRow. */
        void writeTo(Row row, int fromRow, int toRow) {
            Cell cell = row.createCell(column);
            cell.setCellStyle(style);
            if (type == CellType.FORMULA) {
                cell.setCellFormula(withRow(formula, fromRow + 1, toRow + 1));
            }
            switch (cachedType) {
                case STRING -> cell.setCellValue(text);
                case NUMERIC -> cell.setCellValue(number);
                case BOOLEAN -> cell.setCellValue(bool);
                default -> {
                    if (type != CellType.FORMULA) {
                        cell.setBlank();
                    }
                }
            }
        }
    }

    /** The formula with its references to row `from` (1-based) pointing to row `to`; other references unchanged. */
    static String withRow(String formula, int from, int to) {
        Matcher m = CELL_REFERENCE.matcher(formula);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String replacement = Integer.parseInt(m.group(2)) == from ? m.group(1) + to : m.group();
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static boolean stoppedEarly(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof NotOutsideHousehold) {
                return true;
            }
        }
        return false;
    }

    private static String norm(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** Stops reading a sheet whose column keys show it is not the Outside Household sheet. */
    private static final class NotOutsideHousehold extends RuntimeException {
        NotOutsideHousehold() {
            super(null, null, false, false);
        }
    }

    /** Collects the site rows of a sheet streamed by XSSFSheetXMLHandler. */
    private static final class SiteCollector implements XSSFSheetXMLHandler.SheetContentsHandler {
        private final Map<Integer, String> keyByColumn = new HashMap<>();
        private final List<Site> sites = new ArrayList<>();
        private Map<String, String> current;
        private int currentRow;

        boolean isOutsideHouseholdSheet() {
            return keyByColumn.containsValue(NAME);
        }

        @Override
        public void startRow(int rowNum) {
            currentRow = rowNum;
            current = new HashMap<>();
        }

        @Override
        public void endRow(int rowNum) {
            if (rowNum == 0) {
                if (!isOutsideHouseholdSheet()) {
                    throw new NotOutsideHousehold();
                }
                return;
            }
            if (rowNum >= FIRST_DATA_ROW) {
                Site site = new Site(current.get(SITE_ID), current.get(NAME), current.get(TYPE), current.get(BOUNDARY_CODE));
                if (site.isRow()) {
                    sites.add(site);
                }
            }
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            if (cellReference == null || formattedValue == null || formattedValue.trim().isEmpty()) {
                return;
            }
            int column = new CellReference(cellReference).getCol();
            String value = formattedValue.trim();
            if (currentRow == 0) {
                keyByColumn.putIfAbsent(column, value);
            } else if (currentRow >= FIRST_DATA_ROW) {
                String key = keyByColumn.get(column);
                if (key != null) {
                    current.put(key, value);
                }
            }
        }
    }
}
