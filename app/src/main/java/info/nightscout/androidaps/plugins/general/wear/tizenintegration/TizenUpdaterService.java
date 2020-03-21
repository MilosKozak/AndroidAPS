package info.nightscout.androidaps.plugins.general.wear.tizenintegration;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.samsung.android.sdk.SsdkUnsupportedException;
import com.samsung.android.sdk.accessory.SA;
import com.samsung.android.sdk.accessory.SAAgent;
import com.samsung.android.sdk.accessory.SAAuthenticationToken;
import com.samsung.android.sdk.accessory.SAPeerAgent;
import com.samsung.android.sdk.accessory.SASocket;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.nsclient.data.NSDeviceStatus;
import info.nightscout.androidaps.plugins.general.wear.ActionStringHandler;
import info.nightscout.androidaps.plugins.general.wear.WearPlugin;

import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.treatments.Treatment;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.SafeParse;
import info.nightscout.androidaps.utils.ToastUtils;

import static android.app.Service.START_STICKY;

public class TizenUpdaterService extends SAAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(TizenUpdaterService.class);

    public static final String ACTION_RESEND = TizenUpdaterService.class.getName().concat(".Resend");
    public static final String ACTION_OPEN_SETTINGS = TizenUpdaterService.class.getName().concat(".OpenSettings");
    public static final String ACTION_SEND_STATUS = TizenUpdaterService.class.getName().concat(".SendStatus");
    public static final String ACTION_SEND_BASALS = TizenUpdaterService.class.getName().concat(".SendBasals");
    public static final String ACTION_SEND_BOLUSPROGRESS = TizenUpdaterService.class.getName().concat(".BolusProgress");
    public static final String ACTION_SEND_ACTIONCONFIRMATIONREQUEST = TizenUpdaterService.class.getName().concat(".ActionConfirmationRequest");
    public static final String ACTION_SEND_CHANGECONFIRMATIONREQUEST = TizenUpdaterService.class.getName().concat(".ChangeConfirmationRequest");
    public static final String ACTION_CANCEL_NOTIFICATION = TizenUpdaterService.class.getName().concat(".CancelNotification");

    // Draft, to be updated with Tizen library
    public static final String WEARABLE_DATA_PATH = "/nightscout_watch_data";

    private static final int TIZEN_RESEND_CH = SafeParse.stringToInt(MainApp.gs(R.string.tizen_resend_ch));
    private static final int TIZEN_CANCELBOLUS_CH = SafeParse.stringToInt(MainApp.gs(R.string.tizen_cancelbolus_ch));
    private static final int TIZEN_CONFIRM_ACTIONSTRING_CH = SafeParse.stringToInt(MainApp.gs(R.string.tizen_confirm_actionstring_ch));
    private static final int TIZEN_INITIATE_ACTIONSTRING_CH = SafeParse.stringToInt(MainApp.gs(R.string.tizen_initiate_actionstring_ch));

    private static final String OPEN_SETTINGS_PATH = "/openwearsettings";
    private static final String NEW_STATUS_PATH = "/sendstatustowear";
    private static final String NEW_PREFERENCES_PATH = "/sendpreferencestowear";
    public static final String BASAL_DATA_PATH = "/nightscout_watch_basal";
    public static final String BOLUS_PROGRESS_PATH = "/nightscout_watch_bolusprogress";
    public static final int ACTION_CONFIRMATION_REQUEST_CH = SafeParse.stringToInt(MainApp.gs(R.string.action_confirm_actionstring_ch));
    public static final String ACTION_CHANGECONFIRMATION_REQUEST_PATH = "/nightscout_watch_changeconfirmationrequest";
    public static final String ACTION_CANCELNOTIFICATION_REQUEST_PATH = "/nightscout_watch_cancelnotificationrequest";

    public static final String TIZEN_ENABLE = "tizenenable";
    public static final String logPrefix = "Tizen::";

    boolean wear_integration = false;
    private static boolean lastLoopStatus;

    private Handler handler;

// Philoul start of code to compare with Example
    private static final String TAG = MainApp.gs(R.string.app_name);
    private static final Class<ServiceConnection> SASOCKET_CLASS = ServiceConnection.class;
    private final IBinder mBinder = new LocalBinder();
    private ServiceConnection mConnectionHandler = null;
    Handler mHandler = new Handler();

    public TizenUpdaterService() {
        super(TAG, SASOCKET_CLASS);
    }

    @Override
    public void onCreate() {
        listenForChangeInSettings();
        setSettings();

        if (wear_integration && SP.getBoolean(TIZEN_ENABLE, false)) {
            tizenApiConnect();
        }
        if (handler == null) {
            HandlerThread handlerThread = new HandlerThread(this.getClass().getSimpleName() + "Handler");
            handlerThread.start();
            handler = new Handler(handlerThread.getLooper());
        }
    }


    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        /***************************************************
         * Example codes for Android O OS (stopForeground) *
         ***************************************************/
        if (Build.VERSION.SDK_INT >= 26) {
            stopForeground(true);
        }
        super.onDestroy();
        WearPlugin.unRegisterTizenUpdaterService();
    }


    @Override
    protected void onFindPeerAgentsResponse(SAPeerAgent[] peerAgents, int result) {
        Log.d(TAG, "onFindPeerAgentResponse : result =" + result);
    }

    @Override
    protected void onServiceConnectionRequested(SAPeerAgent peerAgent) {
        if (peerAgent != null) {
            //Toast.makeText(getBaseContext(), R.string.ConnectionAcceptedMsg, Toast.LENGTH_SHORT).show();
            // TODO
            acceptServiceConnectionRequest(peerAgent);
        }
    }

    @Override
    protected void onServiceConnectionResponse(SAPeerAgent peerAgent, SASocket socket, int result) {
        if (result == SAAgent.CONNECTION_SUCCESS) {
            if (socket != null) {
                mConnectionHandler = (ServiceConnection) socket;
            }
        } else if (result == SAAgent.CONNECTION_ALREADY_EXIST) {
            Log.e(TAG, "onServiceConnectionResponse, CONNECTION_ALREADY_EXIST");
        }
    }

    @Override
    protected void onAuthenticationResponse(SAPeerAgent peerAgent, SAAuthenticationToken authToken, int error) {
        /*
         * The authenticatePeerAgent(peerAgent) API may not be working properly depending on the firmware
         * version of accessory device. Please refer to another sample application for Security.
         */
    }

    @Override
    protected void onError(SAPeerAgent peerAgent, String errorMessage, int errorCode) {
        super.onError(peerAgent, errorMessage, errorCode);
    }

    private boolean processUnsupportedException(SsdkUnsupportedException e) {
        e.printStackTrace();
        int errType = e.getType();
        if (errType == SsdkUnsupportedException.VENDOR_NOT_SUPPORTED
                || errType == SsdkUnsupportedException.DEVICE_NOT_SUPPORTED) {
            /*
             * Your application can not use Samsung Accessory SDK. You application should work smoothly
             * without using this SDK, or you may want to notify user and close your app gracefully (release
             * resources, stop Service threads, close UI thread, etc.)
             */
            stopSelf();
        } else if (errType == SsdkUnsupportedException.LIBRARY_NOT_INSTALLED) {
            Log.e(TAG, "You need to install Samsung Accessory SDK to use this application.");
        } else if (errType == SsdkUnsupportedException.LIBRARY_UPDATE_IS_REQUIRED) {
            Log.e(TAG, "You need to update Samsung Accessory SDK to use this application.");
        } else if (errType == SsdkUnsupportedException.LIBRARY_UPDATE_IS_RECOMMENDED) {
            Log.e(TAG, "We recommend that you update your Samsung Accessory SDK before using this application.");
            return false;
        }
        return true;
    }

    public class LocalBinder extends Binder {
        public TizenUpdaterService getService() {
            return TizenUpdaterService.this;
        }
    }


    public class ServiceConnection extends SASocket {
        public ServiceConnection() {
            super(ServiceConnection.class.getName());
        }

        @Override
        public void onError(int channelId, String errorMessage, int errorCode) {
            LOGGER.info("ServiceConnection: onError: [channelId=" + channelId + ", errorMessage=" + errorMessage + ", errorCode=" + errorCode);
        }

        @Override
        public void onReceive(int channelId, byte[] data) {

            LOGGER.info("ServiceConnection: onReceive: [channelId=" + channelId + ", mConnectionHandler(isNull)=" + (mConnectionHandler == null) + ", data=" + ByteUtil.getCompactString(data));

            if (mConnectionHandler == null) {
                return;
            }
            /* desactivated philoul to be as close as sample
            if (wear_integration && SP.getBoolean(TIZEN_ENABLE, false)) {
                if (channelId == TIZEN_RESEND_CH) {
                    // todo
                    // resendData();
                }

                if (channelId == TIZEN_CANCELBOLUS_CH) {
                    // todo
                    // cancelBolus();
                }

                if (channelId == TIZEN_INITIATE_ACTIONSTRING_CH) {
                    String actionstring = data.toString();
                    LOGGER.info("Tizen initiate: " + actionstring);
                    ActionStringHandler.handleInitiate(actionstring);
                }

                if (channelId == TIZEN_CONFIRM_ACTIONSTRING_CH) {
                    String actionstring = data.toString();
                    LOGGER.info("Tizen Confirm: " + actionstring);
                    ActionStringHandler.handleConfirmation(actionstring);
                }
            }

            */

            /* to delete replaced by BG Value
            Calendar calendar = new GregorianCalendar();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd aa hh:mm:ss.SSS");
            String timeStr = " " + dateFormat.format(calendar.getTime());
            String strToUpdateUI = new String(data);
            final String message = strToUpdateUI.concat(timeStr);
            */
            final String units = ProfileFunctions.getSystemUnits();
            BgReading lastBG = DatabaseHelper.lastBg();
            final String message = "BG : " + lastBG.valueToUnitsToString(units) + " " + lastBG.directionToSymbol();
            new Thread(new Runnable() {
                public void run() {
                    try {
                        mConnectionHandler.send(getServiceChannelId(0), message.getBytes());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }

        @Override
        protected void onServiceConnectionLost(int reason) {
            mConnectionHandler = null;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    //Toast.makeText(getBaseContext(), R.string.ConnectionTerminateddMsg, Toast.LENGTH_SHORT).show();
                    // TODO
                    LOGGER.info("ServiceConnection: onServiceConnectionLost: [reason=" + reason);

                }
            });
        }
    }
    /************************************************************************************************************************************************
     *
     *
     *   End of template code, lines below are added for AAPS integration
     *
     *
     ************************************************************************************************************************************************/

    public void setSettings() {
        wear_integration = WearPlugin.getPlugin().isEnabled(PluginType.GENERAL);
        // Log.d(TAG, "WR: wear_integration=" + wear_integration);
        if (wear_integration && SP.getBoolean(TIZEN_ENABLE, false)) {
            tizenApiConnect();
        }
    }

    public void listenForChangeInSettings() { WearPlugin.registerTizenUpdaterService(this); }


    private void tizenApiConnect() {
        if (mConnectionHandler != null && mConnectionHandler.isConnected() ) {
            mConnectionHandler = null;
        }
        super.onCreate();


        /****************************************************
         * Example codes for Android O OS (startForeground) *
         ****************************************************/
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager notificationManager = null;
            String channel_id = "sample_channel_01";

            if(notificationManager == null) {
                String channel_name = "Accessory_SDK_Sample";
                notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                NotificationChannel notiChannel = new NotificationChannel(channel_id, channel_name, NotificationManager.IMPORTANCE_LOW);
                notificationManager.createNotificationChannel(notiChannel);
            }

            int notifyID = 1;
            Notification notification = new Notification.Builder(this.getBaseContext(),channel_id)
                    .setContentTitle(TAG)
                    .setContentText("")
                    .setChannelId(channel_id)
                    .build();

            startForeground(notifyID, notification);
        }

        SA mAccessory = new SA();
        try {
            mAccessory.initialize(this);
        } catch (SsdkUnsupportedException e) {
            // try to handle SsdkUnsupportedException
            if (processUnsupportedException(e) == true) {
                return;
            }
        } catch (Exception e1) {
            e1.printStackTrace();
            /*
             * Your application can not use Samsung Accessory SDK. Your application should work smoothly
             * without using this SDK, or you may want to notify user and close your application gracefully
             * (release resources, stop Service threads, close UI thread, etc.)
             */
            stopSelf();
        }
    }


    // for connection with tizen app
    private boolean isConnectionEstablished() {
        return (mConnectionHandler!=null && mConnectionHandler.isConnected());
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        Log.d(TAG, logPrefix + "onStartCommand: " + action);

        if (wear_integration && SP.getBoolean(TIZEN_ENABLE, false)) {
            handler.post(() -> {

                if (isConnectionEstablished()) {
                    if (ACTION_RESEND.equals(action)) {
                        resendData();
                    } else
                    if (ACTION_SEND_STATUS.equals(action)) {
                        sendStatus();
                    } else if (ACTION_SEND_BASALS.equals(action)) {
                        sendBasals();
                    } else if (ACTION_SEND_ACTIONCONFIRMATIONREQUEST.equals(action)) {
                        String title = intent.getStringExtra("title");
                        String message = intent.getStringExtra("message");
                        String actionstring = intent.getStringExtra("actionstring");
                        sendActionConfirmationRequest(title, message, actionstring);
                    } else {
                        sendData();
                    }
                }

//                    } else if (ACTION_OPEN_SETTINGS.equals(action)) {
//                        sendNotification();
//                    } else if (ACTION_SEND_STATUS.equals(action)) {
//                        sendStatus();
//                    } else if (ACTION_SEND_BASALS.equals(action)) {
//                        sendBasals();
//                    } else if (ACTION_SEND_BOLUSPROGRESS.equals(action)) {
//                        sendBolusProgress(intent.getIntExtra("progresspercent", 0), intent.hasExtra("progressstatus") ? intent.getStringExtra("progressstatus") : "");
//                    } else if (ACTION_SEND_CHANGECONFIRMATIONREQUEST.equals(action)) {
//                        String title = intent.getStringExtra("title");
//                        String message = intent.getStringExtra("message");
//                        String actionstring = intent.getStringExtra("actionstring");
//                        sendChangeConfirmationRequest(title, message, actionstring);
//                    } else if (ACTION_CANCEL_NOTIFICATION.equals(action)) {
//                        String actionstring = intent.getStringExtra("actionstring");
//                        sendCancelNotificationRequest(actionstring);
//                    } else {
//                        sendData();
//                    }
//                } else {
//                    googleApiClient.connect();
//                }
            });
        }

        return START_STICKY;
    }

    private void resendData() {
        if (isConnectionEstablished()) {
            long startTime = System.currentTimeMillis() - (long) (60000 * 60 * 5.5);
            BgReading last_bg = DatabaseHelper.lastBg();

            if (last_bg == null) return;

            List<BgReading> graph_bgs = MainApp.getDbHelper().getBgreadingsDataFromTime(startTime, true);
            GlucoseStatus glucoseStatus = GlucoseStatus.getGlucoseStatusData(true);

            if (!graph_bgs.isEmpty()) {
                /*
                DataMap entries = dataMapSingleBG(last_bg, glucoseStatus);
                if (entries == null) {
                    ToastUtils.showToastInUiThread(this, MainApp.gs(R.string.noprofile));
                    return;
                }
                final ArrayList<DataMap> dataMaps = new ArrayList<>(graph_bgs.size());
                for (BgReading bg : graph_bgs) {
                    DataMap dataMap = dataMapSingleBG(bg, glucoseStatus);
                    if (dataMap != null) {
                        dataMaps.add(dataMap);
                    }
                }
                entries.putDataMapArrayList("entries", dataMaps);
                executeTask(new SendToDataLayerThread(WEARABLE_DATA_PATH, googleApiClient), entries);

                 */
            }
            sendPreferences();
            sendBasals();
            sendStatus();
        }
    }

    private void sendPreferences() {
        if (isConnectionEstablished()) {
            /*
            boolean wearcontrol = SP.getBoolean("wearcontrol", false);

            PutDataMapRequest dataMapRequest = PutDataMapRequest.create(NEW_PREFERENCES_PATH);
            //unique content
            dataMapRequest.getDataMap().putLong("timestamp", System.currentTimeMillis());
            dataMapRequest.getDataMap().putBoolean("wearcontrol", wearcontrol);
            PutDataRequest putDataRequest = dataMapRequest.asPutDataRequest();
            debugData("sendPreferences", putDataRequest);
            Wearable.DataApi.putDataItem(googleApiClient, putDataRequest);
            
             */
        } else {
            Log.e("SendStatus", "No connection to wearable available!");
        }
    }


    private void sendActionConfirmationRequest(String title, String message, String actionstring) {
        if (isConnectionEstablished()) {
            try {
                JSONObject data = new JSONObject();
                data.put("timestamp", System.currentTimeMillis());
                data.put("actionConfirmationRequest", "actionConfirmationRequest");
                data.put("title", title);
                data.put("message", message);
                data.put("actionstring", actionstring);

            } catch (JSONException e) {
                LOGGER.info("Unhandled exception", e); //LOGGER.info("sendData: mConnectionHandler(isNull)=" + (mConnectionHandler == null));
            }
            /*
            dataMapRequest.getDataMap().putLong("timestamp", System.currentTimeMillis());
            dataMapRequest.getDataMap().putString("actionConfirmationRequest", "actionConfirmationRequest");
            dataMapRequest.getDataMap().putString("title", title);
            dataMapRequest.getDataMap().putString("message", message);
            dataMapRequest.getDataMap().putString("actionstring", actionstring);
             */
            String rmessage = databaseList().toString();
            LOGGER.info("Requesting confirmation from tizen: " + actionstring);

            new Thread(new Runnable() {
                public void run() {
                    try {
                        mConnectionHandler.send(ACTION_CONFIRMATION_REQUEST_CH, rmessage.getBytes());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();

        } else {
            Log.e("confirmationRequest", "No connection to tizen available!");
        }
    }


    private void sendBasals() {
//      if (isConnectionEstablished2()) {
//
//        long now = System.currentTimeMillis();
//        final long startTimeWindow = now - (long) (60000 * 60 * 5.5);
//
//        ArrayList<DataMap> basals = new ArrayList<>();
//        ArrayList<DataMap> temps = new ArrayList<>();
//        ArrayList<DataMap> boluses = new ArrayList<>();
//        ArrayList<DataMap> predictions = new ArrayList<>();
//
//        Profile profile = ProfileFunctions.getInstance().getProfile();
//
//        if (profile == null) {
//            return;
//        }
//
//        long beginBasalSegmentTime = startTimeWindow;
//        long runningTime = startTimeWindow;
//
//        double beginBasalValue = profile.getBasal(beginBasalSegmentTime);
//        double endBasalValue = beginBasalValue;
//
//        TemporaryBasal tb1 = TreatmentsPlugin.getPlugin().getTempBasalFromHistory(runningTime);
//        TemporaryBasal tb2 = TreatmentsPlugin.getPlugin().getTempBasalFromHistory(runningTime);
//        double tb_before = beginBasalValue;
//        double tb_amount = beginBasalValue;
//        long tb_start = runningTime;
//
//        if (tb1 != null) {
//            tb_before = beginBasalValue;
//            Profile profileTB = ProfileFunctions.getInstance().getProfile(runningTime);
//            if (profileTB != null) {
//                tb_amount = tb1.tempBasalConvertedToAbsolute(runningTime, profileTB);
//                tb_start = runningTime;
//            }
//        }
//
//        for (; runningTime < now; runningTime += 5 * 60 * 1000) {
//            Profile profileTB = ProfileFunctions.getInstance().getProfile(runningTime);
//            if (profileTB == null)
//                return;
//            //basal rate
//            endBasalValue = profile.getBasal(runningTime);
//            if (endBasalValue != beginBasalValue) {
//                //push the segment we recently left
//                basals.add(basalMap(beginBasalSegmentTime, runningTime, beginBasalValue));
//
//                //begin new Basal segment
//                beginBasalSegmentTime = runningTime;
//                beginBasalValue = endBasalValue;
//            }
//
//            //temps
//            tb2 = TreatmentsPlugin.getPlugin().getTempBasalFromHistory(runningTime);
//
//            if (tb1 == null && tb2 == null) {
//                //no temp stays no temp
//
//            } else if (tb1 != null && tb2 == null) {
//                //temp is over -> push it
//                temps.add(tempDatamap(tb_start, tb_before, runningTime, endBasalValue, tb_amount));
//                tb1 = null;
//
//            } else if (tb1 == null && tb2 != null) {
//                //temp begins
//                tb1 = tb2;
//                tb_start = runningTime;
//                tb_before = endBasalValue;
//                tb_amount = tb1.tempBasalConvertedToAbsolute(runningTime, profileTB);
//
//            } else if (tb1 != null && tb2 != null) {
//                double currentAmount = tb2.tempBasalConvertedToAbsolute(runningTime, profileTB);
//                if (currentAmount != tb_amount) {
//                    temps.add(tempDatamap(tb_start, tb_before, runningTime, currentAmount, tb_amount));
//                    tb_start = runningTime;
//                    tb_before = tb_amount;
//                    tb_amount = currentAmount;
//                    tb1 = tb2;
//                }
//            }
//        }
//        if (beginBasalSegmentTime != runningTime) {
//            //push the remaining segment
//            basals.add(basalMap(beginBasalSegmentTime, runningTime, beginBasalValue));
//        }
//        if (tb1 != null) {
//            tb2 = TreatmentsPlugin.getPlugin().getTempBasalFromHistory(now); //use "now" to express current situation
//            if (tb2 == null) {
//                //express the cancelled temp by painting it down one minute early
//                temps.add(tempDatamap(tb_start, tb_before, now - 1 * 60 * 1000, endBasalValue, tb_amount));
//            } else {
//                //express currently running temp by painting it a bit into the future
//                Profile profileNow = ProfileFunctions.getInstance().getProfile(now);
//                double currentAmount = tb2.tempBasalConvertedToAbsolute(now, profileNow);
//                if (currentAmount != tb_amount) {
//                    temps.add(tempDatamap(tb_start, tb_before, now, tb_amount, tb_amount));
//                    temps.add(tempDatamap(now, tb_amount, runningTime + 5 * 60 * 1000, currentAmount, currentAmount));
//                } else {
//                    temps.add(tempDatamap(tb_start, tb_before, runningTime + 5 * 60 * 1000, tb_amount, tb_amount));
//                }
//            }
//        } else {
//            tb2 = TreatmentsPlugin.getPlugin().getTempBasalFromHistory(now); //use "now" to express current situation
//            if (tb2 != null) {
//                //onset at the end
//                Profile profileTB = ProfileFunctions.getInstance().getProfile(runningTime);
//                double currentAmount = tb2.tempBasalConvertedToAbsolute(runningTime, profileTB);
//                temps.add(tempDatamap(now - 1 * 60 * 1000, endBasalValue, runningTime + 5 * 60 * 1000, currentAmount, currentAmount));
//            }
//        }
//
//        List<Treatment> treatments = TreatmentsPlugin.getPlugin().getTreatmentsFromHistory();
//        for (Treatment treatment : treatments) {
//            if (treatment.date > startTimeWindow) {
//                boluses.add(treatmentMap(treatment.date, treatment.insulin, treatment.carbs, treatment.isSMB, treatment.isValid));
//            }
//
//        }
//
//        final LoopPlugin.LastRun finalLastRun = LoopPlugin.lastRun;
//        if (SP.getBoolean("wear_predictions", true) && finalLastRun != null && finalLastRun.request.hasPredictions && finalLastRun.constraintsProcessed != null) {
//            List<BgReading> predArray = finalLastRun.constraintsProcessed.getPredictions();
//
//            if (!predArray.isEmpty()) {
//                for (BgReading bg : predArray) {
//                    if (bg.value < 40) continue;
//                    predictions.add(predictionMap(bg.date, bg.value, bg.getPredectionColor()));
//                }
//            }
//        }
//
//        DataMap dm = new DataMap();
//        dm.putDataMapArrayList("basals", basals);
//        dm.putDataMapArrayList("temps", temps);
//        dm.putDataMapArrayList("boluses", boluses);
//        dm.putDataMapArrayList("predictions", predictions);
//
        /*
            new Thread(new Runnable() {
                public void run() {
                    try {
                        mConnectionHandler.send(ACTION_CONFIRMATION_REQUEST_CH, message.getBytes());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }

         */
    }


    @NonNull
    private String generateStatusString(Profile profile, String currentBasal, String iobSum, String iobDetail, String bgiString) {
        String status = "";
        if (profile == null) {
            status = MainApp.gs(R.string.noprofile);
            return status;
        }
        LoopPlugin activeloop = LoopPlugin.getPlugin();
        if (!activeloop.isEnabled(PluginType.LOOP)) {
            status += MainApp.gs(R.string.disabledloop) + "\n";
            lastLoopStatus = false;
        } else {
            lastLoopStatus = true;
        }
        String iobString = "";
        if (SP.getBoolean(R.string.key_wear_detailediob, false)) {
            iobString = iobSum + " " + iobDetail;
        } else {
            iobString = iobSum + "U";
        }
        status += currentBasal + " " + iobString;
        //add BGI if shown, otherwise return
        if (SP.getBoolean(R.string.key_wear_showbgi, false)) {
            status += " " + bgiString;
        }
        return status;
    }

    @NonNull
    private String generateBasalString(TreatmentsInterface treatmentsInterface) {
        String basalStringResult;
        Profile profile = ProfileFunctions.getInstance().getProfile();
        if (profile == null)
            return "";
        TemporaryBasal activeTemp = treatmentsInterface.getTempBasalFromHistory(System.currentTimeMillis());
        if (activeTemp != null) {
            basalStringResult = activeTemp.toStringShort();
        } else {
            if (SP.getBoolean(R.string.key_danar_visualizeextendedaspercentage, false)) {
                basalStringResult = "100%";
            } else {
                basalStringResult = DecimalFormatter.to2Decimal(profile.getBasal()) + "U/h";
            }
        }
        return basalStringResult;
    }

    public static int getBatteryLevel(Context context) {
        Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryIntent != null) {
            int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level == -1 || scale == -1) {
                return 50;
            }
            return (int) (((float) level / (float) scale) * 100.0f);
        }
        return 50;
    }

    private void sendStatus() {

        if (isConnectionEstablished()) {
            Profile profile = ProfileFunctions.getInstance().getProfile();
            String status = MainApp.gs(R.string.noprofile);
            String iobSum, iobDetail, cobString, currentBasal, bgiString;
            iobSum = iobDetail = cobString = currentBasal = bgiString = "";
            if (profile != null) {
                TreatmentsInterface treatmentsInterface = TreatmentsPlugin.getPlugin();
                treatmentsInterface.updateTotalIOBTreatments();
                IobTotal bolusIob = treatmentsInterface.getLastCalculationTreatments().round();
                treatmentsInterface.updateTotalIOBTempBasals();
                IobTotal basalIob = treatmentsInterface.getLastCalculationTempBasals().round();

                iobSum = DecimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob);
                iobDetail = "(" + DecimalFormatter.to2Decimal(bolusIob.iob) + "|" + DecimalFormatter.to2Decimal(basalIob.basaliob) + ")";
                cobString = IobCobCalculatorPlugin.getPlugin().getCobInfo(false, "WatcherUpdaterService").generateCOBString();
                currentBasal = generateBasalString(treatmentsInterface);
                //bgi
                double bgi = -(bolusIob.activity + basalIob.activity) * 5 * Profile.fromMgdlToUnits(profile.getIsfMgdl(), ProfileFunctions.getSystemUnits());
                bgiString = "" + ((bgi >= 0) ? "+" : "") + DecimalFormatter.to1Decimal(bgi);

                status = generateStatusString(profile, currentBasal, iobSum, iobDetail, bgiString);
            }

            //batteries
            int phoneBattery = getBatteryLevel(getApplicationContext());
            String rigBattery = NSDeviceStatus.getInstance().getUploaderStatus().trim();

            long openApsStatus = -1;
            //OpenAPS status
            if (Config.APS) {
                //we are AndroidAPS
                openApsStatus = LoopPlugin.lastRun != null && LoopPlugin.lastRun.lastTBREnact != 0 ? LoopPlugin.lastRun.lastTBREnact : -1;
            } else {
                //NSClient or remote
                openApsStatus = NSDeviceStatus.getOpenApsTimestamp();
            }

            // PutDataMapRequest dataMapRequest = PutDataMapRequest.create(NEW_STATUS_PATH);
            // unique content
            // dataMapRequest.getDataMap().putString("externalStatusString", status);
            // dataMapRequest.getDataMap().putString("iobSum", iobSum);
            // dataMapRequest.getDataMap().putString("iobDetail", iobDetail);
            // dataMapRequest.getDataMap().putBoolean("detailedIob", SP.getBoolean(R.string.key_wear_detailediob, false));
            // dataMapRequest.getDataMap().putString("cob", cobString);
            // dataMapRequest.getDataMap().putString("currentBasal", currentBasal);
            // dataMapRequest.getDataMap().putString("battery", "" + phoneBattery);
            // dataMapRequest.getDataMap().putString("rigBattery", rigBattery);
            // dataMapRequest.getDataMap().putLong("openApsStatus", openApsStatus);
            // dataMapRequest.getDataMap().putString("bgi", bgiString);
            // dataMapRequest.getDataMap().putBoolean("showBgi", SP.getBoolean(R.string.key_wear_showbgi, false));
            // dataMapRequest.getDataMap().putInt("batteryLevel", (phoneBattery >= 30) ? 1 : 0);
            // PutDataRequest putDataRequest = dataMapRequest.asPutDataRequest();

            /*
            new Thread(new Runnable() {
                public void run() {
                    try {
                        mConnectionHandler.send(ACTION_CONFIRMATION_REQUEST_CH, message.getBytes());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
            */
        } else {
            Log.e("SendStatus", "No connection to wearable available!");
        }
    }


    private void sendData() {

//        BgReading lastBG = DatabaseHelper.lastBg();
//        // Log.d(TAG, logPrefix + "LastBg=" + lastBG);
//        if (lastBG != null) {
//            GlucoseStatus glucoseStatus = GlucoseStatus.getGlucoseStatusData();
//
//            if (googleApiClient != null && !googleApiClient.isConnected() && !googleApiClient.isConnecting()) {
//                googleApiConnect();
//            }
//
//            if (wear_integration) {
//
//                final DataMap dataMap = dataMapSingleBG(lastBG, glucoseStatus);
//                if (dataMap == null) {
//                    ToastUtils.showToastInUiThread(this, MainApp.gs(R.string.noprofile));
//                    return;
//                }
//
//                executeTask(new SendToDataLayerThread(WEARABLE_DATA_PATH, googleApiClient), dataMap);
//            }
//        }
        BgReading lastBG = DatabaseHelper.lastBg();
        LOGGER.info("sendData: mConnectionHandler(isNull)=" + (mConnectionHandler == null));

        if (mConnectionHandler == null) {
            return;
        }
        if (mConnectionHandler != null && !mConnectionHandler.isConnected() ) {
            tizenApiConnect();
        }

        Calendar calendar = new GregorianCalendar();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd aa hh:mm:ss.SSS");
        String timeStr = " " + dateFormat.format(calendar.getTime());
        String strToUpdateUI = new String();
        final String message = strToUpdateUI.concat(timeStr);
        new Thread(new Runnable() {
            public void run() {
                try {
                    mConnectionHandler.send(getServiceChannelId(0), message.getBytes());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        
    }


}
