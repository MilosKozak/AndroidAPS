package info.nightscout.androidaps.plugins.Maintenance;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.content.FileProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSSettingsStatus;
import info.nightscout.utils.SP;

public class UpdatePlugin extends PluginBase {

    private static final Logger LOG = LoggerFactory.getLogger(L.CORE);

    private final Context ctx;

    private static UpdatePlugin updatePlugin;

    public static UpdatePlugin getPlugin() {
        return updatePlugin;
    }

    public static UpdatePlugin initPlugin(Context ctx) {

        if (updatePlugin == null) {
            updatePlugin = new UpdatePlugin(ctx);
        }

        return updatePlugin;
    }

    public UpdatePlugin() {
        // required for testing
        super(null);
        this.ctx = null;
    }

    UpdatePlugin(Context ctx) {
        super(new PluginDescription()
                .mainType(PluginType.GENERAL)
//                .fragmentClass(UpdateFragment.class.getName())
                .alwayVisible(false)
                .alwaysEnabled(true)
                .pluginName(R.string.update)
                .shortName(R.string.update_shortname)
                .preferencesId(R.xml.pref_update)
                .description(R.string.description_maintenance)
        );
        this.ctx = ctx;
    }

}
