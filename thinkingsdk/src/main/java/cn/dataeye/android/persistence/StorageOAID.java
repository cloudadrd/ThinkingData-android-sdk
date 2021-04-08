package cn.dataeye.android.persistence;

import android.content.SharedPreferences;

import java.util.concurrent.Future;

public class StorageOAID extends SharedPreferencesStorage<String>{
    public StorageOAID(Future<SharedPreferences> loadStoredPreferences) {
        super(loadStoredPreferences,"OAID");
    }
}
