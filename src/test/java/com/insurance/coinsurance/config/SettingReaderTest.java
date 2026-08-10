package com.insurance.coinsurance.config;

import com.insurance.coinsurance.exception.FatalException;
import com.insurance.coinsurance.model.CoInsuranceCompany;
import com.insurance.coinsurance.model.ExecutionRequest;
import com.insurance.coinsurance.model.Setting;
import com.insurance.coinsurance.support.TestFixtures;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TC-U-14 / TC-N-01：設定檔讀取與 R-PATH-01 檢核。 */
class SettingReaderTest {

    @Test
    @DisplayName("讀入現行設定檔：16 家、合計 100%、末筆為 國泰產險/N15/6%")
    void loadsCurrentSettingFile() {
        Setting setting = TestFixtures.sampleSetting();

        assertEquals(115, setting.year());
        assertEquals(5, setting.month());
        assertEquals("11505", setting.rocYearMonth());
        assertEquals(2026, setting.adYear());

        List<CoInsuranceCompany> companies = setting.companies();
        assertEquals(16, companies.size());

        BigDecimal total = companies.stream()
                .map(CoInsuranceCompany::share)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, total.compareTo(new BigDecimal("1.00")), "成分合計須為 100%%，實得 " + total);

        CoInsuranceCompany last = companies.get(companies.size() - 1);
        assertEquals("國泰產險", last.name());
        assertEquals("N15", last.code());
        assertEquals(0, last.share().compareTo(new BigDecimal("0.06")));

        CoInsuranceCompany reinsurer = companies.stream()
                .filter(CoInsuranceCompany::isReinsurer).findFirst().orElseThrow();
        assertEquals("中央再保", reinsurer.name());
        assertEquals(0, reinsurer.share().compareTo(new BigDecimal("0.07")));
    }

    @Test
    @DisplayName("CLI 參數之年月優先於設定檔")
    void argumentsOverrideSettingFile() {
        Setting setting = new SettingReader(TestFixtures.sampleConfig())
                .load(new ExecutionRequest(114, 12));
        assertEquals(114, setting.year());
        assertEquals(12, setting.month());
        assertEquals("11412", setting.rocYearMonth());
    }

    @Test
    @DisplayName("成分合計非 100% 時拋 FatalException 並中止")
    void rejectsShareTotalOtherThanOneHundredPercent(@TempDir Path temp) throws Exception {
        Path settingFile = temp.resolve("application.xlsx");
        Files.copy(TestFixtures.sampleConfig().settingFilePath(), settingFile);

        // 將「全球人壽」由 7.5% 竄改為 10%，使合計成為 102.5%
        try (InputStream in = Files.newInputStream(settingFile);
             Workbook workbook = WorkbookFactory.create(in)) {
            workbook.getSheet("工作表1").getRow(12).getCell(2).setCellValue(0.10);
            try (OutputStream out = Files.newOutputStream(settingFile)) {
                workbook.write(out);
            }
        }

        AppConfig config = TestFixtures.sampleConfig();
        config.setSettingFile(settingFile.toString());

        FatalException error = assertThrows(FatalException.class,
                () -> new SettingReader(config).load(ExecutionRequest.useSettingFile()));
        assertTrue(error.getMessage().contains("102.5"), error.getMessage());
        assertTrue(error.getMessage().contains("100%"), error.getMessage());
    }

    @Test
    @DisplayName("設定檔不存在時拋 FatalException")
    void rejectsMissingSettingFile(@TempDir Path temp) {
        AppConfig config = TestFixtures.sampleConfig();
        config.setSettingFile(temp.resolve("not-exists.xlsx").toString());

        FatalException error = assertThrows(FatalException.class,
                () -> new SettingReader(config).load(ExecutionRequest.useSettingFile()));
        assertTrue(error.getMessage().contains("找不到設定檔"), error.getMessage());
    }
}
