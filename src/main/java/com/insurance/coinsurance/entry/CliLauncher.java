package com.insurance.coinsurance.entry;

import com.insurance.coinsurance.CoInsuranceApplication;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * CLI 批次進入點——建立 Spring 容器、呼叫 {@link CliRunner}、以其 exit code 結束程序。
 *
 * <p>執行方式：{@code java -jar xxx.jar [--year=115 --month=5]}，或 {@code 拜託執行我.bat}。
 *
 * <p><b>本類別在三個分支逐字相同</b>（TASK T-40）：
 * <ul>
 *   <li>{@code dev} / {@code feature/phase-two}——無 GUI，本類別即 fat jar 之主類別</li>
 *   <li>{@code feature/gui}——主類別為 {@code FxLauncher}，它在 {@code --mode=cli} 時委派給
 *       {@link #main(String[])}，其餘情況開 JavaFX 視窗</li>
 * </ul>
 * 兩種情況都經由 {@link #createContext(String[])} 建立容器，設定不會兩邊走鐘。
 *
 * <p>{@code --mode=cli} 本身在無 GUI 的分支已無作用，但仍<b>刻意容許</b>出現在參數中——
 * 業務方機器上的 {@code 拜託執行我.bat} 與既有排程都帶著它，移除支援只會換來一個沒必要的
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

    /**
     * 建立本應用之 Spring 容器（非 Web、關閉 banner）。
     *
     * <p>{@code feature/gui} 之 {@code FxLauncher} 亦呼叫本方法，故為 package-private 而非 private。
     */
    static ConfigurableApplicationContext createContext(String[] args) {
        SpringApplication application = new SpringApplicationBuilder(CoInsuranceApplication.class)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF)
                .build();
        return application.run(args);
    }
}
