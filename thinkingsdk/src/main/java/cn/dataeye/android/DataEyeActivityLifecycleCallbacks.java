package cn.dataeye.android;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;

import cn.dataeye.android.utils.PropertyUtils;
import cn.dataeye.android.utils.DataEyeConstants;
import cn.dataeye.android.utils.DataEyeUtils;
import cn.dataeye.android.utils.ITime;
import cn.dataeye.android.utils.DataEyeLog;

import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
class DataEyeActivityLifecycleCallbacks implements Application.ActivityLifecycleCallbacks {
    private static final String TAG = "ThinkingAnalytics.ThinkingDataActivityLifecycleCallbacks";
    private boolean resumeFromBackground = false;
    private final Object mActivityLifecycleCallbacksLock = new Object();
    private final DataEyeAnalyticsSDK mThinkingDataInstance;
    private final String mMainProcessName;

    private final List<WeakReference<Activity>> mStartedActivityList = new ArrayList<>();

    private volatile boolean isTrackAppStart = false;

    DataEyeActivityLifecycleCallbacks(DataEyeAnalyticsSDK instance, String mainProcessName) {
        this.mThinkingDataInstance = instance;
        this.mMainProcessName = mainProcessName;
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle bundle) {
    }

    private boolean notStartedActivity(Activity activity, boolean remove) {
        synchronized (mActivityLifecycleCallbacksLock) {
            Iterator<WeakReference<Activity>> it = mStartedActivityList.iterator();
            while (it.hasNext()) {
                WeakReference<Activity> current = it.next();
                if (current.get() == activity) {
                    if (remove) it.remove();
                    return false;
                }
            }
            return true;
        }
    }

    @Override
    public void onActivityStarted(Activity activity) {
        try {
            DataEyeLog.i(TAG, "onActivityStarted: activity = " + activity);
            synchronized (mActivityLifecycleCallbacksLock) {
                if (mStartedActivityList.size() == 0) {
                    try {
                        mThinkingDataInstance.appBecomeActive();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    trackAppStart(activity, null);
                }

                if (notStartedActivity(activity, false)) {
                    mStartedActivityList.add(new WeakReference<>(activity));
                } else {
                    DataEyeLog.w(TAG, "Unexpected state. The activity might not be stopped correctly: " + activity);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void trackAppStart(Context context, ITime time) {
        if (isMainProcess(context)) {
            if (isTrackAppStart) {
                return;
            }
            if (mThinkingDataInstance.isAutoTrackEnabled()) {
                try {
                    if (!mThinkingDataInstance.isAutoTrackEventTypeIgnored(DataEyeAnalyticsSDK.AutoTrackEventType.APP_START)) {

                        JSONObject properties = new JSONObject();
                        properties.put(DataEyeConstants.KEY_RESUME_FROM_BACKGROUND, resumeFromBackground);

                        if (context != null && context instanceof Activity) {
                            DataEyeUtils.getScreenNameAndTitleFromActivity(properties, (Activity) context);
                        }

                        if (null == time) {
                            mThinkingDataInstance.autoTrack(DataEyeConstants.APP_START_EVENT_NAME, properties);
                            isTrackAppStart = true;
                        } else {
                            if (!mThinkingDataInstance.hasDisabled()) {
                                // track APP_START with cached time and properties.
                                JSONObject finalProperties = mThinkingDataInstance.getAutoTrackStartProperties();

                                DataEyeUtils.mergeJSONObject(properties, finalProperties, mThinkingDataInstance.mConfig.getDefaultTimeZone());

                                DataEyeDataDescription dataEyeDataDescription = new DataEyeDataDescription(mThinkingDataInstance, DataEyeConstants.DataType.TRACK, finalProperties, time);
                                dataEyeDataDescription.eventName = DataEyeConstants.APP_START_EVENT_NAME;

                                mThinkingDataInstance.trackInternal(dataEyeDataDescription);
                                isTrackAppStart = true;
                            }
                        }
                    }

                    if (time == null && !mThinkingDataInstance.isAutoTrackEventTypeIgnored(DataEyeAnalyticsSDK.AutoTrackEventType.APP_END)) {
                        mThinkingDataInstance.timeEvent(DataEyeConstants.APP_END_EVENT_NAME);
                    }
                } catch (Exception e) {
                    DataEyeLog.i(TAG, e);
                }
            }
            resumeFromBackground = true;
        }
    }

    @Override
    public void onActivityResumed(Activity activity) {
        DataEyeLog.i(TAG, "onActivityResumed: activity = " + activity);
        synchronized (mActivityLifecycleCallbacksLock) {
            if (notStartedActivity(activity, false)) {
                DataEyeLog.i(TAG, "onActivityResumed: the SDK was initialized after the onActivityStart of " + activity);
                mStartedActivityList.add(new WeakReference<>(activity));
                if (mStartedActivityList.size() == 1) {
                    trackAppStart(activity, mThinkingDataInstance.getAutoTrackStartTime());
                }
            }
        }

        try {
            boolean mShowAutoTrack = true;
            if (mThinkingDataInstance.isActivityAutoTrackAppViewScreenIgnored(activity.getClass())) {
                mShowAutoTrack = false;
            }

            if (mThinkingDataInstance.isAutoTrackEnabled() && mShowAutoTrack && !mThinkingDataInstance.isAutoTrackEventTypeIgnored(DataEyeAnalyticsSDK.AutoTrackEventType.APP_VIEW_SCREEN)) {
                try {
                    JSONObject properties = new JSONObject();
                    properties.put(DataEyeConstants.SCREEN_NAME, activity.getClass().getCanonicalName());
                    DataEyeUtils.getScreenNameAndTitleFromActivity(properties, activity);

                    if (activity instanceof DataEyeScreenAutoTracker) {
                        DataEyeScreenAutoTracker dataEyeScreenAutoTracker = (DataEyeScreenAutoTracker) activity;

                        String screenUrl = dataEyeScreenAutoTracker.getScreenUrl();
                        JSONObject otherProperties = dataEyeScreenAutoTracker.getTrackProperties();
                        if (otherProperties != null && PropertyUtils.checkProperty(otherProperties)) {
                            DataEyeUtils.mergeJSONObject(otherProperties, properties, mThinkingDataInstance.mConfig.getDefaultTimeZone());
                        } else {
                            DataEyeLog.d(TAG, "invalid properties: " + otherProperties);
                        }
                        mThinkingDataInstance.trackViewScreenInternal(screenUrl, properties);
                    } else {
                        DataEyeAutoTrackAppViewScreenUrl autoTrackAppViewScreenUrl = activity.getClass().getAnnotation(DataEyeAutoTrackAppViewScreenUrl.class);
                        if (autoTrackAppViewScreenUrl != null && (TextUtils.isEmpty(autoTrackAppViewScreenUrl.appId()) ||
                                mThinkingDataInstance.getToken().equals(autoTrackAppViewScreenUrl.appId()))) {
                            String screenUrl = autoTrackAppViewScreenUrl.url();
                            if (TextUtils.isEmpty(screenUrl)) {
                                screenUrl = activity.getClass().getCanonicalName();
                            }
                            mThinkingDataInstance.trackViewScreenInternal(screenUrl, properties);
                        } else {
                            mThinkingDataInstance.autoTrack(DataEyeConstants.APP_VIEW_EVENT_NAME, properties);
                        }
                    }
                } catch (Exception e) {
                    DataEyeLog.i(TAG, e);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {
        DataEyeLog.i(TAG, "onActivityPaused: activity = " + activity);
        synchronized (mActivityLifecycleCallbacksLock) {
            if (notStartedActivity(activity, false)) {
                DataEyeLog.i(TAG, "onActivityPaused: the SDK was initialized after the onActivityStart of " + activity);
                mStartedActivityList.add(new WeakReference<>(activity));
                if (mStartedActivityList.size() == 1) {
                    trackAppStart(activity, mThinkingDataInstance.getAutoTrackStartTime());
                }
            }
        }
    }

    void onAppStartEventEnabled(Context context) {
        synchronized (mActivityLifecycleCallbacksLock) {
            if (isAppForeground(context)) {
                if (mStartedActivityList.size() > 0) {
                    context = mStartedActivityList.get(0).get();
                }
                trackAppStart(context, null);
            }
        }
    }

    @Override
    public void onActivityStopped(Activity activity) {
        DataEyeLog.i(TAG, "onActivityStopped: activity = " + activity);
        try {
            synchronized (mActivityLifecycleCallbacksLock) {
                if (notStartedActivity(activity, true)) {
                    DataEyeLog.i(TAG, "onActivityStopped: the SDK might be initialized after the onActivityStart of " + activity);
                    return;
                }
                if (mStartedActivityList.size() == 0) {
                    try {
                        mThinkingDataInstance.appEnterBackground();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if (isMainProcess(activity)) {
                        if (mThinkingDataInstance.isAutoTrackEnabled()) {
                            try {
                                if (!mThinkingDataInstance.isAutoTrackEventTypeIgnored(DataEyeAnalyticsSDK.AutoTrackEventType.APP_END)) {
                                    JSONObject properties = new JSONObject();
                                    DataEyeUtils.getScreenNameAndTitleFromActivity(properties, activity);
                                    mThinkingDataInstance.autoTrack(DataEyeConstants.APP_END_EVENT_NAME, properties);
                                }
                            } catch (Exception e) {
                                DataEyeLog.i(TAG, e);
                            }
                        }
                        isTrackAppStart = false;
                    }
                    try {
                        mThinkingDataInstance.flush();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }


    private String getCurrentProcessName(Context context) {

        try {
            int pid = android.os.Process.myPid();

            ActivityManager activityManager = (ActivityManager) context
                    .getSystemService(Context.ACTIVITY_SERVICE);


            if (activityManager == null) {
                return null;
            }

            List<ActivityManager.RunningAppProcessInfo> runningAppProcessInfoList = activityManager.getRunningAppProcesses();
            if (runningAppProcessInfoList != null) {
                for (ActivityManager.RunningAppProcessInfo appProcess : runningAppProcessInfoList) {

                    if (appProcess != null) {
                        if (appProcess.pid == pid) {
                            return appProcess.processName;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return null;
    }

    private boolean isMainProcess(Context context) {
        if (TextUtils.isEmpty(mMainProcessName)) {
            return true;
        }

        if (context == null) {
            return true;
        }

        String currentProcess = getCurrentProcessName(context.getApplicationContext());
        if (TextUtils.isEmpty(currentProcess) || mMainProcessName.equals(currentProcess)) {
            return true;
        }

        return false;
    }


    private boolean isAppForeground(Context context) {
        if (context == null) {
            return false;
        }

        return isAppForegroundWithRunningTask(context) || isAppForegroundWithRunningAppProcess(context);
    }

    public boolean isAppForegroundWithRunningTask(Context context) {

        ActivityManager am = (ActivityManager) context.getSystemService(Service.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
        if (!tasks.isEmpty()) {
            ComponentName topActivity = tasks.get(0).topActivity;
            if (topActivity.getPackageName().equals(context.getPackageName())) {
                return true;
            }
        }

        return false;
    }

    private static boolean isAppForegroundWithRunningAppProcess(Context context) {
        ActivityManager activityManager =
                (ActivityManager) context.getSystemService(Service.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> runningAppProcessInfoList = activityManager.getRunningAppProcesses();

        if (runningAppProcessInfoList == null) {
            return false;
        }

        for (ActivityManager.RunningAppProcessInfo processInfo : runningAppProcessInfoList) {
            if (processInfo.processName.equals(context.getPackageName())
                    && (processInfo.importance ==
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND)) {
                return true;
            }
        }
        return false;
    }
}
