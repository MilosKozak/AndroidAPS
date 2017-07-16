package info.nightscout.androidaps.plugins.NSClientInternal.broadcasts;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.TransactionTooLargeException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.Services.Intents;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSTreatment;

/**
 * Created by mike on 20.02.2016.
 */
public class BroadcastTreatment {
    private static Logger log = LoggerFactory.getLogger(BroadcastTreatment.class);

    public static void handleNewTreatment(NSTreatment treatment, Context context, boolean isDelta) {
        Bundle bundle = new Bundle();
        bundle.putString("treatment", treatment.getData().toString());
        bundle.putBoolean("delta", isDelta);
        Intent intent = new Intent(Intents.ACTION_NEW_TREATMENT);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(intent);
        try {
        List<ResolveInfo> x = context.getPackageManager().queryBroadcastReceivers(intent, 0);

        log.debug("TREAT_ADD " + treatment.getEventType() + " " + x.size() + " receivers");
        } catch (Exception e){
            //for testing
        }
    }

    public static void handleNewTreatment(JSONArray treatments, Context context, boolean isDelta) {
        List<JSONArray> splitted = splitArray(treatments);
        for (JSONArray part: splitted) {
            Bundle bundle = new Bundle();
            bundle.putString("treatments", part.toString());
            bundle.putBoolean("delta", isDelta);
            Intent intent = new Intent(Intents.ACTION_NEW_TREATMENT);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent);
            try {
                List<ResolveInfo> x = context.getPackageManager().queryBroadcastReceivers(intent, 0);
                log.debug("TREAT_ADD " + part.length() + " " + x.size() + " receivers");
            } catch (Exception e){
            //for testing
        }

        }
    }

    public void handleChangedTreatment(JSONObject treatment, Context context, boolean isDelta) {
        Bundle bundle = new Bundle();
        bundle.putString("treatment", treatment.toString());
        bundle.putBoolean("delta", isDelta);
        Intent intent = new Intent(Intents.ACTION_CHANGED_TREATMENT);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(intent);
        try {
        List<ResolveInfo> x = context.getPackageManager().queryBroadcastReceivers(intent, 0);

            log.debug("TREAT_CHANGE " + treatment.getString("_id") + " " + x.size() + " receivers");
        } catch (Exception e) {
        }
    }

    public static void handleChangedTreatment(JSONArray treatments, Context context, boolean isDelta) {
        List<JSONArray> splitted = splitArray(treatments);
        for (JSONArray part: splitted) {
            Bundle bundle = new Bundle();
            bundle.putString("treatments", part.toString());
            bundle.putBoolean("delta", isDelta);
            Intent intent = new Intent(Intents.ACTION_CHANGED_TREATMENT);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent);
            try {
            List<ResolveInfo> x = context.getPackageManager().queryBroadcastReceivers(intent, 0);

            log.debug("TREAT_CHANGE " + part.length() + " " + x.size() + " receivers");
            } catch (Exception e){
                //for testing
            }
        }
    }

    public static void handleRemovedTreatment(JSONObject treatment, Context context, boolean isDelta) {
        Bundle bundle = new Bundle();
        bundle.putString("treatment", treatment.toString());
        bundle.putBoolean("delta", isDelta);
        Intent intent = new Intent(Intents.ACTION_REMOVED_TREATMENT);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(intent);
        try {
        List<ResolveInfo> x = context.getPackageManager().queryBroadcastReceivers(intent, 0);


            log.debug("TREAT_REMOVE " + treatment.getString("_id") + " " + x.size() + " receivers");
        } catch (Exception e){
            //for testing
        }
    }

    public static void handleRemovedTreatment(JSONArray treatments, Context context, boolean isDelta) {
        Bundle bundle = new Bundle();
        bundle.putString("treatments", treatments.toString());
        bundle.putBoolean("delta", isDelta);
        Intent intent = new Intent(Intents.ACTION_REMOVED_TREATMENT);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(intent);
        try {
        List<ResolveInfo> x = context.getPackageManager().queryBroadcastReceivers(intent, 0);

        log.debug("TREAT_REMOVE " + treatments.length() + " treatments " + x.size() + " receivers");
        } catch (Exception e){
            //for testing
        }
    }


    public static List<JSONArray> splitArray(JSONArray array) {
        List<JSONArray> ret = new ArrayList<>();
        try {
            int size = array.length();
            int count = 0;
            JSONArray newarr = null;
            for (int i = 0; i < size; i++) {
                if (count == 0) {
                    if (newarr != null) {
                        ret.add(newarr);
                    }
                    newarr = new JSONArray();
                    count = 200;
                }
                newarr.put(array.get(i));
                --count;
            }
            if (newarr != null && newarr.length() > 0) {
                ret.add(newarr);
            }
        } catch (JSONException e) {
            e.printStackTrace();
            ret = new ArrayList<>();
            ret.add(array);
        }
        return ret;
    }
}
