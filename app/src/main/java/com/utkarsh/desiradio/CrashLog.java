package com.utkarsh.desiradio;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Captures uncaught crashes to a file under the app's internal files dir
 * (files/crashes/). Not externally visible — a future in-app diagnostics
 * screen could let the user view/share these logs.
 */
public final class CrashLog {
    private CrashLog() {}

    public static void install(Context context) {
        final Context app = context.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                write(app, thread, error);
            } catch (Exception ignored) {
            }
            if (previous != null) {
                previous.uncaughtException(thread, error);
            }
        });
    }

    /** Appends a tagged non-fatal error to files/crashes/ for later diagnosis. */
    public static void log(Context context, String tag, Throwable error) {
        try {
            Context app = context.getApplicationContext();
            File dir = new File(app.getFilesDir(), "crashes");
            if (!dir.exists() && !dir.mkdirs()) return;
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                    .format(new Date());
            File out = new File(dir, "log-" + stamp + ".txt");
            try (PrintWriter w = new PrintWriter(new FileWriter(out, true))) {
                w.println("time=" + new Date() + " tag=" + tag);
                error.printStackTrace(w);
            }
        } catch (Exception ignored) {
        }
    }

    private static void write(Context app, Thread thread, Throwable error)
            throws Exception {
        File dir = new File(app.getFilesDir(), "crashes");
        if (!dir.exists() && !dir.mkdirs()) return;
        // Keep only the newest few logs.
        File[] existing = dir.listFiles();
        if (existing != null && existing.length >= 5) {
            java.util.Arrays.sort(existing,
                    (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
            for (int i = 0; i <= existing.length - 5; i++) {
                existing[i].delete();
            }
        }
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                .format(new Date());
        File out = new File(dir, "crash-" + stamp + ".txt");
        try (PrintWriter w = new PrintWriter(new FileWriter(out))) {
            w.println("time=" + new Date());
            w.println("thread=" + thread.getName());
            w.println("android=" + Build.VERSION.RELEASE
                    + " sdk=" + Build.VERSION.SDK_INT
                    + " device=" + Build.MANUFACTURER + " " + Build.MODEL);
            w.println("app=DesiRadio v1.2 (3)");
            error.printStackTrace(w);
            Throwable cause = error.getCause();
            while (cause != null) {
                w.println("Caused by:");
                cause.printStackTrace(w);
                cause = cause.getCause();
            }
        }
    }
}
