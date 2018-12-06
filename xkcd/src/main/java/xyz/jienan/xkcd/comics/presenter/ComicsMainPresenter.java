package xyz.jienan.xkcd.comics.presenter;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import timber.log.Timber;
import xyz.jienan.xkcd.comics.contract.ComicsMainContract;
import xyz.jienan.xkcd.model.XkcdModel;
import xyz.jienan.xkcd.model.XkcdPic;
import xyz.jienan.xkcd.model.persist.SharedPrefManager;

public class ComicsMainPresenter implements ComicsMainContract.Presenter {

    private final SharedPrefManager sharedPrefManager = new SharedPrefManager();
    private final XkcdModel xkcdModel = XkcdModel.getInstance();
    private ComicsMainContract.View view;
    private CompositeDisposable compositeDisposable = new CompositeDisposable();

    private Disposable fabShowDisposable;

    private Disposable searchDisposable = Disposables.empty();

    public ComicsMainPresenter(ComicsMainContract.View view) {
        this.view = view;
    }

    @Override
    public void loadLatest() {
        Disposable d = xkcdModel.loadLatest()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(xkcdPic -> {
                    long latestIndex = xkcdPic.num;
                    sharedPrefManager.setLatestXkcd(latestIndex);
                    view.latestXkcdLoaded(xkcdPic);
                }, e -> Timber.e(e, "load xkcd pic error"));
        compositeDisposable.add(d);
    }

    @Override
    public void liked(long index) {
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
    public void favorited(long index, boolean isFav) {
        if (index < 1) {
            return;
        }
        Disposable d = xkcdModel.fav(index, isFav).subscribe(xkcdPic -> {
                },
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
                    .subscribe(view::showFab,
                            e -> Timber.e("pic pipeline observing error"));
            compositeDisposable.add(fabShowDisposable);
        } else {
            view.showFab(xkcdPic);
        }
    }

    @Override
    public int getLatest() {
        return (int) sharedPrefManager.getLatestXkcd();
    }

    @Override
    public void setLatest(int latestIndex) {
        sharedPrefManager.setLatestXkcd(latestIndex);
    }

    @Override
    public void setLastViewed(int lastViewed) {
        sharedPrefManager.setLastViewedXkcd(lastViewed);
    }

    @Override
    public int getLastViewed(int latestIndex) {
        return (int) sharedPrefManager.getLastViewedXkcd(latestIndex);
    }

    @Override
    public void onDestroy() {
        compositeDisposable.dispose();
    }


    @Override
    public void searchContent(String query) {

        if (!searchDisposable.isDisposed()) {
            searchDisposable.dispose();
        }

        final boolean isNumQuery = isNumQuery(query);

        final XkcdPic numPic = isNumQuery ? xkcdModel.loadXkcdFromDB(Long.parseLong(query)) : null;

        searchDisposable = xkcdModel.search(query)
                .startWith(numPic != null ?
                        Observable.just(numPic).toList().toObservable() : Observable.empty())
                .debounce(200, TimeUnit.MILLISECONDS)
                .map(list -> {
                    if (isNumQuery) {
                        long num = Long.parseLong(query);
                        XkcdPic matchedPic = null;
                        if (numPic != null && list.contains(numPic)) {
                            matchedPic = numPic;
                        } else {
                            for (XkcdPic pic : list) {
                                if (pic.num == num) {
                                    matchedPic = pic;
                                    break;
                                }
                            }
                        }
                        if (matchedPic != null) {
                            list.remove(matchedPic);
                            list.add(0, matchedPic);
                        }
                    }
                    return list;
                })
                .filter(xkcdPics -> xkcdPics != null && !xkcdPics.isEmpty())
                .distinctUntilChanged()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(view::renderXkcdSearch,
                        e -> {
                            Timber.e(e, "search error");
                            if (numPic != null) {
                                view.renderXkcdSearch(Collections.singletonList(numPic));
                            }
                        });
        compositeDisposable.add(searchDisposable);
    }

    @Override
    public long getRandomUntouchedIndex() {
        final List<XkcdPic> list = xkcdModel.getUntouchedList();
        if (list.isEmpty()) {
            return 0;
        } else {
            return list.get(new Random().nextInt(list.size())).num;
        }
    }

    private boolean isNumQuery(String query) {
        try {
            long num = Long.parseLong(query);
            return num > 0 && num <= sharedPrefManager.getLatestXkcd();
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
