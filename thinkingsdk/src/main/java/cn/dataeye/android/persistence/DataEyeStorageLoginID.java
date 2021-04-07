package cn.dataeye.android.persistence;

import android.content.SharedPreferences;

import java.util.concurrent.Future;

public class DataEyeStorageLoginID extends DataEyeSharedPreferencesStorage<String> {
    public DataEyeStorageLoginID(Future<SharedPreferences> loadStoredPreferences) {
        super(loadStoredPreferences,"loginID");
    }
}
