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
 * <p>資料來源為 {@code docs/規格書/檔案位子範例/}（R-04 已同步，可直接使用）。
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
    public static final long TOTAL_MANAGEMENT_FEE = 17_504L;
    public static final long BALANCE_DUE = 205_688L;
    public static final long NON_REINSURER_ALLOCATED_TOTAL = 308_105L;
    public static final long REINSURER_ALLOCATED_PREMIUM = 42_018L;
    public static final long REINSURER_ALLOCATED_CLAIM = 15_228L;
    /** 未篩簽單年度之錯誤結果——反向測試用。 */
    public static final long CLAIM_WITHOUT_YEAR_FILTER = 197_722L;

    private static final Path SAMPLE_ROOT = Path.of("docs", "規格書", "檔案位子範例");

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
