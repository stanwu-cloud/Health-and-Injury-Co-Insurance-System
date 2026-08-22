package com.insurance.coinsurance;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 傷害及健康保險共保月帳單報表產生系統。
 *
 * <p>實際進入點為 {@code entry.CliLauncher}（fat jar 之主類別）。本類別僅作為 Spring Boot
 * 之設定根。GUI 已移出至 {@code feature/gui} 分支（TASK T-40）。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CoInsuranceApplication {
}
