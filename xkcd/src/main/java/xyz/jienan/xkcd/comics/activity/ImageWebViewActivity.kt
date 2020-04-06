package xyz.jienan.xkcd.comics.activity

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import xyz.jienan.xkcd.R
import xyz.jienan.xkcd.base.BaseActivity
import xyz.jienan.xkcd.model.XkcdModel
import xyz.jienan.xkcd.model.XkcdPic
import xyz.jienan.xkcd.ui.updateSettings
import java.util.*


class ImageWebViewActivity : BaseActivity() {

    companion object {

        const val TAG_INTERACTIVE = "interactive"

        const val TAG_XK3D = "xk3d"

        private const val PERMALINK_1663 = "xkcd_permalink_1663"

        private const val PERMALINK_2288 = "xkcd_permalink_2288"

        fun startActivity(context: Context, num: Long, translationMode: Boolean, webPageMode: String = TAG_INTERACTIVE) {
            val intent = Intent(context, ImageWebViewActivity::class.java)
            intent.putExtra("index", num)
            intent.putExtra("translationMode", translationMode)
            intent.putExtra("webPageMode", webPageMode)
            context.startActivity(intent)
        }
    }

    private val isInteractiveMode: Boolean
        get() = intent.getStringExtra("webPageMode") == TAG_INTERACTIVE

    private val webView by lazy { findViewById<WebView>(R.id.imgWebView) }

    private val progress by lazy { findViewById<ProgressBar>(R.id.progressWebView) }

    private val compositeDisposable = CompositeDisposable()

    private var permalink1663: String?
        get() = sharedPreferences.getString(PERMALINK_1663, "")
        set(value) = sharedPreferences.edit { putString(PERMALINK_1663, value) }

    private var permalink2288: String?
        get() = sharedPreferences.getString(PERMALINK_2288, "")
        set(value) = sharedPreferences.edit { putString(PERMALINK_2288, value) }

    private var index: Long = 1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_webview)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        index = intent.getLongExtra("index", 1L)

        val xkcd = XkcdModel.loadXkcdFromDB(index)

        if (xkcd == null) {
            XkcdModel.loadXkcd(index)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .take(1)
                    .doOnNext { title = "xkcd: ${it.title}" }
                    .subscribe({ loadXkcdInWebView(it) }, { Timber.e(it) })
                    .also { compositeDisposable.add(it) }
        } else {
            title = "xkcd: ${xkcd.title}"
            loadXkcdInWebView(xkcd)
        }
    }

    override fun onDestroy() {
        compositeDisposable.clear()
        super.onDestroy()
    }

    @SuppressLint("AddJavascriptInterface")
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        if (index !in listOf(1608L)) {
            return false
        }
        when (index) {
            1608L -> menu?.add(Menu.NONE, R.id.menu_gandalf, Menu.NONE, "i.am.gandalf")
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> onBackPressed()
            R.id.menu_gandalf -> webView.loadUrl("javascript:i.am.gandalf=true")
        }
        return true
    }

    private fun loadXkcdInWebView(xkcd: XkcdPic) {
        val urlScriptPair = if (isInteractiveMode) {
            loadInteractivePage(xkcd)
        } else {
            loadXk3dPage()
        }
        val url = urlScriptPair.first
        val script = urlScriptPair.second
        webView.webViewClient = object : WebViewClient() {

            override fun onPageFinished(view: WebView, url: String) {
                this@ImageWebViewActivity.title = view.title
                Timber.d("Current page $url")
                if (index == 1663L && url.contains("/1663/#".toRegex())) {
                    if (permalink1663.isNullOrBlank()) {
                        permalink1663 = extractUuidFrom1663url(url)
                    }
                } else if (index == 2288L && url.contains("https://xkcd.com/2288/#-".toRegex())) {
                    permalink2288 = url
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                val baseTitle = "xkcd: ${xkcd.title}"
                if (newProgress > 20 && script.isNotBlank()) view?.loadUrl(script)
                title = baseTitle + when (newProgress) {
                    in 0..99 -> {
                        progress.isIndeterminate = false
                        progress.progress = newProgress
                        " (${newProgress}%)"
                    }
                    else -> {
                        progress.visibility = View.GONE
                        ""
                    }
                }
            }
        }

        webView.updateSettings()
        if (index == 2288L) {
            webView.settings.displayZoomControls = true
        }
        webView.loadUrl(url)
    }

    private fun loadInteractivePage(xkcd: XkcdPic): Pair<String, String> {
        var script = ""
        val url = when (xkcd.num.toInt()) {
            1663 -> {
                if (permalink1663.isNullOrBlank()) {
                    "https://zjn0505.github.io/xkcd-undressed/${xkcd.num}/"
                } else {
                    "https://zjn0505.github.io/xkcd-undressed/${xkcd.num}/#${permalink1663}"
                }
            }
            2288 -> {
                script = """
                    javascript:
                    var toDelete = [];
                    toDelete.push(document.getElementById("topContainer"));
                    toDelete.push(document.getElementById("bottom"));
                    toDelete.push(document.getElementById("ctitle"));
                    toDelete.push(document.getElementById("middleFooter"));
                    toDelete.push(document.getElementsByClassName("menuCont")[0]);
                    toDelete.push(document.getElementsByClassName("menuCont")[1]);
                    toDelete.push(document.getElementsByClassName("br")[0]);
                    toDelete.push(document.getElementsByClassName("br")[1]);
                    toDelete.push(document.getElementsByClassName("comicNav")[0]);
                    toDelete.push(document.getElementsByClassName("comicNav")[1]);
                    toDelete.forEach(function (element) {
                        if (element) element.style.display = 'none';
                    });
                """.trimIndent()
                if (permalink2288.isNullOrBlank()) {
                    "https://xkcd.com/2288"
                } else {
                    permalink2288!!
                }
            }
            in resources.getIntArray(R.array.interactive_comics) -> if (!intent.getBooleanExtra("translationMode", false)) {
                "https://zjn0505.github.io/xkcd-undressed/${xkcd.num}/"
            } else {
                Timber.d("region ${Locale.getDefault()}")
                "https://zjn0505.github.io/xkcd-undressed/${xkcd.num}/?region=${Locale.getDefault().toString().replace("#", "_")}"
            }
            else -> {
                ""
            }
        }
        return url to script
    }

    private fun loadXk3dPage(): Pair<String, String> {
        // xk3d
        val script = """
                    javascript:
                    var toDelete = [];
                    toDelete.push(document.getElementById("topContainer"));
                    toDelete.push(document.getElementById("bottom"));
                    toDelete.push(document.getElementById("ctitle"));
                    toDelete.push(document.getElementById("middleFooter"));
                    toDelete.push(document.getElementsByClassName("menuCont")[0]);
                    toDelete.push(document.getElementsByClassName("menuCont")[1]);
                    toDelete.push(document.getElementsByClassName("br")[0]);
                    toDelete.push(document.getElementsByClassName("br")[1]);
                    toDelete.forEach(function (element) {
                        if (element) element.style.display = 'none';
                    });
                    var container = document.getElementById("container");
                    if (container) {
                        container.style.marginRight = 0;
                        container.style.marginLeft = 0;
                        container.style.width = "100vw";
                    }
                    var comic = document.getElementById("comic");
                    if (comic) {
                        comic.style.zoom = window.innerWidth / comic.offsetWidth / 1.2;
                    }
                """.trimIndent()
        val url = "https://xk3d.xkcd.com/$index"
        return url to script
    }

    @VisibleForTesting
    fun extractUuidFrom1663url(url: String) = url.substring(url.indexOf("/1663/#") + 7)
}