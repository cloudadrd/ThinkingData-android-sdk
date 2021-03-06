package cn.dataeye.android.persistence;

import android.content.SharedPreferences;

import java.util.concurrent.Future;

public class DataEyeStorageFlushInterval extends DataEyeSharedPreferencesStorage<Integer> {
    private final int mDefaultFlushInterval;
    public DataEyeStorageFlushInterval(Future<SharedPreferences> loadStoredPreferences, int defaultFlushInterval) {
        super(loadStoredPreferences, "flushInterval");
        mDefaultFlushInterval = defaultFlushInterval;
    }

    @Override
    void save(SharedPreferences.Editor editor, Integer interval) {
        editor.putInt(storageKey, interval);
        editor.apply();
    }

    /* 触发上传时间间隔，单位毫秒 */
    void load(SharedPreferences sharedPreferences) {
        data = sharedPreferences.getInt(this.storageKey, mDefaultFlushInterval);
    }
}
