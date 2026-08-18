package com.insurance.coinsurance.support;

import com.insurance.coinsurance.config.AppConfig;
import com.insurance.coinsurance.config.SettingReader;
import com.insurance.coinsurance.model.ClaimRecord;
import com.insurance.coinsurance.model.ExecutionRequest;
import com.insurance.coinsurance.model.PremiumRecord;
import com.insurance.coinsurance.model.Setting;
import com.insurance.coinsurance.reader.ClaimCsvReader;
import com.insurance.coinsurance.reader.PremiumCsvReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 測試共用之基準輸入。
 *
 * <p>資料來源為 {@code docs/規格來源/第一階段-共保月帳單/檔案位子範例/}（R-04 已同步，可直接使用）。
 * <b>驗收數值一律引用文件基準值，不得比對 {@code 產出範例/} 之金額（R-06）。</b>
 */
public final class TestFixtures {

    /** 樣本年月。 */
    public static final String YEAR_MONTH = "11505";
    public static final int YEAR = 115;
    public static final int MONTH = 5;

    // ── 文件基準值（..._LOG_現況分析與輸入輸出盤點.md §5 / MAPPING §6） ──
    public static final long TOTAL_PREMIUM = 350_123L;
    public static final long TOTAL_CLAIM = 126_931L;
    public static final long TOTAL_MANAGEMENT_FEE = 21_009L;
    public static final long BALANCE_DUE = 202_183L;
    public static final long NON_REINSURER_ALLOCATED_TOTAL = 325_611L;
    public static final long REINSURER_ALLOCATED_PREMIUM = 24_512L;
    public static final long REINSURER_ALLOCATED_CLAIM = 8_882L;
    /** 未篩簽單年度之錯誤結果——反向測試用；亦即第二階段 Σ M9。 */
    public static final long CLAIM_WITHOUT_YEAR_FILTER = 197_722L;

    // ── 第二階段基準值（..._LOG_第二階段問題追蹤清單.md §5.2 / MAPPING §10） ──
    /** M9[113]，賠款 T 字帳之 G6 / G21 / O20 / O21；亦為賠款彙總表之 C15 / C23。 */
    public static final long CLAIM_YEAR_113 = 32_460L;
    /** M9[114]，同上。 */
    public static final long CLAIM_YEAR_114 = 38_331L;

    // ── 第四張報表基準值（..._LOG_第二階段問題追蹤清單.md §5.4.2 / MAPPING §11.4） ──
    /** 樣本理賠全部集中於此公司代號，故彙總表僅第 15 列之 C 欄有值。 */
    public static final String CLAIM_ONLY_COMPANY = "N05";

    /**
     * 賠款彙總表 G 欄（應攤配賠款）之逐家期望值，索引即樣板第 7 ~ 22 列。
     *
     * <p><b>只驗 C23 / G23 / J23 三個合計不足以擋下 TASK K20</b>：G 欄若誤用逐家 M12
     * 而非年度合計，中央再保之差額法會吸收全部差異，三個合計仍然全對，只有本表會露餡。
     */
    public static final long[] CLAIM_SUMMARY_ALLOCATION_113 = {
            2_435L, 2_435L, 2_435L, 2_435L, 2_435L, 2_435L, 2_435L, 2_435L,  // 壽險 8 家各 7.5%
            1_948L,  // 富邦產險 6%
            974L,    // 和泰產險 3%
            649L,    // 泰安產險 2%
            1_948L,  // 明台產險 6%
            1_623L,  // 新光產險 5%
            1_623L,  // 華南產險 5%
            1_948L,  // 國泰產險 6%
            2_267L   // 中央再保（差額法；7% 直算會得 2,272）
    };

    /** 同上，114 年份；中央再保差額法 2,680（7% 直算會得 2,683）。 */
    public static final long[] CLAIM_SUMMARY_ALLOCATION_114 = {
            2_875L, 2_875L, 2_875L, 2_875L, 2_875L, 2_875L, 2_875L, 2_875L,
            2_300L, 1_150L, 767L, 2_300L, 1_917L, 1_917L, 2_300L, 2_680L
    };

    /** 中央再保以成分直算之<b>錯誤</b>值——反向斷言用（R-CALC-22 禁止事項）。 */
    public static final long REINSURER_DIRECT_113 = 2_272L;
    /** 同上，114 年份。 */
    public static final long REINSURER_DIRECT_114 = 2_683L;

    /** 富邦產險列之 J 欄（淨收付共保費）= D + H + I = −C + G。 */
    public static final long CLAIM_SUMMARY_NET_113 = -30_512L;
    /** 同上，114 年份。 */
    public static final long CLAIM_SUMMARY_NET_114 = -36_031L;

    private static final Path SAMPLE_ROOT = Path.of("docs", "規格來源", "第一階段-共保月帳單", "檔案位子範例");

    private TestFixtures() {
    }

    public static Path sampleRoot() {
        return SAMPLE_ROOT;
    }

    /** 指向樣本目錄之設定。 */
    public static AppConfig sampleConfig() {
        AppConfig config = new AppConfig();
        config.setSettingFile(SAMPLE_ROOT.resolve("config").resolve("application.xlsx").toString());
        config.setTemplateDir(SAMPLE_ROOT.resolve("templet").toString());
        config.setInputDir(SAMPLE_ROOT.resolve("input").toString());
        return config;
    }

    /**
     * 將樣本之 config / templet / input 複製到暫存目錄，output / backup / logs 指向該目錄。
     * 供會寫檔之整合測試使用。
     */
    public static AppConfig sandboxConfig(Path sandbox) throws IOException {
        copyDirectory(SAMPLE_ROOT.resolve("config"), sandbox.resolve("config"));
        copyDirectory(SAMPLE_ROOT.resolve("templet"), sandbox.resolve("templet"));
        copyDirectory(SAMPLE_ROOT.resolve("input"), sandbox.resolve("input"));

        AppConfig config = new AppConfig();
        config.setSettingFile(sandbox.resolve("config").resolve("application.xlsx").toString());
        config.setTemplateDir(sandbox.resolve("templet").toString());
        config.setInputDir(sandbox.resolve("input").toString());
        config.setOutputDir(sandbox.resolve("output").toString());
        config.setBackupDir(sandbox.resolve("backup").toString());
        config.setLogDir(sandbox.resolve("logs").toString());
        return config;
    }

    public static Setting sampleSetting() {
        return new SettingReader(sampleConfig()).load(ExecutionRequest.useSettingFile());
    }

    public static Setting settingOf(AppConfig config) {
        return new SettingReader(config).load(ExecutionRequest.useSettingFile());
    }

    public static List<PremiumRecord> samplePremiums() {
        return new PremiumCsvReader().read(premiumPath(sampleConfig()));
    }

    public static List<ClaimRecord> sampleClaims() {
        return new ClaimCsvReader().read(claimPath(sampleConfig()));
    }

    public static Path premiumPath(AppConfig config) {
        return config.inputDirPath(YEAR_MONTH).resolve("VOLP%s.csv".formatted(YEAR_MONTH));
    }

    public static Path claimPath(AppConfig config) {
        return config.inputDirPath(YEAR_MONTH).resolve("VOLC%s.csv".formatted(YEAR_MONTH));
    }

    public static void copyDirectory(Path source, Path target) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    public static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path child : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(child);
            }
        }
    }
}
