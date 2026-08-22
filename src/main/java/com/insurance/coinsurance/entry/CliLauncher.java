package com.insurance.coinsurance.entry;

import com.insurance.coinsurance.CoInsuranceApplication;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * fat jar 之主類別——CLI 批次進入點。
 *
 * <p>執行方式：{@code java -jar xxx.jar [--year=115 --month=5]}，或 {@code 拜託執行我.bat}。
 *
 * <p><b>GUI 已於 2026-08-22 移出至 {@code feature/gui} 分支</b>（TASK T-40）：本分支不含
 * {@code FxLauncher} / {@code FxApplication} / {@code MainController}，{@code pom.xml} 亦無
 * JavaFX 依賴。原先用來切換模式的 {@code --mode=cli} 已無作用，但仍**刻意容許**出現在參數中
 * ——業務方機器上的 {@code 拜託執行我.bat} 與既有排程都帶著它，移除支援只會換來一個沒必要的
 * 啟動失敗。未知參數一律由 {@link CliRunner#parse} 忽略。
 */
public final class CliLauncher {

    private CliLauncher() {
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    private static int run(String[] args) {
        try (ConfigurableApplicationContext context = createContext(args)) {
            return context.getBean(CliRunner.class).run(args);
        }
    }

    private static ConfigurableApplicationContext createContext(String[] args) {
        SpringApplication application = new SpringApplicationBuilder(CoInsuranceApplication.class)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF)
                .build();
        return application.run(args);
    }
}
