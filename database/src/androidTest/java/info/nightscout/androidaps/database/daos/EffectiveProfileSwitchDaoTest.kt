package info.nightscout.androidaps.database.daos

import info.nightscout.androidaps.database.Block
import info.nightscout.androidaps.database.entities.EffectiveProfileSwitch
import java.util.concurrent.TimeUnit

class EffectiveProfileSwitchDaoTest : AbstractDaoTest<EffectiveProfileSwitch>() {

    override fun copy(entry: EffectiveProfileSwitch) = entry.copy()

    override fun getDao() = database.effectiveProfileSwitchDao

    override fun generateTestEntry() = EffectiveProfileSwitch(
            timestamp = 0,
            utcOffset = 0,
            basalBlocks = generateBlocks(),
            duration = TimeUnit.DAYS.toMillis(1)
    )

    private fun generateBlocks(): List<Block> {
        val blocks = mutableListOf<Block>()
        for (i in 1..24) blocks.add(Block(TimeUnit.HOURS.toMillis(1), 1.0))
        return blocks
    }

}