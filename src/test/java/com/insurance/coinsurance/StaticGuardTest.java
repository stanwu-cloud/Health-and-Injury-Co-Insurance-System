package com.insurance.coinsurance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 靜態掃描把關（TASK K3 / K7）。
 *
 * <p>兩項規定無法以一般單元測試驗證，改以原始碼掃描確保不被繞過：
 * <ol>
 *   <li>捨入只能經 {@code RoundingUtil}——他處直接使用 {@code RoundingMode} 或
 *       {@code setScale} 會誤用 HALF_EVEN 而產生 ±1 元帳差</li>
 *   <li>驗收基準不得取自 {@code docs/規格來源/第一階段-共保月帳單/產出範例/}——該兩份範例之管理費率仍為 5%，
 *       且不得回頭引用 12% 版設定檔之舊基準（R-06）</li>
 * </ol>
 */
class StaticGuardTest {

    private static final Path MAIN = Path.of("src", "main", "java");
    private static final Path TEST = Path.of("src", "test", "java");

    @Test
    @DisplayName("捨入僅能由 RoundingUtil 執行")
    void roundingIsCentralised() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(MAIN)) {
            if (file.getFileName().toString().equals("RoundingUtil.java")) {
                continue;
            }
            String source = Files.readString(file, StandardCharsets.UTF_8);
            if (source.contains("RoundingMode.") || source.contains(".setScale(")) {
                offenders.add(file.toString());
            }
        }
        assertTrue(offenders.isEmpty(),
                () -> "下列檔案直接使用了 RoundingMode / setScale，請改走 RoundingUtil：" + offenders);
    }

    @Test
    @DisplayName("測試程式不得以產出範例檔之過時金額作為基準")
    void testsDoNotUseStaleSampleAmounts() throws IOException {
        List<String> staleValues = List.of(
                // 產出範例檔之 5% 管理費舊值：管理費 / Balance Due / 中央再保管理費
                "17505", "205687", "1226",
                // 12% 版設定檔之舊基準：管理費 / Balance Due / 中央再保保費 / 中央再保賠款
                //                     / 15 家分攤合計 / 中央再保管理費
                "17504", "205688", "42018", "15228", "308105", "2101");
        List<String> offenders = new ArrayList<>();

        for (Path file : javaFiles(TEST)) {
            // 本檔自身即列舉這些舊值作為黑名單，須排除
            if (file.getFileName().toString().equals("StaticGuardTest.java")) {
                continue;
            }
            String source = Files.readString(file, StandardCharsets.UTF_8);
            for (String stale : staleValues) {
                if (source.contains(stale) || source.contains(withThousandsSeparator(stale))) {
                    offenders.add("%s → %s".formatted(file, stale));
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                () -> "測試引用了產出範例檔之過時金額（R-06 禁止）：" + offenders);
    }

    @Test
    @DisplayName("測試程式不得以中央再保之成分直算值作為期望值（第四張報表）")
    void testsDoNotUseDirectlyCalculatedReinsurerShare() throws IOException {
        // 中央再保只能用差額法；下列為 7% 直算之結果，與差額法差 3 ~ 5 元（R-CALC-22 禁止事項）
        List<String> directValues = List.of("2272", "2683", "8885");
        List<String> offenders = new ArrayList<>();

        for (Path file : javaFiles(TEST)) {
            String fileName = file.getFileName().toString();
            // 本檔列舉黑名單；TestFixtures 以具名常數保存反向斷言用之值
            if (fileName.equals("StaticGuardTest.java") || fileName.equals("TestFixtures.java")) {
                continue;
            }
            String source = Files.readString(file, StandardCharsets.UTF_8);
            for (String direct : directValues) {
                if (source.contains(direct) || source.contains(withThousandsSeparator(direct))) {
                    offenders.add("%s → %s".formatted(file, direct));
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                () -> "測試以中央再保之成分直算值作為期望值（只能用差額法）：" + offenders);
    }

    @Test
    @DisplayName("ClaimSummaryCell 獨立於 SummaryCell，且不得定義 F27 殘值防護")
    void claimSummaryCellIsIndependent() throws ReflectiveOperationException {
        List<String> references = new ArrayList<>();
        for (java.lang.reflect.Field field
                : com.insurance.coinsurance.constant.ClaimSummaryCell.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    && field.getType() == String.class) {
                references.add((String) field.get(null));
            }
        }

        // F27 是第一階段「範例檔」之手誤殘值防護；CLAIM_SUMMARY.xlsx 實測無此殘值
        assertTrue(references.stream().noneMatch("F27"::equals),
                "ClaimSummaryCell 不需 F27 防護");
        // 檔名與工作表名必須與第一階段不同，否則兩張報表會互相覆寫
        assertTrue(references.contains("CLAIM_SUMMARY.xlsx"));
        assertTrue(references.contains("共保賠款月帳單-彙總表"));
        assertTrue(references.stream().noneMatch(
                        reference -> reference.equals(
                                com.insurance.coinsurance.constant.SummaryCell.OUTPUT_FILE_PATTERN)),
                "輸出檔名樣式不得與第一階段彙整表相同");
    }

    private static String withThousandsSeparator(String digits) {
        return new StringBuilder(digits).insert(digits.length() - 3, '_').toString();
    }

    private static List<Path> javaFiles(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }
}
