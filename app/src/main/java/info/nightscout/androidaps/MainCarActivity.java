package info.nightscout.androidaps;


import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebView;

import com.google.android.apps.auto.sdk.CarActivity;
import com.google.android.apps.auto.sdk.CarUiController;

import info.nightscout.utils.SP;


/**
 * Created by cchunn on 1/30/18.
 */

public class MainCarActivity extends CarActivity {

    static public String nsURL = "";

    public void onCreate(Bundle bundle) {
        setTheme(R.style.AppTheme_Car);
        super.onCreate(bundle);

        setContentView(R.layout.activity_car_main);

        CarUiController carUiController = getCarUiController();
        carUiController.getStatusBarController().showTitle();

        nsURL = SP.getString(R.string.key_nsclientinternal_url, "");

        WebView webview = (WebView)findViewById(R.id.webview);
        webview.getSettings().setJavaScriptEnabled(true);
        webview.getSettings().setDomStorageEnabled(true);
        webview.loadUrl(nsURL);
    }
}
