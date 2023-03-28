package cn.thinkingdata.android.utils;

import android.os.SystemClock;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class DataEyeTimeCalibrated implements ITime {
    private long mSystemElapsedRealtime;
    private TimeZone mTimeZone;
    private ICalibratedTime mCalibratedTime;

    private Date mDate;

    public DataEyeTimeCalibrated(ICalibratedTime calibratedTime, TimeZone timeZone) {
        mCalibratedTime = calibratedTime;
        mTimeZone = timeZone;
        mSystemElapsedRealtime = SystemClock.elapsedRealtime();
    }

    private synchronized Date getDate() {
        if (null == mDate) {
            mDate = mCalibratedTime.get(mSystemElapsedRealtime);
        }
        return mDate;
    }

    @Override
    public String getTime() {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat(DataEyeConstants.TIME_PATTERN, Locale.CHINA);
            dateFormat.setTimeZone(mTimeZone);
            return dateFormat.format(getDate());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public Double getZoneOffset() {
        return DataEyeUtils.getTimezoneOffset(getDate().getTime(), mTimeZone);
    }
}
