package cn.dataeye.android;

import org.json.JSONException;
import org.json.JSONObject;

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
    }

    void setNoCache() {
        this.saveData = false;
    }

    /**
     * 获取数据，可能会阻塞，不要在主线程中调用
     * @return 待上报数据
     */
    public JSONObject get() {
        JSONObject finalData = new JSONObject();

        try {
            finalData.put(DataEyeConstants.KEY_TYPE, mType.getType());
            // 有可能会阻塞
            finalData.put(DataEyeConstants.KEY_TIME, mTime.getTime());
            finalData.put(DataEyeConstants.KEY_DISTINCT_ID, mDistinctId);
            if (null != mAccountId) {
                finalData.put(DataEyeConstants.KEY_ACCOUNT_ID, mAccountId);
            }

            if (null != mExtraFields) {
                for (Map.Entry<String, String> entry : mExtraFields.entrySet()) {
                    finalData.put(entry.getKey(), entry.getValue());
                }
            }

            if (mType.isTrack()) {
                finalData.put(DataEyeConstants.KEY_EVENT_NAME, eventName);
                Double zoneOffset = mTime.getZoneOffset();
                if (null != zoneOffset) {
                    mProperties.put(DataEyeConstants.KEY_ZONE_OFFSET, zoneOffset);
                }
            }

            finalData.put(DataEyeConstants.KEY_PROPERTIES, mProperties);

        } catch (JSONException e) {

            e.printStackTrace();
        }

        return finalData;
    }
}
