package com.insurance.coinsurance.entry;

import com.insurance.coinsurance.service.ReportGenerationService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * JavaFX 應用程式。由 {@link FxLauncher} 間接啟動（DESIGN D8）。
 *
 * <p>Spring 容器於啟動前建立，透過靜態欄位傳入——{@code Application.launch} 以反射
 * 建立本類別實例，無法使用建構子注入。
 */
public class FxApplication extends Application {

    private static ConfigurableApplicationContext springContext;

    /** 由 {@link FxLauncher} 呼叫；本方法會阻塞至視窗關閉。 */
    static void launchWith(ConfigurableApplicationContext context, String[] args) {
        springContext = context;
        Application.launch(FxApplication.class, args);
    }

    @Override
    public void start(Stage stage) {
        MainController controller =
                new MainController(springContext.getBean(ReportGenerationService.class));
        controller.show(stage);
    }

    @Override
    public void stop() {
        if (springContext != null) {
            springContext.close();
        }
        Platform.exit();
    }
}
