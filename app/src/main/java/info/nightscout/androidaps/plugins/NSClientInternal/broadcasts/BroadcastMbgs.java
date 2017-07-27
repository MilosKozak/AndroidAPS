package info.nightscout.androidaps.plugins.NSClientInternal.broadcasts;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;

import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.Services.Intents;

/**
 * Created by mike on 26.06.2016.
 */
public class BroadcastMbgs {
    private static Logger log = LoggerFactory.getLogger(BroadcastMbgs.class);

    public static void handleNewMbg(JSONArray mbgs, Context context, boolean isDelta) {
<<<<<<< HEAD
=======

>>>>>>> 39d41e8e7d841a7b5a22e0b4e14acce3394a7878
        Bundle bundle = new Bundle();
        bundle.putString("mbgs", mbgs.toString());
        bundle.putBoolean("delta", isDelta);
        Intent intent = new Intent(Intents.ACTION_NEW_MBG);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
<<<<<<< HEAD
        context.sendBroadcast(intent);
        List<ResolveInfo> x = context.getPackageManager().queryBroadcastReceivers(intent, 0);

        log.debug("MBG " + x.size() + " receivers");
=======
        LocalBroadcastManager.getInstance(MainApp.instance()).sendBroadcast(intent);

        if(SP.getBoolean(R.string.key_nsclient_localbroadcasts, true)) {
            bundle = new Bundle();
            bundle.putString("mbgs", mbgs.toString());
            bundle.putBoolean("delta", isDelta);
            intent = new Intent(Intents.ACTION_NEW_MBG);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent);
        }
>>>>>>> 39d41e8e7d841a7b5a22e0b4e14acce3394a7878
    }
}
