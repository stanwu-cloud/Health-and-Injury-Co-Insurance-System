package com.insurance.coinsurance.service;

import com.insurance.coinsurance.calculator.AllocationCalculator;
import com.insurance.coinsurance.calculator.ClaimCalculator;
import com.insurance.coinsurance.calculator.ManagementFeeCalculator;
import com.insurance.coinsurance.calculator.PremiumCalculator;
import com.insurance.coinsurance.config.AppConfig;
import com.insurance.coinsurance.config.SettingReader;
import com.insurance.coinsurance.constant.ClaimSummaryCell;
import com.insurance.coinsurance.constant.ClaimTAccountCell;
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
import com.insurance.coinsurance.writer.ClaimSummaryWriter;
import com.insurance.coinsurance.writer.ClaimTAccountWriter;
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
import java.util.stream.Collectors;

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
    private final ClaimTAccountWriter claimTAccountWriter;
    private final ClaimSummaryWriter claimSummaryWriter;
    private final ReportJsonWriter reportJsonWriter;

    @SuppressWarnings("java:S107")
    public ReportGenerationService(AppConfig appConfig, SettingReader settingReader,
                                   PremiumCsvReader premiumCsvReader, ClaimCsvReader claimCsvReader,
                                   PremiumValidator premiumValidator, ClaimValidator claimValidator,
                                   PeriodValidator periodValidator, PremiumCalculator premiumCalculator,
                                   ClaimCalculator claimCalculator, AllocationCalculator allocationCalculator,
                                   ManagementFeeCalculator managementFeeCalculator, BackupService backupService,
                                   TAccountWriter tAccountWriter, SummaryWriter summaryWriter,
                                   ClaimTAccountWriter claimTAccountWriter,
                                   ClaimSummaryWriter claimSummaryWriter,
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
        this.claimTAccountWriter = claimTAccountWriter;
        this.claimSummaryWriter = claimSummaryWriter;
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
        //     保費檔缺檔不再無條件中止（P-12 修訂 R-EXC-03）：賠款 T 字帳只讀理賠檔，
        //     其產出不依賴保費檔，沒有理由被前兩張報表的缺料一起拖垮。
        boolean premiumFileExists = Files.exists(premiumPath);
        List<PremiumRecord> premiums = premiumFileExists ? premiumCsvReader.read(premiumPath) : List.of();
        if (!premiumFileExists) {
            log.warn("保費匯入檔不存在，本次不產出共保月帳單 T 字帳與彙整表：{}", premiumPath.toAbsolutePath());
        }
        List<ClaimRecord> claims = claimCsvReader.read(claimPath);
        report.setPremiumFileMissing(!premiumFileExists);
        report.getInputFiles().add(new ExecutionReport.InputFile(
                premiumPath.getFileName().toString(), premiumPath.toAbsolutePath().toString(),
                premiumFileExists, premiums.size()));
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
        CalculationResult calculation = calculate(setting, premiums, claims, premiumFileExists);
        log.info("計算完成：共保保費 {}，攤付共保賠款 {}，共保管理費 {}，Balance Due {}",
                calculation.totalPremium(), calculation.totalClaim(),
                calculation.totalManagementFee(), calculation.balanceDue());

        // [6] 跨報表一致性檢查
        verifyConsistency(setting, calculation, claims, premiumFileExists);

        // [6.5] 保費檔缺檔且無可產出之賠款月帳單 → 三張全部落空，維持 B27 之中止防呆（R-EXC-03）
        //       保費檔缺檔時 M10 不排除設定年（P-13），故此處僅在理賠檔完全無資料時才成立
        //       須在備份之前判斷：備份會把既有輸出「移走」，中止在後會留下有備份卻無產出的空目錄
        if (!premiumFileExists && calculation.reportYears().isEmpty()) {
            throw new FatalException("[R-EXC-03] 找不到保費匯入檔：%s；%s，本次無任何可產出之報表"
                    .formatted(premiumPath.toAbsolutePath(),
                            Files.exists(claimPath) ? "且理賠匯入檔無任何資料列" : "且理賠匯入檔亦不存在"));
        }

        // [7] 備份既有輸出並清除逾期備份
        //     清單須與「本次真的會重寫」的檔案一致——保費檔缺檔時前兩張不產，
        //     若仍列入備份會把上次的成果移走卻不補回（TASK K14 / P-12）
        List<String> outputNames = new ArrayList<>();
        if (premiumFileExists) {
            outputNames.add(TAccountCell.OUTPUT_FILE_PATTERN.formatted(yearMonth));
            outputNames.add(SummaryCell.OUTPUT_FILE_PATTERN.formatted(yearMonth));
        }
        // 兩張賠款報表共用同一份 M10，故每個年度各有一份 T 字帳與一份彙總表（D23）
        calculation.reportYears().forEach(year -> {
            outputNames.add(ClaimTAccountCell.OUTPUT_FILE_PATTERN.formatted(yearMonth, String.valueOf(year)));
            outputNames.add(ClaimSummaryCell.OUTPUT_FILE_PATTERN.formatted(yearMonth, String.valueOf(year)));
        });
        backupService.backupExisting(outputDir, outputNames, yearMonth, report);
        backupService.purgeExpired(report);

        // [8] 產出報表：前兩張同進退（保費檔缺檔時整組不產），
        //     兩張賠款報表份數隨資料變動（各 0 ~ N 份，且份數恆相等）
        List<Path> outputs = new ArrayList<>();
        if (premiumFileExists) {
            outputs.add(tAccountWriter.write(setting, calculation, outputDir));
            outputs.add(summaryWriter.write(setting, calculation, outputDir));
        }
        outputs.addAll(claimTAccountWriter.writeAll(setting, calculation, outputDir));
        outputs.addAll(claimSummaryWriter.writeAll(setting, calculation, outputDir));
        outputs.forEach(path -> report.getOutputFiles().add(new ExecutionReport.OutputFile(
                path.getFileName().toString(), path.toAbsolutePath().toString())));

        String premiumReportMessage = describePremiumReports(premiumFileExists);
        String claimTAccountMessage = describeClaimReport(calculation, Files.exists(claimPath), "賠款月帳單");
        String claimSummaryMessage = describeClaimReport(calculation, Files.exists(claimPath), "賠款彙總表");
        report.setPremiumReportMessage(premiumReportMessage);
        report.setClaimTAccountMessage(claimTAccountMessage);
        report.setClaimSummaryMessage(claimSummaryMessage);
        report.setSummary(new ExecutionReport.Summary(calculation.totalPremium(), calculation.totalClaim(),
                calculation.totalManagementFee(), calculation.balanceDue(),
                calculation.claimByUnderwritingYear(), calculation.reportYears()));

        return ExecutionResult.success(
                "%d 年 %s 月——%s；%s；%s".formatted(
                        setting.year(), setting.monthOfTwoDigits(),
                        premiumReportMessage, claimTAccountMessage, claimSummaryMessage),
                outputs, calculation, report);
    }

    /**
     * 共保月帳單（T 字帳與彙整表）之產出說明（P-12）。
     *
     * <p>保費檔缺檔時<b>不得沉默</b>：此時 {@code summary} 之保費、管理費、Balance Due 全是
     * 以 0 計算的結果，不標示會被誤讀為「本月保費真的是 0」。
     */
    private String describePremiumReports(boolean premiumFileExists) {
        return premiumFileExists
                ? "已產出共保月帳單 2 張（T 字帳、彙整表）"
                : "未產出共保月帳單：保費匯入檔不存在，保費與管理費相關金額均非實際值";
    }

    /**
     * 兩張賠款報表之產出說明（R-OUT-09 / P-22）。
     *
     * <p>未產出時<b>須能區分原因</b>——承辦人員要能判斷是漏放理賠檔，還是本月真的沒有非當年度簽單資料。
     *
     * <p>兩張報表共用同一份 {@code M10}（D23），故份數與原因永遠相同，訊息只差報表名稱。
     */
    private String describeClaimReport(CalculationResult calculation, boolean claimFileExists, String reportName) {
        if (!calculation.reportYears().isEmpty()) {
            String years = calculation.reportYears().stream()
                    .map(year -> year + " 年")
                    .collect(Collectors.joining("、"));
            return "%s %d 張（簽單年度 %s）".formatted(reportName, calculation.reportYears().size(), years);
        }
        if (!claimFileExists) {
            return "未產出%s：理賠匯入檔不存在".formatted(reportName);
        }
        // 保費檔缺檔時設定年不被排除（P-13），故「無非當年度簽單資料」僅在前兩張會產出時才是真正原因
        return calculation.claimByUnderwritingYear().isEmpty()
                ? "未產出%s：理賠匯入檔無任何資料列".formatted(reportName)
                : "未產出%s：無非當年度簽單資料".formatted(reportName);
    }

    private CalculationResult calculate(Setting setting, List<PremiumRecord> premiums, List<ClaimRecord> claims,
                                        boolean premiumFileExists) {
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

        // 第二階段：M9 取全部年度（與上方 filtered 恰為互補）
        // M10 僅在第一階段兩張報表會產出時才排除設定年——否則設定年之賠款無報表承載（P-13）
        Map<Integer, Long> claimByUnderwritingYear = claimCalculator.claimByUnderwritingYear(claims);
        List<Integer> reportYears = claimCalculator.reportYears(
                claimByUnderwritingYear, setting.year(), premiumFileExists);
        log.info("簽單年度分群 {}，應產出賠款月帳單之年度 {}（設定年{}排除）",
                claimByUnderwritingYear, reportYears, premiumFileExists ? "已" : "未");

        // 第四張報表：M12 為 M9 再多切一層公司維度，只餵彙總表之 C 欄（G 欄之分攤基準另有其人）
        Map<Integer, Map<String, Long>> claimByYearAndCompany = claimCalculator.claimByYearAndCompany(claims);

        return new CalculationResult(totalPremium, premiumByCompany, totalClaim, claimByCompany,
                allocatedPremium, allocatedClaim, managementFee, totalManagementFee, balanceDue,
                claimByUnderwritingYear, reportYears, claimByYearAndCompany);
    }

    /**
     * R-CALC-16：T 字帳 G6 = 彙整表 C23、O6 = B23、G14 = I23；
     * 第二階段追加 {@code M9[設定年] == M3}、{@code Σ M9 == 理賠檔全部已決賠款}
     * 與<b>賠款承載完整性</b>（P-13）。不相等視為程式缺陷，中止並記錄。
     */
    private void verifyConsistency(Setting setting, CalculationResult calculation, List<ClaimRecord> claims,
                                   boolean premiumFileExists) {
        long summaryPremiumTotal = 0L;
        long summaryClaimTotal = 0L;
        for (CoInsuranceCompany company : setting.companies()) {
            summaryPremiumTotal += calculation.premiumOf(company.code());
            summaryClaimTotal += calculation.claimOf(company.code());
        }
        long summaryFeeTotal = calculation.managementFee().values().stream()
                .mapToLong(Long::longValue).sum();

        checkEqual("共保保費（T 字帳 O6 vs 彙整表 B23）", calculation.totalPremium(), summaryPremiumTotal);
        checkEqual("攤付共保賠款（T 字帳 G6 vs 彙整表 C23）", calculation.totalClaim(), summaryClaimTotal);
        checkEqual("共保管理費（T 字帳 G14 vs 彙整表 I23）", calculation.totalManagementFee(), summaryFeeTotal);

        // 第二階段：M9 與 M3 之交叉驗證——分群路徑與篩選路徑須得到同一組數字
        long claimYearSum = calculation.claimByUnderwritingYear().values().stream()
                .mapToLong(Long::longValue).sum();
        checkEqual("設定年賠款（M9[%d] vs M3）".formatted(setting.year()),
                calculation.claimOfYear(setting.year()), calculation.totalClaim());
        checkEqual("理賠檔全部已決賠款（Σ M9 vs 理賠檔加總）",
                claimYearSum, claimCalculator.totalClaim(claims));

        // 賠款承載完整性（P-13）：每筆已決賠款必定且只被一張報表承載一次。
        // 設定年之賠款平時由第一階段報表承載；保費檔缺檔時前兩張不產，改由賠款 T 字帳承載。
        // 這條若不成立，代表有金額憑空消失或被重複計入——正是 M10 排除條件寫錯時的症狀。
        long carriedByPhaseOne = premiumFileExists ? calculation.totalClaim() : 0L;
        long carriedByClaimTAccounts = calculation.reportYears().stream()
                .mapToLong(calculation::claimOfYear).sum();
        checkEqual("賠款承載完整性（Σ M9 vs 第一階段承載 %d + 賠款 T 字帳承載 %d）"
                        .formatted(carriedByPhaseOne, carriedByClaimTAccounts),
                claimYearSum, carriedByPhaseOne + carriedByClaimTAccounts);

        // 第四張報表：M12 是 M9 再多切一層公司維度，總額必相等；設定年那一群則恆等於 M4（R-CALC-21）
        calculation.claimByYearAndCompany().forEach((year, byCompany) -> {
            long companySum = byCompany.values().stream().mapToLong(Long::longValue).sum();
            checkEqual("簽單年度 %d 之逐家賠款（Σ M12[%d] vs M9[%d]）".formatted(year, year, year),
                    calculation.claimOfYear(year), companySum);
        });
        long configYearByCompanySum = setting.companies().stream()
                .mapToLong(company -> calculation.claimOf(setting.year(), company.code())).sum();
        checkEqual("設定年逐家賠款（M12[%d] vs M4）".formatted(setting.year()),
                calculation.totalClaim(), configYearByCompanySum);

        log.info("跨報表一致性檢查通過（賠款承載：第一階段 {}、兩張賠款報表各 {} 份共 {}）",
                carriedByPhaseOne, calculation.reportYears().size(), carriedByClaimTAccounts);
    }

    private void checkEqual(String label, long expected, long actual) {
        if (expected != actual) {
            throw new FatalException("[R-CALC-16] 跨報表一致性檢查未通過——%s：%d ≠ %d，此為程式缺陷"
                    .formatted(label, expected, actual));
        }
    }
}
