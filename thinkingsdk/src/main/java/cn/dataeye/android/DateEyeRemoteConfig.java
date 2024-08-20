package cn.dataeye.android;

import android.text.TextUtils;

import org.json.JSONObject;

import cn.dataeye.android.encrypt.SecreteKey;
import cn.dataeye.android.utils.DataEyeLog;

public class DateEyeRemoteConfig {
    private static final String TAG = "DateEyeRemoteConfig";
    public SecreteKey secreteKey;

    private boolean status;
    private String upUrl;

    public boolean isStatus() {
        return status;
    }

    public String getUpUrl() {
        return upUrl;
    }

    public static DateEyeRemoteConfig parseConfig(JSONObject data) {
        DateEyeRemoteConfig config = new DateEyeRemoteConfig();
        if (data == null) {
            return config;
        }
        DataEyeLog.d(TAG, "parseConfig:  " + data.toString());
        try {
            if (data.has("crypto")) {
                JSONObject secretJson = data.getJSONObject("crypto");
                if (secretJson.has("pub_key") && secretJson.has("version")) {
                    String key = secretJson.getString("pub_key");
                    String version = secretJson.optString("version", "");
                    if (!TextUtils.isEmpty(key) && !TextUtils.isEmpty(version)) {
                        config.secreteKey = new SecreteKey(key, version);
                    }
                }
            }

            if (data.has("prd_info")) {
                JSONObject infoJson = data.getJSONObject("prd_info");
                boolean status = infoJson.optBoolean("status");
                String upUrl = infoJson.optString("up-url");

                config.status = status;
                config.upUrl = upUrl;
            }
        } catch (Exception e) {
            e.printStackTrace();
            DataEyeLog.d(TAG, "parseConfig, Exception = " + e);
        }

        return config;
    }
}
