package cn.thinkingdata.android;

// Debug 模式下的异常
public class DataEyeDebugException extends RuntimeException {
    public DataEyeDebugException(String message) {
        super(message);
    }

    public DataEyeDebugException(Throwable cause) {
        super(cause);
    }
}
