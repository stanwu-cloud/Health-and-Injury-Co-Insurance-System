package com.insurance.coinsurance.reader;

import com.insurance.coinsurance.constant.CoInsuranceConstants;
import com.insurance.coinsurance.exception.FatalException;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 匯入 CSV 之共用讀取器（R-VAL-00）。
 *
 * <p>固定以 Big5 / CP950 解碼（<b>不做編碼偵測</b>）、跳過表頭列並驗證表頭名稱與順序，
 * 回傳之欄位一律<b>依位置索引</b>存取。
 */
public final class CsvReader {

    private CsvReader() {
    }

    /** CSV 一列。 */
    public record CsvLine(int rowNumber, List<String> values) {
    }

    /**
     * 讀取並驗證表頭，回傳資料列（不含表頭）。
     *
     * @param path            檔案路徑
     * @param expectedHeaders 預期表頭（依索引順序）
     * @param ruleId          錯誤訊息之規則代碼
     * @throws FatalException 解碼失敗、表頭不符、欄數不符
     */
    public static List<CsvLine> read(Path path, String[] expectedHeaders, String ruleId) {
        Charset charset = resolveCharset(ruleId);
        List<String> rawLines;
        try (BufferedReader reader = Files.newBufferedReader(path, charset)) {
            rawLines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                rawLines.add(line);
            }
        } catch (IOException e) {
            throw new FatalException("[%s] 匯入檔讀取失敗：%s（%s）"
                    .formatted(ruleId, path.toAbsolutePath(), e.getMessage()), e);
        }

        String fileName = path.getFileName().toString();
        if (rawLines.isEmpty()) {
            throw new FatalException("[%s] 匯入檔為空檔，缺少表頭列：%s".formatted(ruleId, fileName));
        }

        List<String> header = splitLine(rawLines.get(0));
        validateHeader(header, expectedHeaders, fileName, ruleId);

        List<CsvLine> lines = new ArrayList<>();
        for (int i = 1; i < rawLines.size(); i++) {
            String raw = rawLines.get(i);
            if (raw.isBlank()) {
                continue;
            }
            int rowNumber = i + 1;
            List<String> values = splitLine(raw);
            if (values.size() != expectedHeaders.length) {
                throw new FatalException("[%s] %s 第 %d 列之欄位數為 %d，應為 %d"
                        .formatted(ruleId, fileName, rowNumber, values.size(), expectedHeaders.length));
            }
            lines.add(new CsvLine(rowNumber, values));
        }
        return lines;
    }

    private static void validateHeader(List<String> actual, String[] expected, String fileName, String ruleId) {
        if (actual.size() != expected.length) {
            throw new FatalException("[%s] %s 之表頭欄位數為 %d，應為 %d"
                    .formatted(ruleId, fileName, actual.size(), expected.length));
        }
        for (int i = 0; i < expected.length; i++) {
            String value = stripBom(actual.get(i));
            if (!expected[i].equals(value)) {
                throw new FatalException("[%s] %s 之表頭第 %d 欄為「%s」，應為「%s」"
                        .formatted(ruleId, fileName, i + 1, value, expected[i]));
            }
        }
    }

    /** 以 RFC 4180 之引號規則切分；本系統來源檔實測未使用引號，仍予支援以策安全。 */
    private static List<String> splitLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == CoInsuranceConstants.CSV_DELIMITER) {
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        values.add(current.toString().trim());
        return values;
    }

    private static String stripBom(String value) {
        return value.isEmpty() || value.charAt(0) != '﻿' ? value : value.substring(1);
    }

    private static Charset resolveCharset(String ruleId) {
        try {
            return Charset.forName(CoInsuranceConstants.CSV_CHARSET);
        } catch (IllegalArgumentException e) {
            throw new FatalException("[%s] 執行環境不支援匯入檔編碼「%s」，請確認 JDK 已含 jdk.charsets 模組"
                    .formatted(ruleId, CoInsuranceConstants.CSV_CHARSET), e);
        }
    }
}
