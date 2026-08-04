package com.insurance.coinsurance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.nio.file.Paths;

/** 技術性設定（DESIGN §7.2），對應 {@code config/application.yml} 之 {@code app.*}。 */
@ConfigurationProperties(prefix = "app")
public class AppConfig {

    private String settingFile = "./config/application.xlsx";
    private String templateDir = "./templet";
    private String inputDir = "./input";
    private String outputDir = "./output";
    private String backupDir = "./backup";
    private String logDir = "./logs";
    /** 備份保留月數（R-PATH-06）。 */
    private int backupRetentionMonths = 3;
    /** 輸出通道之個資遮蔽開關（R-RUN-06）。 */
    private boolean maskPersonalData = true;

    public Path settingFilePath() {
        return Paths.get(settingFile);
    }

    public Path templateDirPath() {
        return Paths.get(templateDir);
    }

    /** 指定年月之匯入目錄 {@code input/{YYYMM}}。 */
    public Path inputDirPath(String rocYearMonth) {
        return Paths.get(inputDir, rocYearMonth);
    }

    /** 指定年月之輸出目錄 {@code output/{YYYMM}}。 */
    public Path outputDirPath(String rocYearMonth) {
        return Paths.get(outputDir, rocYearMonth);
    }

    /** 指定年月之備份目錄 {@code backup/{YYYMM}}。 */
    public Path backupDirPath(String rocYearMonth) {
        return Paths.get(backupDir, rocYearMonth);
    }

    public Path logDirPath() {
        return Paths.get(logDir);
    }

    public String getSettingFile() {
        return settingFile;
    }

    public void setSettingFile(String settingFile) {
        this.settingFile = settingFile;
    }

    public String getTemplateDir() {
        return templateDir;
    }

    public void setTemplateDir(String templateDir) {
        this.templateDir = templateDir;
    }

    public String getInputDir() {
        return inputDir;
    }

    public void setInputDir(String inputDir) {
        this.inputDir = inputDir;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    public String getBackupDir() {
        return backupDir;
    }

    public void setBackupDir(String backupDir) {
        this.backupDir = backupDir;
    }

    public String getLogDir() {
        return logDir;
    }

    public void setLogDir(String logDir) {
        this.logDir = logDir;
    }

    public int getBackupRetentionMonths() {
        return backupRetentionMonths;
    }

    public void setBackupRetentionMonths(int backupRetentionMonths) {
        this.backupRetentionMonths = backupRetentionMonths;
    }

    public boolean isMaskPersonalData() {
        return maskPersonalData;
    }

    public void setMaskPersonalData(boolean maskPersonalData) {
        this.maskPersonalData = maskPersonalData;
    }
}
