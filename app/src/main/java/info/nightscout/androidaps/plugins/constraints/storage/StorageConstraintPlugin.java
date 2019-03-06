package info.nightscout.androidaps.plugins.constraints.storage;

import android.os.Environment;
import android.os.StatFs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.ConstraintChecker;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.interfaces.BgSourceInterface;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.aps.openAPSAMA.OpenAPSAMAPlugin;
import info.nightscout.androidaps.plugins.aps.openAPSMA.OpenAPSMAPlugin;
import info.nightscout.androidaps.plugins.aps.openAPSSMB.OpenAPSSMBPlugin;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.sensitivity.SensitivityOref1Plugin;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.HardLimits;
import info.nightscout.androidaps.utils.Round;
import info.nightscout.androidaps.utils.SP;

/**
 * Created by Rumen on 06.03.2019.
 */
public class StorageConstraintPlugin extends PluginBase implements ConstraintsInterface {
    private static Logger log = LoggerFactory.getLogger(L.CONSTRAINTS);
    static StorageConstraintPlugin plugin = null;

    public static StorageConstraintPlugin getPlugin() {
        if (plugin == null)
            plugin = new StorageConstraintPlugin();
        return plugin;
    }

    public StorageConstraintPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.CONSTRAINTS)
                .neverVisible(true)
                .alwaysEnabled(true)
                .showInList(false)
                .pluginName(R.string.storage)
        );
    }

    /**
     * Constraints interface
     **/

    @Override
    public Constraint<Boolean> isClosedLoopAllowed(Constraint<Boolean> value) {
        long diskfree = getAvailableInternalMemorySize();
        log.debug("Internal storage free (Mb):"+diskfree);
        if (diskfree < 200) {
            value.set(false, MainApp.gs(R.string.diskfull), this);
        }

        return value;
    }

    // Method to get the free space in Mb
    private int getFreeSpace(){
        return 1;
    }

    public static long getAvailableInternalMemorySize() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSizeLong();
        long availableBlocks = stat.getAvailableBlocksLong();
        int size = 1048576; // blocksize of 1 Mb
        return ((availableBlocks * blockSize) / size);
    }

}
