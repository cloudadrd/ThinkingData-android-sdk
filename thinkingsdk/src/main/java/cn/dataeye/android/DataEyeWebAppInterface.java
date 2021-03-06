package cn.dataeye.android;

import android.text.TextUtils;
import android.webkit.JavascriptInterface;

import org.json.JSONException;
import org.json.JSONObject;

import cn.dataeye.android.utils.DataEyeLog;

public class DataEyeWebAppInterface {
    private final static String TAG = "ThinkingAnalytics.TDWebAppInterface";

    // if no exist instance has the same token with H5 data, the data will be tracked to default instance.
    private final DataEyeAnalyticsSDK defaultInstance;

    // for internal use to identify whether the data has been tracked.
    private class TrackFlag {
        private boolean tracked;

        void tracked() {
            tracked = true;
        }

        boolean shouldTrack() {
            return !tracked;
        }
    }

    DataEyeWebAppInterface(DataEyeAnalyticsSDK instance) {
        defaultInstance = instance;
    }

    @JavascriptInterface
    public void thinkingdata_track(final String event) {
        if (TextUtils.isEmpty(event)) {
            return;
        }

        DataEyeLog.d(TAG, event);

        try {
            JSONObject eventData = new JSONObject(event);
            final String token = eventData.getString("#app_id");
            final TrackFlag flag = new TrackFlag();

            DataEyeAnalyticsSDK.allInstances(new DataEyeAnalyticsSDK.InstanceProcessor() {
                @Override
                public void process(DataEyeAnalyticsSDK instance) {
                    if (instance.getToken().equals(token)) {
                        flag.tracked();
                        instance.trackFromH5(event);
                    }
                }
            });

            // if the H5 data could is not match with any instance, track trough default instance
            if (flag.shouldTrack()) {
                defaultInstance.trackFromH5(event);
            }
        } catch (JSONException e) {
            DataEyeLog.w(TAG, "Unexpected exception occurred: " + e.toString());
        }

    }
}
