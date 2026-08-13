package com.insurance.coinsurance.entry;

import com.insurance.coinsurance.exception.FatalException;
import com.insurance.coinsurance.model.ExecutionRequest;
import com.insurance.coinsurance.model.ExecutionResult;
import com.insurance.coinsurance.model.ValidationError;
import com.insurance.coinsurance.service.ReportGenerationService;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * CLI 批次進入點（R-RUN-01~04）。
 *
 * <p>解析 {@code --year} / {@code --month}（<b>優先於設定檔</b>），觸發服務、輸出摘要與
 * 全部錯誤至主控台，並回傳 exit code（DESIGN §8.3）。
 *
 * <p>刻意<b>不</b>實作 {@code CommandLineRunner}：CLI 與 GUI 共用同一個 Spring 容器，
 * 若自動執行會導致 GUI 模式一啟動就跑批次。由 {@link FxLauncher} 明確呼叫。
 */
@Component
public class CliRunner {

    private static final String ARG_YEAR = "--year=";
    private static final String ARG_MONTH = "--month=";

    private final ReportGenerationService service;

    public CliRunner(ReportGenerationService service) {
        this.service = service;
    }

    /** @return exit code：0 成功、1 檢核失敗、2 前置錯誤、3 未預期錯誤 */
    public int run(String[] args) {
        ExecutionRequest request;
        try {
            request = parse(args);
        } catch (FatalException e) {
            System.out.println(e.getMessage());
            return ExecutionResult.Status.FATAL.exitCode();
        }

        ExecutionResult result = service.execute(request);
        print(result);
        return result.exitCode();
    }

    /** 解析命令列參數；未指定時採用設定檔之年月。 */
    static ExecutionRequest parse(String[] args) {
        Integer year = null;
        Integer month = null;
        for (String arg : args) {
            if (arg.startsWith(ARG_YEAR)) {
                year = parseInt(arg.substring(ARG_YEAR.length()), "--year");
            } else if (arg.startsWith(ARG_MONTH)) {
                month = parseInt(arg.substring(ARG_MONTH.length()), "--month");
            }
        }
        if (month != null && (month < 1 || month > 12)) {
            throw new FatalException("[R-RUN-04] 參數 --month 之值「%d」須介於 1 至 12".formatted(month));
        }
        return new ExecutionRequest(year, month);
    }

    private static int parseInt(String value, String argName) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new FatalException("[R-RUN-04] 參數 %s 之值「%s」非數值".formatted(argName, value));
        }
    }

    private void print(ExecutionResult result) {
        System.out.println();
        switch (result.status()) {
            case SUCCESS -> {
                System.out.println("執行成功：" + result.message());
                for (Path path : result.outputFiles()) {
                    System.out.println("  產出：" + path.toAbsolutePath());
                }
                // 保費檔缺檔時下方三項皆為以 0 計算之結果，須先警示再列數字（P-12）
                if (result.report().isPremiumFileMissing()) {
                    System.out.println("  ※ 警告：找不到保費匯入檔，共保保費／管理費／Balance Due 均以 0 計算，"
                            + "非本月實際金額；若非本月確實無保費，請確認匯入檔是否漏放");
                }
                var calculation = result.calculation();
                System.out.printf("  共保保費：%,d%n", calculation.totalPremium());
                System.out.printf("  攤付共保賠款：%,d%n", calculation.totalClaim());
                System.out.printf("  共保管理費：%,d%n", calculation.totalManagementFee());
                System.out.printf("  Balance Due：%,d%n", calculation.balanceDue());
            }
            case VALIDATION_FAILED -> {
                System.out.println("執行失敗：" + result.message());
                for (ValidationError error : result.errors()) {
                    System.out.println("  " + error.format());
                }
            }
            default -> System.out.println("執行失敗：" + result.message());
        }
        System.out.println("執行報告：./logs/report.json");
    }
}
