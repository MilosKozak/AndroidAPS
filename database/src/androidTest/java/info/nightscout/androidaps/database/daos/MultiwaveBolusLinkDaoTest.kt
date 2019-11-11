package info.nightscout.androidaps.database.daos

import info.nightscout.androidaps.database.entities.links.MultiwaveBolusLink

class MultiwaveBolusLinkDaoTest : AbstractDaoTest<MultiwaveBolusLink>() {

    override fun copy(entry: MultiwaveBolusLink) = entry.copy()

    override fun getDao() = database.multiwaveBolusLinkDao

    override fun generateTestEntry() = MultiwaveBolusLink(
            bolusId = 1,
            extendedBolusId = 1
    )

}