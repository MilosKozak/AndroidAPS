package info.nightscout.androidaps.plugins.general.nsclient;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
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
import info.nightscout.androidaps.plugins.general.nsclient.data.DbLogger;
import info.nightscout.androidaps.services.Intents;
import info.nightscout.androidaps.utils.BatteryLevel;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.SP;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Created by mike on 26.05.2017.
 */

public class SocketNSUpload implements UploadService {
    private Logger log = LoggerFactory.getLogger(L.NSCLIENT);

    private void addTreatments(JSONObject data) {
        Bundle bundle = createBundle("dbAdd", "treatments", data.toString());
        Intent intent = createIntent(Intents.ACTION_DATABASE, bundle, Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        sendBroadcast(intent);
        dbAdd(intent, data.toString());
    }

    private void addEntries(JSONObject data) {
        Bundle bundle = createBundle("dbAdd", "entries", data.toString());
        Intent intent = createIntent(Intents.ACTION_DATABASE, bundle, Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        sendBroadcast(intent);
        dbAdd(intent, data.toString());
    }

    private void updateTreatments(JSONObject data, String id) {
        Bundle bundle = createBundle("dbUpdate", "treatments", data.toString());
        bundle.putString("_id", id);
        Intent intent = createIntent(Intents.ACTION_DATABASE, bundle, Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        sendBroadcast(intent);
        dbAdd(intent, data.toString());
    }

    private void removeTreatments(String id) {
        Bundle bundle = createBundle("dbRemove", "treatments", null);
        bundle.putString("_id", id);
        Intent intent = createIntent(Intents.ACTION_DATABASE, bundle, Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        sendBroadcast(intent);
        dbRemove(intent, id);
    }

    private void removeFood(String id) {
        Bundle bundle = createBundle("dbRemove", "food", null);
        bundle.putString("_id", id);
        Intent intent = createIntent(Intents.ACTION_DATABASE, bundle, Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        sendBroadcast(intent);
        dbRemove(intent, id);
    }

    private void addDevicestatus(JSONObject data) {
        Bundle bundle = createBundle("dbAdd", "treatments", data.toString());
        Intent intent = createIntent(Intents.ACTION_DATABASE, bundle, Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        sendBroadcast(intent);
        dbAdd(intent, data.toString());
    }

    private void dbAdd(Intent intent, String data) {
        DbLogger.dbAdd(intent, data);
    }

    private void dbRemove(Intent intent, String id) {
        DbLogger.dbRemove(intent, id);
    }

    private void sendBroadcast(Intent intent) {
        Context context = MainApp.instance().getApplicationContext();
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    private Intent createIntent(String actionDatabase, Bundle bundle, int flags) {
        Intent intent = new Intent(actionDatabase);
        intent.putExtras(bundle);
        intent.addFlags(flags);
        return intent;
    }

    private Bundle createBundle(String action, String collection, String data) {
        Bundle bundle = new Bundle();
        bundle.putString("action", action);
        bundle.putString("collection", collection);
        if (data != null)
            bundle.putString("data", data);
        return bundle;
    }

    public void uploadTempBasalStartAbsolute(TemporaryBasal temporaryBasal, Double originalExtendedAmount) {
        try {
            JSONObject data = new JSONObject();
            data.put("eventType", CareportalEvent.TEMPBASAL);
            data.put("duration", temporaryBasal.durationInMinutes);
            data.put("absolute", temporaryBasal.absoluteRate);
            data.put("rate", temporaryBasal.absoluteRate);
            if (temporaryBasal.pumpId != 0)
                data.put("pumpId", temporaryBasal.pumpId);
            data.put("created_at", DateUtil.toISOString(temporaryBasal.date));
            data.put("enteredBy", "openaps://" + MainApp.gs(R.string.app_name));
            if (originalExtendedAmount != null)
                data.put("originalExtendedAmount", originalExtendedAmount); // for back synchronization
            addTreatments(data);

        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    public void uploadTempBasalStartPercent(TemporaryBasal temporaryBasal) {
        try {
            SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
            boolean useAbsolute = SP.getBoolean("ns_sync_use_absolute", false);
            Profile profile = ProfileFunctions.getInstance().getProfile(temporaryBasal.date);
            double absoluteRate = 0;
            if (profile != null) {
                absoluteRate = profile.getBasal(temporaryBasal.date) * temporaryBasal.percentRate / 100d;
            }
            if (useAbsolute) {
                TemporaryBasal t = temporaryBasal.clone();
                t.isAbsolute = true;
                if (profile != null) {
                    t.absoluteRate = absoluteRate;
                    uploadTempBasalStartAbsolute(t, null);
                }
            } else {
                JSONObject data = new JSONObject();
                data.put("eventType", CareportalEvent.TEMPBASAL);
                data.put("duration", temporaryBasal.durationInMinutes);
                data.put("percent", temporaryBasal.percentRate - 100);
                if (profile != null)
                    data.put("rate", absoluteRate);
                if (temporaryBasal.pumpId != 0)
                    data.put("pumpId", temporaryBasal.pumpId);
                data.put("created_at", DateUtil.toISOString(temporaryBasal.date));
                data.put("enteredBy", "openaps://" + MainApp.gs(R.string.app_name));

                addTreatments(data);
            }
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    public void uploadTempBasalEnd(long time, boolean isFakedTempBasal, long pumpId) {
        try {

            JSONObject data = new JSONObject();
            data.put("eventType", CareportalEvent.TEMPBASAL);
            data.put("created_at", DateUtil.toISOString(time));
            data.put("enteredBy", "openaps://" + MainApp.gs(R.string.app_name));
            if (isFakedTempBasal)
                data.put("isFakedTempBasal", isFakedTempBasal);
            if (pumpId != 0)
                data.put("pumpId", pumpId);
            addTreatments(data);

        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }


    public void uploadExtendedBolus(ExtendedBolus extendedBolus) {
        try {
            JSONObject data = new JSONObject();
            data.put("eventType", CareportalEvent.COMBOBOLUS);
            data.put("duration", extendedBolus.durationInMinutes);
            data.put("splitNow", 0);
            data.put("splitExt", 100);
            data.put("enteredinsulin", extendedBolus.insulin);
            data.put("relative", extendedBolus.insulin / extendedBolus.durationInMinutes * 60); // U/h
            if (extendedBolus.pumpId != 0)
                data.put("pumpId", extendedBolus.pumpId);
            data.put("created_at", DateUtil.toISOString(extendedBolus.date));
            data.put("enteredBy", "openaps://" + MainApp.gs(R.string.app_name));
            addTreatments(data);

        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    public void uploadExtendedBolusEnd(long time, long pumpId) {
        try {
            JSONObject data = new JSONObject();
            data.put("eventType", CareportalEvent.COMBOBOLUS);
            data.put("duration", 0);
            data.put("splitNow", 0);
            data.put("splitExt", 100);
            data.put("enteredinsulin", 0);
            data.put("relative", 0);
            data.put("created_at", DateUtil.toISOString(time));
            data.put("enteredBy", "openaps://" + MainApp.gs(R.string.app_name));
            if (pumpId != 0)
                data.put("pumpId", pumpId);
            addTreatments(data);
        } catch (JSONException e) {
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

            deviceStatus.uploaderBattery = BatteryLevel.getBatteryLevel();

            deviceStatus.created_at = DateUtil.toISOString(new Date());
            addDevicestatus(deviceStatus.mongoRecord());

        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    public void uploadTreatmentRecord(DetailedBolusInfo detailedBolusInfo) {
        JSONObject data = new JSONObject();
        try {
            data.put("eventType", detailedBolusInfo.eventType);
            if (detailedBolusInfo.insulin != 0d) data.put("insulin", detailedBolusInfo.insulin);
            if (detailedBolusInfo.carbs != 0d) data.put("carbs", (int) detailedBolusInfo.carbs);
            data.put("created_at", DateUtil.toISOString(detailedBolusInfo.date));
            data.put("date", detailedBolusInfo.date);
            data.put("isSMB", detailedBolusInfo.isSMB);
            if (detailedBolusInfo.pumpId != 0)
                data.put("pumpId", detailedBolusInfo.pumpId);
            if (detailedBolusInfo.glucose != 0d)
                data.put("glucose", detailedBolusInfo.glucose);
            if (!detailedBolusInfo.glucoseType.equals(""))
                data.put("glucoseType", detailedBolusInfo.glucoseType);
            if (detailedBolusInfo.boluscalc != null)
                data.put("boluscalc", detailedBolusInfo.boluscalc);
            if (detailedBolusInfo.carbTime != 0)
                data.put("preBolus", detailedBolusInfo.carbTime);
            if (!StringUtils.isEmpty(detailedBolusInfo.notes)) {
                data.put("notes", detailedBolusInfo.notes);
            }
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        uploadCareportalEntryToNS(data);
    }

    public void uploadProfileSwitch(ProfileSwitch profileSwitch) {
        try {
            JSONObject data = getJson(profileSwitch);
            uploadCareportalEntryToNS(data);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    public void uploadTempTarget(TempTarget tempTarget) {
        try {
            Profile profile = ProfileFunctions.getInstance().getProfile();

            if (profile == null) {
                log.error("Profile is null. Skipping upload");
                return;
            }

            JSONObject data = new JSONObject();
            data.put("eventType", CareportalEvent.TEMPORARYTARGET);
            data.put("duration", tempTarget.durationInMinutes);
            data.put("reason", tempTarget.reason);
            data.put("targetBottom", Profile.fromMgdlToUnits(tempTarget.low, profile.getUnits()));
            data.put("targetTop", Profile.fromMgdlToUnits(tempTarget.high, profile.getUnits()));
            data.put("created_at", DateUtil.toISOString(tempTarget.date));
            data.put("units", profile.getUnits());
            data.put("enteredBy", MainApp.gs(R.string.app_name));
            uploadCareportalEntryToNS(data);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    public void updateProfileSwitch(ProfileSwitch profileSwitch) {
        try {
            JSONObject data = getJson(profileSwitch);
            if (profileSwitch._id != null) {
                updateTreatments(data, profileSwitch._id);
            }
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    private JSONObject getJson(ProfileSwitch profileSwitch) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("eventType", CareportalEvent.PROFILESWITCH);
        data.put("duration", profileSwitch.durationInMinutes);
        data.put("profile", profileSwitch.getCustomizedName());
        data.put("profileJson", profileSwitch.profileJson);
        data.put("profilePlugin", profileSwitch.profilePlugin);
        if (profileSwitch.isCPP) {
            data.put("CircadianPercentageProfile", true);
            data.put("timeshift", profileSwitch.timeshift);
            data.put("percentage", profileSwitch.percentage);
        }
        data.put("created_at", DateUtil.toISOString(profileSwitch.date));
        data.put("enteredBy", MainApp.gs(R.string.app_name));

        return data;
    }

    public void uploadCareportalEntryToNS(JSONObject data) {
        try {
            if (data.has("preBolus") && data.has("carbs")) {
                JSONObject prebolus = new JSONObject();
                prebolus.put("carbs", data.get("carbs"));
                data.remove("carbs");
                prebolus.put("eventType", data.get("eventType"));
                if (data.has("enteredBy")) prebolus.put("enteredBy", data.get("enteredBy"));
                if (data.has("notes")) prebolus.put("notes", data.get("notes"));
                long mills = DateUtil.fromISODateString(data.getString("created_at")).getTime();
                Date preBolusDate = new Date(mills + data.getInt("preBolus") * 60000L + 1000L);
                prebolus.put("created_at", DateUtil.toISOString(preBolusDate));
                uploadCareportalEntryToNS(prebolus);
            }
            addTreatments(data);

        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }

    }

    public void removeCareportalEntryFromNS(String _id) {
        try {
            removeTreatments(_id);
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }

    }

    public void uploadOpenAPSOffline(int durationInMinutes) {
        try {
            JSONObject data = new JSONObject();
            data.put("eventType", "OpenAPS Offline");
            data.put("duration", durationInMinutes);
            data.put("created_at", DateUtil.toISOString(new Date()));
            data.put("enteredBy", "openaps://" + MainApp.gs(R.string.app_name));
            addTreatments(data);

        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    public void uploadError(String error) {
        uploadError(error, new Date());
    }

    public void uploadError(String error, Date date) {

        JSONObject data = new JSONObject();
        try {
            data.put("eventType", "Announcement");
            data.put("created_at", DateUtil.toISOString(date));
            data.put("enteredBy", SP.getString("careportal_enteredby", MainApp.gs(R.string.app_name)));
            data.put("notes", error);
            data.put("isAnnouncement", true);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        addTreatments(data);

    }

    @Override
    public void uploadCareportalBgCheck(BgReading reading, String source) {
        JSONObject data = new JSONObject();
        try {
            data.put("device", source);
            data.put("date", reading.date);
            data.put("dateString", DateUtil.toISOString(reading.date));
            data.put("sgv", reading.value);
            data.put("direction", reading.direction);
            data.put("type", "sgv");
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        addEntries(data);

    }


    public void uploadAppStart() {
        if (SP.getBoolean(R.string.key_ns_logappstartedevent, true)) {

            JSONObject data = new JSONObject();
            try {
                data.put("eventType", "Note");
                data.put("created_at", DateUtil.toISOString(new Date()));
                data.put("notes", MainApp.gs(R.string.androidaps_start) + " - " + Build.MANUFACTURER + " " + Build.MODEL);
            } catch (JSONException e) {
                log.error("Unhandled exception", e);
            }
            addTreatments(data);

        }
    }

    public void uploadEvent(String careportalEvent, long time, @Nullable String notes) {

        JSONObject data = new JSONObject();
        try {
            data.put("eventType", careportalEvent);
            data.put("created_at", DateUtil.toISOString(time));
            data.put("enteredBy", SP.getString("careportal_enteredby", MainApp.gs(R.string.app_name)));
            if (notes != null) {
                data.put("notes", notes);
            }
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        addTreatments(data);

    }

    public void removeFoodFromNS(String _id) {
        try {
            removeFood(_id);

        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }

    }

    public void sendToXdrip(BgReading bgReading) {
        final String XDRIP_PLUS_NS_EMULATOR = "com.eveningoutpost.dexdrip.NS_EMULATOR";
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US);

        try {
            final JSONArray entriesBody = new JSONArray();
            JSONObject json = new JSONObject();
            json.put("sgv", bgReading.value);
            if (bgReading.direction == null) {
                json.put("direction", "NONE");
            } else {
                json.put("direction", bgReading.direction);
            }
            json.put("device", "G5");
            json.put("type", "sgv");
            json.put("date", bgReading.date);
            json.put("dateString", format.format(bgReading.date));
            entriesBody.put(json);

            final Bundle bundle = new Bundle();
            bundle.putString("action", "add");
            bundle.putString("collection", "entries");
            bundle.putString("data", entriesBody.toString());
            final Intent intent = new Intent(XDRIP_PLUS_NS_EMULATOR);
            intent.putExtras(bundle).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            MainApp.instance().sendBroadcast(intent);
            List<ResolveInfo> receivers = MainApp.instance().getPackageManager().queryBroadcastReceivers(intent, 0);
            if (receivers.size() < 1) {
                log.debug("No xDrip receivers found. ");
            } else {
                log.debug(receivers.size() + " xDrip receivers");
            }


        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void uploadSensorChange(String enteredBy, String created_at, String device) {
        JSONObject data = new JSONObject();
        try {
            data.put("eventType", CareportalEvent.SENSORCHANGE);
            data.put("enteredBy", enteredBy);
            data.put("created_at", created_at);
            if (device != null)
                data.put("device", device);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        addTreatments(data);

    }

    public boolean isIdValid(String _id) {
        if (_id == null)
            return false;
        return _id.length() == 24;
    }

    public void uploadInsulinChangeEvent(String createdBy, String createdAt, String note, String device) {
        try {
            JSONObject data = new JSONObject();
            data.put("eventType", CareportalEvent.INSULINCHANGE);
            data.put("enteredBy", createdBy);
            data.put("created_at", createdAt);
            data.put("notes", note);
            if (device != null)
                data.put("device", device);
            addTreatments(data);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    @Override
    public void uploadCareportalNote(String createdAt, String createdBy, String note, String device) {
        try {
            JSONObject data = new JSONObject();
            data.put("eventType", CareportalEvent.NOTE);
            data.put("enteredBy", createdBy);
            data.put("created_at", createdAt);
            data.put("notes", note);
            if (device != null)
                data.put("device", device);
            addTreatments(data);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    @Override
    public void uploadCareportalBgCheck(String createdAt, String createdBy, String glucoseType, Number glucose, String units, String device) {
        try {
            JSONObject data = new JSONObject();
            data.put("eventType", CareportalEvent.BGCHECK);
            data.put("enteredBy", createdBy);
            data.put("created_at", createdAt);
            data.put("glucoseType", glucoseType);
            data.put("glucose", glucose);
            data.put("units", units);
            if (device != null)
                data.put("device", device);
            addTreatments(data);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    @Override
    public void uploadComboBolus(String createdAt, String enteredBy, String deviceSignature, Double insulin, Integer duration,
                                 Double relative, Integer splitNow, Integer splitExt) {
        try {
            JSONObject nsrec = new JSONObject();
            nsrec.put("eventType", CareportalEvent.COMBOBOLUS);
            if (deviceSignature != null)
                nsrec.put("device", deviceSignature);
            if (insulin != null)
                nsrec.put("insulin", insulin);
            if (duration != null)
                nsrec.put("duration", duration);
            if (relative != null)
                nsrec.put("relative", relative);
            if (splitNow != null)
                nsrec.put("splitNow", splitNow);
            if (splitExt != null)
                nsrec.put("splitExt", splitExt);
            nsrec.put("created_at", createdAt);
            nsrec.put("enteredBy", enteredBy);

            addTreatments(nsrec);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }

    }

    @Override
    public void uploadCareportalMealBolus(String createdAt, String enteredBy, String pumpSignature, Double insulin, Double carbs) {
        try {
            JSONObject nsrec = new JSONObject();
            nsrec.put("eventType", "Meal Bolus");
            nsrec.put("device", pumpSignature);
            nsrec.put("insulin", insulin);
            nsrec.put("carbs", carbs);
            nsrec.put("created_at", createdAt);
            nsrec.put("enteredBy", enteredBy);
            addTreatments(nsrec);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    @Override
    public void uploadTempBasal(String createdAt, String createdBy, String device, Integer duration, Double absolute) {
        try {
            JSONObject nsrec = new JSONObject();
            if (device != null)
                nsrec.put("device", device);
            nsrec.put("eventType", CareportalEvent.TEMPBASAL);
            nsrec.put("absolute", absolute);
            nsrec.put("duration", duration);
            nsrec.put("created_at", createdAt);
            nsrec.put("enteredBy", createdBy);
            addTreatments(nsrec);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    @Override
    public void uploadBatteryChanged(String createdBy, String createdAt, String note, String device) {
        try {
            JSONObject nsrec = new JSONObject();
            if (device != null)
                nsrec.put("device", device);
            nsrec.put("eventType", CareportalEvent.PUMPBATTERYCHANGE);
            nsrec.put("created_at", createdAt);
            nsrec.put("enteredBy", createdBy);
            nsrec.put("note", note);
            addTreatments(nsrec);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    @Override
    public void uploadSiteChange(String enteredBy, String created_at, String device) {
        try {
            JSONObject nsrec = new JSONObject();
            if (device != null)
                nsrec.put("device", device);
            nsrec.put("eventType", CareportalEvent.SITECHANGE);
            nsrec.put("created_at", created_at);
            nsrec.put("enteredBy", enteredBy);
            addTreatments(nsrec);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }
}
