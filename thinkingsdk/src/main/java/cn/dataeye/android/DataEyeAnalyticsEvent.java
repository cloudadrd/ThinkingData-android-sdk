package cn.dataeye.android;

import org.json.JSONObject;

import java.util.Date;
import java.util.TimeZone;

import cn.dataeye.android.utils.DataEyeConstants;

abstract class DataEyeAnalyticsEvent {
    private final String mEventName;
    private final JSONObject mProperties;
    private Date mEventTime;
    private TimeZone mTimeZone;

    DataEyeAnalyticsEvent(String eventName, JSONObject properties) {
        mEventName = eventName;
        mProperties = properties;
    }

    public void setEventTime(Date time) {
        mEventTime = time;
    }

    public void setEventTime(Date time, TimeZone timeZone) {
        mEventTime = time;
        mTimeZone = timeZone;
    }

    abstract String getExtraField();

    abstract String getExtraValue();

    abstract DataEyeConstants.DataType getDataType();

    String getEventName() {
        return mEventName;
    }

    JSONObject getProperties() {
        return mProperties;
    }

    Date getEventTime() {
        return mEventTime;
    }

    TimeZone getTimeZone() {
        return mTimeZone;
    }
}
