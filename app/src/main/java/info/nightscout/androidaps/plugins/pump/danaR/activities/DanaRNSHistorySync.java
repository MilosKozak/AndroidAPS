package info.nightscout.androidaps.plugins.pump.danaR.activities;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.DanaRHistoryRecord;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.pump.danaR.comm.RecordTypes;
import info.nightscout.androidaps.plugins.pump.danaR.events.EventDanaRSyncStatus;
import info.nightscout.androidaps.utils.DateUtil;
import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.List;

/**
 * Created by mike on 20.07.2016.
 */

public class DanaRNSHistorySync {
    public final static int SYNC_BOLUS = 0b00000001;
    public final static int SYNC_ERROR = 0b00000010;
    public final static int SYNC_REFILL = 0b00000100;
    public final static int SYNC_GLUCOSE = 0b00001000;
    public final static int SYNC_CARBO = 0b00010000;
    public final static int SYNC_ALARM = 0b00100000;
    public final static int SYNC_BASALHOURS = 0b01000000;
    public final static int SYNC_ALL = 0b11111111;
    public final static String DANARSIGNATURE = "DANARMESSAGE";
    private static Logger log = LoggerFactory.getLogger(L.PUMP);
    private List<DanaRHistoryRecord> historyRecords;

    public DanaRNSHistorySync(List<DanaRHistoryRecord> historyRecords) {
        this.historyRecords = historyRecords;
    }


    public void sync(int what) {
        try {
            Calendar cal = Calendar.getInstance();
            long records = historyRecords.size();
            long processing = 0;
            long uploaded = 0;
            if (L.isEnabled(L.PUMP))
                log.debug("Database contains " + records + " records");
            EventDanaRSyncStatus ev = new EventDanaRSyncStatus();
            for (DanaRHistoryRecord record : historyRecords) {
                processing++;
                if (record._id != null) continue;
                //log.debug(record.bytes);
                ev.message = MainApp.gs(R.string.uploading) + " " + processing + "/" + records + " "; // TODO: translations
                switch (record.recordCode) {
                    case RecordTypes.RECORD_TYPE_BOLUS:
                        if ((what & SYNC_BOLUS) == 0) break;
                        switch (record.bolusType) {
                            case "S":
                                if (L.isEnabled(L.PUMP))
                                    log.debug("Syncing standard bolus record " + record.recordValue + "U " + DateUtil.toISOString(record.recordDate));
                                NSUpload.getActiveUploader().uploadCareportalMealBolus(DateUtil.toISOString(record.recordDate), "openaps://" + MainApp.gs(R.string.app_name), DANARSIGNATURE + record.bytes, record.recordValue, null);
                                uploaded++;
                                ev.message += MainApp.gs(R.string.danar_sbolus);
                                break;
                            case "E":
                                if (record.recordDuration > 0) {
                                    if (L.isEnabled(L.PUMP))
                                        log.debug("Syncing extended bolus record " + record.recordValue + "U " + DateUtil.toISOString(record.recordDate));
                                    cal.setTimeInMillis(record.recordDate);
                                    cal.add(Calendar.MINUTE, -1 * record.recordDuration);
                                    NSUpload.getActiveUploader().uploadComboBolus(DateUtil.toISOString(cal.getTime()), "openaps://" + MainApp.gs(R.string.app_name), DANARSIGNATURE + record.bytes, 0.0, record.recordDuration, record.recordValue / record.recordDuration * 60, 0, 100);
                                    uploaded++;
                                    ev.message += MainApp.gs(R.string.danar_ebolus);
                                } else {
                                    if (L.isEnabled(L.PUMP))
                                        log.debug("NOT Syncing extended bolus record " + record.recordValue + "U " + DateUtil.toISOString(record.recordDate) + " zero duration");
                                }
                                break;
                            case "DS":
                                if (L.isEnabled(L.PUMP))
                                    log.debug("Syncing dual(S) bolus record " + record.recordValue + "U " + DateUtil.toISOString(record.recordDate));
                                NSUpload.getActiveUploader().uploadComboBolus(DateUtil.toISOString(record.recordDate),
                                        "openaps://" + MainApp.gs(R.string.app_name), DANARSIGNATURE + record.bytes,
                                        record.recordValue, null, null, 100, 0);
                                uploaded++;
                                ev.message += MainApp.gs(R.string.danar_dsbolus);
                                break;
                            case "DE":
                                if (L.isEnabled(L.PUMP))
                                    log.debug("Syncing dual(E) bolus record " + record.recordValue + "U " + DateUtil.toISOString(record.recordDate));
                                NSUpload.getActiveUploader().uploadComboBolus(DateUtil.toISOString(cal.getTime()),
                                        "openaps://" + MainApp.gs(R.string.app_name), DANARSIGNATURE + record.bytes,
                                        null, record.recordDuration, record.recordValue / record.recordDuration * 60, 0, 100);
                                uploaded++;
                                ev.message += MainApp.gs(R.string.danar_debolus);
                                break;
                            default:
                                log.error("Unknown bolus record");
                                break;
                        }
                        break;
                    case RecordTypes.RECORD_TYPE_ERROR:
                        if ((what & SYNC_ERROR) == 0) break;
                        if (L.isEnabled(L.PUMP))
                            log.debug("Syncing error record " + DateUtil.toISOString(record.recordDate));
                        NSUpload.getActiveUploader().uploadCareportalNote(DateUtil.toISOString(record.recordDate),
                                "openaps://" + MainApp.gs(R.string.app_name), "Error", DANARSIGNATURE + record.bytes);
                        uploaded++;
                        ev.message += MainApp.gs(R.string.danar_error);
                        break;
                    case RecordTypes.RECORD_TYPE_REFILL:
                        if ((what & SYNC_REFILL) == 0) break;
                        if (L.isEnabled(L.PUMP))
                            log.debug("Syncing refill record " + record.recordValue + " " + DateUtil.toISOString(record.recordDate));
                        NSUpload.getActiveUploader().uploadInsulinChangeEvent(DateUtil.toISOString(record.recordDate),
                                "openaps://" + MainApp.gs(R.string.app_name), "Refill " + record.recordValue + "U",
                                DANARSIGNATURE + record.bytes);
                        uploaded++;
                        ev.message += MainApp.gs(R.string.danar_refill);
                        break;
                    case RecordTypes.RECORD_TYPE_BASALHOUR:
                        if ((what & SYNC_BASALHOURS) == 0) break;
                        if (L.isEnabled(L.PUMP))
                            log.debug("Syncing basal hour record " + record.recordValue + " " + DateUtil.toISOString(record.recordDate));
                        NSUpload.getActiveUploader().uploadTempBasal(DateUtil.toISOString(record.recordDate), "openaps://" + MainApp.gs(R.string.app_name),
                                DANARSIGNATURE + record.bytes, 60, record.recordValue);
                        uploaded++;
                        ev.message += MainApp.gs(R.string.danar_basalhour);
                        break;
                    case RecordTypes.RECORD_TYPE_TB:
                        //log.debug("Ignoring TB record " + record.bytes + " " + DateUtil.toISOString(record.recordDate));
                        break;
                    case RecordTypes.RECORD_TYPE_GLUCOSE:
                        if ((what & SYNC_GLUCOSE) == 0) break;
                        if (L.isEnabled(L.PUMP))
                            log.debug("Syncing glucose record " + record.recordValue + " " + DateUtil.toISOString(record.recordDate));
                        NSUpload.getActiveUploader().uploadCareportalBgCheck(DateUtil.toISOString(record.recordDate),
                                "openaps://" + MainApp.gs(R.string.app_name), "Finger",
                                Profile.fromMgdlToUnits(record.recordValue, ProfileFunctions.getInstance().getProfileUnits()), null, DANARSIGNATURE + record.bytes);
                        uploaded++;
                        ev.message += MainApp.gs(R.string.danar_glucose);
                        break;
                    case RecordTypes.RECORD_TYPE_CARBO:
                        if ((what & SYNC_CARBO) == 0) break;
                        if (L.isEnabled(L.PUMP))
                            log.debug("Syncing carbo record " + record.recordValue + "g " + DateUtil.toISOString(record.recordDate));
                        NSUpload.getActiveUploader().uploadCareportalMealBolus(DateUtil.toISOString(record.recordDate),
                                "openaps://" + MainApp.gs(R.string.app_name), DANARSIGNATURE + record.bytes,
                                null, record.recordValue);
                        uploaded++;
                        ev.message += MainApp.gs(R.string.danar_carbohydrate);
                        break;
                    case RecordTypes.RECORD_TYPE_ALARM:
                        if ((what & SYNC_ALARM) == 0) break;
                        if (L.isEnabled(L.PUMP))
                            log.debug("Syncing alarm record " + record.recordAlarm + " " + DateUtil.toISOString(record.recordDate));
                        NSUpload.getActiveUploader().uploadCareportalNote(DateUtil.toISOString(record.recordDate),
                                "openaps://" + MainApp.gs(R.string.app_name), "Alarm: " + record.recordAlarm,
                                DANARSIGNATURE + record.bytes);
                        uploaded++;
                        ev.message += MainApp.gs(R.string.danar_alarm);
                        break;
                    case RecordTypes.RECORD_TYPE_SUSPEND: // TODO: this too
                    case RecordTypes.RECORD_TYPE_DAILY:
                    case RecordTypes.RECORD_TYPE_PRIME:
                        // Ignore
                        break;
                    default:
                        log.error("Unknown record type");
                        break;
                }
                MainApp.bus().post(ev);
            }
            ev.message = String.format(MainApp.gs(R.string.danar_totaluploaded), uploaded);
            MainApp.bus().post(ev);

        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
    }
}
