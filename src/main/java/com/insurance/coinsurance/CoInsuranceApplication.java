package com.insurance.coinsurance;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 傷害及健康保險共保月帳單報表產生系統。
 *
 * <p>本類別僅作為 Spring Boot 之設定根，實際進入點在 {@code entry} 之下：
 * {@code CliLauncher}（CLI，三個分支皆有）與 {@code FxLauncher}（GUI，僅 {@code feature/gui}）。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CoInsuranceApplication {
}
