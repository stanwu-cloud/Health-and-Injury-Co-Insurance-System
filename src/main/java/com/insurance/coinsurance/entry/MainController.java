package com.insurance.coinsurance.entry;

import com.insurance.coinsurance.model.ExecutionRequest;
import com.insurance.coinsurance.model.ExecutionResult;
import com.insurance.coinsurance.model.ValidationError;
import com.insurance.coinsurance.service.ReportGenerationService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.nio.file.Path;

/**
 * 主畫面（R-RUN-05）。
 *
 * <p>功能範圍：指定年月、觸發執行、顯示執行結果與檢核錯誤表格。
 * <b>不提供匯出、不保留執行歷史。</b>
 *
 * <p>版面採程式化建構而非 FXML，以免 fat jar 之資源載入問題。
 */
public class MainController {

    private static final String HINT = "年月留白時採用 config/application.xlsx 之設定值";

    private final ReportGenerationService service;
    private final TextField yearField = new TextField();
    private final TextField monthField = new TextField();
    private final Label statusLabel = new Label(HINT);
    private final Label outputLabel = new Label();
    private final ObservableList<ValidationError> errors = FXCollections.observableArrayList();
    private final Button executeButton = new Button("產生月帳單報表");

    public MainController(ReportGenerationService service) {
        this.service = service;
    }

    /** 建立版面並顯示視窗。 */
    public void show(Stage stage) {
        stage.setTitle("傷害及健康保險共保月帳單報表產生系統");
        stage.setScene(new Scene(buildRoot(), 900, 560));
        stage.show();
    }

    private BorderPane buildRoot() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(16));
        root.setTop(buildTop());
        root.setCenter(buildErrorTable());
        return root;
    }

    private VBox buildTop() {
        yearField.setPromptText("民國年，例 115");
        yearField.setPrefColumnCount(6);
        monthField.setPromptText("月，例 5");
        monthField.setPrefColumnCount(4);

        executeButton.setOnAction(event -> execute());

        HBox inputs = new HBox(8,
                new Label("年度："), yearField,
                new Label("月份："), monthField,
                executeButton);
        inputs.setAlignment(Pos.CENTER_LEFT);

        statusLabel.setWrapText(true);
        outputLabel.setWrapText(true);

        Label title = new Label("共保月帳單報表產生");
        title.setFont(Font.font(18));

        VBox top = new VBox(10, title, inputs, statusLabel, outputLabel,
                new Label("檢核錯誤明細："));
        top.setPadding(new Insets(0, 0, 12, 0));
        return top;
    }

    private TableView<ValidationError> buildErrorTable() {
        TableView<ValidationError> table = new TableView<>(errors);
        table.setPlaceholder(new Label("目前無檢核錯誤"));

        table.getColumns().add(column("檔名", 160, ValidationError::fileName));
        table.getColumns().add(column("列號", 70, error -> String.valueOf(error.rowNumber())));
        table.getColumns().add(column("欄位", 150, ValidationError::fieldName));
        table.getColumns().add(column("實際值", 140, ValidationError::actualValue));
        table.getColumns().add(column("規則", 110, ValidationError::ruleId));
        table.getColumns().add(column("說明", 260, ValidationError::message));
        return table;
    }

    private TableColumn<ValidationError, String> column(
            String title, int width, java.util.function.Function<ValidationError, String> mapper) {
        TableColumn<ValidationError, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(mapper.apply(data.getValue())));
        return column;
    }

    private void execute() {
        ExecutionRequest request;
        try {
            request = new ExecutionRequest(parseOptional(yearField.getText(), "年度"),
                    parseOptional(monthField.getText(), "月份"));
        } catch (IllegalArgumentException e) {
            statusLabel.setText(e.getMessage());
            return;
        }

        executeButton.setDisable(true);
        statusLabel.setText("執行中，請稍候…");
        outputLabel.setText("");
        errors.clear();

        Thread worker = new Thread(() -> {
            ExecutionResult result = service.execute(request);
            Platform.runLater(() -> showResult(result));
        }, "report-generation");
        worker.setDaemon(true);
        worker.start();
    }

    private void showResult(ExecutionResult result) {
        executeButton.setDisable(false);
        statusLabel.setText(result.isSuccess() ? "執行成功：" + result.message() : result.message());
        errors.setAll(result.errors());

        if (result.isSuccess()) {
            StringBuilder text = new StringBuilder();
            for (Path path : result.outputFiles()) {
                text.append("產出：").append(path.toAbsolutePath()).append(System.lineSeparator());
            }
            var calculation = result.calculation();
            text.append("共保保費 %,d｜攤付共保賠款 %,d｜共保管理費 %,d｜Balance Due %,d".formatted(
                    calculation.totalPremium(), calculation.totalClaim(),
                    calculation.totalManagementFee(), calculation.balanceDue()));
            outputLabel.setText(text.toString());
        } else {
            outputLabel.setText("兩張報表皆未產出；詳見 ./logs/report.json");
        }
    }

    private Integer parseOptional(String text, String label) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("%s「%s」非數值".formatted(label, text));
        }
    }
}
