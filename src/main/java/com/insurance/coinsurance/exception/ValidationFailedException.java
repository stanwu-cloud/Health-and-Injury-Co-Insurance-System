package com.insurance.coinsurance.exception;

import com.insurance.coinsurance.model.ValidationError;

import java.util.List;

/**
 * 匯入檔資料檢核失敗，攜帶<b>全部</b>錯誤明細（不於第一筆中止）。
 *
 * <p>進入點對應行為：CLI 印全部錯誤並以 exit code 1 結束；GUI 於表格列出全部錯誤。
 * 兩表皆不產出，但仍須產出 {@code logs/report.json}。
 */
public class ValidationFailedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<ValidationError> errors;

    public ValidationFailedException(List<ValidationError> errors) {
        super("匯入檔檢核失敗，共 %d 筆錯誤".formatted(errors.size()));
        this.errors = List.copyOf(errors);
    }

    public List<ValidationError> getErrors() {
        return errors;
    }
}
