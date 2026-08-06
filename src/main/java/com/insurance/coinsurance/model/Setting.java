package com.insurance.coinsurance.model;

import com.insurance.coinsurance.constant.CoInsuranceConstants;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 設定檔內容：設定年月 + 共保公司清單。
 *
 * @param year      設定年（民國年）
 * @param month     設定月（1–12）
 * @param companies 公司清單，順序同設定檔
 */
public record Setting(int year, int month, List<CoInsuranceCompany> companies) {

    public Setting {
        companies = List.copyOf(companies);
    }

    /** 民國年月字串，<b>月份補零 2 位</b>：115 / 5 → {@code 11505}。 */
    public String rocYearMonth() {
        return "%d%02d".formatted(year, month);
    }

    /** 西元年：民國年 + 1911。 */
    public int adYear() {
        return year + CoInsuranceConstants.ROC_YEAR_OFFSET;
    }

    /** 補零 2 位之月份字串。 */
    public String monthOfTwoDigits() {
        return "%02d".formatted(month);
    }

    /** 公司名稱 → 公司，供彙整表依樣板 A 欄名稱查表。 */
    public Map<String, CoInsuranceCompany> byName() {
        Map<String, CoInsuranceCompany> map = new LinkedHashMap<>();
        companies.forEach(company -> map.put(company.name(), company));
        return map;
    }

    /** 公司代號 → 公司。 */
    public Map<String, CoInsuranceCompany> byCode() {
        Map<String, CoInsuranceCompany> map = new LinkedHashMap<>();
        companies.forEach(company -> map.put(company.code(), company));
        return map;
    }
}
