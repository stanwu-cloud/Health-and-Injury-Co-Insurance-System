package com.insurance.coinsurance.reader;

import com.insurance.coinsurance.constant.ClaimColumn;
import com.insurance.coinsurance.model.ClaimRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 理賠匯入檔讀取器。
 *
 * <p><b>缺檔不視為錯誤</b>：回傳空集合，所有理賠相關參數以 0 計算。
 */
@Component
public class ClaimCsvReader {

    private static final Logger log = LoggerFactory.getLogger(ClaimCsvReader.class);
    private static final String RULE_ID = "R-VAL-00";

    public List<ClaimRecord> read(Path path) {
        if (!Files.exists(path)) {
            log.warn("理賠匯入檔不存在，理賠相關金額以 0 計算：{}", path.toAbsolutePath());
            return List.of();
        }
        List<ClaimRecord> records = CsvReader.read(path, ClaimColumn.expectedHeaders(), RULE_ID).stream()
                .map(line -> new ClaimRecord(line.rowNumber(), line.values()))
                .toList();
        log.info("理賠匯入檔讀取完成：{}，資料 {} 列", path, records.size());
        return records;
    }
}
