package com.insurance.coinsurance.service;

import com.insurance.coinsurance.calculator.AllocationCalculator;
import com.insurance.coinsurance.calculator.ClaimCalculator;
import com.insurance.coinsurance.calculator.ManagementFeeCalculator;
import com.insurance.coinsurance.calculator.PremiumCalculator;
import com.insurance.coinsurance.config.AppConfig;
import com.insurance.coinsurance.config.SettingReader;
import com.insurance.coinsurance.constant.CoInsuranceConstants;
import com.insurance.coinsurance.constant.SummaryCell;
import com.insurance.coinsurance.constant.TAccountCell;
import com.insurance.coinsurance.exception.FatalException;
import com.insurance.coinsurance.exception.ValidationFailedException;
import com.insurance.coinsurance.model.CalculationResult;
import com.insurance.coinsurance.model.ClaimRecord;
import com.insurance.coinsurance.model.CoInsuranceCompany;
import com.insurance.coinsurance.model.ExecutionReport;
import com.insurance.coinsurance.model.ExecutionRequest;
import com.insurance.coinsurance.model.ExecutionResult;
import com.insurance.coinsurance.model.PremiumRecord;
import com.insurance.coinsurance.model.Setting;
import com.insurance.coinsurance.model.ValidationError;
import com.insurance.coinsurance.reader.ClaimCsvReader;
import com.insurance.coinsurance.reader.PremiumCsvReader;
import com.insurance.coinsurance.validator.ClaimValidator;
import com.insurance.coinsurance.validator.PeriodValidator;
import com.insurance.coinsurance.validator.PremiumValidator;
import com.insurance.coinsurance.writer.BackupService;
import com.insurance.coinsurance.writer.ReportJsonWriter;
import com.insurance.coinsurance.writer.SummaryWriter;
import com.insurance.coinsurance.writer.TAccountWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 流程編排（DESIGN §5.1）——服務層之唯一對外入口。
 *
 * <p>DESIGN D9：本類別<b>不得</b> {@code System.exit()} 或直接印訊息；
 * 中止行為由進入點依 {@link ExecutionResult} 決定。
 */
@Service
public class ReportGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ReportGenerationService.class);
    private static final DateTimeFormatter EXECUTED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AppConfig appConfig;
    private final SettingReader settingReader;
    private final PremiumCsvReader premiumCsvReader;
    private final ClaimCsvReader claimCsvReader;
    private final PremiumValidator premiumValidator;
    private final ClaimValidator claimValidator;
    private final PeriodValidator periodValidator;
    private final PremiumCalculator premiumCalculator;
    private final ClaimCalculator claimCalculator;
    private final AllocationCalculator allocationCalculator;
    private final ManagementFeeCalculator managementFeeCalculator;
    private final BackupService backupService;
    private final TAccountWriter tAccountWriter;
    private final SummaryWriter summaryWriter;
    private final ReportJsonWriter reportJsonWriter;

    @SuppressWarnings("java:S107")
    public ReportGenerationService(AppConfig appConfig, SettingReader settingReader,
                                   PremiumCsvReader premiumCsvReader, ClaimCsvReader claimCsvReader,
                                   PremiumValidator premiumValidator, ClaimValidator claimValidator,
                                   PeriodValidator periodValidator, PremiumCalculator premiumCalculator,
                                   ClaimCalculator claimCalculator, AllocationCalculator allocationCalculator,
                                   ManagementFeeCalculator managementFeeCalculator, BackupService backupService,
                                   TAccountWriter tAccountWriter, SummaryWriter summaryWriter,
                                   ReportJsonWriter reportJsonWriter) {
        this.appConfig = appConfig;
        this.settingReader = settingReader;
        this.premiumCsvReader = premiumCsvReader;
        this.claimCsvReader = claimCsvReader;
        this.premiumValidator = premiumValidator;
        this.claimValidator = claimValidator;
        this.periodValidator = periodValidator;
        this.premiumCalculator = premiumCalculator;
        this.claimCalculator = claimCalculator;
        this.allocationCalculator = allocationCalculator;
        this.managementFeeCalculator = managementFeeCalculator;
        this.backupService = backupService;
        this.tAccountWriter = tAccountWriter;
        this.summaryWriter = summaryWriter;
        this.reportJsonWriter = reportJsonWriter;
    }

    /** 執行一次月帳單產出；任何結果皆會產出 {@code logs/report.json}。 */
    public ExecutionResult execute(ExecutionRequest request) {
        ExecutionReport report = new ExecutionReport();
        report.setExecutedAt(LocalDateTime.now().format(EXECUTED_AT));
        report.setParamOverride(request.overriddenByArgs());

        try {
            ExecutionResult result = run(request, report);
            reportJsonWriter.write(report);
            return result;

        } catch (ValidationFailedException e) {
            report.getValidationErrors().addAll(e.getErrors());
            reportJsonWriter.write(report);
            return ExecutionResult.validationFailed(e.getErrors(), report);

        } catch (FatalException e) {
            log.error("執行中止：{}", e.getMessage());
            report.setFatalMessage(e.getMessage());
            reportJsonWriter.write(report);
            return ExecutionResult.fatal(e.getMessage(), report);

        } catch (RuntimeException e) {
            log.error("未預期錯誤", e);
            String message = "未預期錯誤：%s".formatted(e.toString());
            report.setFatalMessage(message);
            reportJsonWriter.write(report);
            return ExecutionResult.unexpected(message, report);
        }
    }

    private ExecutionResult run(ExecutionRequest request, ExecutionReport report) {
        // [1] 設定檔
        Setting setting = settingReader.load(request);
        report.setConfigYear(setting.year());
        report.setConfigMonth(setting.month());

        // [2] 路徑
        String yearMonth = setting.rocYearMonth();
        Path inputDir = appConfig.inputDirPath(yearMonth);
        Path premiumPath = inputDir.resolve(CoInsuranceConstants.PREMIUM_FILE_PATTERN.formatted(yearMonth));
        Path claimPath = inputDir.resolve(CoInsuranceConstants.CLAIM_FILE_PATTERN.formatted(yearMonth));
        Path outputDir = appConfig.outputDirPath(yearMonth);
        log.info("開始處理 {} 年 {} 月（年月{}）", setting.year(), setting.month(),
                request.overriddenByArgs() ? "由執行參數指定" : "取自設定檔");

        // [3] 讀檔
        List<PremiumRecord> premiums = premiumCsvReader.read(premiumPath);
        List<ClaimRecord> claims = claimCsvReader.read(claimPath);
        report.getInputFiles().add(new ExecutionReport.InputFile(
                premiumPath.getFileName().toString(), premiumPath.toAbsolutePath().toString(),
                true, premiums.size()));
        report.getInputFiles().add(new ExecutionReport.InputFile(
                claimPath.getFileName().toString(), claimPath.toAbsolutePath().toString(),
                Files.exists(claimPath), claims.size()));

        // [4] 檢核：收集全部錯誤不中斷
        List<ValidationError> errors = new ArrayList<>();
        String premiumFileName = premiumPath.getFileName().toString();
        String claimFileName = claimPath.getFileName().toString();
        errors.addAll(premiumValidator.validate(premiumFileName, premiums, setting));
        errors.addAll(periodValidator.validatePremium(premiumFileName, premiums, setting));
        if (!claims.isEmpty()) {
            errors.addAll(claimValidator.validate(claimFileName, claims, setting));
            errors.addAll(periodValidator.validateClaim(claimFileName, claims, setting));
        }
        if (!errors.isEmpty()) {
            log.error("匯入檔檢核失敗，共 {} 筆錯誤", errors.size());
            errors.forEach(error -> log.error("  {}", error.format()));
            throw new ValidationFailedException(errors);
        }

        // [5] 計算
        CalculationResult calculation = calculate(setting, premiums, claims);
        log.info("計算完成：共保保費 {}，攤付共保賠款 {}，共保管理費 {}，Balance Due {}",
                calculation.totalPremium(), calculation.totalClaim(),
                calculation.totalManagementFee(), calculation.balanceDue());

        // [6] 跨報表一致性檢查
        verifyConsistency(setting, calculation);

        // [7] 備份既有輸出並清除逾期備份
        List<String> outputNames = List.of(
                TAccountCell.OUTPUT_FILE_PATTERN.formatted(yearMonth),
                SummaryCell.OUTPUT_FILE_PATTERN.formatted(yearMonth));
        backupService.backupExisting(outputDir, outputNames, yearMonth, report);
        backupService.purgeExpired(report);

        // [8] 產出兩張報表（兩表同進退）
        Path tAccount = tAccountWriter.write(setting, calculation, outputDir);
        Path summary = summaryWriter.write(setting, calculation, outputDir);
        List<Path> outputs = List.of(tAccount, summary);
        outputs.forEach(path -> report.getOutputFiles().add(new ExecutionReport.OutputFile(
                path.getFileName().toString(), path.toAbsolutePath().toString())));

        report.setSummary(new ExecutionReport.Summary(calculation.totalPremium(), calculation.totalClaim(),
                calculation.totalManagementFee(), calculation.balanceDue()));

        return ExecutionResult.success(
                "已產出 %d 年 %s 月報表 2 張".formatted(setting.year(), setting.monthOfTwoDigits()),
                outputs, calculation, report);
    }

    private CalculationResult calculate(Setting setting, List<PremiumRecord> premiums, List<ClaimRecord> claims) {
        long totalPremium = premiumCalculator.totalPremium(premiums);
        Map<String, Long> premiumByCompany = premiumCalculator.premiumByCompany(premiums);

        // 攤付共保賠款：必先以簽單年度 = 設定年篩選（只篩年、不篩月）
        List<ClaimRecord> filtered = claimCalculator.filterByUnderwritingYear(claims, setting.year());
        long totalClaim = claimCalculator.totalClaim(filtered);
        Map<String, Long> claimByCompany = claimCalculator.claimByCompany(filtered);
        log.info("理賠資料 {} 列，篩出簽單年度 {} 者 {} 列", claims.size(), setting.year(), filtered.size());

        Map<String, Long> allocatedPremium = allocationCalculator.allocate(totalPremium, setting);
        Map<String, Long> allocatedClaim = allocationCalculator.allocate(totalClaim, setting);
        Map<String, Long> managementFee = managementFeeCalculator.managementFeeByCompany(allocatedPremium, setting);
        long totalManagementFee = managementFeeCalculator.totalManagementFee(managementFee);
        long balanceDue = managementFeeCalculator.balanceDue(totalPremium, totalClaim, totalManagementFee);

        return new CalculationResult(totalPremium, premiumByCompany, totalClaim, claimByCompany,
                allocatedPremium, allocatedClaim, managementFee, totalManagementFee, balanceDue);
    }

    /**
     * R-CALC-16：T 字帳 G6 = 彙總表 C23、O6 = B23、G14 = I23。
     * 不相等視為程式缺陷，中止並記錄。
     */
    private void verifyConsistency(Setting setting, CalculationResult calculation) {
        long summaryPremiumTotal = 0L;
        long summaryClaimTotal = 0L;
        for (CoInsuranceCompany company : setting.companies()) {
            summaryPremiumTotal += calculation.premiumOf(company.code());
            summaryClaimTotal += calculation.claimOf(company.code());
        }
        long summaryFeeTotal = calculation.managementFee().values().stream()
                .mapToLong(Long::longValue).sum();

        checkEqual("共保保費（T 字帳 O6 vs 彙總表 B23）", calculation.totalPremium(), summaryPremiumTotal);
        checkEqual("攤付共保賠款（T 字帳 G6 vs 彙總表 C23）", calculation.totalClaim(), summaryClaimTotal);
        checkEqual("共保管理費（T 字帳 G14 vs 彙總表 I23）", calculation.totalManagementFee(), summaryFeeTotal);
        log.info("跨報表一致性檢查通過");
    }

    private void checkEqual(String label, long expected, long actual) {
        if (expected != actual) {
            throw new FatalException("[R-CALC-16] 跨報表一致性檢查未通過——%s：%d ≠ %d，此為程式缺陷"
                    .formatted(label, expected, actual));
        }
    }
}
