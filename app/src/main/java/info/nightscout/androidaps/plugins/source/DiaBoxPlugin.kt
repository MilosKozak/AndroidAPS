package info.nightscout.androidaps.plugins.source

import android.content.Intent
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.db.BgReading
import info.nightscout.androidaps.interfaces.BgSourceInterface
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.BundleLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.services.Intents
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiaBoxPlugin @Inject constructor(
    injector: HasAndroidInjector,
    resourceHelper: ResourceHelper,
    aapsLogger: AAPSLogger
) : PluginBase(PluginDescription()
    .mainType(PluginType.BGSOURCE)
    .fragmentClass(BGSourceFragment::class.java.name)
    .pluginName(R.string.diabox)
    .description(R.string.description_source_diabox),
    aapsLogger, resourceHelper, injector
), BgSourceInterface {

    private var advancedFiltering = false

    override fun advancedFilteringSupported(): Boolean {
        return advancedFiltering
    }

    override fun handleNewData(intent: Intent) {
        if (!isEnabled(PluginType.BGSOURCE)) return
        val bundle = intent.extras ?: return
        aapsLogger.debug(LTag.BGSOURCE, "Received diabox data: " + BundleLogger.log(intent.extras))
        val bgReading = BgReading()
        bgReading.value = bundle.getDouble(Intents.DIABOX_EXTRA_BG_ESTIMATE)
        bgReading.direction = bundle.getString(Intents.DIABOX_EXTRA_BG_SLOPE_NAME)
        bgReading.date = bundle.getLong(Intents.DIABOX_EXTRA_TIMESTAMP)
        bgReading.raw = bundle.getDouble(Intents.DIABOX_EXTRA_RAW)
        MainApp.getDbHelper().createIfNotExists(bgReading, "DiaBox")
    }

}