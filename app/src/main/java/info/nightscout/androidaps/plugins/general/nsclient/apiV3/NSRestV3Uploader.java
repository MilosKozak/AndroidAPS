package info.nightscout.androidaps.plugins.general.nsclient.apiV3;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.*;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.aps.loop.APSResult;
import info.nightscout.androidaps.plugins.aps.loop.DeviceStatus;
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.nsclient.UploadService;
import info.nightscout.androidaps.plugins.general.nsclient.data.DbLogger;
import info.nightscout.androidaps.services.Intents;
import info.nightscout.androidaps.utils.BatteryLevel;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.api.v3.documents.Collections;
import info.nightscout.api.v3.documents.DocumentBase;
import info.nightscout.api.v3.documents.Entry;
import info.nightscout.api.v3.documents.Treatment;
import info.nightscout.api.v3.documents.treatments.TempBasal;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import static info.nightscout.api.v3.documents.EventType.*;

/**
 * @author tanja
 */
public class NSRestV3Uploader implements UploadService {
    private Logger log = LoggerFactory.getLogger(L.NSRESTV3);

    private boolean isUseAbsoluteBasal() {
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        return SP.getBoolean("ns_sync_use_absolute", false);
    }

    public void uploadTempBasalStartPercent(TemporaryBasal temporaryBasal) {
        try {
            Profile profile = ProfileFunctions.getInstance().getProfile(temporaryBasal.date);
            double absoluteRate = 0;
            if (profile != null) {
                absoluteRate = profile.getBasal(temporaryBasal.date) * temporaryBasal.percentRate / 100d;
            }
            if (isUseAbsoluteBasal()) {
                TemporaryBasal t = temporaryBasal.clone();
                t.isAbsolute = true;
                if (profile != null) {
                    t.absoluteRate = absoluteRate;
                    uploadTempBasalStartAbsolute(t, null);
                }
            } else {
                TempBasal data = new TempBasal();
                data.duration = temporaryBasal.durationInMinutes;
                data.percent = temporaryBasal.percentRate - 100;
                if (profile != null) {
                    data.rate = absoluteRate;
                }
                if (temporaryBasal.pumpId != 0) {
                    data.device = Long.toString(temporaryBasal.pumpId);
                }
                data.created_at = DateUtil.toISOString(temporaryBasal.date);
                data.enteredBy = "openaps://" + MainApp.gs(R.string.app_name);
                add(data, "treatments");
            }
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
    }

    private void add(DocumentBase data, String collection) {
        Context context = MainApp.instance().getApplicationContext();
        Bundle bundle = getBundle("dbAdd", collection);
        bundle.putSerializable("data", data);
        Intent intent = getIntent(bundle);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        DbLogger.dbAdd(intent, data.toString());
    }


    private void dbUpdate(DocumentBase data, String collection) {
        Context context = MainApp.instance().getApplicationContext();
        Bundle bundle = getBundle("dbUpdate", collection);
        bundle.putSerializable("data", data);
        bundle.putString("_id", data.identifier);
        Intent intent = getIntent(bundle);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        DbLogger.dbAdd(intent, data.toString());
    }

    @NotNull
    private Intent getIntent(Bundle bundle) {
        Intent intent = new Intent(Intents.ACTION_DATABASE);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        return intent;
    }


    @NotNull
    private Bundle getBundle(String action, String collection) {
        Bundle bundle = new Bundle();
        bundle.putString("action", action);
        bundle.putString("collection", collection);
        return bundle;
    }

    private void dbRemove(String _id, String collection) {
        Context context = MainApp.instance().getApplicationContext();
        Bundle bundle = getBundle("dbRemove", collection);
        bundle.putString("_id", _id);
        Intent intent = getIntent(bundle);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        DbLogger.dbRemove(intent, _id);
    }

    public void uploadTempBasalStartAbsolute(TemporaryBasal temporaryBasal, Double originalExtendedAmount) {

        TempBasal tempBasal = new TempBasal();
        tempBasal.duration = temporaryBasal.durationInMinutes;
        tempBasal.rate = temporaryBasal.absoluteRate;
        if (temporaryBasal.pumpId != 0) {
            tempBasal.device = Long.toString(temporaryBasal.pumpId);
        }
        tempBasal.created_at = DateUtil.toISOString(temporaryBasal.date);
        tempBasal.enteredBy = "openaps://" + MainApp.gs(R.string.app_name);
        //if (originalExtendedAmount != null)
        //    data.put("originalExtendedAmount", originalExtendedAmount); // for back synchronization
        add(tempBasal, "treatments");

    }

    public void uploadTempBasalEnd(long time, boolean isFakedTempBasal, long pumpId) {
        try {
            TempBasal data = new TempBasal();
            data.created_at = DateUtil.toISOString(time);
            data.enteredBy = "openaps://" + MainApp.gs(R.string.app_name);
            if (isFakedTempBasal) {
                data.isFakedTempBasal = Boolean.TRUE;
            }
            if (pumpId != 0) {
                data.device = Long.toString(pumpId);
            }
            add(data, "treatments");

        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
    }

    public void uploadExtendedBolus(ExtendedBolus extendedBolus) {
        try {
            info.nightscout.api.v3.documents.treatments.ExtendedBolus bolus = new info.nightscout.api.v3.documents.treatments.ExtendedBolus();
            bolus.duration = extendedBolus.durationInMinutes;
            bolus.splitNow = 0;
            bolus.splitExt = 100;
            bolus.enteredInsulin = extendedBolus.insulin;
            bolus.relative = extendedBolus.insulin / extendedBolus.durationInMinutes * 60; // U/h
            if (extendedBolus.pumpId != 0) {
                bolus.device = Long.toString(extendedBolus.pumpId);
            }
            bolus.created_at = DateUtil.toISOString(extendedBolus.date);
            bolus.enteredBy = "openaps://" + MainApp.gs(R.string.app_name);
            add(bolus, "treatments");
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
    }

    public void uploadExtendedBolusEnd(long time, long pumpId) {
        try {
            info.nightscout.api.v3.documents.treatments.ExtendedBolus treatment = new info.nightscout.api.v3.documents.treatments.ExtendedBolus();
            treatment.duration = 0;
            treatment.splitNow = 0;
            treatment.splitExt = 100;
            treatment.enteredinsulin = 0;
            treatment.relative = 0;
            treatment.created_at = DateUtil.toISOString(time);
            treatment.enteredBy = "openaps://" + MainApp.gs(R.string.app_name);
            if (pumpId != 0) {
                treatment.device = Long.toString(pumpId);
            }
            add(treatment, "treatments");

        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
    }

    public void uploadDeviceStatus() {
        Profile profile = ProfileFunctions.getInstance().getProfile();
        String profileName = ProfileFunctions.getInstance().getProfileName();

        if (profile == null || profileName == null) {
            log.error("Profile is null. Skipping upload");
            return;
        }

        DeviceStatus deviceStatus = new DeviceStatus();
        try {
            LoopPlugin.LastRun lastRun = LoopPlugin.lastRun;
            if (lastRun != null && lastRun.lastAPSRun.getTime() > System.currentTimeMillis() - 300 * 1000L) {
                // do not send if result is older than 1 min
                APSResult apsResult = lastRun.request;
                apsResult.json().put("timestamp", DateUtil.toISOString(lastRun.lastAPSRun));
                deviceStatus.suggested = apsResult.json();

                deviceStatus.iob = lastRun.request.iob.json();
                deviceStatus.iob.put("time", DateUtil.toISOString(lastRun.lastAPSRun));

                JSONObject requested = new JSONObject();

                if (lastRun.tbrSetByPump != null && lastRun.tbrSetByPump.enacted) { // enacted
                    deviceStatus.enacted = lastRun.request.json();
                    deviceStatus.enacted.put("rate", lastRun.tbrSetByPump.json(profile).get("rate"));
                    deviceStatus.enacted.put("duration", lastRun.tbrSetByPump.json(profile).get("duration"));
                    deviceStatus.enacted.put("recieved", true);
                    requested.put("duration", lastRun.request.duration);
                    requested.put("rate", lastRun.request.rate);
                    requested.put("temp", "absolute");
                    deviceStatus.enacted.put("requested", requested);
                }
                if (lastRun.smbSetByPump != null && lastRun.smbSetByPump.enacted) { // enacted
                    if (deviceStatus.enacted == null) {
                        deviceStatus.enacted = lastRun.request.json();
                    }
                    deviceStatus.enacted.put("smb", lastRun.smbSetByPump.bolusDelivered);
                    requested.put("smb", lastRun.request.smb);
                    deviceStatus.enacted.put("requested", requested);
                }
            } else {
                if (L.isEnabled(L.NSCLIENT))
                    log.debug("OpenAPS data too old to upload");
            }
            deviceStatus.device = "openaps://" + Build.MANUFACTURER + " " + Build.MODEL;
            JSONObject pumpstatus = ConfigBuilderPlugin.getPlugin().getActivePump().getJSONStatus(profile, profileName);
            if (pumpstatus != null) {
                deviceStatus.pump = pumpstatus;
            }

            int batteryLevel = BatteryLevel.getBatteryLevel();
            deviceStatus.uploaderBattery = batteryLevel;

            deviceStatus.created_at = DateUtil.toISOString(new Date());
            Context context = MainApp.instance().getApplicationContext();
            Bundle bundle = getBundle("dbAdd", "devicestatus");
            bundle.putString("data", deviceStatus.mongoRecord().toString());
            Intent intent = getIntent(bundle);
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
            DbLogger.dbAdd(intent, deviceStatus.mongoRecord().toString());
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    public void uploadTreatmentRecord(DetailedBolusInfo detailedBolusInfo) {
        Treatment data = new Treatment();
        try {
            data.eventType = detailedBolusInfo.eventType;

            if (detailedBolusInfo.insulin != 0d) {
                data.insulin = detailedBolusInfo.insulin;
            }
            if (detailedBolusInfo.carbs != 0d) {
                data.carbs = (int) detailedBolusInfo.carbs;
            }
            data.created_at = DateUtil.toISOString(detailedBolusInfo.date);
            data.date = detailedBolusInfo.date;
            data.isSMB = detailedBolusInfo.isSMB;
            if (detailedBolusInfo.pumpId != 0) {
                data.device = String.valueOf(detailedBolusInfo.pumpId);
            }
            if (detailedBolusInfo.glucose != 0d) {
                data.glucose = detailedBolusInfo.glucose;
            }
            if (!detailedBolusInfo.glucoseType.equals("")) {
                data.glucoseType = detailedBolusInfo.glucoseType;
            }
            if (detailedBolusInfo.boluscalc != null) {
                data.boluscalc = detailedBolusInfo.boluscalc.toString();
            }
            if (detailedBolusInfo.carbTime != 0) {
                data.carbTime = detailedBolusInfo.carbTime;
            }
            if (!StringUtils.isEmpty(detailedBolusInfo.notes)) {
                data.notes = detailedBolusInfo.notes;
            }
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
        uploadCareportalEntryToNS(data);
    }

    public void uploadProfileSwitch(ProfileSwitch profileSwitch) {

        Treatment data = mapProfileSwitch(profileSwitch);
        uploadCareportalEntryToNS(data);

    }

    public void uploadTempTarget(TempTarget tempTarget) {
        Profile profile = ProfileFunctions.getInstance().getProfile();

        if (profile == null) {
            log.error("Profile is null. Skipping upload");
            return;
        }

        info.nightscout.api.v3.documents.treatments.TempTarget data = new info.nightscout.api.v3.documents.treatments.TempTarget();
        data.duration = tempTarget.durationInMinutes;
        data.reason = tempTarget.reason;
        data.targetBottom = Profile.fromMgdlToUnits(tempTarget.low, profile.getUnits());
        data.targetTop = Profile.fromMgdlToUnits(tempTarget.high, profile.getUnits());
        data.created_at = DateUtil.toISOString(tempTarget.date);
        data.units = profile.getUnits();
        data.enteredBy = MainApp.gs(R.string.app_name);
        uploadCareportalEntryToNS(data);
    }

    public void updateProfileSwitch(ProfileSwitch profileSwitch) {
        Treatment data = mapProfileSwitch(profileSwitch);
        if (profileSwitch._id != null) {
            dbUpdate(data, "treatments");
        }
    }

    private Treatment mapProfileSwitch(ProfileSwitch profileSwitch) {
        info.nightscout.api.v3.documents.treatments.ProfileSwitch data = new info.nightscout.api.v3.documents.treatments.ProfileSwitch();
        data.duration = profileSwitch.durationInMinutes;
        data.profile = profileSwitch.getCustomizedName();
        data.profileJson = profileSwitch.profileJson;
        data.profilePlugin = profileSwitch.profilePlugin;
        if (profileSwitch.isCPP) {
            data.CircadianPercentageProfile = true;
            data.timeshift = profileSwitch.timeshift;
            data.percentage = profileSwitch.percentage;
        }
        data.created_at = DateUtil.toISOString(profileSwitch.date);
        data.enteredBy = MainApp.gs(R.string.app_name);

        return data;
    }

    public void uploadCareportalEntryToNS(Treatment data) {
        try {
            if (data.carbTime != null && data.carbs != null) {
                Treatment prebolus = new Treatment();
                prebolus.carbs = data.carbs;
                data.carbs = 0;
                prebolus.eventType = data.eventType;

                if (data.enteredBy != null) {
                    prebolus.enteredBy = data.enteredBy;
                }
                if (data.notes != null) {
                    prebolus.notes = data.notes;
                }
                long mills = DateUtil.fromISODateString(data.created_at).getTime();
                Date preBolusDate = new Date(mills + data.carbTime.intValue() * 60000L + 1000L);
                prebolus.created_at = DateUtil.toISOString(preBolusDate);
                uploadCareportalEntryToNS(prebolus);
            }
            add(data, "treatments");

        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }

    }

    public void removeCareportalEntryFromNS(String _id) {
        try {
            dbRemove(_id, "treatments");

        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }

    }

    public void uploadOpenAPSOffline(int durationInMinutes) {
        try {
            Treatment data = new Treatment();
            data.eventType = OPENAPS_OFFLINE;
            data.duration = durationInMinutes;
            data.created_at = DateUtil.toISOString(new Date());
            data.enteredBy = "openaps://" + MainApp.gs(R.string.app_name);
            add(data, "treatments");

        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
    }

    public void uploadError(String error) {
        uploadError(error, new Date());
    }

    public void uploadError(String error, Date date) {
        Treatment data = new Treatment();
        data.eventType = ANNOUNCEMENT;
        data.created_at = DateUtil.toISOString(date);
        data.enteredBy = SP.getString("careportal_enteredby", MainApp.gs(R.string.app_name));
        data.notes = error;
        //data.isAnnouncement = true; //TODO duplicate to eventType

        add(data, "treatments");
    }

    public void uploadCareportalBgCheck(BgReading reading, String source) {
        Entry data = new Entry();
        data.device = source;
        data.date = reading.date;
        data.dateString = DateUtil.toISOString(reading.date);
        data.sgv = reading.value;
        data.direction = reading.direction;
        data.type = "sgv";

        add(data, "entries");
    }

//    @Override
//    public void uploadCareportalBgCheck(String device, String createdAt, String type, Integer glucose, String units) {
//        Entry data = new Entry();
//        data.device = device;
//        data.dateString = createdAt;
//        data.sgv = glucose;
//        data.units = units;
//        data.type = type;
//        add(data, "entries");
//    }

    public void uploadAppStart() {
        if (SP.getBoolean(R.string.key_ns_logappstartedevent, true)) {
            Treatment data = new Treatment();
            try {
                data.eventType = NOTE;
                data.created_at = DateUtil.toISOString(new Date());
                data.notes = MainApp.gs(R.string.androidaps_start) + " - " + Build.MANUFACTURER + " " + Build.MODEL;
            } catch (Exception e) {
                log.error("Unhandled exception", e);
            }
            add(data, "treatments");
        }
    }

    public void uploadEvent(String careportalEvent, long time, @Nullable String notes) {
        Treatment data = new Treatment();
        try {
            data.eventType = careportalEvent;
            data.created_at = DateUtil.toISOString(time);
            data.enteredBy = SP.getString("careportal_enteredby", MainApp.gs(R.string.app_name));
            if (notes != null) {
                data.notes = notes;
            }
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
        add(data, "treatments");
    }

    public void removeFoodFromNS(String _id) {
        try {
            dbRemove(_id, "food");
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
    }

    public boolean isIdValid(String _id) {
        if (_id == null)
            return false;
        return _id.length() == 24;
    }

    @Override
    public void uploadSensorChange(String enteredBy, String created_at, String device) {
        Treatment data = new Treatment();
        data.eventType = CareportalEvent.SENSORCHANGE;
        data.enteredBy = enteredBy;
        data.created_at = created_at;
        data.device = device;
        addTreatments(data);
    }

    @Override
    public void uploadInsulinChangeEvent(String createdBy, String createdAt, String note, String device) {
        Treatment treatment = new Treatment();
        treatment.eventType = CareportalEvent.INSULINCHANGE;
        treatment.enteredBy = createdBy;
        treatment.created_at = createdAt;
        treatment.notes = note;
        if (device != null)
            treatment.device = device;
        addTreatments(treatment);
    }

    @Override
    public void uploadBatteryChanged(String createdBy, String createdAt, String note, String device) {
        Treatment treatment = new Treatment();
        treatment.eventType = CareportalEvent.PUMPBATTERYCHANGE;
        treatment.enteredBy = createdBy;
        treatment.created_at = createdAt;
        treatment.notes = note;
        if (device != null)
            treatment.device = device;
        addTreatments(treatment);
    }

    @Override
    public void uploadSiteChange(String enteredBy, String created_at, String device) {
        Treatment treatment = new Treatment();
        treatment.eventType = CareportalEvent.SITECHANGE;
        treatment.enteredBy = enteredBy;
        treatment.created_at = created_at;
        treatment.device = device;
        addTreatments(treatment);
    }

    @Override
    public void uploadCareportalNote(String createdAt, String createdBy, String note, String device) {
        Treatment treatment = new Treatment();
        treatment.eventType = CareportalEvent.NOTE;
        treatment.enteredBy = createdBy;
        treatment.created_at = createdAt;
        treatment.notes = note;
        if (device != null)
            treatment.device = device;
        addTreatments(treatment);
    }

    public void addTreatments(Treatment data) {
        add(data, Collections.TREATMENTS);
    }

    @Override
    public void uploadCareportalBgCheck(String createdAt, String createdBy, String glucoseType, Number glucose, String units, String device) {

        Treatment data = new Treatment();
        data.eventType = CareportalEvent.BGCHECK;
        data.enteredBy = createdBy;
        data.created_at = createdAt;
        data.glucoseType = glucoseType;
        data.glucose = glucose.doubleValue();
        data.units = units;
        data.device = device;
        addTreatments(data);
    }

    @Override
    public void uploadComboBolus(String createdAt, String enteredBy, String deviceSignature, Double insulin, Integer duration,
                                 Double relative, Integer splitNow, Integer splitExt) {
        Treatment treatment = new Treatment();
        treatment.device = deviceSignature;
        treatment.eventType = COMBO_BOLUS;
        treatment.insulin = insulin;
        treatment.duration = duration;
        treatment.relative = relative;
        treatment.splitNow = splitNow;
        treatment.splitExt = splitExt;
        treatment.created_at = createdAt;
        treatment.enteredBy = enteredBy;
        addTreatments(treatment);
    }

    @Override
    public void uploadCareportalMealBolus(String createdAt, String enteredBy, String pumpSignature, Double insulin, Double carbs) {
        Treatment treatment = new Treatment();
        treatment.eventType = MEAL;
        treatment.device = pumpSignature;
        treatment.insulin = insulin;
        treatment.carbs = carbs;
        treatment.created_at = createdAt;
        treatment.enteredBy = enteredBy;
        add(treatment, Collections.TREATMENTS);
    }

    @Override
    public void uploadTempBasal(String createdAt, String createdBy, String device, Integer duration, Double absolute) {
        TempBasal tbr = new TempBasal();
        tbr.created_at = createdAt;
        tbr.enteredBy = createdBy;
        tbr.device = device;
        tbr.duration = duration;
        tbr.rate = absolute;
        addTreatments(tbr);
    }
}
