package info.nightscout.androidaps.database.daos

import info.nightscout.androidaps.database.entities.Carbs

class CarbsDaoTest : AbstractDaoTest<Carbs>() {

    override fun copy(entry: Carbs) = entry.copy()

    override fun getDao() = database.carbsDao

    override fun generateTestEntry() = Carbs(
            timestamp = 0,
            utcOffset = 0,
            amount = 50.0,
            duration = 0
    )

}