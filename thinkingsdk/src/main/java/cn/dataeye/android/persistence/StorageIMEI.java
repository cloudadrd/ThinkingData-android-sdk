package cn.dataeye.android.persistence;

import android.content.SharedPreferences;

import java.util.concurrent.Future;

public class StorageIMEI extends SharedPreferencesStorage<String>{
    public StorageIMEI(Future<SharedPreferences> loadStoredPreferences) {
        super(loadStoredPreferences,"IMEI");
    }
}
