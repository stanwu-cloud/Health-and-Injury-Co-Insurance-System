package com.insurance.coinsurance.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.insurance.coinsurance.config.AppConfig;
import com.insurance.coinsurance.constant.CoInsuranceConstants;
import com.insurance.coinsurance.model.ExecutionReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 執行報告產出（R-OUT-07）。
 *
 * <p><b>每次覆寫、只留最後一次</b>；檢核失敗或中止時<b>仍須產出</b>。
 * 個資已於錯誤蒐集階段遮蔽。
 */
@Component
public class ReportJsonWriter {

    private static final Logger log = LoggerFactory.getLogger(ReportJsonWriter.class);

    private final AppConfig appConfig;
    private final ObjectMapper objectMapper;

    public ReportJsonWriter(AppConfig appConfig) {
        this.appConfig = appConfig;
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * 寫入 {@code logs/report.json}。
     *
     * <p>本方法<b>不拋出例外</b>——報告產出失敗不應遮蔽原本的執行結果，僅記錄警告。
     */
    public void write(ExecutionReport report) {
        Path path = appConfig.logDirPath().resolve(CoInsuranceConstants.REPORT_JSON_FILE_NAME);
        try {
            Files.createDirectories(path.getParent());
            objectMapper.writeValue(path.toFile(), report);
            log.info("執行報告產出完成：{}", path.toAbsolutePath());
        } catch (IOException e) {
            log.warn("執行報告產出失敗：{}（{}）", path.toAbsolutePath(), e.getMessage());
        }
    }
}
