package com.insurance.coinsurance.reader;

import com.insurance.coinsurance.config.AppConfig;
import com.insurance.coinsurance.constant.ClaimColumn;
import com.insurance.coinsurance.constant.CoInsuranceConstants;
import com.insurance.coinsurance.constant.PremiumColumn;
import com.insurance.coinsurance.exception.FatalException;
import com.insurance.coinsurance.model.ClaimRecord;
import com.insurance.coinsurance.model.PremiumRecord;
import com.insurance.coinsurance.model.Setting;
import com.insurance.coinsurance.model.ValidationError;
import com.insurance.coinsurance.support.TestFixtures;
import com.insurance.coinsurance.validator.ClaimValidator;
import com.insurance.coinsurance.validator.PeriodValidator;
import com.insurance.coinsurance.validator.PremiumValidator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TC-E2E / TC-N 系列：CSV 讀取（Big5、表頭驗證）與欄位檢核。 */
class ReaderAndValidatorTest {

    private static final Charset BIG5 = Charset.forName(CoInsuranceConstants.CSV_CHARSET);

    private static Setting setting;
    private static List<PremiumRecord> premiums;
    private static List<ClaimRecord> claims;

    private final PremiumValidator premiumValidator = new PremiumValidator();
    private final ClaimValidator claimValidator = new ClaimValidator(new AppConfig());
    private final PeriodValidator periodValidator = new PeriodValidator();

    @BeforeAll
    static void loadSample() {
        setting = TestFixtures.sampleSetting();
        premiums = TestFixtures.samplePremiums();
        claims = TestFixtures.sampleClaims();
    }

    @Test
    @DisplayName("以 Big5 解碼，保費檔 2,193 列、理賠檔 14 列，中文欄位未亂碼")
    void readsBig5Correctly() {
        assertEquals(2193, premiums.size());
        assertEquals(14, claims.size());

        // 若誤以 UTF-8 解碼，下列中文欄位會成為亂碼
        assertTrue(claims.stream().anyMatch(record -> record.accidentReason().contains("跌")),
                "出險原因應含可讀中文");
        assertEquals("N05", premiums.get(0).companyCode());
        assertEquals(2, premiums.get(0).rowNumber(), "第一筆資料位於實體第 2 行（表頭為第 1 行）");
    }

    @Test
    @DisplayName("表頭欄位錯置之檔案被拒絕")
    void rejectsWrongHeader(@TempDir Path temp) throws IOException {
        Path original = TestFixtures.premiumPath(TestFixtures.sampleConfig());
        List<String> lines = Files.readAllLines(original, BIG5);

        List<String> broken = new ArrayList<>(lines);
        broken.set(0, broken.get(0).replace("保費合計", "保費總計"));
        Path file = temp.resolve("VOLP11505.csv");
        Files.write(file, broken, BIG5);

        FatalException error = assertThrows(FatalException.class, () -> new PremiumCsvReader().read(file));
        assertTrue(error.getMessage().contains("表頭"), error.getMessage());
    }

    @Test
    @DisplayName("理賠檔缺檔回傳空集合，不視為錯誤")
    void missingClaimFileYieldsEmptyList(@TempDir Path temp) {
        assertTrue(new ClaimCsvReader().read(temp.resolve("VOLC11505.csv")).isEmpty());
    }

    @Test
    @DisplayName("保費檔缺檔為中止型例外")
    void missingPremiumFileIsFatal(@TempDir Path temp) {
        assertThrows(FatalException.class, () -> new PremiumCsvReader().read(temp.resolve("VOLP11505.csv")));
    }

    @Test
    @DisplayName("現行樣本 2,193 + 14 列全部通過檢核（含批單起迄日空白）")
    void sampleDataPassesValidation() {
        List<ValidationError> errors = new ArrayList<>();
        errors.addAll(premiumValidator.validate("VOLP11505.csv", premiums, setting));
        errors.addAll(periodValidator.validatePremium("VOLP11505.csv", premiums, setting));
        errors.addAll(claimValidator.validate("VOLC11505.csv", claims, setting));
        errors.addAll(periodValidator.validateClaim("VOLC11505.csv", claims, setting));

        assertTrue(errors.isEmpty(), () -> "不應有檢核錯誤，實得：" + errors.stream()
                .map(ValidationError::format).toList());

        long blankEndorsementDates = premiums.stream()
                .filter(record -> record.endorsementStartDate().isEmpty())
                .count();
        assertEquals(2072, blankEndorsementDates, "批單起日空白之列數應與文件一致且不報錯");
    }

    @Test
    @DisplayName("出生日期 6 碼被判為錯誤，且錯誤值已遮蔽")
    void rejectsSixDigitBirthDate() {
        ClaimRecord broken = withValue(claims.get(0), ClaimColumn.INSURED_BIRTH_DATE, "671116");
        List<ValidationError> errors = claimValidator.validate("VOLC11505.csv", List.of(broken), setting);

        ValidationError error = errors.stream()
                .filter(item -> "R-VAL-02-11".equals(item.ruleId()))
                .findFirst().orElseThrow();
        assertEquals("被保險人出生日期", error.fieldName());
        // 遮蔽保留年（前 3 碼），其餘逐字元遮蔽——6 碼輸入故為 3 個星號
        assertEquals("671***", error.actualValue(), "個資須遮蔽後才寫入錯誤明細");
        assertTrue(error.format().startsWith("[R-VAL-02-11] VOLC11505.csv 第 "), error.format());
    }

    @Test
    @DisplayName("一次注入多筆錯誤時全部列出，不於第一筆中止")
    void collectsAllErrors() {
        ClaimRecord broken = withValue(
                withValue(claims.get(0), ClaimColumn.INSURED_BIRTH_DATE, "671116"),
                ClaimColumn.INSURED_NAME, "");
        List<ValidationError> errors = claimValidator.validate("VOLC11505.csv", List.of(broken), setting);
        assertTrue(errors.size() >= 2, "應同時列出出生日期與姓名之錯誤，實得 " + errors.size());
    }

    @Test
    @DisplayName("設定月改為 6 時，全部資料列之帳單月份報錯")
    void periodMismatchIsReported() {
        Setting june = new Setting(setting.year(), 6, setting.companies());
        List<ValidationError> errors = periodValidator.validatePremium("VOLP11505.csv", premiums, june);

        assertEquals(premiums.size(), errors.size());
        assertTrue(errors.stream().allMatch(error -> "R-VAL-03".equals(error.ruleId())));
        assertTrue(errors.get(0).message().contains("設定月 6"), errors.get(0).format());
    }

    @Test
    @DisplayName("帳單月份為 1 碼 5 時，以數值比對通過（不得以字串比對 05）")
    void monthComparedNumerically() {
        assertEquals("5", premiums.get(0).billMonth());
        assertTrue(periodValidator.validatePremium("VOLP11505.csv", premiums, setting).isEmpty());
    }

    @Test
    @DisplayName("公司代號不存在於設定檔時報 R-EXC-04")
    void unknownCompanyCodeIsReported() {
        PremiumRecord broken = withValue(premiums.get(0), PremiumColumn.COMPANY_CODE, "N99");
        List<ValidationError> errors = premiumValidator.validate("VOLP11505.csv", List.of(broken), setting);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(error -> "R-EXC-04".equals(error.ruleId())));
    }

    private static PremiumRecord withValue(PremiumRecord record, PremiumColumn column, String value) {
        List<String> values = new ArrayList<>(record.values());
        values.set(column.index(), value);
        return new PremiumRecord(record.rowNumber(), values);
    }

    private static ClaimRecord withValue(ClaimRecord record, ClaimColumn column, String value) {
        List<String> values = new ArrayList<>(record.values());
        values.set(column.index(), value);
        return new ClaimRecord(record.rowNumber(), values);
    }
}
