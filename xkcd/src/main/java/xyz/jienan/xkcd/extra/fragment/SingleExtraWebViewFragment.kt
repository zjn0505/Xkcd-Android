package xyz.jienan.xkcd.extra.fragment

import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.webkit.WebSettings
import android.widget.TextView
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.android.synthetic.main.fragment_extra_single.*
import me.dkzwm.widget.srl.SmoothRefreshLayout
import me.dkzwm.widget.srl.extra.footer.ClassicFooter
import me.dkzwm.widget.srl.extra.header.ClassicHeader
import org.jsoup.Jsoup
import timber.log.Timber
import xyz.jienan.xkcd.Const.*
import xyz.jienan.xkcd.R
import xyz.jienan.xkcd.model.ExtraComics
import xyz.jienan.xkcd.model.ExtraModel
import xyz.jienan.xkcd.model.util.appendCss
import xyz.jienan.xkcd.ui.RefreshFooterView
import xyz.jienan.xkcd.ui.RefreshHeaderView
import xyz.jienan.xkcd.ui.getColorResCompat
import xyz.jienan.xkcd.ui.getUiNightModeFlag
import xyz.jienan.xkcd.whatif.fragment.SingleWhatIfFragment
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Created by jienanzhang on 03/03/2018.
 */

class SingleExtraWebViewFragment : SingleWhatIfFragment() {

    private val extraComics by lazy { arguments?.getSerializable("entity") as ExtraComics }

    private var currentPage = 0

    private val refreshTextSize by lazy { resources.getDimension(R.dimen.refresh_text_size) }

    override val layoutResId = R.layout.fragment_extra_single

    private val refreshListener = object : SmoothRefreshLayout.OnRefreshListener {
        override fun onRefreshing() {
            Timber.d("Refresh")
            loadLinkPage(--currentPage)
            refreshLayout!!.refreshComplete()
            updateReleaseText()
        }

        override fun onLoadingMore() {
            Timber.d("LoadMore")
            loadLinkPage(++currentPage)
            refreshLayout!!.refreshComplete()
            Observable.timer(700, TimeUnit.MILLISECONDS)
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnNext { webView.scrollTo(0, 0) }
                    .subscribe()
            updateReleaseText()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        customWebViewSettings()

        if (savedInstanceState != null) {
            currentPage = savedInstanceState.getInt("current", 0)
        }
        loadLinkPage(currentPage)
        if (extraComics.links?.size ?: 0 > 1) {
            refreshLayout?.setupMultipage()
            updateReleaseText()
        }
        val color = ColorStateList.valueOf(context!!.getColorResCompat(android.R.attr.textColorPrimary))
        refreshLayout!!.findViewById<TextView>(R.id.sr_classic_title).setTextColor(color)
        refreshLayout!!.findViewById<TextView>(R.id.sr_classic_last_update).setTextColor(color)
        (refreshLayout!!.footerView as ClassicFooter<*>).findViewById<TextView>(R.id.sr_classic_title).setTextColor(color)
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onDestroyView() {
        compositeDisposable.dispose()
        if (webView != null) {
            webView.clearTasks()
        }
        super.onDestroyView()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_share -> {
                val shareIntent = Intent()
                shareIntent.action = Intent.ACTION_SEND
                shareIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_text_article_extra, extraComics.links?.get(0)))
                shareIntent.type = "text/plain"
                startActivity(Intent.createChooser(shareIntent, resources.getText(R.string.share_to)))
                logUXEvent(FIRE_SHARE_BAR + FIRE_EXTRA_SUFFIX)
                return true
            }
            R.id.action_go_explain -> {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(extraComics.explainUrl))
                startActivity(browserIntent)
                logUXEvent(FIRE_GO_EXTRA_MENU)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("current", currentPage)
    }

    private fun loadLinkPage(pageIndex: Int) {

        val links = extraComics.links

        if (links != null) {
            ExtraModel.parseContentFromUrl(links[abs(pageIndex % links.size)])
                    .map {
                        if (context?.getUiNightModeFlag() == Configuration.UI_MODE_NIGHT_YES) {
                            val doc = Jsoup.parse(it)
                            doc.head().appendCss("night_style.css")
                            doc.html()
                        } else {
                            it
                        }
                    }
                    .subscribe({ html ->
                        webView.loadDataWithBaseURL("file:///android_asset/.",
                                html, "text/html", "UTF-8", null)
                    }, { Timber.e(it) })
                    .also { compositeDisposable.add(it) }
        }
    }

    private fun updateReleaseText() {
        if (extraComics.num == 1L) {
            val texRes = if (abs(currentPage) % 2 != 0) {
                R.string.release_for_puzzle
            } else {
                R.string.release_for_solution
            }

            (refreshLayout!!.headerView as ClassicHeader<*>).setReleaseToRefreshRes(texRes)
            (refreshLayout!!.footerView as ClassicFooter<*>).setReleaseToLoadRes(texRes)
        }
    }

    private fun customWebViewSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        webView.settings.textZoom = (sharedPref.whatIfZoom * 1.5).toInt()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            webView.settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.SINGLE_COLUMN
        }
    }

    private fun SmoothRefreshLayout.setupMultipage() {
        setDisableLoadMore(false)
        setDisablePerformRefresh(false)
        setDisablePerformLoadMore(false)
        setEnableKeepRefreshView(false)
        setOnRefreshListener(refreshListener)
        setEnableAutoRefresh(true)
        setEnableAutoLoadMore(true)
        setHeaderView(RefreshHeaderView(context).apply { setTextSize(refreshTextSize) })
        setFooterView(RefreshFooterView(context).apply { setTextSize(refreshTextSize) })
    }

    companion object {
        fun newInstance(extraComics: ExtraComics) =
                SingleExtraWebViewFragment().apply { arguments = Bundle(1).apply { putSerializable("entity", extraComics) } }
    }
}
