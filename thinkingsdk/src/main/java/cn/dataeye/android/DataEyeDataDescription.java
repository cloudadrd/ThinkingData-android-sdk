package cn.dataeye.android;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import cn.dataeye.android.utils.ITime;
import cn.dataeye.android.utils.DataEyeConstants;

/**
 * TD 数据类
 */
class DataEyeDataDescription {
    private static final boolean SAVE_TO_DATABASE = true;

    String eventName; // 事件名称，如果有

    // 数据时间, #time 字段
    private final ITime mTime;
    // 数据类型
    final DataEyeConstants.DataType mType;

    private String mDistinctId;
    private String mAccountId;
    private String mOaid;
    private String mGaid;

    private final JSONObject mProperties; // 属性

    private Map<String, String> mExtraFields;

    void setExtraFields(Map<String, String> extraFields) {
        mExtraFields = extraFields;
    }

    boolean saveData = SAVE_TO_DATABASE;

    final String mToken;

    DataEyeDataDescription(DataEyeAnalyticsSDK instance, DataEyeConstants.DataType type, JSONObject properties, ITime time) {
        mType = type;
        mProperties = properties;
        mTime = time;
        mToken = instance.getToken();
        mDistinctId = instance.getDistinctId();
        mAccountId = instance.getLoginId();
        mOaid = instance.mConfig.getOAID();
        mGaid = instance.mConfig.getGAID();
    }

    void setNoCache() {
        this.saveData = false;
    }

    /**
     * 获取数据，可能会阻塞，不要在主线程中调用
     *
     * @return 待上报数据
     */
    public JSONObject get(Context context) {
        JSONObject finalData = new JSONObject();

        try {
            addPresetProperties(finalData, context);
            dealCustomProperties(finalData);
            finalData.put(DataEyeConstants.KEY_PROPERTIES, mProperties);

        } catch (JSONException e) {

            e.printStackTrace();
        }

        return finalData;
    }

    private void addPresetProperties(JSONObject finalData, Context context) {
        try {
            finalData.put(DataEyeConstants.KEY_TYPE, mType.getType());
            // 有可能会阻塞
            finalData.put(DataEyeConstants.KEY_TIME, mTime.getTime());
            finalData.put(DataEyeConstants.KEY_TIMESTAMP, DataEyeAnalyticsSDK.getTimeFormat());
            finalData.put(DataEyeConstants.KEY_DISTINCT_ID, mDistinctId);
            if (null != mAccountId) {
                finalData.put(DataEyeConstants.KEY_ACCOUNT_ID, mAccountId);
            }

            if (null != mOaid) {
                finalData.put(DataEyeConstants.KEY_OAID, mOaid);
            }

            if (null != mGaid) {
                finalData.put(DataEyeConstants.KEY_GAID, mGaid);
            }

            if (null != mExtraFields) {
                for (Map.Entry<String, String> entry : mExtraFields.entrySet()) {
                    finalData.put(entry.getKey(), entry.getValue());
                }
            }

            if (mType.isTrack()) {
                finalData.put(DataEyeConstants.KEY_EVENT_NAME, eventName);
            }

            Double zoneOffset = mTime.getZoneOffset();
            if (null != zoneOffset) {
                finalData.put(DataEyeConstants.KEY_ZONE_OFFSET, zoneOffset);
            }

            finalData.put(DataEyeConstants.KEY_LIB_VERSION, DataEyeSystemInformation.getLibVersion());
            if (!TextUtils.isEmpty(Build.VERSION.RELEASE)) {
                finalData.put(DataEyeConstants.KEY_OS_VERSION, Build.VERSION.RELEASE);
            }
            String operatorString = DataEyeSystemInformation.getCarrier(context);
            if (!TextUtils.isEmpty(operatorString)) {
                finalData.put(DataEyeConstants.KEY_CARRIER, operatorString);
            }
            DataEyeSystemInformation systemInformation = DataEyeSystemInformation.getInstance(context);
            finalData.put(DataEyeConstants.KEY_NETWORK_TYPE, systemInformation.getNetworkType());

            String systemLanguage = DataEyeSystemInformation.getSystemLanguage();
            if (!TextUtils.isEmpty(systemLanguage)) {
                finalData.put(DataEyeConstants.KEY_SYSTEM_LANGUAGE, systemLanguage);
            }

            if (!TextUtils.isEmpty(systemInformation.getAppVersionName())) {
                finalData.put(DataEyeConstants.KEY_APP_VERSION, systemInformation.getAppVersionName());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void dealCustomProperties(JSONObject finalData) {
        try {

            if (mProperties == null) {
                return;
            }

            moveProperties(finalData, DataEyeConstants.KEY_FIRST_INSTALL_TIME);
            moveProperties(finalData, DataEyeConstants.KEY_RESUME_FROM_BACKGROUND);
            moveProperties(finalData, DataEyeConstants.KEY_CRASH_REASON);
            moveProperties(finalData, DataEyeConstants.KEY_DURATION);
            moveProperties(finalData, DataEyeConstants.TITLE);
            moveProperties(finalData, DataEyeConstants.SCREEN_NAME);


            HashSet<String> keySet = new HashSet<>();
            Iterator<String> keys= mProperties.keys();
            while (keys.hasNext()) {
                keySet.add(keys.next());
            }

            for (String key :keySet) {
                Object value = mProperties.get(key);
                mProperties.remove(key);
                String newKey = key.replace("#", "").toLowerCase();
                mProperties.put(newKey, value);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void moveProperties(JSONObject finalData, String propertiesName) {
        try {

            if (mProperties == null) {
                return;
            }

            if (mProperties.has(propertiesName)) {
                finalData.put(propertiesName, mProperties.get(propertiesName));
                mProperties.remove(propertiesName);
            }
        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    public static void checkDataBeforeUpload(DataEyeConfig config, JSONObject uploadData) {
        try {
            String currentGaid = config.getGAID();
            String dataGaid = uploadData.optString(DataEyeConstants.KEY_GAID);
            if (!TextUtils.isEmpty(currentGaid) && TextUtils.isEmpty(dataGaid)) {
                uploadData.put(DataEyeConstants.KEY_GAID, currentGaid);
            }
        } catch (Exception e) {

        }

        try {
            String currentOaid = config.getOAID();
            String dataOaid = uploadData.optString(DataEyeConstants.KEY_OAID);
            if (!TextUtils.isEmpty(currentOaid) && TextUtils.isEmpty(dataOaid)) {
                uploadData.put(DataEyeConstants.KEY_OAID, currentOaid);
            }
        } catch (Exception e) {

        }
    }
}
