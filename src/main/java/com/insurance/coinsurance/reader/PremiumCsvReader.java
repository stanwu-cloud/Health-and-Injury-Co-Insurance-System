package com.insurance.coinsurance.reader;

import com.insurance.coinsurance.constant.PremiumColumn;
import com.insurance.coinsurance.exception.FatalException;
import com.insurance.coinsurance.model.PremiumRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** 保費匯入檔讀取器。保費檔<b>不得缺檔</b>。 */
@Component
public class PremiumCsvReader {

    private static final Logger log = LoggerFactory.getLogger(PremiumCsvReader.class);
    private static final String RULE_ID = "R-VAL-00";

    /**
     * @throws FatalException 檔案不存在、解碼失敗、表頭不符、欄數不符
     */
    public List<PremiumRecord> read(Path path) {
        if (!Files.exists(path)) {
            throw new FatalException("[R-PATH-03] 找不到保費匯入檔：%s".formatted(path.toAbsolutePath()));
        }
        List<PremiumRecord> records = CsvReader.read(path, PremiumColumn.expectedHeaders(), RULE_ID).stream()
                .map(line -> new PremiumRecord(line.rowNumber(), line.values()))
                .toList();
        log.info("保費匯入檔讀取完成：{}，資料 {} 列", path, records.size());
        return records;
    }
}
