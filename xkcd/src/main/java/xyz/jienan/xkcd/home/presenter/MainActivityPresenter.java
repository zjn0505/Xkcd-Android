package xyz.jienan.xkcd.home.presenter;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import timber.log.Timber;
import xyz.jienan.xkcd.SharedPrefManager;
import xyz.jienan.xkcd.XkcdModel;
import xyz.jienan.xkcd.XkcdPic;
import xyz.jienan.xkcd.home.contract.MainActivityContract;

public class MainActivityPresenter implements MainActivityContract.Presenter {

    private final SharedPrefManager sharedPrefManager = new SharedPrefManager();

    private MainActivityContract.View view;

    private final XkcdModel xkcdModel = XkcdModel.getInstance();

    private CompositeDisposable compositeDisposable = new CompositeDisposable();

    private Disposable fabShowDisposable;

    public MainActivityPresenter(MainActivityContract.View view) {
        this.view = view;
    }

    @Override
    public void loadLatestXkcd() {
        Disposable d = xkcdModel.loadLatest()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(xkcdPic -> {
                    long latestIndex = xkcdPic.num;
                    sharedPrefManager.setLatest(latestIndex);
                    view.latestXkcdLoaded(xkcdPic);
                }, e -> Timber.e(e, "load xkcd pic error"));
        compositeDisposable.add(d);
    }

    @Override
    public void comicLiked(long index) {
        if (index < 1) {
            return;
        }
        Disposable d = xkcdModel.thumbsUp(index)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(view::showThumbUpCount,
                        e -> Timber.e(e, "Thumbs up failed"));
        compositeDisposable.add(d);
    }

    @Override
    public void comicFavorited(long index, boolean isFav) {
        if (index < 1) {
            return;
        }
        Disposable d = xkcdModel.fav(index, isFav).subscribe(xkcdPic -> {},
                e -> Timber.e(e, "error on get one pic: %d", index));
        compositeDisposable.add(d);
        view.toggleFab(isFav);
    }

    @Override
    public void fastLoad(int latestIndex) {
        if (latestIndex <= 0) {
            return;
        }
        Disposable d = xkcdModel.fastLoad(latestIndex)
                .subscribe(ignore -> Timber.d("Fast load succeed"),
                        e -> Timber.e(e, "Error in fast load"));
        compositeDisposable.add(d);
    }

    @Override
    public void getInfoAndShowFab(int index) {
        if (fabShowDisposable != null && !fabShowDisposable.isDisposed()) {
            fabShowDisposable.dispose();
        }
        XkcdPic xkcdPic = xkcdModel.loadXkcdFromDB(index);
        if (xkcdPic == null) {
            fabShowDisposable = xkcdModel.observe()
                    .filter(xkcdPic1 -> xkcdPic1.num == index)
                    .doOnNext(view::showFab)
                    .subscribe();
            compositeDisposable.add(fabShowDisposable);
        } else {
            view.showFab(xkcdPic);
        }
    }

    @Override
    public void setLatest(int latestIndex) {
        sharedPrefManager.setLatest(latestIndex);
    }

    @Override
    public int getLatest() {
        return (int) sharedPrefManager.getLatest();
    }

    @Override
    public void setLastViewed(int lastViewed) {
        sharedPrefManager.setLastViewed(lastViewed);
    }

    @Override
    public int getLastViewed(int latestIndex) {
        return (int) sharedPrefManager.getLastViewed(latestIndex);
    }

    @Override
    public void onDestroy() {
        compositeDisposable.dispose();
    }
}
