package com.insurance.coinsurance.entry;

/**
 * fat jar 之主類別（**僅 {@code feature/gui} 分支**）。
 *
 * <p><b>刻意不繼承 {@code javafx.application.Application}</b>（DESIGN D8）：JavaFX 打包進
 * fat jar 時，主類別若直接繼承 {@code Application}，JavaFX 啟動器會因偵測不到模組路徑而
 * 以「JavaFX runtime components are missing」中止。改由本類別間接呼叫
 * {@code Application.launch(FxApplication.class, args)} 即可正常啟動。
 *
 * <p>執行模式：
 * <ul>
 *   <li>{@code java -jar xxx.jar --mode=cli [--year=115 --month=5]} → 委派給 {@link CliLauncher}</li>
 *   <li>雙擊 {@code 開啟畫面.bat} 或 {@code java -jar xxx.jar} → GUI</li>
 * </ul>
 *
 * <p>CLI 路徑<b>不自行實作</b>而是委派：{@code CliLauncher} 在三個分支逐字相同，容器建立方式
 * 若在此複製一份，兩邊的 Spring 設定遲早會走鐘（TASK T-40 / K21）。
 */
public final class FxLauncher {

    private static final String CLI_MODE = "--mode=cli";

    private FxLauncher() {
    }

    public static void main(String[] args) {
        if (isCliMode(args)) {
            CliLauncher.main(args);
            return;
        }
        FxApplication.launchWith(CliLauncher.createContext(args), args);
    }

    static boolean isCliMode(String[] args) {
        for (String arg : args) {
            if (CLI_MODE.equalsIgnoreCase(arg)) {
                return true;
            }
        }
        return false;
    }
}
