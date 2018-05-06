package xyz.jienan.xkcd.home.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.github.chrisbanes.photoview.PhotoView;
import com.github.piasy.biv.view.BigImageView;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.objectbox.Box;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import timber.log.Timber;
import xyz.jienan.xkcd.R;
import xyz.jienan.xkcd.XkcdApplication;
import xyz.jienan.xkcd.XkcdPic;
import xyz.jienan.xkcd.XkcdSideloadUtils;
import xyz.jienan.xkcd.base.network.NetworkService;

import static xyz.jienan.xkcd.Const.FIRE_COMIC_ID;
import static xyz.jienan.xkcd.Const.FIRE_COMIC_URL;
import static xyz.jienan.xkcd.Const.FIRE_DETAIL_PAGE;
import static xyz.jienan.xkcd.Const.FIRE_LARGE_IMAGE;

/**
 * Created by jienanzhang on 09/07/2017.
 */

public class ImageDetailPageActivity extends Activity {
    @BindView(R.id.photo_view)
    PhotoView photoView;
    @BindView(R.id.big_image_view)
    BigImageView bigImageView;
    @BindView(R.id.pb_loading)
    ProgressBar pbLoading;
    private int index;
    private Box<XkcdPic> box;
    private CompositeDisposable compositeDisposable = new CompositeDisposable();
    private FirebaseAnalytics mFirebaseAnalytics;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
        setContentView(R.layout.activity_image_detail);
        ButterKnife.bind(this);
        box = ((XkcdApplication) getApplication()).getBoxStore().boxFor(XkcdPic.class);
        String url = getIntent().getStringExtra("URL");
        index = (int) getIntent().getLongExtra("ID", 0L);
        photoView.setMaximumScale(10);
        if (!TextUtils.isEmpty(url)) {
            renderPic(url);
        } else if (index != 0) {
            requestImage();
        } else {
            Timber.e("No valid info for detail page");
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.fadein, R.anim.fadeout);
    }

    @Override
    protected void onDestroy() {
        if (photoView.getVisibility() == View.VISIBLE) {
            Glide.clear(photoView);
        }
        compositeDisposable.dispose();
        super.onDestroy();
    }

    private void renderPic(String url) {
        Bundle bundle = new Bundle();
        if (XkcdSideloadUtils.useLargeImageView(index)) {
            bigImageView.showImage(Uri.parse(url));
            bigImageView.setVisibility(View.VISIBLE);
            photoView.setVisibility(View.GONE);
            bundle.putBoolean(FIRE_LARGE_IMAGE, true);
        } else {
            Glide.with(this)
                    .load(url)
                    .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                    .listener(new RequestListener<String, GlideDrawable>() {
                        @Override
                        public boolean onException(Exception e, String model, Target<GlideDrawable> target, boolean isFirstResource) {
                            pbLoading.setVisibility(View.GONE);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(GlideDrawable resource, String model, Target<GlideDrawable> target, boolean isFromMemoryCache, boolean isFirstResource) {
                            pbLoading.setVisibility(View.GONE);
                            return false;
                        }
                    }).into(photoView);
            bigImageView.setVisibility(View.GONE);
            photoView.setVisibility(View.VISIBLE);
            bundle.putBoolean(FIRE_LARGE_IMAGE, false);
        }
        bundle.putInt(FIRE_COMIC_ID, index);
        bundle.putString(FIRE_COMIC_URL, url);
        mFirebaseAnalytics.logEvent(FIRE_DETAIL_PAGE, bundle);
        final View.OnClickListener listener = v -> {
            ImageDetailPageActivity.this.finish();
            ImageDetailPageActivity.this.overridePendingTransition(R.anim.fadein, R.anim.fadeout);
        };
        compositeDisposable.add(Observable.timer(500, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread()).subscribe(ignored -> {
                    photoView.setOnClickListener(listener);
                    bigImageView.setOnClickListener(listener);
                }));
    }

    private void requestImage() {
        Disposable d = NetworkService.getXkcdAPI()
                .getComics(String.valueOf(index))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe(ignored -> pbLoading.setVisibility(View.VISIBLE))
                .subscribe(resXkcdPic -> {
                    XkcdPic xkcdPic = box.get(resXkcdPic.num);
                    if (xkcdPic != null) {
                        resXkcdPic.isFavorite = xkcdPic.isFavorite;
                        resXkcdPic.hasThumbed = xkcdPic.hasThumbed;
                    }
                    box.put(resXkcdPic);
                    renderPic(resXkcdPic.getTargetImg());
                }, e -> Timber.e(e, "Request pic in detail page error, %d", index));
        compositeDisposable.add(d);
    }
}
