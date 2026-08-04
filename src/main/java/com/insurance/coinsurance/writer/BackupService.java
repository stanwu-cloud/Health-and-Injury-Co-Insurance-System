package com.insurance.coinsurance.writer;

import com.insurance.coinsurance.config.AppConfig;
import com.insurance.coinsurance.constant.CoInsuranceConstants;
import com.insurance.coinsurance.exception.FatalException;
import com.insurance.coinsurance.model.ExecutionReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 輸出檔備份與逾期清除（R-PATH-06）。
 *
 * <p>產出前先將既有同名檔移至 {@code ./backup/{YYYMM}/{yyyyMMddHHmmss}/}；
 * <b>備份失敗即中止且不覆寫原檔</b>（R-EXC-05）。
 */
@Component
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern(CoInsuranceConstants.BACKUP_TIMESTAMP_PATTERN);

    private final AppConfig appConfig;

    public BackupService(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    /**
     * 備份指定輸出目錄下之既有同名檔。
     *
     * @param outputDir     本次輸出目錄
     * @param outputNames   本次將產出之檔名
     * @param rocYearMonth  民國年月（備份目錄之第一層）
     * @param report        執行報告，備份結果寫入 {@code backupFiles}
     * @throws FatalException 備份失敗（含檔案被 Excel 鎖定）
     */
    public void backupExisting(Path outputDir, List<String> outputNames,
                               String rocYearMonth, ExecutionReport report) {
        List<Path> existing = outputNames.stream()
                .map(outputDir::resolve)
                .filter(Files::exists)
                .toList();
        if (existing.isEmpty()) {
            return;
        }

        Path targetDir = appConfig.backupDirPath(rocYearMonth)
                .resolve(LocalDateTime.now().format(TIMESTAMP));
        try {
            Files.createDirectories(targetDir);
        } catch (IOException e) {
            throw new FatalException("[R-EXC-05] 無法建立備份目錄：%s（%s）"
                    .formatted(targetDir.toAbsolutePath(), e.getMessage()), e);
        }

        for (Path source : existing) {
            Path target = targetDir.resolve(source.getFileName().toString());
            try {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new FatalException(
                        "[R-EXC-05] 備份失敗，已中止且未覆寫原檔：%s → %s（%s）。若檔案正由 Excel 開啟，請先關閉後重試"
                                .formatted(source.toAbsolutePath(), target.toAbsolutePath(), e.getMessage()), e);
            }
            report.getBackupFiles().add(new ExecutionReport.BackupFile(
                    source.toAbsolutePath().toString(), target.toAbsolutePath().toString()));
            log.info("既有輸出檔已備份：{} → {}", source, target);
        }
    }

    /** 清除超過保留月數之備份子目錄。 */
    public void purgeExpired(ExecutionReport report) {
        Path backupRoot = Path.of(appConfig.getBackupDir());
        if (!Files.isDirectory(backupRoot)) {
            return;
        }
        LocalDateTime threshold = LocalDateTime.now().minusMonths(appConfig.getBackupRetentionMonths());
        List<Path> expired = new ArrayList<>();

        try (Stream<Path> monthDirs = Files.list(backupRoot)) {
            for (Path monthDir : monthDirs.filter(Files::isDirectory).toList()) {
                try (Stream<Path> stampDirs = Files.list(monthDir)) {
                    stampDirs.filter(Files::isDirectory)
                            .filter(dir -> isExpired(dir, threshold))
                            .forEach(expired::add);
                }
            }
        } catch (IOException e) {
            log.warn("備份目錄掃描失敗，略過逾期清除：{}（{}）", backupRoot.toAbsolutePath(), e.getMessage());
            return;
        }

        for (Path dir : expired) {
            if (deleteRecursively(dir)) {
                report.getPurgedBackups().add(dir.toAbsolutePath().toString());
                log.info("已清除逾期備份目錄：{}", dir);
            }
        }
    }

    private boolean isExpired(Path stampDir, LocalDateTime threshold) {
        try {
            return LocalDateTime.parse(stampDir.getFileName().toString(), TIMESTAMP).isBefore(threshold);
        } catch (DateTimeParseException e) {
            // 目錄名非時間戳，不屬本程式產生之備份，保留不動
            return false;
        }
    }

    private boolean deleteRecursively(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
            return true;
        } catch (IOException e) {
            log.warn("逾期備份目錄清除失敗：{}（{}）", dir.toAbsolutePath(), e.getMessage());
            return false;
        }
    }
}
