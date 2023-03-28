package cn.thinkingdata.android.encrypt;

import android.text.TextUtils;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import cn.thinkingdata.android.TDConfig;

public class ThinkingDataEncrypt {

    private static final Map<String, ThinkingDataEncrypt> sInstances = new HashMap<>();

    /**
     * < createInstance >.
     *
     * @param token   项目ID
     * @param mConfig TDConfig
     * @return {@link ThinkingDataEncrypt}
     */
    public static ThinkingDataEncrypt createInstance(String token, TDConfig mConfig) {
        synchronized (sInstances) {
            ThinkingDataEncrypt mInstance = sInstances.get(token);
            if (mInstance == null) {
                mInstance = new ThinkingDataEncrypt(mConfig);
                sInstances.put(token, mInstance);
            }
            return mInstance;
        }
    }

    /**
     * < getInstance >.
     *
     * @param token 项目ID
     * @return {@link ThinkingDataEncrypt}
     */
    public static ThinkingDataEncrypt getInstance(String token) {
        synchronized (sInstances) {
            return sInstances.get(token);
        }
    }

    private TDConfig config;
    private byte[] aesKey = null;
    private String encryptedKey = null;
    private ThinkingDataEncrypt(TDConfig config) {
        this.config = config;
    }

    private void ensureAesKey() {
        if (aesKey != null && !TextUtils.isEmpty(encryptedKey)) {
            return;
        }
        if (config != null && isSecretKeyValid(config.getSecreteKey())) {
            try {
                aesKey = ThinkingDataEncryptUtils.generateAESKey();
                encryptedKey = ThinkingDataEncryptUtils.rsaEncrypt(config.getSecreteKey().publicKey, aesKey);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 对上报数据进行加密.
     *
     * @param json JSONObject
     * @return JSONObject
     */
    public JSONObject encryptTrackData(JSONObject json) {
        try {
            if (config == null) {
                return json;
            }

            if(!config.isEnableEncrypt()){
                return json;
            }

            SecreteKey secreteKey = config.getSecreteKey();

            if (!isSecretKeyValid(secreteKey)) {
                return json;
            }

            ensureAesKey();
            if (aesKey == null || TextUtils.isEmpty(encryptedKey)) {
                return json;
            }

            String encryptData = ThinkingDataEncryptUtils.aesEncrypt(aesKey, json.toString());

            JSONObject dataJson = new JSONObject();
            dataJson.put("ekey", encryptedKey);
            dataJson.put("pkv", secreteKey.version);
            dataJson.put("payload", encryptData);
            return dataJson;
        } catch (Exception e) {
            //ignored
        }
        return json;
    }

    public JSONObject decryptTrackData(JSONObject json) {
        if (!ThinkingDataEncryptUtils.isEncryptedData(json)) {
            return json;
        }

        if (aesKey == null) {
            return json;
        }

        String payload = json.optString("payload");

        String decryptStr = ThinkingDataEncryptUtils.aesDecrypt(aesKey, payload);

        try {
            return new JSONObject(decryptStr);
        } catch (Exception e) {

        }

        return json;
    }

    /**
     * 判断公钥是否为空.
     *
     * @return boolean
     */
    private boolean isSecretKeyValid(SecreteKey secreteKey) {
        return secreteKey != null && !TextUtils.isEmpty(secreteKey.publicKey);
    }
}
