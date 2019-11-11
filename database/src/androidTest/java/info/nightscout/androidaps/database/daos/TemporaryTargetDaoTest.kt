package info.nightscout.androidaps.database.daos

import info.nightscout.androidaps.database.entities.TemporaryTarget
import java.util.concurrent.TimeUnit

class TemporaryTargetDaoTest : AbstractDaoTest<TemporaryTarget>() {

    override fun copy(entry: TemporaryTarget) = entry.copy()

    override fun getDao() = database.temporaryTargetDao

    override fun generateTestEntry() = TemporaryTarget(
            timestamp = 0,
            utcOffset = 0,
            duration = TimeUnit.HOURS.toMillis(1),
            reason = TemporaryTarget.Reason.CUSTOM,
            target = 150.0
    )

}