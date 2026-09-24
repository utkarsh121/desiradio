package com.utkarsh.desiradio;

import android.app.Application;

/** Installs the crash log capture as early as possible. */
public class RadioApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashLog.install(this);
    }
}
