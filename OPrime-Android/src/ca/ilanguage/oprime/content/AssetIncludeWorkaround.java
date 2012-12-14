package ca.ilanguage.oprime.content;

import java.io.IOException;
import java.io.InputStream;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetManager;
import android.net.Uri;
import android.util.Log;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/*
 * Source http://code.google.com/p/android/issues/attachmentText?id=17535&aid=175350100000&name=AssetIncludeWorkaround.java&token=2Xxhmu5Rbyi1ARW-ryk5L5MvaZU%3A1354052830422
 * 
 * for sdk 11-15 
 */
public class AssetIncludeWorkaround extends WebViewClient {
  private Context mContext;

  public AssetIncludeWorkaround(Context context) {
    mContext = context;
  }

  @SuppressLint("NewApi")
  @Override
  public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
    Log.d("OPrime", "Intercepting an URL requestin the webview " + url);
    InputStream stream = inputStreamForAndroidResource(url);
    if (stream != null) {
      return new WebResourceResponse(null, null, stream);
    }
    return super.shouldInterceptRequest(view, url);
  }

  private InputStream inputStreamForAndroidResource(String url) {
    final String ANDROID_ASSET = "file:///android_asset/";

    if (url.startsWith(ANDROID_ASSET)) {
      url = url.replaceFirst(ANDROID_ASSET, "");
      try {
        AssetManager assets = mContext.getAssets();
        Uri uri = Uri.parse(url);
        Log.d(
            "OPrime",
            "The URL was in the assets. (removed the assets and sending the contents of the file)? to the browser. "
                + url);

        return assets.open(uri.getPath(), AssetManager.ACCESS_STREAMING);
      } catch (IOException e) {
        Log.d("OPrime",
            "The URL was in the assets. But there was an IOException when opening it. "
                + url);

      }
    } else {
      Log.d(
          "OPrime",
          "The URL was not the assets. Not performing any action, letting the WebView handle it normally. "
              + url);

    }
    return null;
  }
}
