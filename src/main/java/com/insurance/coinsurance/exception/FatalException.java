package com.insurance.coinsurance.exception;

/**
 * 前置資源或環境問題（缺檔、格式錯誤、成分合計不符、名稱查無對應、備份失敗）。
 *
 * <p>進入點對應行為：CLI 印訊息並以 exit code 2 結束；GUI 彈出錯誤對話框。
 */
public class FatalException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FatalException(String message) {
        super(message);
    }

    public FatalException(String message, Throwable cause) {
        super(message, cause);
    }
}
