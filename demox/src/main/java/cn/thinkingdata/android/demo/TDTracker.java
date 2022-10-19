package cn.thinkingdata.android.demo;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

import cn.dataeye.android.DataEyeAnalyticsSDK;

public class TDTracker {
    /**
     * 项目APP_ID，在申请项目时会给出
     */
    private static final String TA_APP_ID = "12552a8a2bd540a1af3748aac990903d";
    private static final String TA_APP_ID_DEBUG = "12552a8a2bd540a1af3748aac990903d";

    /**
     * 数据上传地址
     * 如果您使用的是云服务，请输入以下URL:
     * http://receiver.ta.thinkingdata.cn:9080
     * 如果您使用的是私有化部署的版本，请输入以下URL:
     * http://数据采集地址:9080
     */
//    private static final String TA_SERVER_URL = "http://172.31.4.170:8080/v1/sdk/report";
//    private static final String TA_SERVER_URL = "http://deapi.adsgreat.cn/v1/sdk/report";
    private static final String TA_SERVER_URL = "http://172.30.139.126/v1/sdk/encrypted_report";

    private static DataEyeAnalyticsSDK mInstance;
    private static DataEyeAnalyticsSDK mDebugInstance;
    private static DataEyeAnalyticsSDK mLightInstance;

    public static DataEyeAnalyticsSDK getInstance() {
        return mInstance;
    }

    public static DataEyeAnalyticsSDK getLightInstance() {
        return mLightInstance;
    }

    public static DataEyeAnalyticsSDK getDebugInstance() {
        return mDebugInstance;
    }

    /**
     * 仅在自动测试中使用，Demo App 自身不会调用此函数
     * @param instance
     * @param debugInstance
     */
    public static void initThinkingDataSDK(DataEyeAnalyticsSDK instance, DataEyeAnalyticsSDK debugInstance) {
        mInstance = instance;
        mDebugInstance = debugInstance;
        setUp();
    }

    /** 初始化 TA SDK */
    static void initThinkingDataSDK(Context context) {
        Context mContext = context.getApplicationContext();
        mInstance = DataEyeAnalyticsSDK.sharedInstance(mContext, "1007", TA_SERVER_URL);//TA_APP_ID
        mDebugInstance = DataEyeAnalyticsSDK.sharedInstance(mContext, TA_APP_ID_DEBUG, TA_SERVER_URL);
        setUp();
    }

    private static void setUp() {

        //Log.d("ThinkingDataDemo","get distinct id: " + ThinkingAnalyticsSDK.sharedInstance(this).getDistinctId());

        // set distinct id
        mInstance.identify("instance_id");
//        mDebugInstance.identify("debug_instance_id");
//        mLightInstance = mInstance.createLightInstance();
        mInstance.reyunAppID("123456");

        // enable auto track
//        List<DataEyeAnalyticsSDK.AutoTrackEventType> eventTypeList = new ArrayList<>();
//        eventTypeList.add(DataEyeAnalyticsSDK.AutoTrackEventType.APP_INSTALL);
//        eventTypeList.add(DataEyeAnalyticsSDK.AutoTrackEventType.APP_START);
//        eventTypeList.add(DataEyeAnalyticsSDK.AutoTrackEventType.APP_END);
//        eventTypeList.add(DataEyeAnalyticsSDK.AutoTrackEventType.APP_VIEW_SCREEN);
//        eventTypeList.add(ThinkingAnalyticsSDK.AutoTrackEventType.APP_CLICK);
//        eventTypeList.add(ThinkingAnalyticsSDK.AutoTrackEventType.APP_CRASH);
//        mDebugInstance.enableAutoTrack(eventTypeList);

        List<DataEyeAnalyticsSDK.AutoTrackEventType> eventTypeList1 = new ArrayList<>();
        eventTypeList1.add(DataEyeAnalyticsSDK.AutoTrackEventType.APP_INSTALL);
        eventTypeList1.add(DataEyeAnalyticsSDK.AutoTrackEventType.APP_START);
        eventTypeList1.add(DataEyeAnalyticsSDK.AutoTrackEventType.APP_END);
//        eventTypeList1.add(DataEyeAnalyticsSDK.AutoTrackEventType.APP_VIEW_SCREEN);
//        eventTypeList1.add(ThinkingAnalyticsSDK.AutoTrackEventType.APP_CLICK);
//        eventTypeList1.add(ThinkingAnalyticsSDK.AutoTrackEventType.APP_CRASH);
        mInstance.enableAutoTrack(eventTypeList1);

        //// enable fragment auto track
        ////mInstance.trackFragmentAppViewScreen();
        //mDebugInstance.trackFragmentAppViewScreen();

        // 设置动态属性
        //mInstance.setDynamicSuperPropertiesTracker(
        //        new ThinkingAnalyticsSDK.DynamicSuperPropertiesTracker() {
        //    @Override
        //    public JSONObject getDynamicSuperProperties() {
        //        JSONObject dynamicSuperProperties = new JSONObject();
        //        String pattern = "yyyy-MM-dd HH:mm:ss.SSS";
        //        SimpleDateFormat sDateFormat = new SimpleDateFormat(pattern, Locale.CHINA);
        //        String timeString = sDateFormat.format(new Date());
        //        try {
        //            dynamicSuperProperties.put("dynamicTime", timeString);
        //        } catch (JSONException e) {
        //            e.printStackTrace();
        //        }
        //        return dynamicSuperProperties;
        //    }
        //});
    }
}
