package cn.dataeye.android.encrypt;

import android.text.TextUtils;
import android.util.Pair;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import cn.dataeye.android.utils.Base64Coder;
import cn.dataeye.android.utils.DataEyeLog;

public class DataEyeEncryptUtils {
    
    private static final String ALGORITHM_RSA = "RSA";
    private static final String ALGORITHM_AES = "AES";

    private static final String TAG = "ThinkingAnalytics.TAEncryptUtils";

    public static Pair<String, String> generateRSAKey() {
        try {
            // 获取指定算法的密钥对生成器
            KeyPairGenerator gen = KeyPairGenerator.getInstance(ALGORITHM_RSA);

            // 初始化密钥对生成器（指定密钥长度, 使用默认的安全随机数源）
            gen.initialize(2048);

            // 随机生成一对密钥（包含公钥和私钥）
            KeyPair keyPair = gen.generateKeyPair();

            // 获取 公钥 和 私钥
            PublicKey pubKey = keyPair.getPublic();
            PrivateKey priKey = keyPair.getPrivate();

            String pubKeyStr = new String(Base64Coder.encode(pubKey.getEncoded()));
            String priKeyStr = new String(Base64Coder.encode(priKey.getEncoded()));
            return new Pair(pubKeyStr, priKeyStr);
        } catch (Exception e) {
            DataEyeLog.d(TAG, "generateRSAKey :" + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    /**
     * 生成AES加密密钥.
     *
     * @return byte[]
     * @throws NoSuchAlgorithmException Exception
     */
    public static byte[] generateAESKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM_AES);
        keyGen.init(128);
        SecretKey aesKey = keyGen.generateKey();
        return aesKey.getEncoded();
    }

    /**
     * 非对称加密 AES key.
     *
     * @return 加密后的公钥
     */
    public static String rsaEncrypt(String publicKey, byte[] content) {
        if (TextUtils.isEmpty(publicKey)) {
            DataEyeLog.i(TAG, "PublicKey is null.");
            return null;
        }
        try {
            byte[] keyBytes = Base64Coder.decode(publicKey);
            KeySpec x509EncodedKeySpec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM_RSA);
            Key rsaPublicKey = keyFactory.generatePublic(x509EncodedKeySpec);

            Cipher cipher = Cipher.getInstance(keyFactory.getAlgorithm());
            cipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey);
            byte[] encryptedData = cipher.doFinal(content);
            return new String(Base64Coder.encode(encryptedData));
        } catch (Exception ex) {
            DataEyeLog.d(TAG, "RSA加密失败:" + ex.getMessage());
        }
        return null;
    }

    public static byte[] rsaDecrypt(String priKey, String content) {
        try{
            byte[] keyBytes = Base64Coder.decode(priKey);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);

            KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM_RSA);
            Cipher cipher = Cipher.getInstance(keyFactory.getAlgorithm());
            cipher.init(Cipher.DECRYPT_MODE, keyFactory.generatePrivate(spec));

            return cipher.doFinal(Base64Coder.decode(content));
        }catch (Exception e){
            DataEyeLog.d(TAG, "RSA解密失败:" + e.getMessage());
        }
        return null;
    }

    /**
     * AES 加密.
     *
     * @param key Key
     * @return 加密后的数据
     */
    public static String aesEncrypt(byte[] key, String content) {

        if (key == null || content == null) {
            return null;
        }

        try {
            byte[] contentBytes = content.getBytes();
            SecretKeySpec secretKeySpec = new SecretKeySpec(key, ALGORITHM_AES);
            Cipher cipher = Cipher.getInstance(secretKeySpec.getAlgorithm());
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
            byte[] encryptedBytes = cipher.doFinal(contentBytes);
            return new String(Base64Coder.encode(encryptedBytes));
        } catch (Exception ex) {
            DataEyeLog.d(TAG, "AES加密失败:" + ex.getMessage());
        }
        return null;
    }

    public static String aesDecrypt(byte[] key, String content) {
        if (key == null || content == null) {
            return null;
        }

        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(key, ALGORITHM_AES);
            Cipher cipher = Cipher.getInstance(secretKeySpec.getAlgorithm());
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
            byte[] result = cipher.doFinal(Base64Coder.decode(content));
            return new String(result);
        } catch (Exception ex) {
            DataEyeLog.d(TAG, "AES解密失败:" + ex.getMessage());
        }

        return null;
    }

    /**
     * 是否包含加密数据.
     *
     * @param array JSONArray
     * @return boolean
     */
    public static boolean hasEncryptedData(JSONArray array) {
        try {
            for (int i = 0; i < array.length(); i++) {
                if (isEncryptedData(array.getJSONObject(i))) {
                    return true;
                }
            }
        } catch (Exception e) {
            //ignored
        }
        return false;
    }

    /**
     * 是否是加密数据.
     *
     * @param json JSONObject
     * @return boolean
     */
    public static boolean isEncryptedData(JSONObject json) {
        if (json == null) {
            return false;
        }
        return json.length() == 3 && json.has("ekey") && json.has("pkv") && json.has("payload");
    }
}
