package xyz.jienan.xkcd.base.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.text.TextUtils;

import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.Cache;
import okhttp3.CipherSuite;
import okhttp3.ConnectionSpec;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.TlsVersion;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import timber.log.Timber;
import xyz.jienan.xkcd.BuildConfig;
import xyz.jienan.xkcd.XkcdApplication;
import xyz.jienan.xkcd.model.XkcdPic;
import xyz.jienan.xkcd.model.XkcdPicDeserializer;

/**
 * Created by Jienan on 2018/3/2.
 */

public class NetworkService {

    public static final String XKCD_SPECIAL_LIST = "https://raw.githubusercontent.com/zjn0505/Xkcd-Android/master/xkcd/src/main/res/raw/xkcd_special.json";

    public static final String XKCD_EXTRA_LIST = "https://raw.githubusercontent.com/zjn0505/Xkcd-Android/master/xkcd/src/main/res/raw/xkcd_extra.json"; // TODO change to master

    public static final String XKCD_SEARCH_SUGGESTION = "https://api.jienan.xyz/xkcd/xkcd-suggest";
    public static final String XKCD_BROWSE_LIST = "https://api.jienan.xyz/xkcd/xkcd-list";
    public static final String XKCD_THUMBS_UP = "https://api.jienan.xyz/xkcd/xkcd-thumb-up";
    public static final String XKCD_TOP = "https://api.jienan.xyz/xkcd/xkcd-top";
    public static final String WHAT_IF_THUMBS_UP = "https://api.jienan.xyz/xkcd/what-if-thumb-up";
    public static final String WHAT_IF_TOP = "https://api.jienan.xyz/xkcd/what-if-top";
    public static final String XKCD_TOP_SORT_BY_THUMB_UP = "thumb-up";
    public static final String XKCD_EXPLAIN_URL = "https://www.explainxkcd.com/wiki/index.php/";
    public static final String XKCD_BASE_URL = "https://xkcd.com/";

    static final String QUOTE_LIST = "https://raw.githubusercontent.com/zjn0505/Xkcd-Android/master/quotes.json";

    private static final String WHAT_IF_BASE_URL = "https://what-if.xkcd.com/";
    private static final int DEFAULT_READ_TIMEOUT = 30; // in seconds
    private static final int DEFAULT_CONNECT_TIMEOUT = 15; // in seconds
    private static final String HEADER_CACHE_CONTROL = "Cache-Control";
    private static XkcdAPI xkcdAPI;
    private static WhatIfAPI whatIfAPI;
    private static QuoteAPI quoteAPI;
    private static ConnectionSpec spec = new ConnectionSpec.Builder(ConnectionSpec.COMPATIBLE_TLS)
            .supportsTlsExtensions(true)
            .tlsVersions(TlsVersion.TLS_1_2, TlsVersion.TLS_1_1, TlsVersion.TLS_1_0)
            .cipherSuites(
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_RC4_128_SHA,
                    CipherSuite.TLS_ECDHE_RSA_WITH_RC4_128_SHA,
                    CipherSuite.TLS_DHE_RSA_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_DHE_DSS_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_DHE_RSA_WITH_AES_256_CBC_SHA)
            .build();

    private NetworkService() {
        OkHttpClient client = getOkHttpClientBuilder().build();

        Retrofit.Builder builder = new Retrofit.Builder()
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(
                        new GsonBuilder().registerTypeAdapter(XkcdPic.class, new XkcdPicDeserializer()).create()))
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create());
        xkcdAPI = builder.baseUrl(XKCD_BASE_URL).build().create(XkcdAPI.class);
        whatIfAPI = builder.baseUrl(WHAT_IF_BASE_URL).build().create(WhatIfAPI.class);
        quoteAPI = builder.build().create(QuoteAPI.class);
    }

    public static OkHttpClient.Builder getOkHttpClientBuilder() {
        OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder();
        httpClientBuilder.addNetworkInterceptor(new NetworkCacheInterceptor())
                .addInterceptor(new ApplicationCacheInterceptor());

        //setup cache
        File httpCacheDirectory = new File(XkcdApplication.getInstance().getCacheDir(), "responses");
        int cacheSize = 10 * 1024 * 1024; // 10 MiB
        Cache cache = new Cache(httpCacheDirectory, cacheSize);

        //add cache to the client
        httpClientBuilder.cache(cache);

        if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();
            interceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);
            httpClientBuilder.addInterceptor(interceptor);
        }


        httpClientBuilder = enableTls12(httpClientBuilder)
                .connectTimeout(DEFAULT_CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(DEFAULT_READ_TIMEOUT, TimeUnit.SECONDS);

        return httpClientBuilder;
    }

    /**
     * Enable TLS on the OKHttp builder by setting a custom SocketFactory
     */
    private static OkHttpClient.Builder enableTls12(OkHttpClient.Builder client) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN || Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            return client;
        }
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init((KeyStore) null);
            TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
            if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
                throw new IllegalStateException("Unexpected default trust managers:"
                        + Arrays.toString(trustManagers));
            }
            X509TrustManager trustManager = (X509TrustManager) trustManagers[0];

            client.sslSocketFactory(new TLSSocketFactory(), trustManager);

            client.connectionSpecs(Collections.singletonList(spec));
        } catch (Exception exc) {
            Timber.e(exc, "Error while setting TLS");
        }
        return client;
    }

    public static XkcdAPI getXkcdAPI() {
        if (xkcdAPI == null) {
            new NetworkService();
        }
        return xkcdAPI;
    }

    public static WhatIfAPI getWhatIfAPI() {
        if (whatIfAPI == null) {
            new NetworkService();
        }
        return whatIfAPI;
    }

    public static QuoteAPI getQuoteAPI() {
        if (quoteAPI == null) {
            new NetworkService();
        }
        return quoteAPI;
    }

    private static boolean isNetworkAvailable() {
        Context context = XkcdApplication.getInstance();
        ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivity == null) {
            return false;
        } else {
            NetworkInfo[] info = connectivity.getAllNetworkInfo();
            if (info != null) {
                for (NetworkInfo anInfo : info) {
                    if (anInfo.getState() == NetworkInfo.State.CONNECTED) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Interceptor to cache data and maintain it for four weeks.
     * <p>
     * If the device is offline, stale (at most four weeks old)
     * response is fetched from the cache.
     */
    private static class ApplicationCacheInterceptor implements Interceptor {
        @Override
        public okhttp3.Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            if (!isNetworkAvailable()) {
                request = request.newBuilder()
                        .removeHeader("pragma")
                        .header(HEADER_CACHE_CONTROL,
                                "public, only-if-cached, max-stale=" + 2419200)
                        .build();
            }
            if ("1".equals(request.header("bypass"))) {
                Request.Builder builder = request.newBuilder().addHeader(HEADER_CACHE_CONTROL, "no-cache");
                request = builder.build();
            }
            return chain.proceed(request);
        }
    }

    /**
     * Interceptor to cache data and maintain it for a minute.
     * <p>
     * If the same network request is sent within a minute,
     * the response is retrieved from cache.
     */
    private static class NetworkCacheInterceptor implements Interceptor {
        @Override
        public okhttp3.Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            String cacheable = request.header("cacheable");
            okhttp3.Response originalResponse = chain.proceed(request);
            Response.Builder builder = originalResponse.newBuilder().removeHeader("pragma").removeHeader("cacheable");
            if (TextUtils.isEmpty(cacheable)) {
                return builder.header(HEADER_CACHE_CONTROL, "no-cache").build();
            } else {
                return builder.header(HEADER_CACHE_CONTROL, "public, max-age=" + cacheable).build();
            }
        }
    }
}
