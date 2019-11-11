package info.nightscout.androidaps.utils

import info.nightscout.androidaps.database.BlockingAppRepository
import info.nightscout.androidaps.database.entities.Bolus
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.database.transactions.CancelTemporaryBasalTransaction
import info.nightscout.androidaps.database.transactions.InsertTemporaryBasalAndCancelCurrentTransaction
import info.nightscout.androidaps.database.transactions.MealBolusTransaction
import info.nightscout.androidaps.database.transactions.TherapyEventTransaction
import info.nightscout.androidaps.db.CareportalEvent
import org.json.JSONObject

fun saveCareportalJSON(json: JSONObject) {
    var type: TherapyEvent.Type? = null
    val timestamp: Long = DateUtil.fromISODateString(json.getString("created_at")).time
    var note: String? = null
    var amount: Double? = null
    var duration: Long = 0
    when (json.getString("eventType")) {
        CareportalEvent.BGCHECK -> {
            if (json.has("glucose")) {
                type = TherapyEvent.Type.FINGER_STICK_BG_VALUE
                amount = json.getDouble("glucose")
            }
        }
        CareportalEvent.ANNOUNCEMENT -> {
            if (json.has("notes")) {
                type = TherapyEvent.Type.ANNOUNCEMENT
                note = json.getString("notes")
            }
        }
        CareportalEvent.QUESTION -> {
            if (json.has("notes")) {
                type = TherapyEvent.Type.QUESTION
                note = json.getString("notes")
            }
        }
        CareportalEvent.NOTE -> {
            if (json.has("notes")) {
                type = TherapyEvent.Type.NOTE
                note = json.getString("notes")
            }
        }
        CareportalEvent.SENSORCHANGE -> type = TherapyEvent.Type.SENSOR_INSERTED
        "Sensor Start" -> type = TherapyEvent.Type.SENSOR_STARTED
        CareportalEvent.EXERCISE -> {
            type = TherapyEvent.Type.ACTIVITY
            if (json.has("duration")) {
                val jsonDuration = json.getDouble("duration")
                duration = (jsonDuration * 60000).toLong()
            }
        }
        CareportalEvent.SITECHANGE -> type = TherapyEvent.Type.CANNULA_CHANGED
        CareportalEvent.INSULINCHANGE -> type = TherapyEvent.Type.RESERVOIR_CHANGED
        CareportalEvent.PUMPBATTERYCHANGE -> type = TherapyEvent.Type.BATTERY_CHANGED
        CareportalEvent.OPENAPSOFFLINE -> {
            type = TherapyEvent.Type.APS_OFFLINE
            if (json.has("duration")) {
                val jsonDuration = json.getDouble("duration")
                duration = (jsonDuration * 60000).toLong()
            }
        }
        CareportalEvent.CORRECTIONBOLUS -> {
            if (json.has("insulin")) {
                val insulin = json.getDouble("insulin")
                if (insulin != 0.0) {
                    BlockingAppRepository.runTransaction(MealBolusTransaction(timestamp,
                            insulin, 0.0, Bolus.Type.NORMAL))
                }
            }
        }
        CareportalEvent.CARBCORRECTION -> {
            if (json.has("carbs")) {
                val carbs = json.getDouble("carbs")
                if (carbs != 0.0) {
                    BlockingAppRepository.runTransaction(MealBolusTransaction(timestamp,
                            0.0, carbs, Bolus.Type.NORMAL))
                }
            }
        }
        CareportalEvent.MEALBOLUS, "Snack Bolus" -> {
            val carbs = if (json.has("carbs")) {
                json.getDouble("carbs")
            } else {
                0.0
            }
            val carbTime = if (json.has("preBolus")) {
                (json.getDouble("preBolus") * 60000).toLong()
            } else {
                0L
            }
            val insulin = if (json.has("insulin")) {
                json.getDouble("insulin")
            } else {
                0.0
            }
            if (!(carbs == 0.0 && insulin == 0.0)) {
                BlockingAppRepository.runTransaction(MealBolusTransaction(timestamp,
                        insulin, carbs, Bolus.Type.NORMAL, carbTime))
            }
        }
        CareportalEvent.TEMPBASAL -> {
            if (!json.has("duration")) {
                try {
                    BlockingAppRepository.runTransaction(CancelTemporaryBasalTransaction(timestamp))
                } catch (ignored: IllegalStateException) {
                }
            } else {
                val duration = (json.getDouble("duration") * 60000).toLong()
                if (json.has("percent")) {
                    val percent = json.getDouble("percent")
                    BlockingAppRepository.runTransaction(InsertTemporaryBasalAndCancelCurrentTransaction(timestamp, duration, false, percent))
                } else {
                    val absolute = json.getDouble("percent")
                    BlockingAppRepository.runTransaction(InsertTemporaryBasalAndCancelCurrentTransaction(timestamp, duration, true, absolute))
                }
            }
        }
    }
    if (type != null) {
        BlockingAppRepository.runTransaction(TherapyEventTransaction(timestamp, type, amount,note, duration))
    }
}