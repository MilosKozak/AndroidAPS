package info.nightscout.androidaps.database.daos

import info.nightscout.androidaps.database.entities.APSResult

class APSResultDaoTest : AbstractDaoTest<APSResult>() {

    override fun copy(entry: APSResult) = entry.copy()

    override fun getDao() = database.apsResultDao

    override fun generateTestEntry() = APSResult(
            timestamp = 0,
            utcOffset = 0
    )

}