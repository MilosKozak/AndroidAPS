package info.nightscout.androidaps.plugins.source;

import android.content.Intent;
import android.os.Bundle;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.interfaces.BgSourceInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.constraints.objectives.ObjectivesPlugin;
import info.nightscout.androidaps.plugins.general.nsclient.data.NSSgv;
import info.nightscout.androidaps.utils.JsonHelper;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.api.v3.documents.DocumentBase;
import info.nightscout.api.v3.documents.Entry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by mike on 05.08.2016.
 */
public class SourceNSClientPlugin extends PluginBase implements BgSourceInterface {
    private static Logger log = LoggerFactory.getLogger(L.BGSOURCE);

    private static SourceNSClientPlugin plugin = null;

    public static SourceNSClientPlugin getPlugin() {
        if (plugin == null)
            plugin = new SourceNSClientPlugin();
        return plugin;
    }

    private long lastBGTimeStamp = 0;
    private boolean isAdvancedFilteringEnabled = false;

    private SourceNSClientPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.BGSOURCE)
                .fragmentClass(BGSourceFragment.class.getName())
                .pluginName(R.string.nsclientbg)
                .description(R.string.description_source_ns_client)
        );
    }

    @Override
    public boolean advancedFilteringSupported() {
        return isAdvancedFilteringEnabled;
    }

    @Override
    public void handleNewData(Intent intent) {

        if (!isEnabled(PluginType.BGSOURCE) && !SP.getBoolean(R.string.key_ns_autobackfill, true))
            return;

        Bundle bundles = intent.getExtras();

        try {
            if(bundles.containsKey("data")) {
                DocumentBase documentBase = bundles.getParcelable("data");
                if(documentBase instanceof Entry) {
                    Entry sgv = (Entry) documentBase;
                    if (L.isEnabled(L.BGSOURCE))
                        log.debug("Received NS Data: " + sgv.sgv);
                    storeSgv(sgv);
                }
            }
            if (bundles.containsKey("sgv")) {
                String sgvstring = bundles.getString("sgv");
                if (L.isEnabled(L.BGSOURCE))
                    log.debug("Received NS Data: " + sgvstring);

                JSONObject sgvJson = new JSONObject(sgvstring);
                storeSgv(sgvJson);
            }

            if (bundles.containsKey("sgvs")) {
                String sgvstring = bundles.getString("sgvs");
                if (L.isEnabled(L.BGSOURCE))
                    log.debug("Received NS Data: " + sgvstring);
                JSONArray jsonArray = new JSONArray(sgvstring);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject sgvJson = jsonArray.getJSONObject(i);
                    storeSgv(sgvJson);
                }
            }
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }

        // Objectives 0
        ObjectivesPlugin.getPlugin().bgIsAvailableInNS = true;
        ObjectivesPlugin.getPlugin().saveProgress();
    }

    private void storeSgv(JSONObject sgvJson) {
        NSSgv nsSgv = new NSSgv(sgvJson);
        BgReading bgReading = new BgReading(nsSgv);
        MainApp.getDbHelper().createIfNotExists(bgReading, "NS");
        detectSource(JsonHelper.safeGetString(sgvJson, "device", "none"), JsonHelper.safeGetLong(sgvJson, "mills"));
    }

    private void storeSgv(Entry entry) {
        BgReading bgReading = new BgReading(entry);
        MainApp.getDbHelper().createIfNotExists(bgReading, "NS");
        detectSource(entry);
    }

    public void detectSource(String source, long timeStamp) {
        if (timeStamp > lastBGTimeStamp) {
            if (source.contains("G5 Native") || source.contains("G6 Native") || source.contains("AndroidAPS-DexcomG5") || source.contains("AndroidAPS-DexcomG6"))
                isAdvancedFilteringEnabled = true;
            else
                isAdvancedFilteringEnabled = false;
            lastBGTimeStamp = timeStamp;
        }
    }

    protected void detectSource(Entry entry) {
        if (entry.date != null && entry.date > lastBGTimeStamp) {
            String source = entry.device;
            if (source != null && (source.contains("G5 Native") || source.contains("G6 Native") || source.contains("AndroidAPS-DexcomG5") || source.contains("AndroidAPS-DexcomG6")))
                isAdvancedFilteringEnabled = true;
            else
                isAdvancedFilteringEnabled = false;
            lastBGTimeStamp = entry.date;
        }
    }
}
