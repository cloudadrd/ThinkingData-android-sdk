package cn.dataeye.android;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.LocaleList;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.DisplayMetrics;

import cn.dataeye.android.utils.DataEyeConstants;
import cn.dataeye.android.utils.DataEyeLog;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

class DataEyeSystemInformation {
    private static final String KEY_LIB = "#lib";
    private static final String KEY_LIB_VERSION = "#lib_version";
    private static final String KEY_OS = "#os";
    private static String sLibName = "Android";
    private static String sLibVersion = DataEyeConfig.VERSION;

    private static DataEyeSystemInformation sInstance;
    private final static Object sInstanceLock = new Object();

    private boolean hasNotUpdated;

    static void setLibraryInfo(String libName, String libVersion) {
        if (!TextUtils.isEmpty(libName)) {
            sLibName = libName;
            DataEyeLog.d(TAG, "#lib has been changed to: " + libName);
        }

        if (!TextUtils.isEmpty(libVersion)) {
            sLibVersion = libVersion;
            DataEyeLog.d(TAG, "#lib_version has been changed to: " + libVersion);
        }
    }

    static String getLibName() {
        return sLibName;
    }

    static String getLibVersion() {
        return sLibVersion;
    }

    static DataEyeSystemInformation getInstance(Context context) {
        synchronized (sInstanceLock) {
            if (null == sInstance) {
                sInstance = new DataEyeSystemInformation(context);
            }
            return sInstance;
        }
    }



    public boolean hasNotBeenUpdatedSinceInstall() {
        return hasNotUpdated;
    }

    private DataEyeSystemInformation(Context context) {
        mContext = context.getApplicationContext();
        mHasPermission = checkHasPermission(mContext, "android.permission.ACCESS_NETWORK_STATE");

        try {
            final PackageManager manager = context.getPackageManager();
            final PackageInfo info = manager.getPackageInfo(context.getPackageName(), 0);
            mAppVersionName = info.versionName;
            hasNotUpdated = info.firstInstallTime == info.lastUpdateTime;
            DataEyeLog.d(TAG, "First Install Time: " + info.firstInstallTime);
            DataEyeLog.d(TAG, "Last Update Time: " + info.lastUpdateTime);
        } catch (final Exception e) {
            DataEyeLog.d(TAG, "Exception occurred in getting app version");
        }

        mDeviceInfo = setupDeviceInfo(context);
    }

    private Map<String, Object> setupDeviceInfo(Context mContext)
    {
        final Map<String, Object> deviceInfo = new HashMap<>();
        {
            deviceInfo.put(KEY_LIB, sLibName);
            deviceInfo.put(KEY_LIB_VERSION, sLibVersion);
            deviceInfo.put(KEY_OS, "Android");

            if (!TextUtils.isEmpty(Build.VERSION.RELEASE)) {
                deviceInfo.put(DataEyeConstants.KEY_OS_VERSION, Build.VERSION.RELEASE);
            }

            if (!TextUtils.isEmpty(Build.MANUFACTURER)) {
                deviceInfo.put(DataEyeConstants.KEY_MANUFACTURER, Build.MANUFACTURER);
            }

            if (!TextUtils.isEmpty(Build.MODEL)) {
                deviceInfo.put(DataEyeConstants.KEY_DEVICE_MODEL, Build.MODEL);
            }

            DisplayMetrics displayMetrics = mContext.getResources().getDisplayMetrics();
            deviceInfo.put(DataEyeConstants.KEY_SCREEN_HEIGHT, displayMetrics.heightPixels);
            deviceInfo.put(DataEyeConstants.KEY_SCREEN_WIDTH, displayMetrics.widthPixels);

            String operatorString = getCarrier(mContext);
            if (!TextUtils.isEmpty(operatorString)) {
                deviceInfo.put(DataEyeConstants.KEY_CARRIER, operatorString);
            }

            String androidID = getAndroidID(mContext);
            if (!TextUtils.isEmpty(androidID)) {
                deviceInfo.put(DataEyeConstants.KEY_DEVICE_ID, androidID);
            }

            String systemLanguage = getSystemLanguage();
            if (!TextUtils.isEmpty(systemLanguage)) {
                deviceInfo.put(DataEyeConstants.KEY_SYSTEM_LANGUAGE, systemLanguage);
            }
        }
        return Collections.unmodifiableMap(deviceInfo);
    }

    // 获取运营商信息
    private static String getCarrier(Context context) {
        final Map<String, String> carrierMap = new HashMap<String, String>() {
            {
                put("46000", "中国移动");
                put("46002", "中国移动");
                put("46007", "中国移动");
                put("46008", "中国移动");

                put("46001", "中国联通");
                put("46006", "中国联通");
                put("46009", "中国联通");

                put("46003", "中国电信");
                put("46005", "中国电信");
                put("46011", "中国电信");

                put("46004", "中国卫通");

                put("46020", "中国铁通");

            }
        };

        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            String simOperator = tm.getSimOperator();
            if (!TextUtils.isEmpty(simOperator) && carrierMap.containsKey(simOperator)) {
                return carrierMap.get(simOperator);
            }

            String simOperatorName = tm.getSimOperatorName();
            if (!TextUtils.isEmpty(simOperatorName)) {
                return simOperatorName;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;

    }

    private String getAndroidID(Context mContext) {
        String androidID = "";
        try {
            androidID = Settings.Secure.getString(mContext.getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return androidID;
    }

    private String getSystemLanguage() {
        Locale locale;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            locale = LocaleList.getDefault().get(0);
        } else locale = Locale.getDefault();

        return locale.getLanguage();
    }

    String getAppVersionName() {
        return mAppVersionName;
    }

    Map<String, Object> getDeviceInfo() {
        return mDeviceInfo;
    }


    private boolean checkHasPermission(Context context, String permission) {
        try {
            Class<?> contextCompat = null;
            try {
                contextCompat = Class.forName("android.support.v4.content.ContextCompat");
            } catch (Exception e) {
                //ignored
            }
            if (contextCompat == null) {
                try {
                    contextCompat = Class.forName("androidx.core.content.ContextCompat");
                } catch (Exception e) {
                    //ignored
                }
            }

            if (contextCompat == null) {
                return true;
            }

            Method checkSelfPermissionMethod = contextCompat.getMethod("checkSelfPermission", new Class[]{Context.class, String.class});
            int result = (int)checkSelfPermissionMethod.invoke(null, new Object[]{context, permission});
            if (result != PackageManager.PERMISSION_GRANTED) {
                DataEyeLog.w(TAG, "You can fix this by adding the following to your AndroidManifest.xml file:\n"
                        + "<uses-permission android:name=\"" + permission + "\" />");
                return false;
            }

            return true;
        } catch (Exception e) {
            DataEyeLog.w(TAG, e.toString());
            return true;
        }
    }

    boolean isOnline() {
        if (!mHasPermission) {
            return false;
        }
        try {
            ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = cm.getActiveNetworkInfo();
            if (networkInfo != null && networkInfo.isConnected()) {
                return true;
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    String getNetworkType() {
        if (!mHasPermission) {
            return "NULL";
        }

        // Wifi
        ConnectivityManager manager = (ConnectivityManager)
                mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager != null) {
            NetworkInfo networkInfo = manager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if (networkInfo != null && networkInfo.isConnectedOrConnecting()) {
                return "WIFI";
            }
        }

        TelephonyManager telephonyManager = (TelephonyManager) mContext.getSystemService(Context
                .TELEPHONY_SERVICE);

        if (null != telephonyManager) {
            int networkType = telephonyManager.getNetworkType();

            switch (networkType) {
                case TelephonyManager.NETWORK_TYPE_GPRS:
                case TelephonyManager.NETWORK_TYPE_EDGE:
                case TelephonyManager.NETWORK_TYPE_CDMA:
                case TelephonyManager.NETWORK_TYPE_1xRTT:
                case TelephonyManager.NETWORK_TYPE_IDEN:
                    return "2G";
                case TelephonyManager.NETWORK_TYPE_UMTS:
                case TelephonyManager.NETWORK_TYPE_EVDO_0:
                case TelephonyManager.NETWORK_TYPE_EVDO_A:
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                case TelephonyManager.NETWORK_TYPE_HSPA:
                case TelephonyManager.NETWORK_TYPE_EVDO_B:
                case TelephonyManager.NETWORK_TYPE_EHRPD:
                case TelephonyManager.NETWORK_TYPE_HSPAP:
                    return "3G";
                case TelephonyManager.NETWORK_TYPE_LTE:
                    return "4G";
                case TelephonyManager.NETWORK_TYPE_NR:
                    return "5G";
            }
        }
        return "NULL";
    }


    private final static  String TAG = "ThinkingAnalytics.SystemInformation";
    private String mAppVersionName;
    private final Map<String, Object> mDeviceInfo;
    private final Context mContext;
    private final boolean mHasPermission;
}
