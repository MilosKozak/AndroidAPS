package info.nightscout.androidaps.utils

import info.nightscout.androidaps.database.entities.BolusCalculatorResult
import org.json.JSONObject
import java.util.*

fun BolusCalculatorResult.nsJSON(): String =
    JSONObject().apply {
        put("eventTime", DateUtil.toISOString(Date()))
        put("targetBGLow", targetBGLow)
        put("targetBGHigh", targetBGHigh)
        put("isf", isf)
        put("ic", ic)
        put("iob", -(bolusIOB + basalIOB))
        put("bolusiob", bolusIOB)
        put("basaliob", basalIOB)
        put("bolusiobused", wasBolusIOBUsed)
        put("basaliobused", wasBasalIOBUsed)
        put("bg", glucoseValue)
        put("insulinbg", glucoseInsulin)
        put("insulinbgused", wasGlucoseUsed)
        put("bgdiff", glucoseDifference)
        put("insulincarbs", carbsInsulin)
        put("carbs", carbs)
        put("cob", cob)
        put("cobused", wasCOBUsed)
        put("insulincob", cobInsulin)
        put("othercorrection", otherCorrection)
        put("insulintrend", trendInsulin)
        put("insulin", totalInsulin)
        put("superbolusused", wasSuperbolusUsed)
        put("insulinsuperbolus", superbolusInsulin)
        put("trendused", wasTrendUsed)
        put("insulintrend", trendInsulin)
        put("trend", glucoseTrend)
        put("ttused", wasTempTargetUsed)
    }.toString()
