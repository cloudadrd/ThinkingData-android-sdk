package cn.dataeye.android;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.app.Fragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.webkit.WebView;

import cn.dataeye.android.encrypt.DataEyeEncrypt;
import cn.dataeye.android.persistence.*;
import cn.dataeye.android.utils.ICalibratedTime;
import cn.dataeye.android.utils.ITime;
import cn.dataeye.android.utils.PropertyUtils;
import cn.dataeye.android.utils.DataEyeCalibratedTime;
import cn.dataeye.android.utils.DataEyeCalibratedTimeWithNTP;
import cn.dataeye.android.utils.DataEyeConstants;
import cn.dataeye.android.utils.DataEyeLog;
import cn.dataeye.android.utils.DataEyeTime;
import cn.dataeye.android.utils.DataEyeTimeCalibrated;
import cn.dataeye.android.utils.DataEyeTimeConstant;
import cn.dataeye.android.utils.DataEyeUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DataEyeAnalyticsSDK implements DataEyeAnalyticsAPI {

    /**
     * 当 SDK 初始化完成后，可以通过此接口获得保存的单例
     *
     * @param context app context
     * @param appId   APP ID
     * @return SDK 实例
     */
    public static DataEyeAnalyticsSDK sharedInstance(Context context, String appId) {
        return sharedInstance(context, appId, null, false);
    }

    /**
     * 初始化 SDK. 在调用此接口之前，track 功能不可用.
     *
     * @param context app context
     * @param appId   APP ID
     * @param url     接收端地址
     * @return SDK 实例
     */
    public static DataEyeAnalyticsSDK sharedInstance(Context context, String appId, String url) {
        return sharedInstance(context, appId, url, true);
    }

    /**
     * 谨慎使用此接口，大多数情况下会默认绑定老版本数据到第一个实例化的 SDK 中
     *
     * @param trackOldData 是否绑定老版本(1.2.0 及之前)的数据
     */
    public static DataEyeAnalyticsSDK sharedInstance(Context context, String appId, String url, boolean trackOldData) {
        if (null == context) {
            DataEyeLog.w(TAG, "App context is required to get SDK instance.");
            return null;
        }

        if (TextUtils.isEmpty(appId)) {
            DataEyeLog.w(TAG, "APP ID is required to get SDK instance.");
            return null;
        }

        DataEyeConfig config;
        try {
            config = DataEyeConfig.getInstance(context, appId, url);
        } catch (IllegalArgumentException e) {
            DataEyeLog.w(TAG, "Cannot get valid TDConfig instance. Returning null");
            return null;
        }
        config.setTrackOldData(trackOldData);
        return sharedInstance(config);
    }

    public static DataEyeAnalyticsSDK sharedInstance(DataEyeConfig config) {
        if (null == config) {
            DataEyeLog.w(TAG, "Cannot initial SDK instance with null config instance.");
            return null;
        }

        synchronized (sInstanceMap) {

            Map<String, DataEyeAnalyticsSDK> instances = sInstanceMap.get(config.mContext);

            if (null == instances) {
                instances = new HashMap<>();
                sInstanceMap.put(config.mContext, instances);
                DataEyeSystemInformation systemInformation = DataEyeSystemInformation.getInstance(config.mContext);
                long installTime = systemInformation.getFirstInstallTime();
                long lastInstallTime = config.getLastInstallTime();
                boolean installTimeMatched;
                if (lastInstallTime <= 0L) {
                    installTimeMatched = false;
                } else {
                    installTimeMatched = installTime <= lastInstallTime;
                }
                if (!installTimeMatched) {
                    config.setLastInstallTime(installTime);
                }

                boolean hasNotUpdated = systemInformation.hasNotBeenUpdatedSinceInstall();
                DataEyeLog.d(TAG, "sharedInstance, installTimeMatched = " + installTimeMatched + "   hasNotUpdated = " + hasNotUpdated);
                if (!installTimeMatched && hasNotUpdated) {
                    sAppFirstInstallationMap.put(config.mContext, new LinkedList<String>());
                }
                DataEyeQuitSafelyService.getInstance(config.mContext).start();
            }

            DataEyeAnalyticsSDK instance = instances.get(config.mToken);
            if (null == instance) {
                instance = new DataEyeAnalyticsSDK(config);
                instances.put(config.mToken, instance);
                if (sAppFirstInstallationMap.containsKey(config.mContext)) {
                    sAppFirstInstallationMap.get(config.mContext).add(config.mToken);
                }
            }
            return instance;
        }
    }

    // only for automatic test
    static void addInstance(DataEyeAnalyticsSDK instance, Context context, String appId) {
        synchronized (sInstanceMap) {
            Map<String, DataEyeAnalyticsSDK> instances = sInstanceMap.get(context);
            if (null == instances) {
                instances = new HashMap<>();
                sInstanceMap.put(context, instances);
            }

            instances.put(appId, instance);
        }
    }


    // only the first instance is allowed to bind old data.
    private static boolean isOldDataTracked() {
        synchronized (sInstanceMap) {
            if (sInstanceMap.size() > 0) {
                for (Map<String, DataEyeAnalyticsSDK> instanceMap : sInstanceMap.values()) {
                    for (DataEyeAnalyticsSDK instance : instanceMap.values()) {
                        if (instance.mEnableTrackOldData) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }

    protected DataEyeDataHandle getDataHandleInstance(Context context) {
        return DataEyeDataHandle.getInstance(context);
    }

    /**
     * SDK 构造函数，需要传入 TDConfig 实例. 用户可以获取 TDConfig 实例， 并做相关配置后初始化 SDK.
     *
     * @param config TDConfig 实例
     * @param light  是否是轻实例（内部使用)
     */
    DataEyeAnalyticsSDK(final DataEyeConfig config, boolean... light) {
        mConfig = config;

        if (light.length > 0 && light[0]) {
            mLoginId = null;
            mIdentifyId = null;
            mSuperProperties = null;
            mOptOutFlag = null;
            mEnableFlag = null;
            mReyunAppID = null;
            mEnableTrackOldData = false;
            mTrackTimer = new HashMap<>();
            mMessages = getDataHandleInstance(config.mContext);
            mDataEyeSystemInformation = DataEyeSystemInformation.getInstance(config.mContext);
            return;
        }

        if (null == sStoredSharedPrefs) {
            sStoredSharedPrefs = sPrefsLoader.loadPreferences(config.mContext, PREFERENCE_NAME);
            sRandomID = new StorageRandomID(sStoredSharedPrefs);
            sOldLoginId = new StorageLoginID(sStoredSharedPrefs);
        }

        if (config.trackOldData() && !isOldDataTracked()) {
            mEnableTrackOldData = true;
        } else {
            mEnableTrackOldData = false;
        }

        // 获取保存在本地的用户ID和公共属性
        Future<SharedPreferences> storedPrefs = sPrefsLoader.loadPreferences(config.mContext, PREFERENCE_NAME + "_" + config.mToken);
        mLoginId = new StorageLoginID(storedPrefs);
        mIdentifyId = new StorageIdentifyId(storedPrefs);
        mSuperProperties = new StorageSuperProperties(storedPrefs);
        mOptOutFlag = new StorageOptOutFlag(storedPrefs);
        mEnableFlag = new StorageEnableFlag(storedPrefs);
        mReyunAppID = new StorageReyunAppID(storedPrefs);
        mDataEyeSystemInformation = DataEyeSystemInformation.getInstance(config.mContext);

        //设置aid到accountid
        mLoginId.put(mDataEyeSystemInformation.getAndroidID(config.mContext));

        mMessages = getDataHandleInstance(config.mContext);
        if (config.isEnableEncrypt()) {
            DataEyeEncrypt.createInstance(config.mToken, config);
        }

        if (mEnableTrackOldData) {
            mMessages.flushOldData(config.mToken);
        }

        mTrackTimer = new HashMap<>();

        mAutoTrackIgnoredActivities = new ArrayList<>();
        mAutoTrackEventTypeList = new ArrayList<>();


        mLifecycleCallbacks = new DataEyeActivityLifecycleCallbacks(this, mConfig.getMainProcessName());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            final Application app = (Application) config.mContext.getApplicationContext();
            app.registerActivityLifecycleCallbacks(mLifecycleCallbacks);
        }

        if (!config.isNormal()) {
            enableTrackLog(true);
        }

        DataEyeLog.i(TAG, String.format("Thinking Analytics SDK %s instance initialized successfully with mode: %s, APP ID ends with: %s, server url: %s, device ID: %s", DataEyeConfig.VERSION,
                config.getMode().name(), DataEyeUtils.getSuffix(config.mToken, 4), config.getServerUrl(), getDeviceId()));
    }

    /**
     * 打开/关闭 日志打印
     *
     * @param enableLog true 打开日志; false 关闭日志
     */
    public static void enableTrackLog(boolean enableLog) {
        DataEyeLog.setEnableLog(enableLog);
    }

    /**
     * 谨慎调用此接口。此接口用于使用第三方框架或者游戏引擎的场景中，更准确的设置上报方式。
     *
     * @param libName    对应事件表中 #lib 预置属性
     * @param libVersion 对应事件标准 #lib_version 预置属性
     */
    public static void setCustomerLibInfo(String libName, String libVersion) {
        DataEyeSystemInformation.setLibraryInfo(libName, libVersion);
    }

    // H5 与原生 SDK 打通，通过原生 SDK 发送数据
    void trackFromH5(String event) {
        if (hasDisabled()) return;
        if (TextUtils.isEmpty(event)) return;
        try {
            JSONArray data = new JSONObject(event).getJSONArray("data");
            for (int i = 0; i < data.length(); i++) {
                JSONObject eventObject = data.getJSONObject(i);

                String timeString = eventObject.getString(DataEyeConstants.KEY_TIME);

                Double zoneOffset = null;
                if (eventObject.has(DataEyeConstants.KEY_ZONE_OFFSET)) {
                    zoneOffset = eventObject.getDouble(DataEyeConstants.KEY_ZONE_OFFSET);
                }

                ITime time = getTime(timeString, zoneOffset);

                String eventType = eventObject.getString(DataEyeConstants.KEY_TYPE);

                DataEyeConstants.DataType type = DataEyeConstants.DataType.get(eventType);
                if (null == type) {
                    DataEyeLog.w(TAG, "Unknown data type from H5. ignoring...");
                    return;
                }

                JSONObject properties = eventObject.getJSONObject(DataEyeConstants.KEY_PROPERTIES);
                for (Iterator iterator = properties.keys(); iterator.hasNext(); ) {
                    String key = (String) iterator.next();
                    if (key.equals(DataEyeConstants.KEY_ACCOUNT_ID) || key.equals(DataEyeConstants.KEY_DISTINCT_ID) || mDataEyeSystemInformation.getDeviceInfo().containsKey(key)) {
                        iterator.remove();
                    }
                }

                DataEyeDataDescription dataEyeDataDescription;
                if (type.isTrack()) {
                    String eventName = eventObject.getString(DataEyeConstants.KEY_EVENT_NAME);

                    Map<String, String> extraFields = new HashMap<>();
                    if (eventObject.has(DataEyeConstants.KEY_FIRST_CHECK_ID)) {
                        extraFields.put(DataEyeConstants.KEY_FIRST_CHECK_ID, eventObject.getString(DataEyeConstants.KEY_FIRST_CHECK_ID));
                    }
                    if (eventObject.has(DataEyeConstants.KEY_EVENT_ID)) {
                        extraFields.put(DataEyeConstants.KEY_EVENT_ID, eventObject.getString(DataEyeConstants.KEY_EVENT_ID));
                    }

                    track(eventName, properties, time, false, extraFields, type);
                } else {
                    dataEyeDataDescription = new DataEyeDataDescription(this, type, properties, time);
                    trackInternal(dataEyeDataDescription);
                }
            }
        } catch (Exception e) {
            DataEyeLog.w(TAG, "Exception occurred when track data from H5.");
            e.printStackTrace();
        }
    }

    /**
     * 允许上报的网络类型
     */
    public enum ThinkingdataNetworkType {
        /**
         * 默认设置，在3G、4G、5G、WiFi 环境下上报数据
         */
        NETWORKTYPE_DEFAULT,
        /**
         * 只在 WiFi 环境上报数据
         */
        NETWORKTYPE_WIFI,
        /**
         * 在所有网络类型中上报
         */
        NETWORKTYPE_ALL
    }

    @Override
    public void setNetworkType(ThinkingdataNetworkType type) {
        if (hasDisabled()) return;
        mConfig.setNetworkType(type);
    }

    // used by unity SDK. Unity 2019.2.1f1 doesn't support enum type.
    public void setNetworkType(int type) {
        ThinkingdataNetworkType networkType;
        switch (type) {
            case 0:
                networkType = ThinkingdataNetworkType.NETWORKTYPE_DEFAULT;
                break;
            case 1:
                networkType = ThinkingdataNetworkType.NETWORKTYPE_WIFI;
                break;
            case 2:
                networkType = ThinkingdataNetworkType.NETWORKTYPE_ALL;
                break;
            default:
                return;
        }
        setNetworkType(networkType);
    }

    // autoTrack is used internal without property checking.
    void autoTrack(String eventName, JSONObject properties) {
        //autoTrack(eventName, properties, null);
        if (hasDisabled()) return;
        track(eventName, properties, getTime(), false);
    }

    @Override
    public void track(String eventName, JSONObject properties) {
        if (hasDisabled()) return;
        track(eventName, properties, getTime());
    }

    @Override
    public void track(String eventName, JSONObject properties, Date time) {
        if (hasDisabled()) return;
        track(eventName, properties, getTime(time, null));
    }

    @Override
    public void track(String eventName, JSONObject properties, Date time, TimeZone timeZone) {
        if (hasDisabled()) return;
        track(eventName, properties, getTime(time, timeZone));
    }

    private void track(String eventName, JSONObject properties, ITime time) {
        track(eventName, properties, time, true);
    }

    private void track(String eventName, JSONObject properties, ITime time, boolean doFormatChecking) {
        track(eventName, properties, time, doFormatChecking, null, null);
    }

    private void track(String eventName, JSONObject properties, ITime time, boolean doFormatChecking, Map<String, String> extraFields, DataEyeConstants.DataType type) {
        if (mConfig.isDisabledEvent(eventName)) {
            DataEyeLog.d(TAG, "Ignoring disabled event [" + eventName + "]");
            return;
        }

        try {
            if (doFormatChecking && PropertyUtils.isInvalidName(eventName)) {
                DataEyeLog.w(TAG, "Event name[" + eventName + "] is invalid. Event name must be string that starts with English letter, " +
                        "and contains letter, number, and '_'. The max length of the event name is 50.");
                if (mConfig.shouldThrowException())
                    throw new DataEyeDebugException("Invalid event name: " + eventName);
            }

            if (doFormatChecking && !PropertyUtils.checkProperty(properties)) {
                DataEyeLog.w(TAG, "The data contains invalid key or value: " + properties.toString());
                if (mConfig.shouldThrowException())
                    throw new DataEyeDebugException("Invalid properties. Please refer to SDK debug log for detail reasons.");
            }

            JSONObject finalProperties = obtainDefaultEventProperties(eventName);

            if (null != properties) {
                DataEyeUtils.mergeJSONObject(properties, finalProperties, mConfig.getDefaultTimeZone());
            }

            DataEyeConstants.DataType dataType = type == null ? DataEyeConstants.DataType.TRACK : type;

            DataEyeDataDescription dataEyeDataDescription = new DataEyeDataDescription(this, dataType, finalProperties, time);
            dataEyeDataDescription.eventName = eventName;
            if (null != extraFields) {
                dataEyeDataDescription.setExtraFields(extraFields);
            }

            trackInternal(dataEyeDataDescription);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void track(String eventName) {
        if (hasDisabled()) return;
        track(eventName, null, getTime());
    }

    @Override
    public void track(DataEyeAnalyticsEvent event) {
        if (hasDisabled()) return;
        if (null == event) {
            DataEyeLog.w(TAG, "Ignoring empty event...");
            return;
        }
        ITime time;
        if (event.getEventTime() != null) {
            time = getTime(event.getEventTime(), event.getTimeZone());
        } else {
            time = getTime();
        }

        Map<String, String> extraFields = new HashMap<>();
        if (TextUtils.isEmpty(event.getExtraField())) {
            DataEyeLog.w(TAG, "Invalid ExtraFields. Ignoring...");
        } else {
            String extraValue;
            if (event instanceof DataEyeFirstEvent && event.getExtraValue() == null) {
                extraValue = getDeviceId();
            } else {
                extraValue = event.getExtraValue();
            }

            extraFields.put(event.getExtraField(), extraValue);
        }

        track(event.getEventName(), event.getProperties(), time, true, extraFields, event.getDataType());
    }

    void trackInternal(DataEyeDataDescription dataEyeDataDescription) {
        if (mConfig.isDebugOnly() || mConfig.isDebug()) {
            mMessages.postToDebug(dataEyeDataDescription);
        } else if (dataEyeDataDescription.saveData) {
            mMessages.saveClickData(dataEyeDataDescription);
        } else {
            mMessages.postClickData(dataEyeDataDescription);
        }
    }

    private static String timeStamp2Date(long time, String format) {
        if (format == null || format.isEmpty()) {
            format = "yyyy-MM-dd HH:mm:ss";
        }
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        return sdf.format(new Date(time));
    }

    private JSONObject obtainDefaultEventProperties(String eventName) {

        JSONObject finalProperties = new JSONObject();
        try {
            DataEyeUtils.mergeJSONObject(getSuperProperties(), finalProperties, mConfig.getDefaultTimeZone());

            try {
                if (mDynamicSuperPropertiesTracker != null) {
                    JSONObject dynamicSuperProperties = mDynamicSuperPropertiesTracker.getDynamicSuperProperties();
                    if (dynamicSuperProperties != null && PropertyUtils.checkProperty(dynamicSuperProperties)) {
                        DataEyeUtils.mergeJSONObject(dynamicSuperProperties, finalProperties, mConfig.getDefaultTimeZone());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            finalProperties.put(DataEyeConstants.KEY_NETWORK_TYPE, mDataEyeSystemInformation.getNetworkType());

            if (eventName == "app_install") {
                String fit = timeStamp2Date(DataEyeSystemInformation.getInstance(mConfig.mContext).getFIT(), "yyyy-MM-dd HH:mm:ss.SSS");
                finalProperties.put(DataEyeConstants.KEY_FIRST_INSTALL_TIME, fit);

                if (!TextUtils.isEmpty(mReyunAppID.get())) {
                    finalProperties.put(DataEyeConstants.KEY_REYUN_APPID, mReyunAppID.get());
                }
            }
            if (!TextUtils.isEmpty(mDataEyeSystemInformation.getAppVersionName())) {
                finalProperties.put(DataEyeConstants.KEY_APP_VERSION, mDataEyeSystemInformation.getAppVersionName());
            }

            final DataEyeEventTimer dataEyeEventTimer;
            synchronized (mTrackTimer) {
                dataEyeEventTimer = mTrackTimer.get(eventName);
                mTrackTimer.remove(eventName);
            }

            if (null != dataEyeEventTimer) {
                try {
                    Double duration = Double.valueOf(dataEyeEventTimer.duration());
                    if (duration > 0) {
                        finalProperties.put(DataEyeConstants.KEY_DURATION, duration);
                    }
                } catch (JSONException e) {
                    // ignore
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {

        }

        return finalProperties;
    }

    @Override
    public void user_add(String propertyName, Number propertyValue) {
        if (hasDisabled()) return;
        try {
            if (null == propertyValue) {
                DataEyeLog.d(TAG, "user_add value must be Number");
                if (mConfig.shouldThrowException())
                    throw new DataEyeDebugException("Invalid property values for user add.");
            } else {
                JSONObject properties = new JSONObject();
                properties.put(propertyName, propertyValue);
                user_add(properties);
            }
        } catch (JSONException e) {
            e.printStackTrace();
            if (mConfig.shouldThrowException()) throw new DataEyeDebugException(e);
        }
    }

    @Override
    public void user_append(JSONObject properties) {
        user_append(properties, null);
    }

    public void user_append(JSONObject properties, Date date) {
        if (hasDisabled()) return;
        user_operations(DataEyeConstants.DataType.USER_APPEND, properties, date);
    }

    @Override
    public void user_add(JSONObject properties) {
        user_add(properties, null);
    }

    public void user_add(JSONObject properties, Date date) {
        if (hasDisabled()) return;
        user_operations(DataEyeConstants.DataType.USER_ADD, properties, date);
    }

    @Override
    public void user_setOnce(JSONObject properties) {
        user_setOnce(properties, null);
    }

    public void user_setOnce(JSONObject properties, Date date) {
        if (hasDisabled()) return;
        user_operations(DataEyeConstants.DataType.USER_SET_ONCE, properties, date);
    }

    @Override
    public void user_set(JSONObject properties) {
        user_set(properties, null);
    }

    public void user_set(JSONObject properties, Date date) {
        user_operations(DataEyeConstants.DataType.USER_SET, properties, date);
    }

    private void user_operations(DataEyeConstants.DataType type, JSONObject properties, Date date) {
        if (hasDisabled()) return;
        if (!PropertyUtils.checkProperty(properties)) {
            DataEyeLog.w(TAG, "The data contains invalid key or value: " + properties.toString());
            if (mConfig.shouldThrowException())
                throw new DataEyeDebugException("Invalid properties. Please refer to SDK debug log for detail reasons.");
        }
        try {
            ITime time = date == null ? getTime() : getTime(date, null);
            JSONObject finalProperties = new JSONObject();
            if (properties != null) {
                DataEyeUtils.mergeJSONObject(properties, finalProperties, mConfig.getDefaultTimeZone());
            }
            trackInternal(new DataEyeDataDescription(this, type, finalProperties, time));
        } catch (Exception e) {
            DataEyeLog.w(TAG, e.getMessage());
        }
    }

    @Override
    public void user_delete() {
        user_delete(null);
    }

    public void user_delete(Date date) {
        if (hasDisabled()) return;
        user_operations(DataEyeConstants.DataType.USER_DEL, null, date);
    }

    @Override
    public void user_unset(String... properties) {
        if (hasDisabled()) return;
        if (properties == null) return;
        JSONObject props = new JSONObject();
        for (String s : properties) {
            try {
                props.put(s, 0);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        if (props.length() > 0) {
            user_unset(props, null);
        }
    }

    public void user_unset(JSONObject properties, Date date) {
        if (hasDisabled()) return;
        user_operations(DataEyeConstants.DataType.USER_UNSET, properties, date);

    }

    @Override
    public void identify(String identity) {
        if (hasDisabled()) return;
        if (TextUtils.isEmpty(identity)) {
            DataEyeLog.w(TAG, "The identity cannot be empty.");
            if (mConfig.shouldThrowException())
                throw new DataEyeDebugException("distinct id cannot be empty");
            return;
        }

        synchronized (mIdentifyId) {
            mIdentifyId.put(identity);
        }
    }

    public void reyunAppID(String reyunAppID) {
        if (hasDisabled()) return;
        if (TextUtils.isEmpty(reyunAppID)) {
            DataEyeLog.w(TAG, "The reyunAppID cannot be empty.");
            if (mConfig.shouldThrowException())
                throw new DataEyeDebugException("reyunAppID id cannot be empty");
            return;
        }
        synchronized (mReyunAppID) {
            mReyunAppID.put(reyunAppID);
        }
    }

    @Override
    public void login(String loginId) {
        return;
//        if (hasDisabled()) return;
//        try {
//            if(TextUtils.isEmpty(loginId)) {
//                TDLog.d(TAG,"The account id cannot be empty.");
//                if (mConfig.shouldThrowException()) throw new TDDebugException("account id cannot be empty");
//                return;
//            }
//
//            synchronized (mLoginId) {
//                if (!loginId.equals(mLoginId.get())) {
//                    mLoginId.put(loginId);
//                }
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }

    @Override
    public void logout() {
        return;
//        if (hasDisabled()) return;
//        try {
//            synchronized (mLoginId) {
//                mLoginId.put(null);
//                if (mEnableTrackOldData) {
//                    synchronized (sOldLoginIdLock) {
//                        if (!TextUtils.isEmpty(sOldLoginId.get())) {
//                            sOldLoginId.put(null);
//                        }
//                    }
//                }
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }

    String getLoginId() {
        synchronized (mLoginId) {
            String loginId = mLoginId.get();
            if (TextUtils.isEmpty(loginId) && mEnableTrackOldData) {
                synchronized (sOldLoginIdLock) {
                    loginId = sOldLoginId.get();
                    if (!TextUtils.isEmpty(loginId)) {
                        mLoginId.put(loginId);
                        sOldLoginId.put(null);
                    }
                }
            }
            return loginId;
        }
    }

    String getRandomID() {
        synchronized (sRandomIDLock) {
            return sRandomID.get();
        }
    }

    private String getIdentifyID() {
        synchronized (mIdentifyId) {
            return mIdentifyId.get();
        }
    }

    @Override
    public String getDistinctId() {
        String identifyId = getIdentifyID();
        if (identifyId == null) {
            return getRandomID();
        } else {
            return identifyId;
        }
    }

    @Override
    public JSONObject getSuperProperties() {
        synchronized (mSuperProperties) {
            return mSuperProperties.get();
        }
    }

    @Override
    public void setSuperProperties(JSONObject superProperties) {
        if (hasDisabled()) return;
        try {
            if (superProperties == null || !PropertyUtils.checkProperty(superProperties)) {
                if (mConfig.shouldThrowException())
                    throw new DataEyeDebugException("Set super properties failed. Please refer to the SDK debug log for details.");
                return;
            }

            synchronized (mSuperProperties) {
                JSONObject properties = mSuperProperties.get();
                DataEyeUtils.mergeJSONObject(superProperties, properties, mConfig.getDefaultTimeZone());
                mSuperProperties.put(properties);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 动态公共属性接口.
     */
    public interface DynamicSuperPropertiesTracker {
        /**
         * 获取动态公共属性
         *
         * @return 动态公共属性
         */
        JSONObject getDynamicSuperProperties();
    }

    @Override
    public void setDynamicSuperPropertiesTracker(DynamicSuperPropertiesTracker dynamicSuperPropertiesTracker) {
        if (hasDisabled()) return;
        mDynamicSuperPropertiesTracker = dynamicSuperPropertiesTracker;
    }

    @Override
    public void unsetSuperProperty(String superPropertyName) {
        if (hasDisabled()) return;
        try {
            if (superPropertyName == null) {
                return;
            }
            synchronized (mSuperProperties) {
                JSONObject superProperties = mSuperProperties.get();
                superProperties.remove(superPropertyName);
                mSuperProperties.put(superProperties);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void clearSuperProperties() {
        if (hasDisabled()) return;
        synchronized (mSuperProperties) {
            mSuperProperties.put(new JSONObject());
        }
    }

    @Override
    public void timeEvent(final String eventName) {
        if (hasDisabled()) return;
        try {
            if (PropertyUtils.isInvalidName(eventName)) {
                DataEyeLog.w(TAG, "timeEvent event name[" + eventName + "] is not valid");
                //if (mConfig.shouldThrowException()) throw new TDDebugException("Invalid event name for time event");
                //return;
            }

            synchronized (mTrackTimer) {
                mTrackTimer.put(eventName, new DataEyeEventTimer(TimeUnit.SECONDS));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    boolean isActivityAutoTrackAppViewScreenIgnored(Class<?> activity) {
        if (activity == null) {
            return false;
        }
        if (mAutoTrackIgnoredActivities != null &&
                mAutoTrackIgnoredActivities.contains(activity.hashCode())) {
            return true;
        }

        DataEyeIgnoreTrackAppViewScreenAndAppClick annotation1 =
                activity.getAnnotation(DataEyeIgnoreTrackAppViewScreenAndAppClick.class);
        if (null != annotation1 && (TextUtils.isEmpty(annotation1.appId()) ||
                getToken().equals(annotation1.appId()))) {
            return true;
        }

        DataEyeIgnoreTrackAppViewScreen annotation2 = activity.getAnnotation(DataEyeIgnoreTrackAppViewScreen.class);
        if (null != annotation2 && (TextUtils.isEmpty(annotation2.appId()) ||
                getToken().equals(annotation2.appId()))) {
            return true;
        }


        return false;
    }

    boolean isAutoTrackEventTypeIgnored(AutoTrackEventType eventType) {
        if (eventType != null && !mAutoTrackEventTypeList.contains(eventType)) {
            return true;
        }
        return false;
    }

    boolean isAutoTrackEnabled() {
        if (hasDisabled()) return false;
        return mAutoTrack;
    }

    /**
     * 自动采集事件类型
     */
    public enum AutoTrackEventType {
        /**
         * APP 启动事件 app_start
         */
        APP_START(DataEyeConstants.APP_START_EVENT_NAME),
        /**
         * APP 关闭事件 app_end
         */
        APP_END(DataEyeConstants.APP_END_EVENT_NAME),
        /**
         * 控件点击事件 app_click
         */
        APP_CLICK(DataEyeConstants.APP_CLICK_EVENT_NAME),
        /**
         * 页面浏览事件 app_view
         */
        APP_VIEW_SCREEN(DataEyeConstants.APP_VIEW_EVENT_NAME),
        /**
         * APP 崩溃事件 app_crash
         */
        APP_CRASH(DataEyeConstants.APP_CRASH_EVENT_NAME),
        /**
         * APP 安装事件 app_install
         */
        APP_INSTALL(DataEyeConstants.APP_INSTALL_EVENT_NAME);

        private final String eventName;

        public static AutoTrackEventType autoTrackEventTypeFromEventName(String eventName) {
            if (TextUtils.isEmpty(eventName)) {
                return null;
            }

            switch (eventName) {
                case DataEyeConstants.APP_START_EVENT_NAME:
                    return APP_START;
                case DataEyeConstants.APP_END_EVENT_NAME:
                    return APP_END;
                case DataEyeConstants.APP_CLICK_EVENT_NAME:
                    return APP_CLICK;
                case DataEyeConstants.APP_VIEW_EVENT_NAME:
                    return APP_VIEW_SCREEN;
                case DataEyeConstants.APP_CRASH_EVENT_NAME:
                    return APP_CRASH;
                case DataEyeConstants.APP_INSTALL_EVENT_NAME:
                    return APP_INSTALL;
            }

            return null;
        }

        AutoTrackEventType(String eventName) {
            this.eventName = eventName;
        }

        String getEventName() {
            return eventName;
        }
    }

    /* package */ void trackViewScreenInternal(String url, JSONObject properties) {
        if (hasDisabled()) return;
        try {
            if ((!TextUtils.isEmpty(url) || properties != null)) {
                JSONObject trackProperties = new JSONObject();
                if (!TextUtils.isEmpty(mLastScreenUrl)) {
                    trackProperties.put(DataEyeConstants.KEY_REFERRER, mLastScreenUrl);
                }

                trackProperties.put(DataEyeConstants.KEY_URL, url);
                mLastScreenUrl = url;
                if (properties != null) {
                    DataEyeUtils.mergeJSONObject(properties, trackProperties, mConfig.getDefaultTimeZone());
                }
                autoTrack(DataEyeConstants.APP_VIEW_EVENT_NAME, trackProperties);
            }
        } catch (JSONException e) {
            DataEyeLog.i(TAG, "trackViewScreen:" + e);
        }
    }

    @Override
    public void trackViewScreen(Activity activity) {
        if (hasDisabled()) return;
        try {
            if (activity == null) {
                return;
            }

            JSONObject properties = new JSONObject();
            properties.put(DataEyeConstants.SCREEN_NAME, activity.getClass().getCanonicalName());
            DataEyeUtils.getScreenNameAndTitleFromActivity(properties, activity);

            if (activity instanceof DataEyeScreenAutoTracker) {
                DataEyeScreenAutoTracker dataEyeScreenAutoTracker = (DataEyeScreenAutoTracker) activity;

                String screenUrl = dataEyeScreenAutoTracker.getScreenUrl();
                JSONObject otherProperties = dataEyeScreenAutoTracker.getTrackProperties();
                if (otherProperties != null) {
                    DataEyeUtils.mergeJSONObject(otherProperties, properties, mConfig.getDefaultTimeZone());
                }

                trackViewScreenInternal(screenUrl, properties);
            } else {
                autoTrack(DataEyeConstants.APP_VIEW_EVENT_NAME, properties);
            }
        } catch (Exception e) {
            DataEyeLog.i(TAG, "trackViewScreen:" + e);
        }
    }

    @Override
    public void trackViewScreen(android.app.Fragment fragment) {
        if (hasDisabled()) return;
        try {
            if (fragment == null) {
                return;
            }

            JSONObject properties = new JSONObject();
            String fragmentName = fragment.getClass().getCanonicalName();
            String screenName = fragmentName;
            String title = DataEyeUtils.getTitleFromFragment(fragment, getToken());

            Activity activity = fragment.getActivity();
            if (activity != null) {
                if (TextUtils.isEmpty(title)) {
                    title = DataEyeUtils.getActivityTitle(activity);
                }
                screenName = String.format(Locale.CHINA, "%s|%s", activity.getClass().getCanonicalName(), fragmentName);
            }

            if (!TextUtils.isEmpty(title)) {
                properties.put(DataEyeConstants.TITLE, title);
            }

            properties.put(DataEyeConstants.SCREEN_NAME, screenName);
            autoTrack(DataEyeConstants.APP_VIEW_EVENT_NAME, properties);
        } catch (Exception e) {
            DataEyeLog.i(TAG, "trackViewScreen:" + e);
        }
    }


    @Override
    public void trackViewScreen(final Object fragment) {
        if (hasDisabled()) return;
        if (fragment == null) {
            return;
        }

        Class<?> supportFragmentClass = null;
        Class<?> appFragmentClass = null;
        Class<?> androidXFragmentClass = null;

        try {
            try {
                supportFragmentClass = Class.forName("android.support.v4.app.Fragment");
            } catch (Exception e) {
                //ignored
            }

            try {
                appFragmentClass = Class.forName("android.app.Fragment");
            } catch (Exception e) {
                //ignored
            }

            try {
                androidXFragmentClass = Class.forName("androidx.fragment.app.Fragment");
            } catch (Exception e) {
                //ignored
            }
        } catch (Exception e) {
            //ignored
        }

        if (!(supportFragmentClass != null && supportFragmentClass.isInstance(fragment)) &&
                !(appFragmentClass != null && appFragmentClass.isInstance(fragment)) &&
                !(androidXFragmentClass != null && androidXFragmentClass.isInstance(fragment))) {
            return;
        }

        try {
            JSONObject properties = new JSONObject();
            String screenName = fragment.getClass().getCanonicalName();

            String title = DataEyeUtils.getTitleFromFragment(fragment, getToken());

            Activity activity = null;
            try {
                Method getActivityMethod = fragment.getClass().getMethod("getActivity");
                if (getActivityMethod != null) {
                    activity = (Activity) getActivityMethod.invoke(fragment);
                }
            } catch (Exception e) {
                //ignored
            }
            if (activity != null) {
                if (TextUtils.isEmpty(title)) {
                    title = DataEyeUtils.getActivityTitle(activity);
                }
                screenName = String.format(Locale.CHINA, "%s|%s", activity.getClass().getCanonicalName(), screenName);
            }

            if (!TextUtils.isEmpty(title)) {
                properties.put(DataEyeConstants.TITLE, title);
            }

            properties.put(DataEyeConstants.SCREEN_NAME, screenName);
            autoTrack(DataEyeConstants.APP_VIEW_EVENT_NAME, properties);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* package */ void appEnterBackground() {
        synchronized (mTrackTimer) {
            try {
                Iterator iterator = mTrackTimer.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry entry = (Map.Entry) iterator.next();
                    if (entry != null) {
                        if (DataEyeConstants.APP_END_EVENT_NAME.equals(entry.getKey().toString())) {
                            continue;
                        }
                        DataEyeEventTimer dataEyeEventTimer = (DataEyeEventTimer) entry.getValue();
                        if (dataEyeEventTimer != null) {
                            long eventAccumulatedDuration = dataEyeEventTimer.getEventAccumulatedDuration() + SystemClock.elapsedRealtime() - dataEyeEventTimer.getStartTime();
                            dataEyeEventTimer.setEventAccumulatedDuration(eventAccumulatedDuration);
                            dataEyeEventTimer.setStartTime(SystemClock.elapsedRealtime());
                        }
                    }
                }
            } catch (Exception e) {
                DataEyeLog.i(TAG, "appEnterBackground error:" + e.getMessage());
            }
        }
    }

    /* package */ void appBecomeActive() {
        DataEyeQuitSafelyService.getInstance(mConfig.mContext).start();
        synchronized (mTrackTimer) {
            try {
                Iterator iterator = mTrackTimer.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry entry = (Map.Entry) iterator.next();
                    if (entry != null) {
                        DataEyeEventTimer dataEyeEventTimer = (DataEyeEventTimer) entry.getValue();
                        if (dataEyeEventTimer != null) {
                            dataEyeEventTimer.setStartTime(SystemClock.elapsedRealtime());
                        }
                    }
                }
            } catch (Exception e) {
                DataEyeLog.i(TAG, "appBecomeActive error:" + e.getMessage());
            }
        }
    }

    @Override
    public void trackAppInstall() {
        if (hasDisabled()) return;
        enableAutoTrack(new ArrayList<>(Collections.singletonList(AutoTrackEventType.APP_INSTALL)));
    }

    // used by unity SDK. Unity 2019.2.1f1 doesn't support enum type.
    private static final int APP_START = 1;
    private static final int APP_END = 1 << 1;
    private static final int APP_CRASH = 1 << 4;
    private static final int APP_INSTALL = 1 << 5;

    public void enableAutoTrack(int types) {
        List<AutoTrackEventType> eventTypeList = new ArrayList<>();
        if ((types & APP_START) > 0) {
            eventTypeList.add(AutoTrackEventType.APP_START);
        }

        if ((types & APP_END) > 0) {
            eventTypeList.add(AutoTrackEventType.APP_END);
        }

        if ((types & APP_INSTALL) > 0) {
            eventTypeList.add(AutoTrackEventType.APP_INSTALL);
        }

        if ((types & APP_CRASH) > 0) {
            eventTypeList.add(AutoTrackEventType.APP_CRASH);
        }

        if (eventTypeList.size() > 0) {
            enableAutoTrack(eventTypeList);
        }
    }

    @Override
    public void enableAutoTrack(final List<AutoTrackEventType> eventTypeList) {
        if (hasDisabled()) return;

        mAutoTrack = true;
        if (eventTypeList == null || eventTypeList.size() == 0) {
            return;
        }

        enableAutoTrackLast(eventTypeList);

    }

    public void enableAutoTrackLast(List<AutoTrackEventType> eventTypeList) {
        if (eventTypeList.contains(AutoTrackEventType.APP_CRASH)) {
            mTrackCrash = true;
            DataEyeQuitSafelyService quitSafelyService = DataEyeQuitSafelyService.getInstance(mConfig.mContext);
            if (null != quitSafelyService) {
                quitSafelyService.initExceptionHandler();
            }
        }

        // 第一次调用时调用timeEvent，后续调用在生命周期回调中处理
        if (!mAutoTrackEventTypeList.contains(AutoTrackEventType.APP_END)
                && eventTypeList.contains(AutoTrackEventType.APP_END)) {
            timeEvent(DataEyeConstants.APP_END_EVENT_NAME);
        }

        if (eventTypeList.contains(AutoTrackEventType.APP_INSTALL)) {
            synchronized (sInstanceMap) {
                if (sAppFirstInstallationMap.containsKey(mConfig.mContext) &&
                        sAppFirstInstallationMap.get(mConfig.mContext).contains(getToken())) {
                    track(DataEyeConstants.APP_INSTALL_EVENT_NAME);
                    sAppFirstInstallationMap.get(mConfig.mContext).remove(getToken());
                }
            }
        }

        synchronized (this) {
            mAutoTrackStartTime = getTime();
            mAutoTrackStartProperties = obtainDefaultEventProperties(DataEyeConstants.APP_START_EVENT_NAME);
        }

        mAutoTrackEventTypeList.clear();
        mAutoTrackEventTypeList.addAll(eventTypeList);
        if (mAutoTrackEventTypeList.contains(AutoTrackEventType.APP_START)) {
            mLifecycleCallbacks.onAppStartEventEnabled(mConfig.mContext);
        }
    }

    @Override
    public void flush() {
        if (hasDisabled()) return;
        long delayTime = 0;
        if (TextUtils.isEmpty(mConfig.getGAID()) && TextUtils.isEmpty(mConfig.getOAID())) {
            delayTime = 2_000;
        }
        mMessages.flush(getToken(), delayTime);
    }

    /* package */ List<Class> getIgnoredViewTypeList() {
        if (mIgnoredViewTypeList == null) {
            mIgnoredViewTypeList = new ArrayList<>();
        }

        return mIgnoredViewTypeList;
    }

    @Override
    public void ignoreViewType(Class viewType) {
        if (hasDisabled()) return;
        if (viewType == null) {
            return;
        }

        if (mIgnoredViewTypeList == null) {
            mIgnoredViewTypeList = new ArrayList<>();
        }

        if (!mIgnoredViewTypeList.contains(viewType)) {
            mIgnoredViewTypeList.add(viewType);
        }
    }

    /* package */ boolean isActivityAutoTrackAppClickIgnored(Class<?> activity) {
        if (activity == null) {
            return false;
        }
        if (mAutoTrackIgnoredActivities != null &&
                mAutoTrackIgnoredActivities.contains(activity.hashCode())) {
            return true;
        }

        DataEyeIgnoreTrackAppViewScreenAndAppClick annotation1 =
                activity.getAnnotation(DataEyeIgnoreTrackAppViewScreenAndAppClick.class);
        if (null != annotation1 && (TextUtils.isEmpty(annotation1.appId()) ||
                getToken().equals(annotation1.appId()))) {
            return true;
        }

        DataEyeIgnoreTrackAppClick annotation2 = activity.getAnnotation(DataEyeIgnoreTrackAppClick.class);
        return null != annotation2 && (TextUtils.isEmpty(annotation2.appId()) ||
                getToken().equals(annotation2.appId()));

    }

    /* package */ boolean isTrackFragmentAppViewScreenEnabled() {
        return this.mTrackFragmentAppViewScreen;
    }

    @Override
    public void trackFragmentAppViewScreen() {
        if (hasDisabled()) return;
        this.mTrackFragmentAppViewScreen = true;
    }

    @Override
    public void ignoreAutoTrackActivity(Class<?> activity) {
        if (hasDisabled()) return;
        if (activity == null) {
            return;
        }

        if (mAutoTrackIgnoredActivities == null) {
            mAutoTrackIgnoredActivities = new ArrayList<>();
        }

        if (!mAutoTrackIgnoredActivities.contains(activity.hashCode())) {
            mAutoTrackIgnoredActivities.add(activity.hashCode());
        }
    }

    @Override
    public void ignoreAutoTrackActivities(List<Class<?>> activitiesList) {
        if (hasDisabled()) return;
        if (activitiesList == null || activitiesList.size() == 0) {
            return;
        }

        if (mAutoTrackIgnoredActivities == null) {
            mAutoTrackIgnoredActivities = new ArrayList<>();
        }

        for (Class<?> activity : activitiesList) {
            if (activity != null && !mAutoTrackIgnoredActivities.contains(activity.hashCode())) {
                mAutoTrackIgnoredActivities.add(activity.hashCode());
            }
        }
    }

    public void setEnableEncrypt(boolean enableEncrypt) {
        mConfig.setEnableEncrypt(enableEncrypt);
    }

    @Override
    public void setViewID(View view, String viewID) {
        if (hasDisabled()) return;
        if (view != null && !TextUtils.isEmpty(viewID)) {
            DataEyeUtils.setTag(getToken(), view, R.id.thinking_analytics_tag_view_id, viewID);
        }
    }

    @Override
    public void setViewID(android.app.Dialog view, String viewID) {
        if (hasDisabled()) return;
        try {
            if (view != null && !TextUtils.isEmpty(viewID)) {
                if (view.getWindow() != null) {
                    DataEyeUtils.setTag(getToken(), view.getWindow().getDecorView(), R.id.thinking_analytics_tag_view_id, viewID);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public void setViewProperties(View view, JSONObject properties) {
        if (hasDisabled()) return;
        if (view == null || properties == null) {
            return;
        }
        DataEyeUtils.setTag(getToken(), view, R.id.thinking_analytics_tag_view_properties, properties);
    }

    @Override
    public void ignoreView(View view) {
        if (hasDisabled()) return;
        if (view != null) {
            DataEyeUtils.setTag(getToken(), view, R.id.thinking_analytics_tag_view_ignored, "1");
        }
    }

    @Override
    public void setJsBridge(WebView webView) {
        if (null == webView) {
            DataEyeLog.d(TAG, "SetJsBridge failed due to parameter webView is null");
            if (mConfig.shouldThrowException())
                throw new DataEyeDebugException("webView cannot be null for setJsBridge");
            return;
        }

        webView.getSettings().setJavaScriptEnabled(true);
        webView.addJavascriptInterface(new DataEyeWebAppInterface(this), "ThinkingData_APP_JS_Bridge");
    }

    @Override
    public void setJsBridgeForX5WebView(Object x5WebView) {
        if (x5WebView == null) {
            DataEyeLog.d(TAG, "SetJsBridge failed due to parameter webView is null");
            return;
        }

        try {
            Class<?> clazz = x5WebView.getClass();
            Method addJavascriptInterface = clazz.getMethod("addJavascriptInterface", Object.class, String.class);
            if (addJavascriptInterface == null) {
                return;
            }

            addJavascriptInterface.invoke(x5WebView, new DataEyeWebAppInterface(this), "ThinkingData_APP_JS_Bridge");
        } catch (Exception e) {
            DataEyeLog.w(TAG, "setJsBridgeForX5WebView failed: " + e.toString());
        }

    }

    @Override
    public String getDeviceId() {
        if (mDataEyeSystemInformation.getDeviceInfo().containsKey(DataEyeConstants.KEY_DEVICE_ID)) {
            return (String) mDataEyeSystemInformation.getDeviceInfo().get(DataEyeConstants.KEY_DEVICE_ID);
        } else {
            return null;
        }
    }

    /* package */ interface InstanceProcessor {
        void process(DataEyeAnalyticsSDK instance);
    }

    /* package */
    static void allInstances(InstanceProcessor processor) {
        synchronized (sInstanceMap) {
            for (final Map<String, DataEyeAnalyticsSDK> instances : sInstanceMap.values()) {
                for (final DataEyeAnalyticsSDK instance : instances.values()) {
                    processor.process(instance);
                }
            }
        }
    }

    /* package */ boolean shouldTrackCrash() {
        if (hasDisabled()) return false;
        return mTrackCrash;
    }

    /**
     * 打开/关闭 实例功能. 当关闭 SDK 功能时，之前的缓存数据会保留，并继续上报; 但是不会追踪之后的数据和改动.
     *
     * @param enabled true 打开上报; false 关闭上报
     */
    @Override
    public void enableTracking(boolean enabled) {
        DataEyeLog.d(TAG, "enableTracking: " + enabled);
        if (isEnabled() && !enabled) flush();
        mEnableFlag.put(enabled);
    }

    /**
     * 停止上报此用户数据，并且发送 user_del (不会重试)
     */
    @Override
    public void optOutTrackingAndDeleteUser() {
        DataEyeDataDescription userDel = new DataEyeDataDescription(this, DataEyeConstants.DataType.USER_DEL, null, getTime());
        userDel.setNoCache();
        trackInternal(userDel);
        optOutTracking();
    }

    /**
     * 停止上报此用户的数据. 调用此接口之后，会删除本地缓存数据和之前设置; 后续的上报和设置都无效.
     */
    @Override
    public void optOutTracking() {
        DataEyeLog.d(TAG, "optOutTracking...");
        mOptOutFlag.put(true);
        mMessages.emptyMessageQueue(getToken());

        synchronized (mTrackTimer) {
            mTrackTimer.clear();
        }

        mIdentifyId.put(null);
        mLoginId.put(null);
        synchronized (mSuperProperties) {
            mSuperProperties.put(new JSONObject());
        }
    }

    /**
     * 允许此实例的上报.
     */
    @Override
    public void optInTracking() {
        DataEyeLog.d(TAG, "optInTracking...");
        mOptOutFlag.put(false);
        mMessages.flush(getToken(), 0);
    }

    /**
     * 当前实例 Enable 状态. 通过 enableTracking 设置
     *
     * @return true 已经恢复上报; false 已暂停上报.
     */
    public boolean isEnabled() {
        return mEnableFlag.get();
    }

    /**
     * 当前实例是否可以上报
     *
     * @return true 已开启; false 停止
     */
    boolean hasDisabled() {
        return !isEnabled() || hasOptOut();
    }

    /**
     * 当前实例 OptOut 状态. 通过 optOutTracking(), optInTracking() 设置
     *
     * @return true 已停止上报; false 未停止上报.
     */
    public boolean hasOptOut() {
        return mOptOutFlag.get();
    }

    /**
     * 创建轻量级的 SDK 实例. 轻量级的 SDK 实例不支持缓存本地账号ID，访客ID，公共属性等.
     *
     * @return SDK 实例
     */
    @Override
    public DataEyeAnalyticsSDK createLightInstance() {
        return new LightDataEyeAnalyticsSDK(mConfig);
    }

    /**
     * 获取当前实例的 APP ID
     *
     * @return APP ID
     */
    public String getToken() {
        return mConfig.mToken;
    }

    public String getTimeString(Date date) {
        return getTime(date, mConfig.getDefaultTimeZone()).getTime();
    }

    // 本地缓存（SharePreference) 相关变量，所有实例共享
    private static final DataEyeSharedPreferencesLoader sPrefsLoader = new DataEyeSharedPreferencesLoader();
    private static Future<SharedPreferences> sStoredSharedPrefs;
    private static final String PREFERENCE_NAME = "com.dataeye.analyse";
    private static StorageLoginID sOldLoginId;
    private static final Object sOldLoginIdLock = new Object();
    private static StorageRandomID sRandomID;
    private static final Object sRandomIDLock = new Object();

    // 本地缓存（SharePreference 相关变量), 单个实例独有. 其文件名称为 PREFERENCE_NAME_{{mToken}}
    private final StorageLoginID mLoginId;
    private final StorageIdentifyId mIdentifyId;
    private final StorageEnableFlag mEnableFlag;
    private final StorageOptOutFlag mOptOutFlag;
    private final StorageSuperProperties mSuperProperties;
    private final StorageReyunAppID mReyunAppID;

    // 动态公共属性接口
    private DynamicSuperPropertiesTracker mDynamicSuperPropertiesTracker;

    // 缓存 timeEvent 累计时间
    private final Map<String, DataEyeEventTimer> mTrackTimer;

    // 自动采集相关变量
    private boolean mAutoTrack;
    private boolean mTrackCrash;
    private boolean mTrackFragmentAppViewScreen;
    private List<AutoTrackEventType> mAutoTrackEventTypeList;
    private List<Integer> mAutoTrackIgnoredActivities;
    private List<Class> mIgnoredViewTypeList = new ArrayList<>();
    private String mLastScreenUrl;
    private DataEyeActivityLifecycleCallbacks mLifecycleCallbacks;

    // 保存已经初始化的所有实例对象
    private static final Map<Context, Map<String, DataEyeAnalyticsSDK>> sInstanceMap = new HashMap<>();

    // 用于采集 APP 安装事件的逻辑
    private static final Map<Context, List<String>> sAppFirstInstallationMap = new HashMap<>();

    // 是否同步老版本数据，v1.3.0+ 与之前版本兼容所做的内部使用变量
    private final boolean mEnableTrackOldData;

    private final DataEyeDataHandle mMessages;
    DataEyeConfig mConfig;
    private DataEyeSystemInformation mDataEyeSystemInformation;

    private static final String TAG = "DataEyeAnalyticsSDK";

    // 对启动事件的特殊处理，记录开启自动采集的时间
    private ITime mAutoTrackStartTime;

    synchronized ITime getAutoTrackStartTime() {
        return mAutoTrackStartTime;
    }

    private JSONObject mAutoTrackStartProperties;

    synchronized JSONObject getAutoTrackStartProperties() {
        return mAutoTrackStartProperties == null ? new JSONObject() : mAutoTrackStartProperties;
    }

    private static ICalibratedTime sCalibratedTime;
    private final static ReentrantReadWriteLock sCalibratedTimeLock = new ReentrantReadWriteLock();

    // 获取当前时间的 ITime 实例
    private ITime getTime() {
        ITime result;
        sCalibratedTimeLock.readLock().lock();
        if (null != sCalibratedTime) {
            result = new DataEyeTimeCalibrated(sCalibratedTime, mConfig.getDefaultTimeZone());
        } else {
            result = new DataEyeTime(new Date(), mConfig.getDefaultTimeZone());
        }
        sCalibratedTimeLock.readLock().unlock();
        return result;
    }

    public static long getTimeFormat() {
        Date time;
        sCalibratedTimeLock.readLock().lock();
        if (null != sCalibratedTime) {
            time = sCalibratedTime.get(SystemClock.elapsedRealtime());
        } else {
            time = new Date();
        }
        sCalibratedTimeLock.readLock().unlock();
        return time.getTime();
    }

    // 获取与指定 date 和 timeZone 相关的 ITime 实例
    // 如果 timeZone 为 null, 则不会在事件中上传 #zone_offset 字段.
    private ITime getTime(Date date, TimeZone timeZone) {
        if (null == timeZone) {
            DataEyeTime time = new DataEyeTime(date, mConfig.getDefaultTimeZone());
            time.disableZoneOffset();
            return time;
        }
        return new DataEyeTime(date, timeZone);
    }

    // 获取常量类型的 ITime 实例
    private ITime getTime(String timeString, Double zoneOffset) {
        return new DataEyeTimeConstant(timeString, zoneOffset);
    }

    /**
     * 校准时间
     *
     * @param timestamp 当前时间戳
     */
    public static void calibrateTime(long timestamp) {
        setCalibratedTime(new DataEyeCalibratedTime(timestamp));
    }

    /**
     * 使用指定的 NTP Server 校准时间
     *
     * @param ntpServer NTP Server 列表
     */
    public static void calibrateTimeWithNtp(String... ntpServer) {
        if (null == ntpServer) return;
        setCalibratedTime(new DataEyeCalibratedTimeWithNTP(ntpServer));
    }

    // For Unity 2018.04 version
    public static void calibrateTimeWithNtpForUnity(String ntpServer) {
        calibrateTimeWithNtp(ntpServer);
    }

    /**
     * 使用自定义的 ICalibratedTime 校准时间
     *
     * @param calibratedTime ICalibratedTime 实例
     */
    private static void setCalibratedTime(ICalibratedTime calibratedTime) {
        sCalibratedTimeLock.writeLock().lock();
        sCalibratedTime = calibratedTime;
        sCalibratedTimeLock.writeLock().unlock();
    }
}

/**
 * 轻量级实例，不支持本地缓存，与主实例共享 APP ID.
 */
class LightDataEyeAnalyticsSDK extends DataEyeAnalyticsSDK {
    private String mDistinctId;
    private String mAccountId;
    private final JSONObject mSuperProperties;
    private boolean mEnabled = true;

    LightDataEyeAnalyticsSDK(DataEyeConfig config) {
        super(config, true);
        mSuperProperties = new JSONObject();
    }

    @Override
    public void identify(String identity) {
        mDistinctId = identity;
    }

    @Override
    public void setSuperProperties(JSONObject superProperties) {
        if (hasDisabled()) return;
        try {
            if (superProperties == null || !PropertyUtils.checkProperty(superProperties)) {
                return;
            }

            synchronized (mSuperProperties) {
                DataEyeUtils.mergeJSONObject(superProperties, mSuperProperties, mConfig.getDefaultTimeZone());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void unsetSuperProperty(String superPropertyName) {
        if (hasDisabled()) return;
        try {
            if (superPropertyName == null) {
                return;
            }
            synchronized (mSuperProperties) {
                mSuperProperties.remove(superPropertyName);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public void clearSuperProperties() {
        if (hasDisabled()) return;
        synchronized (mSuperProperties) {
            Iterator keys = mSuperProperties.keys();
            while (keys.hasNext()) {
                keys.next();
                keys.remove();
            }
        }
    }

    @Override
    public String getDistinctId() {
        if (null != mDistinctId) {
            return mDistinctId;
        } else {
            return getRandomID();
        }
    }

    @Override
    public JSONObject getSuperProperties() {
        return mSuperProperties;
    }

    @Override
    public void setNetworkType(DataEyeAnalyticsSDK.ThinkingdataNetworkType type) {

    }

    @Override
    public void enableAutoTrack(List<DataEyeAnalyticsSDK.AutoTrackEventType> eventTypeList) {

    }

    @Override
    public void trackFragmentAppViewScreen() {

    }

    @Override
    public void trackViewScreen(Activity activity) {

    }

    @Override
    public void trackViewScreen(Fragment fragment) {

    }

    @Override
    public void trackViewScreen(Object fragment) {

    }

    @Override
    public void setViewID(View view, String viewID) {

    }

    @Override
    public void setViewID(Dialog view, String viewID) {

    }

    @Override
    public void setViewProperties(View view, JSONObject properties) {

    }

    @Override
    public void ignoreAutoTrackActivity(Class<?> activity) {

    }

    @Override
    public void ignoreAutoTrackActivities(List<Class<?>> activitiesList) {

    }

    @Override
    public void ignoreViewType(Class viewType) {

    }

    @Override
    public void ignoreView(View view) {

    }

    @Override
    public void setJsBridge(WebView webView) {

    }

    @Override
    public void setJsBridgeForX5WebView(Object x5WebView) {

    }

    @Override
    public void login(String accountId) {
        if (hasDisabled()) return;
        mAccountId = accountId;
    }

    @Override
    public void logout() {
        if (hasDisabled()) return;
        mAccountId = null;
    }

    @Override
    String getLoginId() {
        return mAccountId;
    }

    @Override
    public void optOutTracking() {
    }

    @Override
    public void optInTracking() {
    }

    @Override
    public boolean isEnabled() {
        return mEnabled;
    }

    @Override
    public boolean hasOptOut() {
        return false;
    }

    @Override
    public void optOutTrackingAndDeleteUser() {

    }

    @Override
    public void enableTracking(boolean enabled) {
        mEnabled = enabled;
    }
}
