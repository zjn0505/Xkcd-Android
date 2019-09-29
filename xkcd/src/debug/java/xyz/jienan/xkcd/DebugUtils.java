package xyz.jienan.xkcd;

import android.app.Application;
import android.content.Context;

import com.gu.toolargetool.TooLargeTool;

import io.objectbox.BoxStore;
import io.objectbox.android.AndroidObjectBrowser;
import timber.log.Timber;

public class DebugUtils {
    public static boolean init() {
        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }
        return true;
    }

    static void debugDB(Context context, BoxStore boxStore) {
        if (BuildConfig.DEBUG) {
            new AndroidObjectBrowser(boxStore).start(context);

            TooLargeTool.startLogging((Application) context);
        }
    }
}
