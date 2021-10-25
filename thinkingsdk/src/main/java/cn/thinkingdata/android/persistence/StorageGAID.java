package cn.thinkingdata.android.persistence;

import android.content.SharedPreferences;

import java.util.concurrent.Future;

public class StorageGAID extends SharedPreferencesStorage<String>{
    public StorageGAID(Future<SharedPreferences> loadStoredPreferences) {
        super(loadStoredPreferences,"IMEI");
    }
}
