package cn.dataeye.android;

import android.content.Context;
import android.content.SharedPreferences;

import cn.dataeye.android.encrypt.SecreteKey;
import cn.dataeye.android.persistence.StorageFlushBulkSize;
import cn.dataeye.android.persistence.StorageFlushInterval;
import cn.dataeye.android.persistence.StorageRemoteConfig;
import cn.dataeye.android.utils.DataEyeLog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

public class DataEyeConfig {
    public static final String VERSION = BuildConfig.VERSION_NAME;

    private static final DataEyeSharedPreferencesLoader sPrefsLoader = new DataEyeSharedPreferencesLoader();
    private static final String PREFERENCE_NAME_PREFIX = "cn.dataeye.android.config";

    static final int DEFAULT_FLUSH_INTERVAL = 15000; // 默认每 15 秒发起一次上报
    static final int DEFAULT_FLUSH_BULK_SIZE = 20; // 默认每次上报请求最多包含 20 条数据

    private static final Map<Context, Map<String, DataEyeConfig>> sInstances = new HashMap<>();

    private final Set<String> mDisabledEvents = new HashSet<>();
    private final ReadWriteLock mDisabledEventsLock = new ReentrantReadWriteLock();

    private StorageRemoteConfig remoteConfigStorage;
    private boolean enableEncrypt = true;
    private SecreteKey secreteKey = null;

    /**
     * 实例运行模式, 默认为 NORMAL 模式.
     */
    public enum ModeEnum {
        /* 正常模式，数据会存入缓存，并依据一定的缓存策略上报 */
        NORMAL,
        /* Debug 模式，数据逐条上报。当出现问题时会以日志和异常的方式提示用户 */
        DEBUG,
        /* Debug Only 模式，只对数据做校验，不会入库 */
        DEBUG_ONLY
    }

    /**
     * 事件是否已经被禁用。在 TA 2.7 版本之后可以设置
     *
     * @param eventName 事件名
     * @return true 如果事件被禁用
     */
    boolean isDisabledEvent(String eventName) {
        mDisabledEventsLock.readLock().lock();
        try {
            return mDisabledEvents.contains(eventName);
        } finally {
            mDisabledEventsLock.readLock().unlock();
        }
    }

    private volatile ModeEnum mMode = ModeEnum.NORMAL;
    private volatile boolean mAllowedDebug;

    void setAllowDebug() {
        mAllowedDebug = true;
    }

    // for Unity
    public void setModeInt(int mode) {
        if (mode < 0 || mode > 2) {
            DataEyeLog.d(TAG, "Invalid mode value");
            return;
        }

        mMode = ModeEnum.values()[mode];
    }

    // for Unity
    public int getModeInt() {
        return mMode.ordinal();
    }

    /**
     * 设置 SDK 运行模式
     *
     * @param mode 运行模式
     */
    public void setMode(ModeEnum mode) {
        this.mMode = mode;
    }

    /**
     * 获取 SDK 当前运行模式
     *
     * @return ModeEnum
     */
    public ModeEnum getMode() {
        return mMode;
    }

    // Internal use only. This method should be called after the instance was initialed.
    static DataEyeConfig getInstance(Context context, String token) {
        try {
            return getInstance(context, token, "");
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 获取 TDConfig 实例. 该实例可以用于初始化 ThinkingAnalyticsSDK. 每个 SDK 实例对应一个 TDConfig 实例.
     *
     * @param context app context
     * @param token   APP ID, 创建项目时会给出.
     * @param url     数据接收端 URL, 必须是带协议的完整 URL，否则会抛异常
     * @return TDConfig 实例
     */
    public static DataEyeConfig getInstance(Context context, String token, String url) {
        Context appContext = context.getApplicationContext();

        synchronized (sInstances) {
            Map<String, DataEyeConfig> instances = sInstances.get(appContext);
            if (null == instances) {
                instances = new HashMap<>();
                sInstances.put(appContext, instances);
            }

            DataEyeConfig instance = instances.get(token);
            if (null == instance) {
                URL serverUrl;

                try {
                    serverUrl = new URL(url);
                } catch (MalformedURLException e) {
                    DataEyeLog.e(TAG, "Invalid server URL: " + url);
                    throw new IllegalArgumentException(e);
                }

                instance = new DataEyeConfig(appContext, token, serverUrl.getProtocol()
                        + "://" + serverUrl.getHost()
                        + (serverUrl.getPort() > 0 ? ":" + serverUrl.getPort() : ""), url);
                instances.put(token, instance);
                instance.getRemoteConfig();
            }
            return instance;
        }
    }

    private DataEyeConfig(Context context, String token, String serverUrl, String originalUrl) {
        mContext = context.getApplicationContext();

        Future<SharedPreferences> storedSharedPrefs = sPrefsLoader.loadPreferences(
                mContext, PREFERENCE_NAME_PREFIX + "_" + token);
        mContextConfig = DataEyeContextConfig.getInstance(mContext);

        mToken = token;
//        mServerUrl = serverUrl + "/sync";
        mServerUrl = originalUrl;
        mDebugUrl = serverUrl + "/data_debug";
//        mConfigUrl = serverUrl + "/config?appid=" + token;

        mFlushInterval = new StorageFlushInterval(storedSharedPrefs, DEFAULT_FLUSH_INTERVAL);
        mFlushBulkSize = new StorageFlushBulkSize(storedSharedPrefs, DEFAULT_FLUSH_BULK_SIZE);
        remoteConfigStorage = new StorageRemoteConfig(storedSharedPrefs);

        parseRemoteConfig(remoteConfigStorage.get());
    }

    synchronized boolean isShouldFlush(String networkType) {
        return (convertToNetworkType(networkType) & mNetworkType) != 0;
    }

    private int convertToNetworkType(String networkType) {
        if ("NULL".equals(networkType)) {
            return NetworkType.TYPE_ALL;
        } else if ("WIFI".equals(networkType)) {
            return NetworkType.TYPE_WIFI;
        } else if ("2G".equals(networkType)) {
            return NetworkType.TYPE_2G;
        } else if ("3G".equals(networkType)) {
            return NetworkType.TYPE_3G;
        } else if ("4G".equals(networkType)) {
            return NetworkType.TYPE_4G;
        } else if ("5G".equals(networkType)) {
            return NetworkType.TYPE_5G;
        }
        return NetworkType.TYPE_ALL;
    }

    private void getRemoteConfig() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
                InputStream in = null;

                try {
                    URL url = new URL(mConfigUrl);
                    connection = (HttpURLConnection) url.openConnection();
                    final SSLSocketFactory socketFactory = getSSLSocketFactory();
                    if (null != socketFactory && connection instanceof HttpsURLConnection) {
                        ((HttpsURLConnection) connection).setSSLSocketFactory(socketFactory);
                    }
                    connection.setRequestMethod("GET");

                    if (200 == connection.getResponseCode()) {
                        in = connection.getInputStream();

                        BufferedReader br = new BufferedReader(new InputStreamReader(in));
                        StringBuffer buffer = new StringBuffer();
                        String line;
                        while ((line = br.readLine()) != null) {
                            buffer.append(line);
                        }
                        JSONObject rjson = new JSONObject(buffer.toString());
                        if (rjson.getString("code").equals("10000")) {
                            JSONObject data = rjson.optJSONObject("data");
                            remoteConfigStorage.put(data);
                            parseRemoteConfig(data);
                        }

                        in.close();
                        br.close();
                    } else {
                        DataEyeLog.d(TAG, "Getting remote config failed, responseCode is " + connection.getResponseCode());
                    }

                } catch (IOException e) {
                    DataEyeLog.d(TAG, "Getting remote config failed due to: " + e.getMessage());
                } catch (JSONException e) {
                    DataEyeLog.d(TAG, "Getting remote config failed due to: " + e.getMessage());
                } finally {
                    if (null != in) {
                        try {
                            in.close();
                        } catch (final IOException e) {
                            e.printStackTrace();
                        }
                    }
                    if (null != connection) {
                        connection.disconnect();
                    }
                }
            }
        }).start();
    }

    private void parseRemoteConfig(JSONObject remoteConfigJsonData) {
        DataEyeLog.d(TAG, "parseRemoteConfig, remoteConfigJsonData = " + remoteConfigJsonData);
        DateEyeRemoteConfig remoteConfig = DateEyeRemoteConfig.parseConfig(remoteConfigJsonData);
        secreteKey = remoteConfig.secreteKey;
    }

    String getServerUrl() {
        return mServerUrl;
    }

    String getDebugUrl() {
        return mDebugUrl;
    }

    boolean isDebug() {
        return ModeEnum.DEBUG.equals(mMode);
    }

    boolean isDebugOnly() {
        return ModeEnum.DEBUG_ONLY.equals(mMode);
    }

    boolean isNormal() {
        return ModeEnum.NORMAL.equals(mMode);
    }

    boolean shouldThrowException() {
        return false;
    }

    /**
     * Flush interval, 单位毫秒
     *
     * @return 上报间隔
     */
    int getFlushInterval() {
        return mFlushInterval.get();
    }

    int getFlushBulkSize() {
        return mFlushBulkSize.get();
    }

    String getMainProcessName() {
        return mContextConfig.getMainProcessName();
    }


    synchronized void setNetworkType(DataEyeAnalyticsSDK.ThinkingdataNetworkType type) {
        switch (type) {
            case NETWORKTYPE_DEFAULT:
                mNetworkType = NetworkType.TYPE_3G | NetworkType.TYPE_4G | NetworkType.TYPE_5G | NetworkType.TYPE_WIFI;
                break;
            case NETWORKTYPE_WIFI:
                mNetworkType = NetworkType.TYPE_WIFI;
                break;
            case NETWORKTYPE_ALL:
                mNetworkType = NetworkType.TYPE_3G | NetworkType.TYPE_4G | NetworkType.TYPE_5G | NetworkType.TYPE_WIFI | NetworkType.TYPE_2G;
                break;
        }
    }

    private final class NetworkType {
        public static final int TYPE_2G = 1; //2G
        public static final int TYPE_3G = 1 << 1; //3G
        public static final int TYPE_4G = 1 << 2; //4G
        public static final int TYPE_WIFI = 1 << 3; //WIFI
        public static final int TYPE_5G = 1 << 4; // 5G
        public static final int TYPE_ALL = 0xFF; //ALL
    }

    private int mNetworkType = NetworkType.TYPE_3G | NetworkType.TYPE_4G | NetworkType.TYPE_5G | NetworkType.TYPE_WIFI;

    /**
     * 设置是否追踪老版本数据
     *
     * @param trackOldData
     */
    public void setTrackOldData(boolean trackOldData) {
        mTrackOldData = trackOldData;
    }

    public boolean trackOldData() {
        return mTrackOldData;
    }

    public synchronized void setDefaultTimeZone(TimeZone timeZone) {
        mDefaultTimeZone = timeZone;
    }

    public synchronized TimeZone getDefaultTimeZone() {
        return mDefaultTimeZone == null ? TimeZone.getDefault() : mDefaultTimeZone;
    }

    /**
     * 设置自签证书. 自签证书对实例所有网络请求有效.
     *
     * @param sslSocketFactory
     */
    public synchronized void setSSLSocketFactory(SSLSocketFactory sslSocketFactory) {
        if (null != sslSocketFactory) {
            mSSLSocketFactory = sslSocketFactory;
            getRemoteConfig();
        }
    }

    /**
     * 返回当前自签证书设置.
     *
     * @return SSLSocketFactory
     */
    public synchronized SSLSocketFactory getSSLSocketFactory() {
        return mSSLSocketFactory;
    }

    public boolean isEnableEncrypt() {
        return enableEncrypt;
    }

    public SecreteKey getSecreteKey() {
        return secreteKey;
    }

    // 兼容 1.2.0 之前老版本. 1.3.0 开始会在本地缓存中存放 app ID. 默认情况下会将之前遗留数据上报到第一个初始化的实例中.
    private volatile boolean mTrackOldData = true;

    // 同一个 Context 下所有实例共享的配置
    private final DataEyeContextConfig mContextConfig;

    private final StorageFlushInterval mFlushInterval;
    private final StorageFlushBulkSize mFlushBulkSize;
    private final String mServerUrl;
    private final String mDebugUrl;
    private final String mConfigUrl = "http://172.30.139.126/v1/settings";
    final String mToken;
    final Context mContext;

    private SSLSocketFactory mSSLSocketFactory;

    private TimeZone mDefaultTimeZone;

    private static final String TAG = "DataEyeAnalytics.DataEyeConfig";
}