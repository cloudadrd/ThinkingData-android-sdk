package cn.dataeye.android.persistence;

import android.content.SharedPreferences;

import java.util.concurrent.Future;

public class DataEyeStorageEnableFlag extends DataEyeSharedPreferencesStorage<Boolean> {

    public DataEyeStorageEnableFlag(Future<SharedPreferences> loadStoredPreferences) {
        super(loadStoredPreferences, "enableFlag");
    }

    @Override
    protected void save(SharedPreferences.Editor editor, Boolean data) {
        editor.putBoolean(storageKey, data);
        editor.apply();
    }

    @Override
    protected void load(SharedPreferences sharedPreferences) {
        data = sharedPreferences.getBoolean(this.storageKey, true);
    }
}
