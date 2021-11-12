package cn.gamedataeye.android;

// Debug 模式下的异常
public class GameDataEyeDebugException extends RuntimeException {
    public GameDataEyeDebugException(String message) {
        super(message);
    }

    public GameDataEyeDebugException(Throwable cause) {
        super(cause);
    }
}
