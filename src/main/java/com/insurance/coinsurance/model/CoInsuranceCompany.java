package com.insurance.coinsurance.model;

import com.insurance.coinsurance.constant.CoInsuranceConstants;

import java.math.BigDecimal;

/**
 * 共保公司（設定檔一列）。
 *
 * @param name  公司名稱，供彙整表以樣板 A 欄名稱查表
 * @param code  公司代號（3 碼）
 * @param share 認受成分，<b>小數</b>（0.05 = 5%）
 */
public record CoInsuranceCompany(String name, String code, BigDecimal share) {

    /**
     * 是否為中央再保。<b>依代號判定，不依列號</b>——設定檔列順序可能變動（TASK K5）。
     */
    public boolean isReinsurer() {
        return CoInsuranceConstants.REINSURER_CODE.equals(code);
    }
}
