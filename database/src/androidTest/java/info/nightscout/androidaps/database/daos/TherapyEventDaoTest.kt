package info.nightscout.androidaps.database.daos

import info.nightscout.androidaps.database.entities.TherapyEvent

class TherapyEventDaoTest : AbstractDaoTest<TherapyEvent>() {

    override fun copy(entry: TherapyEvent) = entry.copy()

    override fun getDao() = database.therapyEventDao

    override fun generateTestEntry() = TherapyEvent(
            timestamp = 0,
            utcOffset = 0,
            type = TherapyEvent.Type.FINGER_STICK_BG_VALUE,
            amount = 100.0
    )

}