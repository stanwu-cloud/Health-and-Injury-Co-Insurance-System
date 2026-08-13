package com.insurance.coinsurance.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 執行報告（MAPPING §9 之 F1~F10），序列化為 {@code logs/report.json}。
 *
 * <p>本物件於流程中逐步累積，故採可變型別；欄位名稱即 JSON 鍵名。
 * 檢核失敗時<b>仍須產出</b>本報告。
 */
public class ExecutionReport {

    /** F4 匯入檔資訊。 */
    public record InputFile(String fileName, String path, boolean exists, int rowCount) {
    }

    /** F7 產出檔資訊。 */
    public record OutputFile(String fileName, String path) {
    }

    /** F8 備份資訊。 */
    public record BackupFile(String source, String target) {
    }

    /**
     * F10 計算結果摘要。
     *
     * @param claimByUnderwritingYear M9 各簽單年度賠款合計（第二階段）
     * @param reportYears             M10 本次產出賠款 T 字帳之年度（第二階段，降冪）
     */
    public record Summary(long totalPremium, long totalClaim, long totalManagementFee, long balanceDue,
                          Map<Integer, Long> claimByUnderwritingYear, List<Integer> reportYears) {
    }

    /** F1 執行時間戳。 */
    private String executedAt;
    /** F2 本次處理年（民國年）。 */
    private int configYear;
    /** F2 本次處理月。 */
    private int configMonth;
    /** F3 年月是否由參數覆寫。 */
    private boolean paramOverride;
    /** F4 匯入檔清單。 */
    private final List<InputFile> inputFiles = new ArrayList<>();
    /** F5 檢核錯誤清單（列出全部，不於第一筆中止）。 */
    private final List<ValidationError> validationErrors = new ArrayList<>();
    /** F7 產出檔清單（檢核失敗時為空陣列）。 */
    private final List<OutputFile> outputFiles = new ArrayList<>();
    /** F8 備份清單。 */
    private final List<BackupFile> backupFiles = new ArrayList<>();
    /** F9 本次清除之逾期備份目錄。 */
    private final List<String> purgedBackups = new ArrayList<>();
    /** F10 計算結果摘要；檢核失敗時為 null。 */
    private Summary summary;
    /** 中止原因（非 MAPPING 必要欄位，供排錯）。 */
    private String fatalMessage;
    /**
     * 賠款 T 字帳之產出說明（第二階段，R-OUT-09）。
     *
     * <p>未產出時<b>須能區分原因</b>：理賠匯入檔不存在 vs 全部簽單年度皆為設定年。
     */
    private String claimTAccountMessage;
    /**
     * F11 保費匯入檔是否缺檔（P-12）。
     *
     * <p>為 {@code true} 時前兩張共保月帳單<b>未產出</b>，且 {@code summary} 之保費、管理費、
     * Balance Due 皆非實際業務值——承辦人員須據此判斷是漏放檔案還是本月確實無保費。
     */
    private boolean premiumFileMissing;
    /**
     * 共保月帳單（T 字帳與彙整表）之產出說明（P-12）。
     *
     * <p>與 {@link #claimTAccountMessage} 對稱：三張報表各自的產出結果皆須在報告中可讀。
     */
    private String premiumReportMessage;

    public String getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(String executedAt) {
        this.executedAt = executedAt;
    }

    public int getConfigYear() {
        return configYear;
    }

    public void setConfigYear(int configYear) {
        this.configYear = configYear;
    }

    public int getConfigMonth() {
        return configMonth;
    }

    public void setConfigMonth(int configMonth) {
        this.configMonth = configMonth;
    }

    public boolean isParamOverride() {
        return paramOverride;
    }

    public void setParamOverride(boolean paramOverride) {
        this.paramOverride = paramOverride;
    }

    public List<InputFile> getInputFiles() {
        return inputFiles;
    }

    public List<ValidationError> getValidationErrors() {
        return validationErrors;
    }

    /** F6 錯誤總數。 */
    public int getErrorCount() {
        return validationErrors.size();
    }

    public List<OutputFile> getOutputFiles() {
        return outputFiles;
    }

    public List<BackupFile> getBackupFiles() {
        return backupFiles;
    }

    public List<String> getPurgedBackups() {
        return purgedBackups;
    }

    public Summary getSummary() {
        return summary;
    }

    public void setSummary(Summary summary) {
        this.summary = summary;
    }

    public String getFatalMessage() {
        return fatalMessage;
    }

    public void setFatalMessage(String fatalMessage) {
        this.fatalMessage = fatalMessage;
    }

    public String getClaimTAccountMessage() {
        return claimTAccountMessage;
    }

    public void setClaimTAccountMessage(String claimTAccountMessage) {
        this.claimTAccountMessage = claimTAccountMessage;
    }

    public boolean isPremiumFileMissing() {
        return premiumFileMissing;
    }

    public void setPremiumFileMissing(boolean premiumFileMissing) {
        this.premiumFileMissing = premiumFileMissing;
    }

    public String getPremiumReportMessage() {
        return premiumReportMessage;
    }

    public void setPremiumReportMessage(String premiumReportMessage) {
        this.premiumReportMessage = premiumReportMessage;
    }
}
