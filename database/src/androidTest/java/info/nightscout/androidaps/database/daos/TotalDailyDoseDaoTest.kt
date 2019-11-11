package info.nightscout.androidaps.database.daos

import info.nightscout.androidaps.database.entities.TotalDailyDose

class TotalDailyDoseDaoTest : AbstractDaoTest<TotalDailyDose>() {

    override fun copy(entry: TotalDailyDose) = entry.copy()

    override fun getDao() = database.totalDailyDoseDao

    override fun generateTestEntry() = TotalDailyDose(
            timestamp = 0,
            utcOffset = 0,
            basalAmount = 20.0,
            bolusAmount = 30.0,
            totalAmount = 50.0
    )

}