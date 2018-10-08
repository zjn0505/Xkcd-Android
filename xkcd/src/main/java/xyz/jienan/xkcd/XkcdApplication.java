package xyz.jienan.xkcd;

import android.app.Application;

import com.github.piasy.biv.BigImageViewer;
import com.google.firebase.messaging.FirebaseMessaging;

import io.objectbox.BoxStore;
import xyz.jienan.xkcd.base.glide.GlideImageLoader;
import xyz.jienan.xkcd.model.MyObjectBox;
import xyz.jienan.xkcd.model.util.XkcdSideloadUtils;

/**
 * Created by Jienan on 2018/3/2.
 */

public class XkcdApplication extends Application {

    private static XkcdApplication mInstance;
    private BoxStore boxStore;

    public static XkcdApplication getInstance() {
        return mInstance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        DebugUtils.init(this);
        mInstance = this;
        boxStore = MyObjectBox.builder().androidContext(this).maxReaders(300).build();
        XkcdSideloadUtils.init(this);
        FirebaseMessaging.getInstance().subscribeToTopic("new_comics");
        FirebaseMessaging.getInstance().subscribeToTopic("new_what_if");
        BigImageViewer.initialize(GlideImageLoader.with(getApplicationContext()));
    }

    public BoxStore getBoxStore() {
        return boxStore;
    }
}
