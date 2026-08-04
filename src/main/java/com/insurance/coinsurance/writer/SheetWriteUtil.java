package com.insurance.coinsurance.writer;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellReference;

/** 以 A1 位址存取儲存格之共用工具。 */
final class SheetWriteUtil {

    private SheetWriteUtil() {
    }

    /** 取得儲存格；不存在則建立（保留樣板既有樣式）。 */
    static Cell cellAt(Sheet sheet, String reference) {
        CellReference ref = new CellReference(reference);
        return cellAt(sheet, ref.getRow(), ref.getCol());
    }

    /** 取得儲存格；不存在則建立。列與欄皆為 0-based。 */
    static Cell cellAt(Sheet sheet, int rowIndex, int columnIndex) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            row = sheet.createRow(rowIndex);
        }
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            cell = row.createCell(columnIndex);
        }
        return cell;
    }

    /** 讀取儲存格之字串內容；空白回傳空字串。 */
    static String stringAt(Sheet sheet, int rowIndex, int columnIndex) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            return "";
        }
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default -> "";
        };
    }
}
