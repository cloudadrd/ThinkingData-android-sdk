package cn.thinkingdata.android.demo;

import android.util.Log;
import android.util.Pair;

import cn.dataeye.android.encrypt.DataEyeEncryptUtils;
import cn.dataeye.android.utils.Base64Coder;

public class EncryptTest {

    private static final String TAG = "EncryptTestTAG";

    public static final String SRC_CONTENT = "BI SDK EncryptTest";

    public static void test() {

        try {
            Log.d(TAG, "test: src content = " + SRC_CONTENT);

            // 生成RSA 公钥和私钥
            Pair<String, String> rsaKey = DataEyeEncryptUtils.generateRSAKey();

            // 生成AES秘钥
            byte[] aesKey = DataEyeEncryptUtils.generateAESKey();
            Log.d(TAG, "test: aesKey = " + new String(Base64Coder.encode(aesKey)));

            // 对数据使用aes加密
            String encryptContent = DataEyeEncryptUtils.aesEncrypt(aesKey, SRC_CONTENT);
            Log.d(TAG, "test: encryptContent = " + encryptContent);

            // 对 aes 密钥 执行非对称加密
            String encryptAesKey = DataEyeEncryptUtils.rsaEncrypt(rsaKey.first, aesKey);
            Log.d(TAG, "test: encryptAesKey = " + encryptAesKey);

            // 对 aes的密钥 执行非对称解密
            byte[] decryptAesKey = DataEyeEncryptUtils.rsaDecrypt(rsaKey.second, encryptAesKey);
            Log.d(TAG, "test: decryptAesKey = " + new String(Base64Coder.encode(decryptAesKey)));

            // 对 数据使用 解析后的aes秘钥解密
            String decryptContent = DataEyeEncryptUtils.aesDecrypt(decryptAesKey, encryptContent);
            Log.d(TAG, "test: decryptContent = " + decryptContent);

        } catch (Exception e) {
            Log.d(TAG, "test: Exception = " + e.getMessage());
            e.printStackTrace();
        }
    }
}
