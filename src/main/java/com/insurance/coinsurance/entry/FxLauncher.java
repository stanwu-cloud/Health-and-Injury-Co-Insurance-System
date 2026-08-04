package com.insurance.coinsurance.entry;

import com.insurance.coinsurance.CoInsuranceApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * fat jar 之主類別。
 *
 * <p><b>刻意不繼承 {@code javafx.application.Application}</b>（DESIGN D8）：JavaFX 打包進
 * fat jar 時，主類別若直接繼承 {@code Application}，JavaFX 啟動器會因偵測不到模組路徑而
 * 以「JavaFX runtime components are missing」中止。改由本類別間接呼叫
 * {@code Application.launch(FxApplication.class, args)} 即可正常啟動。
 *
 * <p>執行模式：
 * <ul>
 *   <li>{@code java -jar xxx.jar --mode=cli [--year=115 --month=5]} → CLI 批次</li>
 *   <li>雙擊 jar 或 {@code java -jar xxx.jar} → GUI</li>
 * </ul>
 */
public final class FxLauncher {

    private static final String CLI_MODE = "--mode=cli";

    private FxLauncher() {
    }

    public static void main(String[] args) {
        if (isCliMode(args)) {
            System.exit(runCli(args));
        }
        FxApplication.launchWith(createContext(args), args);
    }

    static boolean isCliMode(String[] args) {
        for (String arg : args) {
            if (CLI_MODE.equalsIgnoreCase(arg)) {
                return true;
            }
        }
        return false;
    }

    private static int runCli(String[] args) {
        try (ConfigurableApplicationContext context = createContext(args)) {
            return context.getBean(CliRunner.class).run(args);
        }
    }

    private static ConfigurableApplicationContext createContext(String[] args) {
        SpringApplication application = new SpringApplicationBuilder(CoInsuranceApplication.class)
                .web(WebApplicationType.NONE)
                .bannerMode(org.springframework.boot.Banner.Mode.OFF)
                .build();
        return application.run(args);
    }
}
