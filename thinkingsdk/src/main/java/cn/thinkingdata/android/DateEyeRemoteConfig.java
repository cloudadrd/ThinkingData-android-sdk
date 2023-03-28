package cn.thinkingdata.android;

import android.text.TextUtils;

import org.json.JSONObject;

import cn.thinkingdata.android.encrypt.SecreteKey;
import cn.thinkingdata.android.utils.DataEyeLog;

public class DateEyeRemoteConfig {
    private static final String TAG = "DateEyeRemoteConfig";
    public SecreteKey secreteKey;

    public static DateEyeRemoteConfig parseConfig(JSONObject data) {
        DateEyeRemoteConfig config = new DateEyeRemoteConfig();
        if (data == null) {
            return config;
        }
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
        } catch (Exception e) {
            e.printStackTrace();
            DataEyeLog.d(TAG, "parseConfig, Exception = " + e);
        }

        return config;
    }
}
