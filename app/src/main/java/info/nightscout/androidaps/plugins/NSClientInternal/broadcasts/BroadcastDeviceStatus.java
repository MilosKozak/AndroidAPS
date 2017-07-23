package info.nightscout.androidaps.plugins.NSClientInternal.broadcasts;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import info.nightscout.androidaps.Services.DataService;
import info.nightscout.androidaps.Services.Intents;
import info.nightscout.utils.SP;


public class BroadcastDeviceStatus {
    private static Logger log = LoggerFactory.getLogger(BroadcastDeviceStatus.class);

    public static void handleNewDeviceStatus(JSONArray statuses, Context context, boolean isDelta) {


        //send internally
        List<JSONArray> splitted = BroadcastTreatment.splitArray(statuses);
        for (JSONArray part: splitted) {
            DataService.actionNewDevicestatus(null, part.toString());
        }


        if(SP.getBoolean("nsclient_localbroadcasts", true)) {
            splitted = BroadcastTreatment.splitArray(statuses);
            for (JSONArray part : splitted) {
                Bundle bundle = new Bundle();
                bundle.putString("devicestatuses", part.toString());
                bundle.putBoolean("delta", isDelta);
                Intent intent = new Intent(Intents.ACTION_NEW_DEVICESTATUS);
                intent.putExtras(bundle);
                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                context.sendBroadcast(intent);
            }
        }
    }
}
