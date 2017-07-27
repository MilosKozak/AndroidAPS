package info.nightscout.androidaps.plugins.NSClientInternal.broadcasts;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.Services.Intents;

/**
 * Created by mike on 22.02.2016.
 */
public class BroadcastSgvs {
    private static Logger log = LoggerFactory.getLogger(BroadcastSgvs.class);

    public static void handleNewSgv(JSONObject sgv, Context context, boolean isDelta) {
<<<<<<< HEAD
=======

>>>>>>> 39d41e8e7d841a7b5a22e0b4e14acce3394a7878
        Bundle bundle = new Bundle();
        bundle.putString("sgv", sgv.toString());
        bundle.putBoolean("delta", isDelta);
        Intent intent = new Intent(Intents.ACTION_NEW_SGV);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
<<<<<<< HEAD
        context.sendBroadcast(intent);
        List<ResolveInfo> x = context.getPackageManager().queryBroadcastReceivers(intent, 0);

        log.debug("SGV " + x.size() + " receivers");
=======
        LocalBroadcastManager.getInstance(MainApp.instance()).sendBroadcast(intent);

        if(SP.getBoolean(R.string.key_nsclient_localbroadcasts, true)) {
            bundle = new Bundle();
            bundle.putString("sgv", sgv.toString());
            bundle.putBoolean("delta", isDelta);
            intent = new Intent(Intents.ACTION_NEW_SGV);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent);
        }
>>>>>>> 39d41e8e7d841a7b5a22e0b4e14acce3394a7878
    }

    public static void handleNewSgv(JSONArray sgvs, Context context, boolean isDelta) {
        Bundle bundle = new Bundle();
        bundle.putString("sgvs", sgvs.toString());
        bundle.putBoolean("delta", isDelta);
        Intent intent = new Intent(Intents.ACTION_NEW_SGV);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
<<<<<<< HEAD
        context.sendBroadcast(intent);
        List<ResolveInfo> x = context.getPackageManager().queryBroadcastReceivers(intent, 0);

        log.debug("SGV " + x.size() + " receivers");
=======
        LocalBroadcastManager.getInstance(MainApp.instance()).sendBroadcast(intent);

        if(SP.getBoolean(R.string.key_nsclient_localbroadcasts, true)) {
            bundle = new Bundle();
            bundle.putString("sgvs", sgvs.toString());
            bundle.putBoolean("delta", isDelta);
            intent = new Intent(Intents.ACTION_NEW_SGV);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent);
        }
>>>>>>> 39d41e8e7d841a7b5a22e0b4e14acce3394a7878
    }

}
