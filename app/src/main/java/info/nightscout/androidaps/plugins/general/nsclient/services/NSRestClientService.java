package info.nightscout.androidaps.plugins.general.nsclient.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.*;
import com.google.common.base.Strings;
import com.j256.ormlite.dao.CloseableIterator;
import com.squareup.otto.Subscribe;
import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.DbRequest;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventConfigBuilderChange;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.general.nsclient.NSClientPlugin;
import info.nightscout.androidaps.plugins.general.nsclient.UploadQueue;
import info.nightscout.androidaps.plugins.general.nsclient.broadcasts.*;
import info.nightscout.androidaps.plugins.general.nsclient.data.NSSettingsStatus;
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientNewLog;
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientRestart;
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientStatus;
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientUpdateGUI;
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.JsonHelper;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.T;
import info.nightscout.api.v3.CGMService;
import info.nightscout.api.v3.NightscoutService;
import info.nightscout.api.v3.ProfilesService;
import info.nightscout.api.v3.TreatmentsService;
import info.nightscout.api.v3.documents.*;
import info.nightscout.api.v3.err.AuthorizationException;
import info.nightscout.api.v3.err.NightscoutException;
import info.nightscout.api.v3.search.SearchResultListener;
import io.socket.emitter.Emitter;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NSRestClientService extends Service implements SearchResultListener {
    private Logger log = LoggerFactory.getLogger(L.NSCLIENT);

    private static PowerManager.WakeLock mWakeLock;
    private IBinder mBinder = new NSRestClientService.LocalBinder();

    static public Handler handler;

    private NightscoutService nsService;
    private CGMService cgmService;
    private TreatmentsService treatmentsService;
    private ProfilesService profileService;

    private boolean isAuthorized = false;
    private boolean hasWriteAuth = false;

    private static String nightscoutVersionName = "";
    private static String nightscoutApiVersion = "";

    private long lastQueryTime = 0;
    private long lastUploadTime = 0;
    private long latestDateInReceivedData = 0;
    private long lastConnectTime = 0;

    private boolean nsEnabled = false;
    private String nsURL = "";
    private String nsToken = "";
    private String nsDevice = "";
    private Integer nsHours = 48;

    public long lastResendTime = 0;

    public static UploadQueue uploadQueue = new UploadQueue();

    private ArrayList<Long> reconnections = new ArrayList<>();
    // default interval for syncing data
    public static final long DEFAULT_SYNC_INTERVAL = 5 * 1000;
    public static final int DEFAULT_SYNC_LIMIT = 100;

    private int WATCHDOG_INTERVAL_MINUTES = 2;
    private int WATCHDOG_RECONNECT_IN = 15;
    private int WATCHDOG_MAXCONNECTIONS = 5;

    private Runnable runnableService = new Runnable() {
        @Override
        public void run() {
            PowerManager powerManager = (PowerManager) MainApp.instance().getApplicationContext().getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "AndroidAPS:NightscoutV3UploaderService_onDataUpdate");
            wakeLock.acquire();
            try {

                pollServer();
                uploadNightscout();

            } catch (AuthorizationException e) {
                isAuthorized = false;
                Notification noperm = new Notification(Notification.NSCLIENT_NO_WRITE_PERMISSION, MainApp.gs(R.string.nopermission), Notification.URGENT);
                MainApp.bus().post(new EventNewNotification(noperm));

            } catch (NightscoutException e) {
                e.printStackTrace();
            } finally {
                if (wakeLock.isHeld()) {
                    wakeLock.release();
                }
                // Repeat this runnable code block again every ... min
                handler.postDelayed(runnableService, DEFAULT_SYNC_INTERVAL);
            }
        }
    };

    public NSRestClientService() {
        registerBus();

        PowerManager powerManager = (PowerManager) MainApp.instance().getApplicationContext().getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AndroidAPS:NSRestClientService");
        initialize();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (handler == null) {
            HandlerThread handlerThread = new HandlerThread(NSRestClientService.class.getSimpleName() + "Handler");
            handlerThread.start();
            // Create the Handler object
            handler = new Handler(handlerThread.getLooper());
            // Execute a runnable task as soon as possible
            handler.post(runnableService);
        }
        return START_STICKY;

    }

    private void registerBus() {
        try {
            MainApp.bus().unregister(this);
        } catch (RuntimeException x) {
            // Ignore
        }
        MainApp.bus().register(this);
    }

    private void readPreferences() {
        nsEnabled = MainApp.getSpecificPlugin(NSClientPlugin.class).isEnabled(PluginType.GENERAL);
        nsURL = SP.getString(R.string.key_nsclientinternal_url, "");
        nsToken = SP.getString(R.string.key_nsclientinternal_api_token, "");
        nsDevice = SP.getString("careportal_enteredby", "");
    }

    private void initialize() {

        readPreferences();

        if (nsToken.isEmpty()) {
            MainApp.bus().post(new EventNSClientNewLog("NSRESTCLIENT", "Missing NS token"));
            MainApp.bus().post(new EventNSClientStatus("Missing NS token"));
        }

        MainApp.bus().post(new EventNSClientStatus("Initializing"));
        if (!MainApp.getSpecificPlugin(NSClientPlugin.class).isAllowed()) {
            MainApp.bus().post(new EventNSClientNewLog("NSRESTCLIENT", "not allowed"));
            MainApp.bus().post(new EventNSClientStatus("Not allowed"));
        } else if (MainApp.getSpecificPlugin(NSClientPlugin.class).paused) {
            MainApp.bus().post(new EventNSClientNewLog("NSRESTCLIENT", "paused"));
            MainApp.bus().post(new EventNSClientStatus("Paused"));
        } else if (!nsEnabled) {
            MainApp.bus().post(new EventNSClientNewLog("NSRESTCLIENT", "disabled"));
            MainApp.bus().post(new EventNSClientStatus("Disabled"));
        } else if (!nsURL.isEmpty()) {
            try {
                MainApp.bus().post(new EventNSClientStatus("Connecting ..."));
                checkNightscoutStatus();

            } catch (AuthorizationException | NightscoutException e) {
                MainApp.bus().post(new EventNSClientNewLog("NSRESTCLIENT", "Wrong URL syntax"));
                MainApp.bus().post(new EventNSClientStatus("Wrong URL syntax"));
            }
        } else {
            MainApp.bus().post(new EventNSClientNewLog("NSRESTCLIENT", "No NS URL specified"));
            MainApp.bus().post(new EventNSClientStatus("Not configured"));
        }
    }


    private synchronized void watchdog() {
        long now = DateUtil.now();
        reconnections.add(now);
        for (int i = 0; i < reconnections.size(); i++) {
            Long r = reconnections.get(i);
            if (r < now - T.mins(WATCHDOG_INTERVAL_MINUTES).msecs()) {
                reconnections.remove(r);
            }
        }
        MainApp.bus().post(new EventNSClientNewLog("WATCHDOG", "connections in last " + WATCHDOG_INTERVAL_MINUTES + " mins: " + reconnections.size() + "/" + WATCHDOG_MAXCONNECTIONS));
        if (reconnections.size() >= WATCHDOG_MAXCONNECTIONS) {
            Notification n = new Notification(Notification.NSMALFUNCTION, MainApp.gs(R.string.nsmalfunction), Notification.URGENT);
            MainApp.bus().post(new EventNewNotification(n));
            MainApp.bus().post(new EventNSClientNewLog("WATCHDOG", "pausing for " + WATCHDOG_RECONNECT_IN + " mins"));
            NSClientPlugin.getPlugin().pause(true);
            MainApp.bus().post(new EventNSClientUpdateGUI());
            new Thread(() -> {
                SystemClock.sleep(T.mins(WATCHDOG_RECONNECT_IN).msecs());
                MainApp.bus().post(new EventNSClientNewLog("WATCHDOG", "reenabling NSClient"));
                NSClientPlugin.getPlugin().pause(false);
            }).start();
        }
    }


    @Override
    public void onCreate() {
        super.onCreate();
        mWakeLock.acquire();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
    }

    public class LocalBinder extends Binder {
        public NSRestClientService getServiceInstance() {
            return NSRestClientService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }


    @Subscribe
    public void onStatusEvent(EventAppExit event) {
        if (L.isEnabled(L.NSCLIENT))
            log.debug("EventAppExit received");

        destroy();

        stopSelf();
    }

    @Subscribe
    public void onStatusEvent(EventPreferenceChange ev) {
        if (ev.isChanged(R.string.key_nsclientinternal_url) ||
                ev.isChanged(R.string.key_nsclientinternal_api_token) ||
                ev.isChanged(R.string.key_nsclientinternal_paused)
        ) {
            latestDateInReceivedData = 0;
            destroy();
            initialize();
        }
    }

    @Subscribe
    public void onStatusEvent(EventConfigBuilderChange ev) {
        if (nsEnabled != MainApp.getSpecificPlugin(NSClientPlugin.class).isEnabled(PluginType.GENERAL)) {
            latestDateInReceivedData = 0;
            destroy();
            initialize();
        }
    }

    @Subscribe
    public void onStatusEvent(final EventNSClientRestart ev) {
        latestDateInReceivedData = 0;
        restart();
    }


    private void destroy() { //TODO Remove
        MainApp.bus().post(new EventNSClientNewLog("NSV3CLIENT", "destroy"));
        hasWriteAuth = false;
    }

    private void receiveStatus(Status status, boolean isDelta) {
        //JSONObject status = data.getJSONObject("status");
        NSSettingsStatus nsSettingsStatus = NSSettingsStatus.getInstance().setData(status);

        nightscoutApiVersion = status.apiVersion;
        Double apiVersion = parseVersion(nightscoutApiVersion);

        if (apiVersion < Config.SUPPORTED_NSAPIVERSION) {
            MainApp.bus().post(new EventNSClientNewLog("ERROR", "Unsupported Nightscout version. Switch off REST API V3 support!"));

        } else {
            nightscoutVersionName = status.version;
        }
        BroadcastStatus.handleNewStatus(nsSettingsStatus, MainApp.instance().getApplicationContext(), isDelta);

    }

    private Double parseVersion(String version) {
        //ex. 3.0.0
        if (StringUtils.countMatches(version, '.') > 1) {
            //shorten to first two digits
            version = StringUtils.substring(version, version.indexOf(".") - 1);
        }
        return Double.parseDouble(version);
    }

    private void receiveMbgs(JSONObject data, boolean isDelta) {
        JSONArray mbgs = data.getJSONArray("mbgs");
        if (mbgs.length() > 0)
            MainApp.bus().post(new EventNSClientNewLog("DATA", "received " + mbgs.length() + " mbgs"));
        for (Integer index = 0; index < mbgs.length(); index++) {
            JSONObject jsonMbg = mbgs.getJSONObject(index);
            // remove from upload queue if Ack is failing
            UploadQueue.removeID(jsonMbg);
        }
        BroadcastMbgs.handleNewMbg(mbgs, MainApp.instance().getApplicationContext(), isDelta);
    }

    public void uploadNightscout() {
        if (UploadQueue.size() == 0) {
            return;
        }

        if (!isAuthorized || !hasWriteAuth) {
            return;
        }
        if (lastResendTime > System.currentTimeMillis() - 10 * 1000L) {
            if (L.isEnabled(L.NSCLIENT))
                log.debug("Skipping resend by lastResendTime: " + ((System.currentTimeMillis() - lastResendTime) / 1000L) + " sec");
            return;
        }
        lastResendTime = System.currentTimeMillis();
        MainApp.bus().post(new EventNSClientNewLog("QUEUE", "Upload started."));
    }

    public void resend(final String reason) {
        if (UploadQueue.size() == 0) {
            return;
        }

        if (!isAuthorized || !hasWriteAuth) {
            return;
        }

        handler.post(new Runnable() {
            @Override
            public void run() {

                CloseableIterator<DbRequest> iterator = null;
                int maxcount = 30;
                try {
                    iterator = MainApp.getDbHelper().getDbRequestInterator();
                    try {
                        while (iterator.hasNext() && maxcount > 0) {
                            DbRequest dbr = iterator.next();
                            if (dbr.action.equals("dbAdd")) {
                                NSAddAck addAck = new NSAddAck();
                                dbAdd(dbr, addAck);
                            } else if (dbr.action.equals("dbRemove")) {
                                NSUpdateAck removeAck = new NSUpdateAck(dbr.action, dbr._id);
                                dbRemove(dbr, removeAck);
                            } else if (dbr.action.equals("dbUpdate")) {
                                dbUpdate(dbr);
                            } else if (dbr.action.equals("dbUpdateUnset")) {
                                NSUpdateAck updateUnsetAck = new NSUpdateAck(dbr.action, dbr._id);
                                dbUpdateUnset(dbr, updateUnsetAck);
                            }
                            maxcount--;
                        }
                    } finally {
                        iterator.close();
                    }
                } catch (SQLException e) {
                    log.error("Unhandled exception", e);
                }

                MainApp.bus().post(new EventNSClientNewLog("QUEUE", "Resend ended: " + reason));
            }
        });
    }

    public void restart() {
        destroy();
        initialize();
    }

    public void pollServer() throws NightscoutException, AuthorizationException {

        if (true)//TODO check if backfilling is on)
        {
            getCGMService().syncEntries(this, lastQueryTime, DEFAULT_SYNC_LIMIT, null);
        }
        getTreatmentService().syncTreatments(this, lastQueryTime, DEFAULT_SYNC_LIMIT, null);
        getProfileService().syncProfiles(this, lastQueryTime, DEFAULT_SYNC_LIMIT, null);

    }

    /*
    private Emitter.Listener onDataUpdate = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            NSRestClientService.handler.post(new Runnable() {
                @Override
                public void run() {

                    try {

                        JSONObject data = (JSONObject) args[0];
                        boolean broadcastProfile = false;
                        try {
                            // delta means only increment/changes are comming
                            boolean isDelta = data.has("delta");
                            boolean isFull = !isDelta;
                            MainApp.bus().post(new EventNSClientNewLog("DATA", "Data packet #" + dataCounter++ + (isDelta ? " delta" : " full")));

                            if (data.has("profiles")) {
                                receiveProfile(data, isDelta);

                            }

                            if (data.has("status")) {
                                receiveStatus(data, isDelta);

                            } else if (!isDelta) {
                                MainApp.bus().post(new EventNSClientNewLog("ERROR", "Unsupported Nightscout version !!!!"));
                            }

                            // If new profile received or change detected broadcast it
                            if (broadcastProfile && profileStore != null) {
                                BroadcastProfile.handleNewTreatment(profileStore, MainApp.instance().getApplicationContext(), isDelta);
                                MainApp.bus().post(new EventNSClientNewLog("PROFILE", "broadcasting"));
                            }

                            if (data.has("treatments")) {
                                receiveTreatment(data, isDelta);
                            }
                            if (data.has("devicestatus")) {
                                receiveDevicestatus(data, isDelta);
                            }
                            if (data.has("food")) {
                                receiveFood(data, isDelta);
                            }
                            if (data.has("mbgs")) {
                                receiveMbgs(data, isDelta);

                            }
                            if (data.has("cals")) {
                                receiveCalibration(data, isDelta);
                            }
                            if (data.has("sgvs")) {
                                receiveSGVS(data, isDelta);
                            }
                            MainApp.bus().post(new EventNSClientNewLog("LAST", DateUtil.dateAndTimeString(latestDateInReceivedData)));
                        } catch (JSONException e) {
                            log.error("Unhandled exception", e);
                        }
                        //MainApp.bus().post(new EventNSClientNewLog("NSV3CLIENT", "onDataUpdate end");

                    }

                });
            }
        }
    }
*/

    private void checkNightscoutStatus() throws NightscoutException, AuthorizationException {
        NightscoutService service = getNightscoutService();
        Status status = service.getStatus();
        if (status != null) {
            this.lastConnectTime = System.currentTimeMillis();
            MainApp.bus().post(new EventNSClientNewLog("NSRESTCLIENT", "connected to nightscout api version " + status.apiVersion));
            nightscoutVersionName = status.version;
            nightscoutApiVersion = status.apiVersion;

            Map<String, String> apiPermissions = status.apiPermissions;
            if (apiPermissions != null) {
                checkPermissions(apiPermissions, Collections.TREATMENTS); //write and update access needed
                checkPermissions(apiPermissions, Collections.DEVICESTATUS); //write acccess needed
                checkPermissions(apiPermissions, Collections.PROFILE); //readaccess is sufficient
                checkPermissions(apiPermissions, Collections.ENTRIES); //TODO only if upload of BG

                if (!hasWriteAuth) {
                    Notification noperm = new Notification(Notification.NSCLIENT_NO_WRITE_PERMISSION, MainApp.gs(R.string.nowritepermission), Notification.URGENT);
                    MainApp.bus().post(new EventNewNotification(noperm));
                } else {
                    MainApp.bus().post(new EventDismissNotification(Notification.NSCLIENT_NO_WRITE_PERMISSION));
                }
            }
        }
        watchdog();
    }

    private void checkPermissions(Map<String, String> permissions, String collection) {
        String pm = permissions.get(collection);
        MainApp.bus().post(new EventNSClientNewLog("NSRESTCLIENT",
                "Checking auth for collection " + collection + ": " + pm));
        if (!pm.contains("c") || !pm.contains("u")) {
            this.hasWriteAuth = false;
            MainApp.bus().post(new EventNSClientNewLog("ERROR",
                    "Write access missing for " + collection));
        }
        if (!pm.contains("d")) {
            this.hasWriteAuth = false;
            MainApp.bus().post(new EventNSClientNewLog("ERROR",
                    "Delete access missing for " + collection));
        }
    }

    private synchronized CGMService getCGMService() {
        if (this.cgmService != null) {
            this.cgmService = new CGMService(nsURL, nsToken);
        }
        return this.cgmService;
    }

    private synchronized TreatmentsService getTreatmentService() {
        if (this.treatmentsService != null) {
            this.treatmentsService = new TreatmentsService(nsURL, nsToken);
        }
        return this.treatmentsService;
    }

    private synchronized ProfilesService getProfileService() {
        if (this.profileService != null) {
            this.profileService = new ProfilesService(nsURL, nsToken);
        }
        return this.profileService;
    }

    private synchronized NightscoutService getNightscoutService() {
        if (this.nsService != null) {
            this.nsService = new NightscoutService(nsURL, nsToken);
        }
        return this.nsService;
    }


    @Override
    public void onTreatment(List<Treatment> results) {

        List<Treatment> removedTreatments = new ArrayList<Treatment>();
        List<Treatment> updatedTreatments = new ArrayList<Treatment>();
        List<Treatment> addedTreatments = new ArrayList<Treatment>();

        if (results.size() > 0) {
            MainApp.bus().post(new EventNSClientNewLog("DATA", "received " + results.size() + " treatments"));
        }
        //for (Integer index = 0; index < treatments.length(); index++) {
        for (Treatment treatment : results) {

            // remove from upload queue if Ack is failing
            if (treatment.device != null) {
                UploadQueue.removeID(treatment.device);//NSCLIENT_ID
            }

            //Find latest date in treatment
            if (treatment.date != null && treatment.date < System.currentTimeMillis())
                if (treatment.date > latestDateInReceivedData)
                    latestDateInReceivedData = treatment.date;

            if (treatment.getAction() == null) {
                addedTreatments.put(jsonTreatment);
            } else if (treatment.getAction().equals("update")) {
                updatedTreatments.put(jsonTreatment);
            } else if (treatment.getAction().equals("remove")) {
                if (treatment.date != null && treatment.date > System.currentTimeMillis() - 24 * 60 * 60 * 1000L) // handle 1 day old deletions only
                    removedTreatments.put(jsonTreatment);
            }
        }
        if (removedTreatments.length() > 0) {
            BroadcastTreatment.handleRemovedTreatment(removedTreatments, isDelta);
        }
        if (updatedTreatments.length() > 0) {
            BroadcastTreatment.handleChangedTreatment(updatedTreatments, isDelta);
        }
        if (addedTreatments.length() > 0) {
            BroadcastTreatment.handleNewTreatment(addedTreatments, isDelta);
        }

    }

    @Override
    public void onProfile(List<Profile> results) {
        // delta means only increment/changes are comming
        boolean isDelta = true;//data.has("delta");
        boolean broadcastProfile = false;
        Profile profile = null;
        if (results.size() > 0) {
            profile = results.get(results.size() - 1);
            broadcastProfile = true;
            MainApp.bus().post(new EventNSClientNewLog("PROFILE", "profile received"));
        }
        if (broadcastProfile && profile != null) {
            BroadcastProfile.handleNewProfile(profile, MainApp.instance().getApplicationContext());
            MainApp.bus().post(new EventNSClientNewLog("PROFILE", "broadcasting"));
        }
    }

    @Override
    public void onEntry(List<Entry> bgs) {
        if (bgs.size() > 0)
            MainApp.bus().post(new EventNSClientNewLog("DATA", "received " + bgs.size() + " mbgs"));
        for (Integer index = 0; index < bgs.size(); index++) {
            Entry mbg = bgs.get(index);
            // remove from upload queue if Ack is failing
            UploadQueue.removeID(mbg.identifier);

            if(mbg.date != null && mbg.date < System.currentTimeMillis()) {
                if(mbg.date > latestDateInReceivedData) {
                    latestDateInReceivedData = mbg.date;
                }
            }
        }
        BroadcastMbgs.handleNewMbg(bgs, MainApp.instance().getApplicationContext(), true);

        // Was that sgv more less 15 mins ago ?
        boolean lessThan15MinAgo = false;
        if ((System.currentTimeMillis() - latestDateInReceivedData) / (60 * 1000L) < 15L)
            lessThan15MinAgo = true;
        if (Notification.isAlarmForStaleData() && lessThan15MinAgo) {
            MainApp.bus().post(new EventDismissNotification(Notification.NSALARM));
        }
    }

    @Override
    public void onDeviceStatus(String json) {
        if (!Strings.isNullOrEmpty(json)) {
            MainApp.bus().post(new EventNSClientNewLog("DATA", "received devicestatuses"));
            try {
                JSONArray devicestatuses = new JSONArray(json);

                if (devicestatuses.length() > 0) {
                    MainApp.bus().post(new EventNSClientNewLog("DATA", "received " + devicestatuses.length() + " devicestatuses"));
                    for (Integer index = 0; index < devicestatuses.length(); index++) {
                        JSONObject jsonStatus = devicestatuses.getJSONObject(index);
                        // remove from upload queue if Ack is failing
                        UploadQueue.removeID(jsonStatus);
                    }
                    BroadcastDeviceStatus.handleNewDeviceStatus(devicestatuses, MainApp.instance().getApplicationContext(), true);
                }
            } catch (JSONException e) {
                log.error("Unhandled exception", e);
            }
        }
    }

    @Override
    public void onFailure(Throwable e) {

        MainApp.bus().post(new EventNSClientNewLog("NSRESTCLIENT",
                e.getMessage()));
        MainApp.bus().post(new EventNSClientStatus(e.getMessage()));
    }
}
