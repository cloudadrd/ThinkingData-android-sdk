package cn.dataeye.android.encrypt;

import android.text.TextUtils;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import cn.dataeye.android.DataEyeConfig;

public class DataEyeEncrypt {

    private static final Map<String, DataEyeEncrypt> sInstances = new HashMap<>();

    /**
     * < createInstance >.
     *
     * @param token   项目ID
     * @param mConfig DataEyeConfig
     * @return {@link DataEyeEncrypt}
     */
    public static DataEyeEncrypt createInstance(String token, DataEyeConfig mConfig) {
        synchronized (sInstances) {
            DataEyeEncrypt mInstance = sInstances.get(token);
            if (mInstance == null) {
                mInstance = new DataEyeEncrypt(mConfig);
                sInstances.put(token, mInstance);
            }
            return mInstance;
        }
    }

    /**
     * < getInstance >.
     *
     * @param token 项目ID
     * @return {@link DataEyeEncrypt}
     */
    public static DataEyeEncrypt getInstance(String token) {
        synchronized (sInstances) {
            return sInstances.get(token);
        }
    }

    private DataEyeConfig config;
    private byte[] aesKey = null;
    private String encryptedKey = null;
    private DataEyeEncrypt(DataEyeConfig config) {
        this.config = config;
    }

    private void ensureAesKey() {
        if (aesKey != null && !TextUtils.isEmpty(encryptedKey)) {
            return;
        }
        if (config != null && isSecretKeyValid(config.getSecreteKey())) {
            try {
                aesKey = DataEyeEncryptUtils.generateAESKey();
                encryptedKey = DataEyeEncryptUtils.rsaEncrypt(config.getSecreteKey().publicKey, aesKey);
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
            SecreteKey secreteKey = config.getSecreteKey();

            if (!isSecretKeyValid(secreteKey)) {
                return json;
            }

            ensureAesKey();
            if (aesKey == null || TextUtils.isEmpty(encryptedKey)) {
                return json;
            }

            String encryptData = DataEyeEncryptUtils.aesEncrypt(aesKey, json.toString());

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
        if (!DataEyeEncryptUtils.isEncryptedData(json)) {
            return json;
        }

        if (aesKey == null) {
            return json;
        }

        String payload = json.optString("payload");

        String decryptStr = DataEyeEncryptUtils.aesDecrypt(aesKey, payload);

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
