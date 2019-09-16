package xyz.jienan.xkcd.comics.presenter

import android.content.SharedPreferences
import android.graphics.Bitmap
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import xyz.jienan.xkcd.Const.PREF_XKCD_TRANSLATION
import xyz.jienan.xkcd.comics.contract.SingleComicContract
import xyz.jienan.xkcd.model.XkcdModel
import xyz.jienan.xkcd.model.XkcdPic
import xyz.jienan.xkcd.model.persist.SharedPrefManager

class SingleComicPresenter(private val view: SingleComicContract.View, private val sharedPreferences: SharedPreferences) : SingleComicContract.Presenter, SharedPreferences.OnSharedPreferenceChangeListener {

    private val compositeDisposable = CompositeDisposable()

    override val showLocalXkcd: Boolean
        get() = sharedPreferences.getBoolean(PREF_XKCD_TRANSLATION, false)
                && XkcdModel.localizedUrl.isNotBlank()

    var translationSwitch = false

    init {
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    private var index : Int? = null

    override fun getExplain(index: Long) {
        val latestIndex = SharedPrefManager.latestXkcd

        val d = XkcdModel.loadExplain(index, latestIndex)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ view.explainLoaded(it) }, { e ->
                    view.explainFailed()
                    Timber.e(e, "Load explainUrl failed")
                })
        compositeDisposable.add(d)
    }

    override fun loadXkcd(index: Int, forceOriginal: Boolean) {
        this.index = index
        val latestIndex = SharedPrefManager.latestXkcd
        val xkcdPicInDB = XkcdModel.loadXkcdFromDB(index.toLong())
        val shouldQueryNetwork = latestIndex - index < 10
        view.setLoading(true)

        if (shouldQueryNetwork || xkcdPicInDB == null) {
            XkcdModel.loadXkcd(index.toLong())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnNext { view.setLoading(false) }
                    .filter { xkcdPicInDB == null }
                    .subscribe({ this.renderComic(it, forceOriginal) },
                            { e -> Timber.e(e, "load xkcd pic error") })
                    .also { compositeDisposable.add(it) }
        }

        if (xkcdPicInDB != null) {
            renderComic(xkcdPicInDB, forceOriginal)
        }
    }

    override fun updateXkcdSize(xkcdPic: XkcdPic?, resource: Bitmap?) {
        if (xkcdPic != null && (xkcdPic.width == 0 || xkcdPic.height == 0) && resource != null) {
            XkcdModel.updateSize(xkcdPic.num, resource.width, resource.height)
        }
    }

    override fun onDestroy() {
        compositeDisposable.dispose()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPref: SharedPreferences?, key: String?) {
        if (key == PREF_XKCD_TRANSLATION) {
            translationSwitch = sharedPref?.getBoolean(PREF_XKCD_TRANSLATION, false)!!
            Timber.d("Translation config changed $translationSwitch")
            if (!translationSwitch) {
                view.renderOriginal()
            } else if (index != null) {
                loadXkcd(index!!)
            }
        }
    }

    private fun renderComic(xkcdPic: XkcdPic, forceOriginal: Boolean) {
        XkcdModel.push(xkcdPic)
        if (showLocalXkcd && !forceOriginal) {
            loadLocalXkcd(xkcdPic)
        }
        view.renderXkcdPic(xkcdPic)
    }

    private fun loadLocalXkcd(xkcdPic: XkcdPic) {
        XkcdModel.loadLocalizedXkcd(xkcdPic.num)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    view.offerTranslation(XkcdPic(
                            year = xkcdPic.year,
                            month = xkcdPic.month,
                            day = xkcdPic.day,
                            width = xkcdPic.width,
                            height = xkcdPic.height,
                            isFavorite = xkcdPic.isFavorite,
                            hasThumbed = xkcdPic.hasThumbed,
                            num = xkcdPic.num,
                            _alt = it._alt,
                            _title = it._title,
                            img = it.img,
                            translated = true))
                }, { Timber.e(it) })
                .also { compositeDisposable.add(it) }
    }
}
