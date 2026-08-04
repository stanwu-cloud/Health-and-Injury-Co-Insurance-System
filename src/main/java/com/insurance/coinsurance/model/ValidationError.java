package com.insurance.coinsurance.model;

/**
 * 檢核錯誤（DESIGN §6.1／MAPPING §9 之 F5，六要素）。
 *
 * @param fileName    檔名
 * @param rowNumber   CSV 實體行號（1-based，表頭為第 1 行）
 * @param fieldName   欄位名稱
 * @param actualValue 實際值；<b>個資須先經 MaskUtil 遮蔽</b>
 * @param ruleId      規則代碼
 * @param message     錯誤說明
 */
public record ValidationError(
        String fileName,
        int rowNumber,
        String fieldName,
        String actualValue,
        String ruleId,
        String message) {

    /**
     * 依 DESIGN §8.2 之格式輸出：
     * {@code [規則代碼] 檔名 第 {行號} 列，欄位「{欄位名稱}」值「{實際值}」不符規則：{說明}}
     */
    public String format() {
        return "[%s] %s 第 %d 列，欄位「%s」值「%s」不符規則：%s"
                .formatted(ruleId, fileName, rowNumber, fieldName, actualValue, message);
    }
}
