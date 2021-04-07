package cn.dataeye.android.persistence;

import android.content.SharedPreferences;

import java.util.UUID;
import java.util.concurrent.Future;

public class DataEyeStorageRandomID extends DataEyeSharedPreferencesStorage<String> {
    public DataEyeStorageRandomID(Future<SharedPreferences> loadStoredPreferences) {
        super(loadStoredPreferences, "randomID");
    }

    @Override
    String create() {
        return UUID.randomUUID().toString();
    }
}