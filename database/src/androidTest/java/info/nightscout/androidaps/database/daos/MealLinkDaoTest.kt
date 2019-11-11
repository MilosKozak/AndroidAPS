package info.nightscout.androidaps.database.daos

import info.nightscout.androidaps.database.entities.links.MealLink

class MealLinkDaoTest : AbstractDaoTest<MealLink>() {

    override fun copy(entry: MealLink) = entry.copy()

    override fun getDao() = database.mealLinkDao

    override fun generateTestEntry() = MealLink()

}