package cn.dataeye.android.persistence;

import android.content.SharedPreferences;

import java.util.concurrent.Future;

public class StorageLastInstallTime extends SharedPreferencesStorage<String> {
    public StorageLastInstallTime(Future<SharedPreferences> loadStoredPreferences) {
        super(loadStoredPreferences, "DataEye_Last_Install_Time");
    }

    public void putLong(long data) {

        try {
            super.put(Long.toString(data));
        } catch (Exception e) {

        }
    }

    public long getLong() {
        try {
            return Long.parseLong(super.get());
        } catch (Exception e) {

        }
        return 0;
    }
}
