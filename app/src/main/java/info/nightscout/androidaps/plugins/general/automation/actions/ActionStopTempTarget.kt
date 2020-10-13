package info.nightscout.androidaps.plugins.general.automation.actions

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.transactions.CancelCurrentTemporaryTargetIfAnyTransaction
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject

class ActionStopTempTarget(injector: HasAndroidInjector) : Action(injector) {
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var repository: AppRepository

    override fun friendlyName(): Int = R.string.stoptemptarget
    override fun shortDescription(): String = resourceHelper.gs(R.string.stoptemptarget)
    override fun icon(): Int = R.drawable.ic_stop_24dp

    override fun doAction(callback: Callback) {
        repository.runTransaction(CancelCurrentTemporaryTargetIfAnyTransaction(DateUtil.now())).blockingAwait()
        callback.result(PumpEnactResult(injector).success(true).comment(R.string.ok))?.run()
    }
}