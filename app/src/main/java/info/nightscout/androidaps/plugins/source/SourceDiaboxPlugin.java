package info.nightscout.androidaps.plugins.source;

import android.content.Intent;
import android.os.Bundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.interfaces.BgSourceInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.BundleLogger;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.services.Intents;

/**
 * Created by mike on 05.08.2016.
 */
public class SourceDiaboxPlugin extends PluginBase implements BgSourceInterface {
    private static Logger log = LoggerFactory.getLogger(L.BGSOURCE);

    private static SourceDiaboxPlugin plugin = null;

    boolean advancedFiltering;

    public static SourceDiaboxPlugin getPlugin() {
        if (plugin == null)
            plugin = new SourceDiaboxPlugin();
        return plugin;
    }

    private SourceDiaboxPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.BGSOURCE)
                .fragmentClass(BGSourceFragment.class.getName())
                .pluginName(R.string.diabox)
                .description(R.string.description_source_diabox)
        );
    }

    @Override
    public boolean advancedFilteringSupported() {
        return advancedFiltering;
    }

    @Override
    public void handleNewData(Intent intent) {

        if (!isEnabled(PluginType.BGSOURCE)) return;

        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        if (L.isEnabled(L.BGSOURCE))
            log.debug("Received Diabox data: " + BundleLogger.log(intent.getExtras()));

        BgReading bgReading = new BgReading();
        bgReading.value = bundle.getDouble(Intents.DIABOX_EXTRA_BG_ESTIMATE);
        bgReading.direction = bundle.getString(Intents.DIABOX_EXTRA_BG_SLOPE_NAME);
        bgReading.date = bundle.getLong(Intents.DIABOX_EXTRA_TIMESTAMP);
        bgReading.raw = bundle.getDouble(Intents.DIABOX_EXTRA_RAW);
        MainApp.getDbHelper().createIfNotExists(bgReading, "DiaBox");
    }

}
