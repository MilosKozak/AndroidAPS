package info.nightscout.androidaps.Services;

import android.app.IntentService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Telephony;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.events.EventNewBasalProfile;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.ConstraintsObjectives.ObjectivesPlugin;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSMbg;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSSgv;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSSettingsStatus;
import info.nightscout.androidaps.plugins.Overview.Notification;
import info.nightscout.androidaps.plugins.Overview.OverviewPlugin;
import info.nightscout.androidaps.plugins.Overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.ProfileNS.NSProfilePlugin;
import info.nightscout.androidaps.plugins.PumpDanaR.activities.DanaRNSHistorySync;
import info.nightscout.androidaps.plugins.SmsCommunicator.events.EventNewSMS;
import info.nightscout.androidaps.plugins.SourceGlimp.SourceGlimpPlugin;
import info.nightscout.androidaps.plugins.SourceMM640g.SourceMM640gPlugin;
import info.nightscout.androidaps.plugins.SourceNSClient.SourceNSClientPlugin;
import info.nightscout.androidaps.plugins.SourceXdrip.SourceXdripPlugin;
import info.nightscout.androidaps.receivers.DataReceiver;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSDeviceStatus;
import info.nightscout.utils.SP;


public class DataService extends IntentService {
    private static Logger log = LoggerFactory.getLogger(DataService.class);

    boolean xDripEnabled = false;
    boolean nsClientEnabled = true;
    boolean mm640gEnabled = false;
    boolean glimpEnabled = false;

    public DataService() {
        super("DataService");
        registerBus();
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        if (Config.logFunctionCalls)
            log.debug("onHandleIntent " + intent);

        if (ConfigBuilderPlugin.getActiveBgSource().getClass().equals(SourceXdripPlugin.class)) {
            xDripEnabled = true;
            nsClientEnabled = false;
            mm640gEnabled = false;
            glimpEnabled = false;
        } else if (ConfigBuilderPlugin.getActiveBgSource().getClass().equals(SourceNSClientPlugin.class)) {
            xDripEnabled = false;
            nsClientEnabled = true;
            mm640gEnabled = false;
            glimpEnabled = false;
        } else if (ConfigBuilderPlugin.getActiveBgSource().getClass().equals(SourceMM640gPlugin.class)) {
            xDripEnabled = false;
            nsClientEnabled = false;
            mm640gEnabled = true;
            glimpEnabled = false;
        } else if (ConfigBuilderPlugin.getActiveBgSource().getClass().equals(SourceGlimpPlugin.class)) {
            xDripEnabled = false;
            nsClientEnabled = false;
            mm640gEnabled = false;
            glimpEnabled = true;
        }

        if (intent != null) {
            final String action = intent.getAction();
            if (Intents.ACTION_NEW_BG_ESTIMATE.equals(action)) {
                if (xDripEnabled) {
                    handleNewDataFromXDrip(intent);
                }
            } else if (Intents.NS_EMULATOR.equals(action)) {
                if (mm640gEnabled) {
                    handleNewDataFromMM640g(intent);
                }
            } else if (Intents.GLIMP_BG.equals(action)) {
                if (glimpEnabled) {
                    handleNewDataFromGlimp(intent);
                }
            } else if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(action)) {
                handleNewSMS(intent);
            }
        }
        if (Config.logFunctionCalls)
            log.debug("onHandleIntent exit " + intent);
        DataReceiver.completeWakefulIntent(intent);
    }

/*
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (Config.logFunctionCalls)
            log.debug("onStartCommand");

        return START_STICKY;
    }
*/

    @Override
    public void onDestroy() {
        super.onDestroy();
        MainApp.bus().unregister(this);
    }

    private void registerBus() {
        try {
            MainApp.bus().unregister(this);
        } catch (RuntimeException x) {
            // Ignore
        }
        MainApp.bus().register(this);
    }

    private void handleNewDataFromXDrip(Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        BgReading bgReading = new BgReading();

        bgReading.value = bundle.getDouble(Intents.EXTRA_BG_ESTIMATE);
        bgReading.direction = bundle.getString(Intents.EXTRA_BG_SLOPE_NAME);
        bgReading.date = bundle.getLong(Intents.EXTRA_TIMESTAMP);
        bgReading.raw = bundle.getDouble(Intents.EXTRA_RAW);

        MainApp.getDbHelper().createIfNotExists(bgReading, "XDRIP");
    }

    private void handleNewDataFromGlimp(Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        BgReading bgReading = new BgReading();

        bgReading.value = bundle.getDouble("mySGV");
        bgReading.direction = bundle.getString("myTrend");
        bgReading.date = bundle.getLong("myTimestamp");
        bgReading.raw = 0;

        MainApp.getDbHelper().createIfNotExists(bgReading, "GLIMP");
    }

    private void handleNewDataFromMM640g(Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        final String collection = bundle.getString("collection");
        if (collection == null) return;

        if (collection.equals("entries")) {
            final String data = bundle.getString("data");

            if ((data != null) && (data.length() > 0)) {
                try {
                    final JSONArray json_array = new JSONArray(data);
                    for (int i = 0; i < json_array.length(); i++) {
                        final JSONObject json_object = json_array.getJSONObject(i);
                        final String type = json_object.getString("type");
                        switch (type) {
                            case "sgv":
                                BgReading bgReading = new BgReading();

                                bgReading.value = json_object.getDouble("sgv");
                                bgReading.direction = json_object.getString("direction");
                                bgReading.date = json_object.getLong("date");
                                bgReading.raw = json_object.getDouble("sgv");

                                MainApp.getDbHelper().createIfNotExists(bgReading, "MM640g");
                                break;
                            default:
                                log.debug("Unknown entries type: " + type);
                        }
                    }
                } catch (JSONException e) {
                    log.error("Got JSON exception: " + e);
                }
            }
        }
    }

    public static void actionNewMBG(String mbg, String mbgs) {

        boolean nsUploadOnly = SP.getBoolean(R.string.key_ns_upload_only, false);
        if(nsUploadOnly) return;

        boolean hasMbg = (mbg == null);
        boolean hasMbgs = (mbgs == null);
        try {
            if (hasMbg) {
                String mbgstring = mbg;
                JSONObject mbgJson = new JSONObject(mbgstring);
                NSMbg nsMbg = new NSMbg(mbgJson);
                CareportalEvent careportalEvent = new CareportalEvent(nsMbg);
                MainApp.getDbHelper().createOrUpdate(careportalEvent);
                if (Config.logIncommingData)
                    log.debug("Adding/Updating new MBG: " + careportalEvent.log());
            }

            if (hasMbgs) {
                String sgvstring = mbgs;
                JSONArray jsonArray = new JSONArray(sgvstring);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject mbgJson = jsonArray.getJSONObject(i);
                    NSMbg nsMbg = new NSMbg(mbgJson);
                    CareportalEvent careportalEvent = new CareportalEvent(nsMbg);
                    MainApp.getDbHelper().createOrUpdate(careportalEvent);
                    if (Config.logIncommingData)
                        log.debug("Adding/Updating new MBG: " + careportalEvent.log());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void actionNewCAL(String mbg, String mbgs) {

        boolean nsUploadOnly = SP.getBoolean(R.string.key_ns_upload_only, false);
        if(nsUploadOnly) return;

       //don't handle cals
    }

    public static void actionNewSGV(String sgv, String sgvs) {

        // Objectives 0
        ObjectivesPlugin.bgIsAvailableInNS = true;
        ObjectivesPlugin.saveProgress();
        

        if(!ConfigBuilderPlugin.getActiveBgSource().getClass().equals(SourceNSClientPlugin.class)) return;

        boolean hasSgv = (sgv == null);
        boolean hasSgvs = (sgvs == null);
        try {
            if (hasSgv) {
                String sgvstring = sgv;
                JSONObject sgvJson = new JSONObject(sgvstring);
                NSSgv nsSgv = new NSSgv(sgvJson);
                BgReading bgReading = new BgReading(nsSgv);
                MainApp.getDbHelper().createIfNotExists(bgReading, "NS");
            }

            if (hasSgvs) {
                String sgvstring = sgvs;
                JSONArray jsonArray = new JSONArray(sgvstring);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject sgvJson = jsonArray.getJSONObject(i);
                    NSSgv nsSgv = new NSSgv(sgvJson);
                    BgReading bgReading = new BgReading(nsSgv);
                    MainApp.getDbHelper().createIfNotExists(bgReading, "NS");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void actionRemovedTreatment(String treatment, String treatments) {

        boolean nsUploadOnly = SP.getBoolean(R.string.key_ns_upload_only, false);
        if(nsUploadOnly) return;

        boolean hasTreatment = treatment == null;
        boolean hasTreatments = treatments == null;

        try {
            if (hasTreatment) {
                String trstring = treatment;
                JSONObject trJson = new JSONObject(trstring);
                String _id = trJson.getString("_id");
                handleRemovedRecordFromNS(_id);
            }

            if (hasTreatments) {
                String trstring = treatments;
                JSONArray jsonArray = new JSONArray(trstring);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject trJson = jsonArray.getJSONObject(i);
                    String _id = trJson.getString("_id");
                    handleRemovedRecordFromNS(_id);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void actionChangedTreatment(String treatment, String treatments) {

        boolean nsUploadOnly = SP.getBoolean(R.string.key_ns_upload_only, false);
        if(nsUploadOnly) return;

        boolean hasTreatment = treatment == null;
        boolean hasTreatments = treatments == null;
        try {
            if (hasTreatment) {
                String trstring = treatment;
                handleAddChangeDataFromNS(trstring);
            }
            if (hasTreatments) {
                String trstring = treatments;
                JSONArray jsonArray = new JSONArray(trstring);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject trJson = jsonArray.getJSONObject(i);
                    String trstr = trJson.toString();
                    handleAddChangeDataFromNS(trstr);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void actionNewProfile(String activeProfile, String profile) {


        boolean isNSProfile = ConfigBuilderPlugin.getActiveProfileInterface().getClass().equals(NSProfilePlugin.class);
        if(!isNSProfile) return;


        try {
            ProfileStore profileStore = new ProfileStore(new JSONObject(profile));
            NSProfilePlugin.storeNewProfile(profileStore);
            MainApp.bus().post(new EventNewBasalProfile());

            if (Config.logIncommingData)
                log.debug("Received profileStore: " + activeProfile + " " + profile);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static void actionNewTreatment(String treatment, String treatments) {

        boolean nsUploadOnly = SP.getBoolean(R.string.key_ns_upload_only, false);
        if(nsUploadOnly) return;

        boolean hasTreatment = treatment == null;
        boolean hasTreatments = treatments == null;
        try {
            if (hasTreatment) {
                handleAddChangeDataFromNS(treatment);
            }
            if (hasTreatments) {
                JSONArray jsonArray = new JSONArray(treatments);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject trJson = jsonArray.getJSONObject(i);
                    String trstr = trJson.toString();
                    handleAddChangeDataFromNS(trstr);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void actionNewDevicestatus(String devicestatus,String devicestatuses) {
        boolean hasDeviceStatus = devicestatus == null;
        boolean hasDevicestatuses = devicestatuses == null;
        try {
            if (hasDeviceStatus) {
                JSONObject devicestatusJson = new JSONObject(devicestatus);
                NSDeviceStatus.getInstance().setData(devicestatusJson);
                if (devicestatusJson.has("pump")) {
                    // Objectives 0
                    ObjectivesPlugin.pumpStatusIsAvailableInNS = true;
                    ObjectivesPlugin.saveProgress();
                }
            }
            if (hasDevicestatuses) {
                JSONArray jsonArray = new JSONArray(devicestatuses);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject devicestatusJson = jsonArray.getJSONObject(i);
                    NSDeviceStatus.getInstance().setData(devicestatusJson);
                    if (devicestatusJson.has("pump")) {
                        // Objectives 0
                        ObjectivesPlugin.pumpStatusIsAvailableInNS = true;
                        ObjectivesPlugin.saveProgress();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void actionNewStatus(String status, boolean updateNSClientInfo, int nightscoutversioncode, String nightscoutversionname, int nsclientversioncode, String nsclientversionname) {

        boolean nsUploadOnly = SP.getBoolean(R.string.key_ns_upload_only, false);
        if(nsUploadOnly) return;

        boolean hasStatus = status == null;
        if (updateNSClientInfo) {
            ConfigBuilderPlugin.nightscoutVersionCode = nightscoutversioncode; // for ver 1.2.3 contains 10203
            ConfigBuilderPlugin.nightscoutVersionName = nightscoutversionname;
            ConfigBuilderPlugin.nsClientVersionCode = nsclientversioncode; // for ver 1.17 contains 117
            ConfigBuilderPlugin.nsClientVersionName = nsclientversionname;
            log.debug("Got versions: NSClient: " + ConfigBuilderPlugin.nsClientVersionName + " Nightscout: " + ConfigBuilderPlugin.nightscoutVersionName);
            try {
                if (ConfigBuilderPlugin.nsClientVersionCode < MainApp.instance().getPackageManager().getPackageInfo(MainApp.instance().getPackageName(), 0).versionCode) {
                    Notification notification = new Notification(Notification.OLD_NSCLIENT, MainApp.sResources.getString(R.string.unsupportedclientver), Notification.URGENT);
                    MainApp.bus().post(new EventNewNotification(notification));
                } else {
                    MainApp.bus().post(new EventDismissNotification(Notification.OLD_NSCLIENT));
                }
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            if (ConfigBuilderPlugin.nightscoutVersionCode < Config.SUPPORTEDNSVERSION) {
                Notification notification = new Notification(Notification.OLD_NS, MainApp.sResources.getString(R.string.unsupportednsversion), Notification.URGENT);
                MainApp.bus().post(new EventNewNotification(notification));
            } else {
                MainApp.bus().post(new EventDismissNotification(Notification.OLD_NS));
            }
        } else {
            Notification notification = new Notification(Notification.OLD_NSCLIENT, MainApp.sResources.getString(R.string.unsupportedclientver), Notification.URGENT);
            MainApp.bus().post(new EventNewNotification(notification));
        }
        if (hasStatus) {
            try {
                JSONObject statusJson = new JSONObject(status);
                NSSettingsStatus.getInstance().setData(statusJson);
                if (Config.logIncommingData)
                    log.debug("Received status: " + statusJson.toString());
                Double targetHigh = NSSettingsStatus.getInstance().getThreshold("bgTargetTop");
                Double targetlow = NSSettingsStatus.getInstance().getThreshold("bgTargetBottom");
                if (targetHigh != null)
                    OverviewPlugin.bgTargetHigh = targetHigh;
                if (targetlow != null)
                    OverviewPlugin.bgTargetLow = targetlow;
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private static void handleRemovedRecordFromNS(String _id) {
        MainApp.getDbHelper().deleteTreatmentById(_id);
        MainApp.getDbHelper().deleteTempTargetById(_id);
        MainApp.getDbHelper().deleteTempBasalById(_id);
        MainApp.getDbHelper().deleteExtendedBolusById(_id);
        MainApp.getDbHelper().deleteCareportalEventById(_id);
        MainApp.getDbHelper().deleteProfileSwitchById(_id);
    }

    private static void handleAddChangeDataFromNS(String trstring) throws JSONException {
        JSONObject trJson = new JSONObject(trstring);
        handleDanaRHistoryRecords(trJson); // update record _id in history
        handleAddChangeTempTargetRecord(trJson);
        handleAddChangeTempBasalRecord(trJson);
        handleAddChangeExtendedBolusRecord(trJson);
        handleAddChangeCareportalEventRecord(trJson);
        handleAddChangeTreatmentRecord(trJson);
        handleAddChangeProfileSwitchRecord(trJson);
    }

    public static void handleDanaRHistoryRecords(JSONObject trJson) {
        if (trJson.has(DanaRNSHistorySync.DANARSIGNATURE)) {
            MainApp.getDbHelper().updateDanaRHistoryRecordId(trJson);
        }
    }

    public static void handleAddChangeTreatmentRecord(JSONObject trJson) throws JSONException {
        if (trJson.has("insulin") || trJson.has("carbs")) {
            MainApp.getDbHelper().createTreatmentFromJsonIfNotExists(trJson);
            return;
        }
    }

    public static void handleAddChangeTempTargetRecord(JSONObject trJson) throws JSONException {
        if (trJson.has("eventType") && trJson.getString("eventType").equals(CareportalEvent.TEMPORARYTARGET)) {
            MainApp.getDbHelper().createTemptargetFromJsonIfNotExists(trJson);
        }
    }

    public static void handleAddChangeTempBasalRecord(JSONObject trJson) throws JSONException {
        if (trJson.has("eventType") && trJson.getString("eventType").equals(CareportalEvent.TEMPBASAL)) {
            MainApp.getDbHelper().createTempBasalFromJsonIfNotExists(trJson);
        }
    }

    public static void handleAddChangeExtendedBolusRecord(JSONObject trJson) throws JSONException {
        if (trJson.has("eventType") && trJson.getString("eventType").equals(CareportalEvent.COMBOBOLUS)) {
            MainApp.getDbHelper().createExtendedBolusFromJsonIfNotExists(trJson);
        }
    }

    public static void handleAddChangeCareportalEventRecord(JSONObject trJson) throws JSONException {
        if (trJson.has("insulin") && trJson.getDouble("insulin") > 0)
            return;
        if (trJson.has("carbs") && trJson.getDouble("carbs") > 0)
            return;
        if (trJson.has("eventType") && (
                trJson.getString("eventType").equals(CareportalEvent.SITECHANGE) ||
                        trJson.getString("eventType").equals(CareportalEvent.INSULINCHANGE) ||
                        trJson.getString("eventType").equals(CareportalEvent.SENSORCHANGE) ||
                        trJson.getString("eventType").equals(CareportalEvent.BGCHECK) ||
                        trJson.getString("eventType").equals(CareportalEvent.NOTE) ||
                        trJson.getString("eventType").equals(CareportalEvent.NONE) ||
                        trJson.getString("eventType").equals(CareportalEvent.ANNOUNCEMENT) ||
                        trJson.getString("eventType").equals(CareportalEvent.QUESTION) ||
                        trJson.getString("eventType").equals(CareportalEvent.EXERCISE) ||
                        trJson.getString("eventType").equals(CareportalEvent.OPENAPSOFFLINE) ||
                        trJson.getString("eventType").equals(CareportalEvent.PUMPBATTERYCHANGE)
        )) {
            MainApp.getDbHelper().createCareportalEventFromJsonIfNotExists(trJson);
        }

        if (trJson.getString("eventType").equals(CareportalEvent.ANNOUNCEMENT)) {
            long date = trJson.getLong("mills");
            long now = System.currentTimeMillis();
            if (date > now - 15 * 60 * 1000L && trJson.has("notes")) {
                Notification announcement = new Notification(Notification.NSANNOUNCEMENT, trJson.getString("notes"), Notification.ANNOUNCEMENT, 60);
                MainApp.bus().post(new EventNewNotification(announcement));
            }
        }
    }

    public static void handleAddChangeProfileSwitchRecord(JSONObject trJson) throws JSONException {
        if (trJson.has("eventType") && trJson.getString("eventType").equals(CareportalEvent.PROFILESWITCH)) {
            MainApp.getDbHelper().createProfileSwitchFromJsonIfNotExists(trJson);
        }
    }

    private void handleNewSMS(Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle == null) return;
        MainApp.bus().post(new EventNewSMS(bundle));
    }

}
