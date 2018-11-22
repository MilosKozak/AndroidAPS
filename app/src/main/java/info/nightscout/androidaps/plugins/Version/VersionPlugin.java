package info.nightscout.androidaps.plugins.Version;

import android.content.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.L;
import info.nightscout.utils.SP;

public class VersionPlugin extends PluginBase {

    private static final Logger LOG = LoggerFactory.getLogger(L.CORE);

    private final Context ctx;

    private VersionHandler handler = new VersionHandler();
    private Version currentVersion;
    private String versionUrl;

    private static VersionPlugin versionPlugin;

    public static VersionPlugin getPlugin() {
        return versionPlugin;
    }

    public static VersionPlugin initPlugin(Context ctx) {
        if (versionPlugin == null) {
            versionPlugin = new VersionPlugin(ctx);
        }

        return versionPlugin;
    }

    public VersionPlugin() {
        // required for testing
        super(null);
        this.ctx = null;
        this.currentVersion = this.handler.currentVerssion();
    }

    VersionPlugin(Context ctx) {
        super(new PluginDescription()
                .mainType(PluginType.GENERAL)
                .fragmentClass(VersionFragment.class.getName())
                .alwayVisible(false)
                .alwaysEnabled(true)
                .pluginName(R.string.version)
                .shortName(R.string.version_shortname)
                .preferencesId(R.xml.pref_version)
                .description(R.string.description_version)
        );
        this.ctx = ctx;
        this.currentVersion = this.handler.currentVerssion();
        this.versionUrl = SP.getString("key_ver_url", "https://raw.githubusercontent.com/MilosKozak/AndroidAPS/master/version.json");
    }


    public void checkVersion() {
        LOG.info("checkVersion");
        String versionString = this.handler.retrieve(this.versionUrl);

        List<Version> versions = this.handler.parse(versionString);



    }



}
