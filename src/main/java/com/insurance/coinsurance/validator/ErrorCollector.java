package com.insurance.coinsurance.validator;

import com.insurance.coinsurance.model.ValidationError;

import java.util.ArrayList;
import java.util.List;

/**
 * 檢核錯誤蒐集器。
 *
 * <p>DESIGN D7：採「收集後判定」而非「遇錯即拋」——須一次列出全部錯誤。
 */
public class ErrorCollector {

    private final String fileName;
    private final List<ValidationError> errors = new ArrayList<>();

    public ErrorCollector(String fileName) {
        this.fileName = fileName;
    }

    /**
     * @param actualValue 個資欄位須<b>先行遮蔽</b>再傳入
     */
    public void add(int rowNumber, String fieldName, String actualValue, String ruleId, String message) {
        errors.add(new ValidationError(fileName, rowNumber, fieldName, actualValue, ruleId, message));
    }

    /** 條件成立時記錄錯誤。 */
    public void addIf(boolean failed, int rowNumber, String fieldName, String actualValue,
                      String ruleId, String message) {
        if (failed) {
            add(rowNumber, fieldName, actualValue, ruleId, message);
        }
    }

    public List<ValidationError> errors() {
        return List.copyOf(errors);
    }

    public boolean isEmpty() {
        return errors.isEmpty();
    }
}
