package cn.gamedataeye.android;

import org.json.JSONObject;

import java.util.Date;
import java.util.TimeZone;

import cn.gamedataeye.android.utils.DataEyeConstants;

abstract class GameDataEyeAnalyticsEvent {
    private final String mEventName;
    private final JSONObject mProperties;
    private Date mEventTime;
    private TimeZone mTimeZone;

    GameDataEyeAnalyticsEvent(String eventName, JSONObject properties) {
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
