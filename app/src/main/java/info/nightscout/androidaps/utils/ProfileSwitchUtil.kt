package info.nightscout.androidaps.utils

import info.nightscout.androidaps.database.Block
import info.nightscout.androidaps.database.BlockingAppRepository
import info.nightscout.androidaps.database.TargetBlock
import info.nightscout.androidaps.database.embedments.InsulinConfiguration
import info.nightscout.androidaps.database.entities.ProfileSwitch
import info.nightscout.androidaps.database.transactions.InsertProfileSwitchTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

fun ProfileSwitch.convert(): info.nightscout.androidaps.db.ProfileSwitch {
    val converted = info.nightscout.androidaps.db.ProfileSwitch()
    converted.backing = this
    converted.profileName = profileName
    converted.date = timestamp
    converted.timeshift = timeshift
    converted.percentage = percentage
    converted.durationInMinutes = if (duration == Long.MAX_VALUE) 0 else Math.round(duration / 60000.0).toInt()
    val json = JSONObject()
    json.put("dia", (insulinConfiguration.insulinEndTime / (60 * 60 * 1000).toDouble()).roundToInt())
    json.put("carbratio", convertBlocks(icBlocks))
    json.put("sens", convertBlocks(isfBlocks))
    json.put("basal", convertBlocks(basalBlocks))
    json.put("target_low", convertBlocks(targetBlocks, false))
    json.put("target_high", convertBlocks(targetBlocks, false))
    json.put("units", when (glucoseUnit) {
        ProfileSwitch.GlucoseUnit.MGDL -> "mg/dl"
        ProfileSwitch.GlucoseUnit.MMOL -> "mmol"
    })
    converted.profileJson = json.toString()
    return converted

}

fun info.nightscout.androidaps.db.ProfileSwitch.storeInNewDatabase() {
    val json = JSONObject(profileJson)
    val glucoseUnit = when(json.getString("units")) {
        "mg/dl" -> ProfileSwitch.GlucoseUnit.MGDL
        "mmol" -> ProfileSwitch.GlucoseUnit.MMOL
        else -> throw IllegalArgumentException("Unknown glucose unit")
    }
    val duration = if (durationInMinutes == 0) Long.MAX_VALUE else durationInMinutes * 60000L
    BlockingAppRepository.runTransaction(InsertProfileSwitchTransaction(
            date,
            profileName,
            glucoseUnit,
            convertBlocks(json.getJSONArray("basal")),
            convertBlocks(json.getJSONArray("sens")),
            convertBlocks(json.getJSONArray("carbratio")),
            convertBlocks(json.getJSONArray("target_low"), json.getJSONArray("target_high")),
            InsulinConfiguration("", json.getInt("dia") * 60L * 60L * 1000L, -1),
            timeshift,
            percentage,
            duration
    ))
}

private fun convertBlocks(blocks: List<Block>): JSONArray {
    val dateFormatter = SimpleDateFormat("HH:mm", Locale.US)
    dateFormatter.timeZone = TimeZone.getTimeZone("UTC")
    val jsonBlocks = JSONArray()
    var timeAsSeconds = 0
    blocks.forEach {
        val jsonBlock = JSONObject()
        jsonBlock.put("timeAsSeconds", timeAsSeconds)
        jsonBlock.put("time", dateFormatter.format(Date(timeAsSeconds * 1000L)))
        jsonBlock.put("value", it.amount)
        timeAsSeconds += (it.duration / 1000L).toInt()
        jsonBlocks.put(jsonBlock)
    }
    return jsonBlocks
}

private fun convertBlocks(jsonBlocks: JSONArray): MutableList<Block> {
    val blocks = mutableListOf<Block>()
    for (i in 0 until jsonBlocks.length()) {
        val jsonBlock = jsonBlocks.getJSONObject(i)
        val next = if (jsonBlocks.length() - 2 < i) null else jsonBlocks.getJSONObject(i + 1)
        val endSeconds = next?.getInt("timeAsSeconds") ?: 24 * 60 * 60
        val duration = (endSeconds - jsonBlock.getInt("timeAsSeconds")) * 1000L
        val amount = jsonBlock.getDouble("value")
        blocks.add(Block(duration, amount))
    }
    return blocks
}

private fun convertBlocks(blocks: List<TargetBlock>, high: Boolean): JSONArray {
    val dateFormatter = SimpleDateFormat("HH:mm", Locale.US)
    dateFormatter.timeZone = TimeZone.getTimeZone("UTC")
    val jsonBlocks = JSONArray()
    var timeAsSeconds = 0
    blocks.forEach {
        val jsonBlock = JSONObject()
        jsonBlock.put("timeAsSecond", timeAsSeconds)
        jsonBlock.put("time", dateFormatter.format(Date(timeAsSeconds * 1000L)))
        jsonBlock.put("value", if (high) it.highTarget else it.lowTarget)
        timeAsSeconds += (it.duration / 1000L).toInt()
        jsonBlocks.put(jsonBlock)
    }
    return jsonBlocks
}

private fun convertBlocks(lowJSONBlocks: JSONArray, highJSONBlocks: JSONArray): MutableList<TargetBlock> {
    val blocks = mutableListOf<TargetBlock>()
    for (i in 0 until lowJSONBlocks.length()) {
        val lowJSONBlock = lowJSONBlocks.getJSONObject(i)
        val highJSONBlock = highJSONBlocks.getJSONObject(i)
        val lowTimeAsSeconds = lowJSONBlock.getInt("timeAsSeconds")
        val highTimeAsSeconds = highJSONBlock.getInt("timeAsSeconds")
        if (lowTimeAsSeconds != highTimeAsSeconds) throw IllegalArgumentException("High and low target blocks are not well aligned.")
        val next = if (lowJSONBlocks.length() - 2 < i) null else lowJSONBlocks.getJSONObject(i + 1)
        val endSeconds = next?.getInt("timeAsSeconds") ?: 24 * 60 * 60
        val duration = (endSeconds - lowTimeAsSeconds) * 1000L
        val lowTarget = lowJSONBlock.getDouble("value")
        val highTarget = highJSONBlock.getDouble("value")
        blocks.add(TargetBlock(duration, lowTarget, highTarget))
    }
    return blocks
}