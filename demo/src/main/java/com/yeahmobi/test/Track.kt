package com.yeahmobi.test

import android.content.Context
import android.util.Log
import cn.dataeye.android.DataEyeAnalyticsSDK
import cn.dataeye.android.DataEyeAnalyticsSDK.AutoTrackEventType
import cn.dataeye.android.DataEyeConfig
import cn.dataeye.android.utils.DataEyeLog
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*

object Track {

    private const val TAG = "TrackTAG"

    private const val TA_APP_ID = "9999"


    // 海外
//    private const val TA_SERVER_URL = "https://deapi.gmoneygame.xyz/v1/sdk/report"

    // 国内
    private const val TA_SERVER_URL = "http://deapi.adsgreat.cn/v1/sdk/report"

    private lateinit var instance: DataEyeAnalyticsSDK
    private lateinit var config: DataEyeConfig

    fun init(context: Context) {
        DataEyeLog.setEnableLog(true)

        config = DataEyeConfig.getInstance(context, TA_APP_ID, TA_SERVER_URL)
        config.isEnableEncrypt = false;
        config.defaultTimeZone
        instance = DataEyeAnalyticsSDK.sharedInstance(config)
        setAutoEvent()

        setIgnoreAppView()
    }

    private fun setAutoEvent() {

        val eventTypeList1: MutableList<AutoTrackEventType> = ArrayList()
        eventTypeList1.add(AutoTrackEventType.APP_INSTALL)
        eventTypeList1.add(AutoTrackEventType.APP_START)
        eventTypeList1.add(AutoTrackEventType.APP_END)
        eventTypeList1.add(AutoTrackEventType.APP_VIEW_SCREEN);
        eventTypeList1.add(AutoTrackEventType.APP_CLICK);
        eventTypeList1.add(AutoTrackEventType.APP_CRASH);
        instance.enableAutoTrack(eventTypeList1)
    }

    private fun setIgnoreAppView() {
        instance.ignoreAutoTrackActivity(MainActivity::class.java)
    }

    fun track() {
        val properties = JSONObject()
        try {
            properties.put("KEY_STRING", "A string value")
            properties.put("KEY_DATE", Date())
            properties.put("KEY_BOOLEAN", true)
            properties.put("KEY_DOUBLE", 56.17)
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        instance.track("test_event", properties)
    }

    fun setSuperProperties() {
        try {
            val superProperties = JSONObject()
            superProperties.put("channel", "ta") //字符串
            superProperties.put("age", 1) //数字
            superProperties.put("isSuccess", true) //布尔
            superProperties.put("birthday", Date()) //时间

            val jsonObject = JSONObject()
            jsonObject.put("key", "value")
            superProperties.put("object", jsonObject) //对象

            val object1 = JSONObject()
            object1.put("key", "value")
            val arr = JSONArray()
            arr.put(object1)
            superProperties.put("object_arr", arr) //对象组

            instance.setSuperProperties(superProperties)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    fun delSuperProperties(name: String) {
        instance.unsetSuperProperty(name)
    }

    fun cleanSuperProperties() {
        instance.clearSuperProperties()
    }

    fun getSuperProperties() {
        Log.d(TAG, "getSuperProperties: ${instance.superProperties}")
    }

    var coin = 0
    fun setDynamicSuperProperties() {
        instance.setDynamicSuperPropertiesTracker {
            coin++ //金币数目频繁更新

            val dynamicSuperProperties = JSONObject()
            try {

                dynamicSuperProperties.put("coin", coin)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            return@setDynamicSuperPropertiesTracker dynamicSuperProperties
        }
    }

    fun testEventDurationStart() {
        instance.timeEvent("stay_shop")
    }

    fun testEventDurationEnd() {
        try {
            instance.track("stay_shop")
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    fun testUserSet() {
        try {
            //此时username为TA
            val properties = JSONObject()
            properties.put("username", "TA")
            instance.user_set(properties)

        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    fun getDeviceId() {
        val deviceId = instance.deviceId
        Log.d(TAG, "getDeviceId: deviceId = $deviceId")
    }

    fun setTimeZone() {
        config.defaultTimeZone = TimeZone.getTimeZone("UTC")
    }

    fun deleteTimeZone() {
        config.defaultTimeZone = null
    }

    fun getLocal() {

    }

    fun flush() {
        instance.flush()
    }
}