package com.insurance.coinsurance.writer;

import com.insurance.coinsurance.constant.CoInsuranceConstants;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Workbook;

import java.util.HashMap;
import java.util.Map;

/**
 * 會計格式套用（R-OUT-03）。
 *
 * <p>以「原樣式 → 新樣式」快取共用 {@link CellStyle} 物件，避免逐格新建而觸及
 * POI 之樣式數量上限（TASK K10）；同時以 {@code cloneStyleFrom} 保留樣板既有之
 * 框線、字型與對齊（R-OUT-04）。
 */
class ExcelStyleHelper {

    private final Workbook workbook;
    private final short accountingFormat;
    private final Map<Short, CellStyle> cache = new HashMap<>();

    ExcelStyleHelper(Workbook workbook) {
        this.workbook = workbook;
        this.accountingFormat = workbook.createDataFormat()
                .getFormat(CoInsuranceConstants.ACCOUNTING_FORMAT);
    }

    /** 將儲存格之數值格式改為會計格式，其餘樣式保持不變。 */
    void applyAccounting(Cell cell) {
        CellStyle source = cell.getCellStyle();
        if (source != null && source.getDataFormat() == accountingFormat) {
            return;
        }
        short sourceIndex = source == null ? -1 : source.getIndex();
        CellStyle styled = cache.computeIfAbsent(sourceIndex, index -> {
            CellStyle created = workbook.createCellStyle();
            if (source != null) {
                created.cloneStyleFrom(source);
            }
            created.setDataFormat(accountingFormat);
            return created;
        });
        cell.setCellStyle(styled);
    }
}
