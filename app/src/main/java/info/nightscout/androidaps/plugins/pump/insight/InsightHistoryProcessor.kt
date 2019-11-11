package info.nightscout.androidaps.plugins.pump.insight

import info.nightscout.androidaps.database.BlockingAppRepository
import info.nightscout.androidaps.database.transactions.insight.InsightHistoryTransaction
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.*
import info.nightscout.androidaps.plugins.pump.insight.descriptors.AlertType
import info.nightscout.androidaps.plugins.pump.insight.descriptors.BolusType
import info.nightscout.androidaps.plugins.pump.insight.descriptors.OperatingMode
import java.util.*

class InsightHistoryProcessor(pumpSerial: String, private var timeOffset: Long, private val historyEvents: List<HistoryEvent>) {

    private var run = false
    private val transaction = InsightHistoryTransaction(pumpSerial)

    fun processHistoryEvents() {
        if (run) throw IllegalStateException("InsightHistoryProcessor cannot be run twice.")
        run = true
        historyEvents.reversed().forEach {
            when (it) {
                //TODO: Respect settings
                is DateTimeChangedEvent -> processDateTimeChangedEvent(it)
                is CannulaFilledEvent -> processTherapyEvent(InsightHistoryTransaction.TherapyEvent.Type.CANNULA_FILLED, it)
                is TubeFilledEvent -> processTherapyEvent(InsightHistoryTransaction.TherapyEvent.Type.TUBE_FILLED, it)
                is SniffingDoneEvent -> processTherapyEvent(InsightHistoryTransaction.TherapyEvent.Type.RESERVOIR_CHANGED, it)
                is PowerUpEvent -> processTherapyEvent(InsightHistoryTransaction.TherapyEvent.Type.BATTERY_CHANGED, it)
                is OperatingModeChangedEvent -> processOperatingModeChangedEvent(it)
                is TotalDailyDoseEvent -> processTotalDailyDoseEvent(it)
                is StartOfTBREvent -> processStartOfTBREvent(it)
                is EndOfTBREvent -> processEndOfTBREvent(it)
                is BolusProgrammedEvent -> processBolusProgrammedEvent(it)
                is BolusDeliveredEvent -> processBolusDeliveredEvent(it)
                is OccurrenceOfAlertEvent -> processOccurrenceOfAlertEvent(it)
            }
        }
        transaction.therapyEvents.sortBy { it.eventId }
        transaction.operatingModeChanges.sortBy { it.eventId }
        transaction.boluses.sortBy { it.eventId }
        transaction.temporaryBasals.sortBy { it.eventId }
        transaction.totalDailyDoses.sortBy { it.eventId }
        BlockingAppRepository.runTransaction(transaction)
    }

    private fun processDateTimeChangedEvent(event: DateTimeChangedEvent) {
        val timeAfter = parseDate(event.eventYear, event.eventMonth, event.eventDay, event.eventHour, event.eventMinute, event.eventSecond)
        val timeBefore = parseDate(event.beforeYear, event.beforeMonth, event.beforeDay, event.beforeHour, event.beforeMinute, event.beforeSecond)
        timeOffset -= timeAfter - timeBefore
    }

    private fun processOccurrenceOfAlertEvent(event: OccurrenceOfAlertEvent) {
        val type = when (event.alertType) {
            AlertType.MAINTENANCE_21 -> InsightHistoryTransaction.TherapyEvent.Type.RESERVOIR_EMPTY
            AlertType.MAINTENANCE_22 -> InsightHistoryTransaction.TherapyEvent.Type.BATTERY_EMPTY
            AlertType.MAINTENANCE_24 -> InsightHistoryTransaction.TherapyEvent.Type.OCCLUSION
            else -> null
        }
        if (type != null) transaction.therapyEvents.add(InsightHistoryTransaction.TherapyEvent(
                event.eventPosition,
                event.adjustedTimestamp,
                type = type
        ))
    }

    private fun processBolusProgrammedEvent(event: BolusProgrammedEvent) {
        transaction.boluses.add(InsightHistoryTransaction.Bolus(
                true,
                event.eventPosition,
                event.bolusType.toTransactionValue(),
                event.adjustedTimestamp,
                event.bolusID,
                event.immediateAmount,
                event.duration.toLong() * 60000L,
                event.extendedAmount
        ))
    }

    private fun processBolusDeliveredEvent(event: BolusDeliveredEvent) {
        transaction.boluses.add(InsightHistoryTransaction.Bolus(
                false,
                event.eventPosition,
                event.bolusType.toTransactionValue(),
                event.adjustedStartTimestamp,
                event.bolusID,
                event.immediateAmount,
                event.duration.toLong() * 60000L,
                event.extendedAmount
        ))
    }

    private fun BolusType.toTransactionValue() = when (this) {
        BolusType.STANDARD -> InsightHistoryTransaction.Bolus.Type.STANDARD
        BolusType.EXTENDED -> InsightHistoryTransaction.Bolus.Type.EXTENDED
        BolusType.MULTIWAVE -> InsightHistoryTransaction.Bolus.Type.MULTIWAVE
    }

    private fun processStartOfTBREvent(event: StartOfTBREvent) {
        transaction.temporaryBasals.add(InsightHistoryTransaction.TemporaryBasal(
                true,
                event.eventPosition,
                event.adjustedTimestamp,
                event.duration.toLong() * 60000L,
                event.amount
        ))
    }

    private fun processEndOfTBREvent(event: EndOfTBREvent) {
        transaction.temporaryBasals.add(InsightHistoryTransaction.TemporaryBasal(
                false,
                event.eventPosition,
                event.adjustedStartTimestamp,
                event.duration.toLong() * 60000L,
                event.amount
        ))
    }

    private fun processTotalDailyDoseEvent(event: TotalDailyDoseEvent) {
        transaction.totalDailyDoses.add(InsightHistoryTransaction.TotalDailyDose(
                event.eventPosition,
                parseDate(event.totalYear, event.totalMonth, event.totalDay, 0, 0, 0, false),
                event.bolusTotal,
                event.basalTotal
        ))
    }

    private fun processOperatingModeChangedEvent(event: OperatingModeChangedEvent) {
        transaction.operatingModeChanges.add(InsightHistoryTransaction.OperatingModeChange(
                event.eventPosition,
                event.adjustedTimestamp,
                event.oldValue.toTransactionValue(),
                event.newValue.toTransactionValue()
        ))
    }

    private fun OperatingMode.toTransactionValue() = when (this) {
        OperatingMode.STARTED -> InsightHistoryTransaction.OperatingModeChange.OperatingMode.STARTED
        OperatingMode.STOPPED -> InsightHistoryTransaction.OperatingModeChange.OperatingMode.STOPPED
        OperatingMode.PAUSED -> InsightHistoryTransaction.OperatingModeChange.OperatingMode.PAUSED
    }

    private fun processTherapyEvent(type: InsightHistoryTransaction.TherapyEvent.Type, event: HistoryEvent) {
        transaction.therapyEvents.add(InsightHistoryTransaction.TherapyEvent(
                event.eventPosition,
                event.adjustedTimestamp,
                type
        ))
    }

    private val HistoryEvent.adjustedTimestamp: Long get() = parseDate(eventYear, eventMonth, eventDay, eventHour, eventMinute, eventSecond) + timeOffset

    private val HistoryEventWithStart.adjustedStartTimestamp: Long get() = parseRelativeDate(eventYear, eventMonth, eventDay, eventHour, eventMinute, eventSecond, startHour, startMinute, startSecond) + timeOffset

    private fun parseDate(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int, utc: Boolean = true): Long {
        val calendar = Calendar.getInstance(if (utc) TimeZone.getTimeZone("UTC") else TimeZone.getDefault())
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.MONTH, month - 1)
        calendar.set(Calendar.DAY_OF_MONTH, day)
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, second)
        return calendar.timeInMillis
    }

    private fun parseRelativeDate(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int, relativeHour: Int, relativeMinute: Int, relativeSecond: Int): Long {
        var day = day
        if (relativeHour * 60 * 60 + relativeMinute * 60 + relativeSecond >= hour * 60 * 60 * minute * 60 + second)
            day--
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.MONTH, month - 1)
        calendar.set(Calendar.DAY_OF_MONTH, day)
        calendar.set(Calendar.HOUR_OF_DAY, relativeHour)
        calendar.set(Calendar.MINUTE, relativeMinute)
        calendar.set(Calendar.SECOND, relativeSecond)
        return calendar.timeInMillis
    }

}