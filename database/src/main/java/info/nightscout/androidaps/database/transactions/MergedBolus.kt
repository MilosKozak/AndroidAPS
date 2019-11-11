package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.Bolus
import info.nightscout.androidaps.database.entities.BolusCalculatorResult
import info.nightscout.androidaps.database.entities.Carbs
import info.nightscout.androidaps.database.entities.links.MealLink

data class MergedBolus(
        val mealLink: MealLink?,
        val bolus: Bolus?,
        val carbs: Carbs?,
        val bolusCalculatorResult: BolusCalculatorResult?
)