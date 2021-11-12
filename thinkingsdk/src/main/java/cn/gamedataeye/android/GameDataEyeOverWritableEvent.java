package cn.gamedataeye.android;

import org.json.JSONObject;

import cn.gamedataeye.android.utils.DataEyeConstants;

/**
 * 可被重写的事件，对应 ta_overwrite 操作。
 *
 * 创建 TDOverWritableEvent 对象以重写之前的事件数据。传入 eventId 指定需要被重写的事件。
 */
public class GameDataEyeOverWritableEvent extends GameDataEyeAnalyticsEvent {
    private final String mEventId;
    public GameDataEyeOverWritableEvent(String eventName, JSONObject properties, String eventId) {
        super(eventName, properties);
        mEventId = eventId;
    }

    @Override
    String getExtraField() {
        return DataEyeConstants.KEY_EVENT_ID;
    }

    @Override
    String getExtraValue() {
        return mEventId;
    }

    @Override
    DataEyeConstants.DataType getDataType() {
        return DataEyeConstants.DataType.TRACK_OVERWRITE;
    }
}
