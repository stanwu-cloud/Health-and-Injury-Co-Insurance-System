package com.insurance.coinsurance;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 傷害及健康保險共保月帳單報表產生系統。
 *
 * <p>實際進入點為 {@code entry.FxLauncher}（DESIGN D8：fat jar 主類別不得繼承
 * {@code javafx.application.Application}）。本類別僅作為 Spring Boot 之設定根。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CoInsuranceApplication {
}
