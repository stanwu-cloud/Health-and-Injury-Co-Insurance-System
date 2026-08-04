package com.insurance.coinsurance.model;

import java.nio.file.Path;
import java.util.List;

/**
 * 服務層執行結果。
 *
 * <p>DESIGN D9：服務層<b>不得</b> {@code System.exit()} 或直接印訊息，中止行為由進入點依本結果決定。
 *
 * @param status      結果狀態
 * @param message     摘要訊息（成功或中止原因）
 * @param errors      檢核錯誤（僅 {@link Status#VALIDATION_FAILED} 時非空）
 * @param outputFiles 產出報表路徑（僅 {@link Status#SUCCESS} 時非空）
 * @param calculation 計算結果（僅 {@link Status#SUCCESS} 時非 null）
 * @param report      執行報告（任何狀態皆產出）
 */
public record ExecutionResult(
        Status status,
        String message,
        List<ValidationError> errors,
        List<Path> outputFiles,
        CalculationResult calculation,
        ExecutionReport report) {

    /** 結果狀態；{@link #exitCode()} 對應 DESIGN §8.3。 */
    public enum Status {
        /** 成功產出兩張報表。 */
        SUCCESS(0),
        /** 匯入檔檢核失敗。 */
        VALIDATION_FAILED(1),
        /** 前置資源／環境錯誤。 */
        FATAL(2),
        /** 未預期錯誤。 */
        UNEXPECTED(3);

        private final int exitCode;

        Status(int exitCode) {
            this.exitCode = exitCode;
        }

        public int exitCode() {
            return exitCode;
        }
    }

    public ExecutionResult {
        errors = List.copyOf(errors);
        outputFiles = List.copyOf(outputFiles);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public int exitCode() {
        return status.exitCode();
    }

    public static ExecutionResult success(String message, List<Path> outputFiles,
                                          CalculationResult calculation, ExecutionReport report) {
        return new ExecutionResult(Status.SUCCESS, message, List.of(), outputFiles, calculation, report);
    }

    public static ExecutionResult validationFailed(List<ValidationError> errors, ExecutionReport report) {
        return new ExecutionResult(Status.VALIDATION_FAILED,
                "匯入檔檢核失敗，共 %d 筆錯誤；兩張報表皆未產出".formatted(errors.size()),
                errors, List.of(), null, report);
    }

    public static ExecutionResult fatal(String message, ExecutionReport report) {
        return new ExecutionResult(Status.FATAL, message, List.of(), List.of(), null, report);
    }

    public static ExecutionResult unexpected(String message, ExecutionReport report) {
        return new ExecutionResult(Status.UNEXPECTED, message, List.of(), List.of(), null, report);
    }
}
