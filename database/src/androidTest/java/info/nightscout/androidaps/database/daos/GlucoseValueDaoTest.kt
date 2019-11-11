package info.nightscout.androidaps.database.daos

import info.nightscout.androidaps.database.entities.GlucoseValue

class GlucoseValueDaoTest : AbstractDaoTest<GlucoseValue>() {

    override fun copy(entry: GlucoseValue) = entry.copy()

    override fun getDao() = database.glucoseValueDao

    override fun generateTestEntry() = GlucoseValue(
            timestamp = 0,
            utcOffset = 0,
            noise = 0.0,
            raw = 100.0,
            value = 100.0,
            sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
            trendArrow = GlucoseValue.TrendArrow.FLAT
    )

}