package cn.dataeye.android.utils;

/**
 * ITime with constant value.
 */
public class DataEyeTimeConstant implements ITime {

    private final String mTimeString;
    private final Double mZoneOffset;

    public DataEyeTimeConstant(String timeString, Double zoneOffset) {
        mTimeString = timeString;
        mZoneOffset = zoneOffset;
    }

    @Override
    public String getTime() {
        return mTimeString;
    }

    @Override
    public Double getZoneOffset() {
        return mZoneOffset;
    }
}
