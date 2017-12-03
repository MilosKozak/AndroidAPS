package info.nightscout.androidaps.plugins.PumpDanaRKorean.services;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.os.SystemClock;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventInitializationChanged;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Overview.Dialogs.BolusProgressDialog;
import info.nightscout.androidaps.plugins.Overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MessageBase;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgBolusProgress;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgBolusStart;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgBolusStop;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgHistoryAlarm;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgHistoryBasalHour;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgHistoryBolus;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgHistoryCarbo;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgHistoryDailyInsulin;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgHistoryDone;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgHistoryError;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgHistoryGlucose;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgHistoryRefill;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgHistorySuspend;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgPCCommStart;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgPCCommStop;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgSetCarbsEntry;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgSetExtendedBolusStart;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgSetExtendedBolusStop;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgSetSingleBasalProfile;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgSetTempBasalStart;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgSetTempBasalStop;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgSetTime;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgSettingGlucose;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgSettingMaxValues;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgSettingMeal;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgSettingProfileRatios;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgSettingPumpTime;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgSettingShippingInfo;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgStatusBolusExtended;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgStatusTempBasal;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.RecordTypes;
import info.nightscout.androidaps.plugins.PumpDanaR.events.EventDanaRNewStatus;
import info.nightscout.androidaps.plugins.PumpDanaRKorean.DanaRKoreanPlugin;
import info.nightscout.androidaps.plugins.PumpDanaRKorean.SerialIOThread;
import info.nightscout.androidaps.plugins.Overview.notifications.Notification;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.PumpDanaRKorean.comm.MsgCheckValue_k;
import info.nightscout.androidaps.plugins.PumpDanaRKorean.comm.MsgSettingBasal_k;
import info.nightscout.androidaps.plugins.PumpDanaRKorean.comm.MsgStatusBasic_k;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.utils.NSUpload;
import info.nightscout.utils.SP;
import info.nightscout.utils.ToastUtils;

public class DanaRKoreanExecutionService extends Service {
    private static Logger log = LoggerFactory.getLogger(DanaRKoreanExecutionService.class);

    private String devName;

    private SerialIOThread mSerialIOThread;
    private BluetoothSocket mRfcommSocket;
    private BluetoothDevice mBTDevice;

    private IBinder mBinder = new LocalBinder();

    private DanaRPump danaRPump = DanaRPump.getInstance();
    private Treatment bolusingTreatment = null;

    private static Boolean connectionInProgress = false;

    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                log.debug("Device was disconnected " + device.getName());//Device was disconnected
                if (mBTDevice != null && mBTDevice.getName() != null && mBTDevice.getName().equals(device.getName())) {
                    if (mSerialIOThread != null) {
                        mSerialIOThread.disconnect("BT disconnection broadcast");
                    }
                    MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTED));
                }
            }
        }
    };

    public DanaRKoreanExecutionService() {
        registerBus();
        MainApp.instance().getApplicationContext().registerReceiver(receiver, new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED));
    }

    public class LocalBinder extends Binder {
        public DanaRKoreanExecutionService getServiceInstance() {
            return DanaRKoreanExecutionService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

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

    @Subscribe
    public void onStatusEvent(EventAppExit event) {
        if (Config.logFunctionCalls)
            log.debug("EventAppExit received");

        if (mSerialIOThread != null)
            mSerialIOThread.disconnect("Application exit");

        MainApp.instance().getApplicationContext().unregisterReceiver(receiver);

        stopSelf();
        if (Config.logFunctionCalls)
            log.debug("EventAppExit finished");
    }

    public boolean isConnected() {
        return mRfcommSocket != null && mRfcommSocket.isConnected();
    }

    public boolean isConnecting() {
        return connectionInProgress;
    }

    public void disconnect(String from) {
        if (mSerialIOThread != null)
            mSerialIOThread.disconnect(from);
    }

    public void connect(String from) {
        if (danaRPump.password != -1 && danaRPump.password != SP.getInt(R.string.key_danar_password, -1)) {
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.wrongpumppassword), R.raw.error);
            return;
        }

        if (connectionInProgress)
            return;

        new Thread(new Runnable() {
            @Override
            public void run() {
                connectionInProgress = true;
                getBTSocketForSelectedPump();
                if (mRfcommSocket == null || mBTDevice == null) {
                    connectionInProgress = false;
                    return; // Device not found
                }

                try {
                    mRfcommSocket.connect();
                } catch (IOException e) {
                    //log.error("Unhandled exception", e);
                    if (e.getMessage().contains("socket closed")) {
                        log.error("Unhandled exception", e);
                    }
                }

                if (isConnected()) {
                    if (mSerialIOThread != null) {
                        mSerialIOThread.disconnect("Recreate SerialIOThread");
                    }
                    mSerialIOThread = new SerialIOThread(mRfcommSocket);
                    MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.CONNECTED, 0));
                }

                connectionInProgress = false;
            }
        }).start();
    }

    public void stopConnecting() {
        if (mSerialIOThread != null)
            mSerialIOThread.disconnect("stopConnecting");
    }

    private void getBTSocketForSelectedPump() {
        devName = SP.getString(MainApp.sResources.getString(R.string.key_danar_bt_name), "");
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter != null) {
            Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();

            for (BluetoothDevice device : bondedDevices) {
                if (devName.equals(device.getName())) {
                    mBTDevice = device;
                    try {
                        mRfcommSocket = mBTDevice.createRfcommSocketToServiceRecord(SPP_UUID);
                    } catch (IOException e) {
                        log.error("Error creating socket: ", e);
                    }
                    break;
                }
            }
        } else {
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.nobtadapter));
        }
        if (mBTDevice == null) {
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.devicenotfound));
        }
    }

    @Subscribe
    public void onStatusEvent(final EventPreferenceChange pch) {
        if (mSerialIOThread != null)
            mSerialIOThread.disconnect("EventPreferenceChange");
    }

    public void getPumpStatus() {
        try {
            MainApp.bus().post(new EventPumpStatusChanged(MainApp.sResources.getString(R.string.gettingpumpstatus)));
            //MsgStatus_k statusMsg = new MsgStatus_k();
            MsgStatusBasic_k statusBasicMsg = new MsgStatusBasic_k();
            MsgStatusTempBasal tempStatusMsg = new MsgStatusTempBasal();
            MsgStatusBolusExtended exStatusMsg = new MsgStatusBolusExtended();
            MsgCheckValue_k checkValue = new MsgCheckValue_k();

            if (danaRPump.isNewPump) {
                mSerialIOThread.sendMessage(checkValue);
                if (!checkValue.received) {
                    return;
                }
            }

            //mSerialIOThread.sendMessage(statusMsg);
            mSerialIOThread.sendMessage(statusBasicMsg);
            MainApp.bus().post(new EventPumpStatusChanged(MainApp.sResources.getString(R.string.gettingtempbasalstatus)));
            mSerialIOThread.sendMessage(tempStatusMsg);
            MainApp.bus().post(new EventPumpStatusChanged(MainApp.sResources.getString(R.string.gettingextendedbolusstatus)));
            mSerialIOThread.sendMessage(exStatusMsg);
            MainApp.bus().post(new EventPumpStatusChanged(MainApp.sResources.getString(R.string.gettingbolusstatus)));

            Date now = new Date();
            if (danaRPump.lastSettingsRead.getTime() + 60 * 60 * 1000L < now.getTime() || !MainApp.getSpecificPlugin(DanaRKoreanPlugin.class).isInitialized()) {
                MainApp.bus().post(new EventPumpStatusChanged(MainApp.sResources.getString(R.string.gettingpumpsettings)));
                mSerialIOThread.sendMessage(new MsgSettingShippingInfo());
                mSerialIOThread.sendMessage(new MsgSettingMeal());
                mSerialIOThread.sendMessage(new MsgSettingBasal_k());
                //0x3201
                mSerialIOThread.sendMessage(new MsgSettingMaxValues());
                mSerialIOThread.sendMessage(new MsgSettingGlucose());
                mSerialIOThread.sendMessage(new MsgSettingProfileRatios());
                MainApp.bus().post(new EventPumpStatusChanged(MainApp.sResources.getString(R.string.gettingpumptime)));
                mSerialIOThread.sendMessage(new MsgSettingPumpTime());
                long timeDiff = (danaRPump.pumpTime.getTime() - System.currentTimeMillis()) / 1000L;
                log.debug("Pump time difference: " + timeDiff + " seconds");
                if (Math.abs(timeDiff) > 10) {
                    mSerialIOThread.sendMessage(new MsgSetTime(new Date()));
                    mSerialIOThread.sendMessage(new MsgSettingPumpTime());
                    timeDiff = (danaRPump.pumpTime.getTime() - System.currentTimeMillis()) / 1000L;
                    log.debug("Pump time difference: " + timeDiff + " seconds");
                }
                danaRPump.lastSettingsRead = now;
            }

            danaRPump.lastConnection = now;
            MainApp.bus().post(new EventDanaRNewStatus());
            MainApp.bus().post(new EventInitializationChanged());
            NSUpload.uploadDeviceStatus();
            if (danaRPump.dailyTotalUnits > danaRPump.maxDailyTotalUnits * Constants.dailyLimitWarning) {
                log.debug("Approaching daily limit: " + danaRPump.dailyTotalUnits + "/" + danaRPump.maxDailyTotalUnits);
                Notification reportFail = new Notification(Notification.APPROACHING_DAILY_LIMIT, MainApp.sResources.getString(R.string.approachingdailylimit), Notification.URGENT);
                MainApp.bus().post(new EventNewNotification(reportFail));
                NSUpload.uploadError(MainApp.sResources.getString(R.string.approachingdailylimit) + ": " + danaRPump.dailyTotalUnits + "/" + danaRPump.maxDailyTotalUnits + "U");
            }
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
        return;
    }

    public boolean tempBasal(int percent, int durationInHours) {
        if (!isConnected()) return false;
        if (danaRPump.isTempBasalInProgress) {
            MainApp.bus().post(new EventPumpStatusChanged(MainApp.sResources.getString(R.string.stoppingtempbasal)));
            mSerialIOThread.sendMessage(new MsgSetTempBasalStop());
            waitMsec(500);
        }
        MainApp.bus().post(new EventPumpStatusChanged(MainApp.sResources.getString(R.string.settingtempbasal)));
        mSerialIOThread.sendMessage(new MsgSetTempBasalStart(percent, durationInHours));
        mSerialIOThread.sendMessage(new MsgStatusTempBasal());
        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTING));
        return true;
    }

    public boolean tempBasalStop() {
        if (!isConnected()) return false;
        MainApp.bus().post(new EventPumpStatusChanged(MainApp.sResources.getString(R.string.stoppingtempbasal)));
        mSerialIOThread.sendMessage(new MsgSetTempBasalStop());
        mSerialIOThread.sendMessage(new MsgStatusTempBasal());
        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTING));
        return true;
    }

    public boolean extendedBolus(double insulin, int durationInHalfHours) {
        if (!isConnected()) return false;
        MainApp.bus().post(new EventPumpStatusChanged(MainApp.sResources.getString(R.string.settingextendedbolus)));
        mSerialIOThread.sendMessage(new MsgSetExtendedBolusStart(insulin, (byte) (durationInHalfHours & 0xFF)));
        mSerialIOThread.sendMessage(new MsgStatusBolusExtended());
        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTING));
        return true;
    }

    public boolean extendedBolusStop() {
        if (!isConnected()) return false;
        MainApp.bus().post(new EventPumpStatusChanged(MainApp.sResources.getString(R.string.stoppingextendedbolus)));
        mSerialIOThread.sendMessage(new MsgSetExtendedBolusStop());
        mSerialIOThread.sendMessage(new MsgStatusBolusExtended());
        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTING));
        return true;
    }

    public boolean bolus(double amount, int carbs, final Treatment t) {
        if (!isConnected()) return false;
        if (BolusProgressDialog.stopPressed) return false;

        bolusingTreatment = t;
        MsgBolusStart start = new MsgBolusStart(amount);
        MsgBolusStop stop = new MsgBolusStop(amount, t);

        if (carbs > 0) {
            mSerialIOThread.sendMessage(new MsgSetCarbsEntry(System.currentTimeMillis(), carbs));
        }

        MsgBolusProgress progress = new MsgBolusProgress(amount, t); // initialize static variables
        long bolusStart = System.currentTimeMillis();

        if (!stop.stopped) {
            mSerialIOThread.sendMessage(start);
        } else {
            t.insulin = 0d;
            return false;
        }
        while (!stop.stopped && !start.failed) {
            waitMsec(100);
            if ((System.currentTimeMillis() - progress.lastReceive) > 15 * 1000L) { // if i didn't receive status for more than 15 sec expecting broken comm
                stop.stopped = true;
                stop.forced = true;
                log.debug("Communication stopped");
            }
        }
        waitMsec(300);

        EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.getInstance();
        bolusingEvent.t = t;
        bolusingEvent.percent = 99;

        bolusingTreatment = null;

        int speed = 12;

        // try to find real amount if bolusing was interrupted or comm failed
        if (t.insulin != amount) {
            disconnect("bolusingInterrupted");
            long bolusDurationInMSec = (long) (amount * speed * 1000);
            long expectedEnd = bolusStart + bolusDurationInMSec + 3000;

            while (System.currentTimeMillis() < expectedEnd) {
                long waitTime = expectedEnd - System.currentTimeMillis();
                bolusingEvent.status = String.format(MainApp.sResources.getString(R.string.waitingforestimatedbolusend), waitTime / 1000);
                MainApp.bus().post(bolusingEvent);
                SystemClock.sleep(1000);
            }

            final Object o = new Object();
            ConfigBuilderPlugin.getCommandQueue().readStatus("bolusingInterrupted", new Callback() {
                @Override
                public void run() {
                    if (danaRPump.lastBolusTime.getTime() > System.currentTimeMillis() - 60 * 1000L) { // last bolus max 1 min old
                        t.insulin = danaRPump.lastBolusAmount;
                        log.debug("Used bolus amount from history: " + danaRPump.lastBolusAmount);
                    } else {
                        log.debug("Bolus amount in history too old: " + danaRPump.lastBolusTime.toLocaleString());
                    }
                    o.notify();
                }
            });
            try {
                o.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else {
            ConfigBuilderPlugin.getCommandQueue().readStatus("bolusOK", null);
        }
        return true;
    }

    public void bolusStop() {
        if (Config.logDanaBTComm)
            log.debug("bolusStop >>>>> @ " + (bolusingTreatment == null ? "" : bolusingTreatment.insulin));
        MsgBolusStop stop = new MsgBolusStop();
        stop.forced = true;
        if (isConnected()) {
            mSerialIOThread.sendMessage(stop);
            while (!stop.stopped) {
                mSerialIOThread.sendMessage(stop);
                waitMsec(200);
            }
        } else {
            stop.stopped = true;
        }
    }

    public boolean carbsEntry(int amount) {
        if (!isConnected()) return false;
        MsgSetCarbsEntry msg = new MsgSetCarbsEntry(System.currentTimeMillis(), amount);
        mSerialIOThread.sendMessage(msg);
        return true;
    }

    public PumpEnactResult loadHistory(byte type) {
        PumpEnactResult result = new PumpEnactResult();
        if (!isConnected()) return result;
        MessageBase msg = null;
        switch (type) {
            case RecordTypes.RECORD_TYPE_ALARM:
                msg = new MsgHistoryAlarm();
                break;
            case RecordTypes.RECORD_TYPE_BASALHOUR:
                msg = new MsgHistoryBasalHour();
                break;
            case RecordTypes.RECORD_TYPE_BOLUS:
                msg = new MsgHistoryBolus();
                break;
            case RecordTypes.RECORD_TYPE_CARBO:
                msg = new MsgHistoryCarbo();
                break;
            case RecordTypes.RECORD_TYPE_DAILY:
                msg = new MsgHistoryDailyInsulin();
                break;
            case RecordTypes.RECORD_TYPE_ERROR:
                msg = new MsgHistoryError();
                break;
            case RecordTypes.RECORD_TYPE_GLUCOSE:
                msg = new MsgHistoryGlucose();
                break;
            case RecordTypes.RECORD_TYPE_REFILL:
                msg = new MsgHistoryRefill();
                break;
            case RecordTypes.RECORD_TYPE_SUSPEND:
                msg = new MsgHistorySuspend();
                break;
        }
        MsgHistoryDone done = new MsgHistoryDone();
        mSerialIOThread.sendMessage(new MsgPCCommStart());
        waitMsec(400);
        mSerialIOThread.sendMessage(msg);
        while (!done.received && mRfcommSocket.isConnected()) {
            waitMsec(100);
        }
        waitMsec(200);
        mSerialIOThread.sendMessage(new MsgPCCommStop());
        result.success = true;
        result.comment = "OK";
        return result;
    }

    public boolean updateBasalsInPump(final Profile profile) {
        if (!isConnected()) return false;
        MainApp.bus().post(new EventPumpStatusChanged(MainApp.sResources.getString(R.string.updatingbasalrates)));
        double[] basal = DanaRPump.buildDanaRProfileRecord(profile);
        MsgSetSingleBasalProfile msgSet = new MsgSetSingleBasalProfile(basal);
        mSerialIOThread.sendMessage(msgSet);
        danaRPump.lastSettingsRead = new Date(0); // force read full settings
        getPumpStatus();
        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTING));
        return true;
    }

    private void waitMsec(long msecs) {
        SystemClock.sleep(msecs);
    }
}
