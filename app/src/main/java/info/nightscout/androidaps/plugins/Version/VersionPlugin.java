package info.nightscout.androidaps.plugins.Version;

import android.content.Context;

import com.squareup.otto.Bus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.Overview.notifications.Notification;
import info.nightscout.utils.SP;

public class VersionPlugin extends PluginBase {

    private static final Logger LOG = LoggerFactory.getLogger(L.CORE);

    private final Context ctx;

    private VersionHandler handler = new VersionHandler();
    private final Version currentVersion;
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
                .description(R.string.version_description)
        );
        this.ctx = ctx;
        this.currentVersion = this.handler.currentVerssion();
        this.versionUrl = SP.getString(R.string.key_ver_url, "https://raw.githubusercontent.com/MilosKozak/AndroidAPS/master/version.json");
    }

    public Bus getBus() {
        return MainApp.bus();
    }

    public void checkVersion() {
        LOG.info("checkVersion");
        String versionString = this.handler.retrieve(this.versionUrl);

        if (versionString == null) {
            // there seems to be a network issue, we should let the user hadnle this one...
            Notification notification = new Notification(Notification.VERSION_NOTIFICATION, MainApp.gs(R.string.ver_cannot_retrieve_info), Notification.INFO);
            this.getBus().post(new EventNewNotification(notification));

            return;
        }

        List<Version> versions = this.handler.parse(versionString);
        Version latest = this.handler.findLatestVersion(SP.getString(R.string.key_version_channel, this.currentVersion.getChannel()), versions);

        if (this.handler.compare(this.currentVersion, latest) > 1) {
            // a later version exists, the user should be notified about this one
        }
    }

}
