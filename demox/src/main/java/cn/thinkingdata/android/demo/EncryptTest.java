package cn.thinkingdata.android.demo;

import android.util.Log;
import android.util.Pair;

import cn.thinkingdata.android.encrypt.ThinkingDataEncryptUtils;
import cn.thinkingdata.android.utils.Base64Coder;

public class EncryptTest {

    private static final String TAG = "EncryptTestTAG";

    public static final String SRC_CONTENT = "BI SDK EncryptTest";

    private static final String key = "MIIBCgKCAQEAnLdoA3ba57YHBAenYbLGTcdC48VVvVVDXV6N/W+1FztBRjvNPV1D\n" +
            "MOcIJBrveTlgKug2PCVynaIttaNql6p/+Bm4G41kyZYy7RSaUCaJ3ryjcXsKfClt\n" +
            "nG9vCwbIN+bVchxRzj739zIA1tBHn9v22PhFcEfsSAy2G2EwM4bQ38n2UrMse9wb\n" +
            "LUGT0kzyquwPQs7vriU+1XBkrdssoAqbwgW5yUqxDosYB5h7D1YTW0qKkJ6PPNnL\n" +
            "XbMv2Meyjxq1sbWoF/m8uboaKklqal1ep5UqTp9OFNOaTrVyXY4Gkt7wq3OoNvk9\n" +
            "2cJ1fHz9wnriGo+oNut9gQr1WVjOzRkAwwIDAQAB";

    public static void test() {

        try {
            Log.d(TAG, "test: src content = " + SRC_CONTENT);

            // 生成RSA 公钥和私钥
            Pair<String, String> rsaKey = ThinkingDataEncryptUtils.generateRSAKey();

            // 生成AES秘钥
            byte[] aesKey = ThinkingDataEncryptUtils.generateAESKey();
            Log.d(TAG, "test: aesKey = " + new String(Base64Coder.encode(aesKey)));

            // 对数据使用aes加密
            String encryptContent = ThinkingDataEncryptUtils.aesEncrypt(aesKey, SRC_CONTENT);
            Log.d(TAG, "test: encryptContent = " + encryptContent);

            // 对 aes 密钥 执行非对称加密
            String encryptAesKey = ThinkingDataEncryptUtils.rsaEncrypt(rsaKey.first, aesKey);
            Log.d(TAG, "test: encryptAesKey = " + encryptAesKey);

            // 对 aes的密钥 执行非对称解密
            byte[] decryptAesKey = ThinkingDataEncryptUtils.rsaDecrypt(rsaKey.second, encryptAesKey);
            Log.d(TAG, "test: decryptAesKey = " + new String(Base64Coder.encode(decryptAesKey)));

            // 对 数据使用 解析后的aes秘钥解密
            String decryptContent = ThinkingDataEncryptUtils.aesDecrypt(decryptAesKey, encryptContent);
            Log.d(TAG, "test: decryptContent = " + decryptContent);

        } catch (Exception e) {
            Log.d(TAG, "test: Exception = " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static final String content = "{\"#type\":\"track\",\"#time\":\"2022-10-19 11:20:23.981\",\"#timestamp\":1666149623993,\"#distinct_id\":\"instance_id\",\"#account_id\":\"d991fbe094bf6cab\",\"#oaid\":\"e5a1cdcc69d46ec84892fd61dc3b2326fa72930a8f2b8aec2b8f9e3f1402f5cb\",\"#gaid\":\"a7713c33-efb6-4f66-8688-33b41d8bbe3f\",\"#event_name\":\"app_end\",\"properties\":{\"#network_type\":\"WIFI\",\"#app_version\":\"1.0\",\"#duration\":38.25,\"#screen_name\":\"cn.thinkingdata.android.demo.MainActivity\",\"#title\":\"ThinkingDataDemo\",\"#zone_offset\":8},\"#uuid\":\"66484a74-dc4a-4df8-9fbd-f9aaefe4cccb\"}";

    public static void test_v2() {
        Pair<String, String> rsaKey = ThinkingDataEncryptUtils.generateRSAKey();

        Log.d(TAG, "test_v2: rsaKey pub = " + rsaKey.first);
        Log.d(TAG, "test_v2: rsaKey pri = " + rsaKey.second);
    }

    public static void test3() {

//        String key = DataEyeEncryptUtils.IV_STRING;
        String key = "lkjsdfrtywabcert";
        String encryptStr = ThinkingDataEncryptUtils.aesEncrypt(key.getBytes(), content);
        Log.d(TAG, "test3: encryptStr = " + encryptStr);

        String decryptStr = ThinkingDataEncryptUtils.aesDecrypt(key.getBytes(),encryptStr );
        Log.d(TAG, "test3: decryptStr = " + decryptStr);
    }
}
