package cn.thinkingdata.android;

import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

import cn.thinkingdata.android.utils.ITime;
import cn.thinkingdata.android.utils.TDConstants;

/**
 * TD 数据类
 */
class DataDescription {
    private static final boolean SAVE_TO_DATABASE = true;

    String eventName; // 事件名称，如果有

    // 数据时间, #time 字段
    private final ITime mTime;
    // 数据类型
    final TDConstants.DataType mType;

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

    DataDescription(ThinkingAnalyticsSDK instance, TDConstants.DataType type, JSONObject properties, ITime time) {
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
    public JSONObject get() {
        JSONObject finalData = new JSONObject();

        try {
            finalData.put(TDConstants.KEY_TYPE, mType.getType());
            // 有可能会阻塞
            finalData.put(TDConstants.KEY_TIME, mTime.getTime());
            finalData.put(TDConstants.KEY_TIMESTAMP, ThinkingAnalyticsSDK.getTimeFormat());
            finalData.put(TDConstants.KEY_DISTINCT_ID, mDistinctId);
            if (null != mAccountId) {
                finalData.put(TDConstants.KEY_ACCOUNT_ID, mAccountId);
            }

            if (null != mOaid) {
                finalData.put(TDConstants.KEY_OAID, mOaid);
            }

            if (null != mGaid) {
                finalData.put(TDConstants.KEY_GAID, mGaid);
            }

            if (null != mExtraFields) {
                for (Map.Entry<String, String> entry : mExtraFields.entrySet()) {
                    finalData.put(entry.getKey(), entry.getValue());
                }
            }

            if (mType.isTrack()) {
                finalData.put(TDConstants.KEY_EVENT_NAME, eventName);
                Double zoneOffset = mTime.getZoneOffset();
                if (null != zoneOffset) {
                    mProperties.put(TDConstants.KEY_ZONE_OFFSET, zoneOffset);
                }
            }

            finalData.put(TDConstants.KEY_PROPERTIES, mProperties);

        } catch (JSONException e) {

            e.printStackTrace();
        }

        return finalData;
    }

    public static void checkDataBeforeUpload(TDConfig config, JSONObject uploadData) {
        try {
            String currentGaid = config.getGAID();
            String dataGaid = uploadData.optString(TDConstants.KEY_GAID);
            if (!TextUtils.isEmpty(currentGaid) && TextUtils.isEmpty(dataGaid)) {
                uploadData.put(TDConstants.KEY_GAID, currentGaid);
            }
        } catch (Exception e) {

        }

        try {
            String currentOaid = config.getOAID();
            String dataOaid = uploadData.optString(TDConstants.KEY_OAID);
            if (!TextUtils.isEmpty(currentOaid) && TextUtils.isEmpty(dataOaid)) {
                uploadData.put(TDConstants.KEY_OAID, currentOaid);
            }
        } catch (Exception e) {

        }
    }
}
