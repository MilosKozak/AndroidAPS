package info.nightscout.androidaps.plugins.general.nsclient;

import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * Created by mike on 26.05.2017.
 */

public class NSUpload {

    final static String XDRIP_PLUS_NS_EMULATOR = "com.eveningoutpost.dexdrip.NS_EMULATOR";
    final static SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US);

    public static UploadService getActiveUploader() {
        return ConfigBuilderPlugin.getPlugin().getActiveNightscoutPlugin().getUploader();
    }

    public static void sendToXdrip(BgReading bgReading) {
        Logger log = LoggerFactory.getLogger(L.BGSOURCE);

        try {
            final JSONArray entriesBody = new JSONArray();
            JSONObject json = new JSONObject();
            json.put("sgv", bgReading.value);
            if (bgReading.direction == null) {
                json.put("direction", "NONE");
            } else {
                json.put("direction", bgReading.direction);
            }
            json.put("device", "G5");
            json.put("type", "sgv");
            json.put("date", bgReading.date);
            json.put("dateString", format.format(bgReading.date));
            entriesBody.put(json);

            final Bundle bundle = new Bundle();
            bundle.putString("action", "add");
            bundle.putString("collection", "entries");
            bundle.putString("data", entriesBody.toString());
            final Intent intent = new Intent(XDRIP_PLUS_NS_EMULATOR);
            intent.putExtras(bundle).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            MainApp.instance().sendBroadcast(intent);
            List<ResolveInfo> receivers = MainApp.instance().getPackageManager().queryBroadcastReceivers(intent, 0);
            if (receivers.size() < 1) {
                log.debug("No xDrip receivers found. ");
            } else {
                log.debug(receivers.size() + " xDrip receivers");
            }


        } catch (JSONException e) {
            log.error(e.getMessage(), e);
        }

    }
}
