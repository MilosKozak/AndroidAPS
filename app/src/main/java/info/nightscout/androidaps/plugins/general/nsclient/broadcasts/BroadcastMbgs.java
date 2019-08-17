package info.nightscout.androidaps.plugins.general.nsclient.broadcasts;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import info.nightscout.api.v3.documents.Entry;
import org.json.JSONArray;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.services.Intents;
import info.nightscout.androidaps.utils.SP;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by mike on 26.06.2016.
 */
public class BroadcastMbgs {


    public static void handleNewMbg(JSONArray mbgs, Context context, boolean isDelta) {

        Bundle bundle = new Bundle();
        bundle.putString("mbgs", mbgs.toString());
        bundle.putBoolean("delta", isDelta);
        Intent intent = new Intent(Intents.ACTION_NEW_MBG);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        LocalBroadcastManager.getInstance(MainApp.instance()).sendBroadcast(intent);

        if(SP.getBoolean(R.string.key_nsclient_localbroadcasts, false)) {
            bundle = new Bundle();
            bundle.putString("mbgs", mbgs.toString());
            bundle.putBoolean("delta", isDelta);
            intent = new Intent(Intents.ACTION_NEW_MBG);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent);
        }
    }
}
