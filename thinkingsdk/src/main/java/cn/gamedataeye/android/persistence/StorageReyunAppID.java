package cn.gamedataeye.android.persistence;

import android.content.SharedPreferences;

import java.util.concurrent.Future;

public class StorageReyunAppID extends SharedPreferencesStorage<String>{
    public StorageReyunAppID(Future<SharedPreferences> loadStoredPreferences) {
        super(loadStoredPreferences,"ReyunAppID");
    }
}
