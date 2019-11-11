package info.nightscout.androidaps.database.daos

import info.nightscout.androidaps.database.Block
import info.nightscout.androidaps.database.TargetBlock
import info.nightscout.androidaps.database.embedments.InsulinConfiguration
import info.nightscout.androidaps.database.entities.ProfileSwitch
import java.util.concurrent.TimeUnit

class ProfileSwitchDaoTest : AbstractDaoTest<ProfileSwitch>() {

    override fun copy(entry: ProfileSwitch) = entry.copy()

    override fun getDao() = database.profileSwitchDao

    override fun generateTestEntry() = ProfileSwitch(
            timestamp = 0,
            utcOffset = 0,
            basalBlocks = generateBlocks(),
            icBlocks = generateBlocks(),
            isfBlocks = generateBlocks(),
            targetBlocks = generateTargetBlocks(),
            duration = Long.MAX_VALUE,
            glucoseUnit = ProfileSwitch.GlucoseUnit.MGDL,
            insulinConfiguration = InsulinConfiguration("", TimeUnit.HOURS.toMillis(6), TimeUnit.MINUTES.toMillis(75)),
            percentage = 100,
            timeshift = 0,
            profileName = "Test"
    )

    private fun generateTargetBlocks(): List<TargetBlock> {
        val blocks = mutableListOf<TargetBlock>()
        for (i in 1..24) blocks.add(TargetBlock(TimeUnit.HOURS.toMillis(1), 100.0, 100.0))
        return blocks
    }

    private fun generateBlocks(): List<Block> {
        val blocks = mutableListOf<Block>()
        for (i in 1..24) blocks.add(Block(TimeUnit.HOURS.toMillis(1), 1.0))
        return blocks
    }

}