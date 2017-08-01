package info.nightscout.androidaps.plugins.Loop;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.app.NotificationCompat;

import com.squareup.otto.Subscribe;

import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainActivity;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.events.EventTreatmentChange;
import info.nightscout.androidaps.interfaces.APSInterface;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Loop.events.EventLoopSetLastRunGui;
import info.nightscout.androidaps.plugins.Loop.events.EventLoopUpdateGui;
import info.nightscout.androidaps.plugins.Loop.events.EventNewOpenLoopNotification;
import info.nightscout.androidaps.plugins.OpenAPSAMA.OpenAPSAMAPlugin;
import info.nightscout.utils.NSUpload;
import info.nightscout.utils.SP;
// Added by Rumen for SMB enact
//import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.plugins.OpenAPSSMB.OpenAPSSMBPlugin;
import info.nightscout.androidaps.interfaces.PumpInterface;
//import info.nightscout.androidaps.interfaces.InsulinInterface;                               
import info.nightscout.utils.SP;
import android.support.v4.app.DialogFragment;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import java.util.List;
import info.nightscout.androidaps.data.DetailedBolusInfo;

import info.nightscout.utils.SafeParse;
// TODO: ALL checks from oref0-pump-loop
/**
 * Created by mike on 05.08.2016.
 * Added support for SMB by Rumen on 01.06.2017
        && wait_for_bg  -- done - SMB bolusing is only triggered by new BG data or press of the Loop button
        && wait_for_silence -- # listen for $1 seconds of silence (no other rigs talking to pump) before continuing -- not sure about that
        && if_mdt_get_bg -- MDT not supported by AAPS
        && refresh_old_pumphistory_enact refresh pumphistory if it's more than 15m old and enact -- DONE see noConnectionLast15Min
        # Read the pump reservoir volume and verify it is within 0.1U of the expected volume - DONE Line #360 but not passed as value to determine-basal.js
        # check if the temp was read more than 5m ago, or has been running more than 10m
        # enact the appropriate temp before SMB'ing, (only if smb_verify_enacted fails) - Rumen: I believe that works but now It does SMB first and then sets required basal. 
        # Read the currently running temp and verify rate matches (within 0.03U/hr) and duration is no shorter than 5m less than smb-suggested.json -- DONE line 484 does that
        # Verify that the suggested.json is less than 5 minutes old and administer the supermicrobolus - in AAPS APS doesn't provide suggestion if BG is more than 8 min old
        # If temp basal duration is zero, unsuspend pump -- duration = 0 means cancel temp basal or extended bolus, if pump is suspended - loop is also suspended
        && refresh_old_pumphistory_24h # refresh pumphistory_24h if it's more than 2h old
        && refresh_old_profile # refresh settings/profile if it's more than 1h old
        && touch monitor/pump_loop_enacted -r monitor/glucose.json \
        && refresh_temp_and_enact \
        && refresh_pumphistory_and_enact \
        && refresh_profile \
        && refresh_pumphistory_24h \
        && echo Completed pump-loop at $(date) \
        && touch monitor/pump_loop_completed -r monitor/pump_loop_enacted \
 
 */
public class LoopPlugin implements PluginBase {
    private static Logger log = LoggerFactory.getLogger(LoopPlugin.class);

    private static Handler sHandler;
    private static HandlerThread sHandlerThread;

    private boolean fragmentEnabled = false;
    private boolean fragmentVisible = false;

    private long loopSuspendedTill = 0L; // end of manual loop suspend
    private boolean isSuperBolus = false;
    public Boolean smbEnacted = false;
    
    public class LastRun {
        public APSResult request = null;
        public APSResult constraintsProcessed = null;
        public PumpEnactResult setByPump = null;
        public String source = null;
        public Date lastAPSRun = null;
        public Date lastEnact = null;
        public Date lastOpenModeAccept;
        public Double smb = null;
        public Boolean smbEnacted = false;
        public String comment= "";
    }

    static public LastRun lastRun = null;

    public LoopPlugin() {
        if (sHandlerThread == null) {
            sHandlerThread = new HandlerThread(LoopPlugin.class.getSimpleName());
            sHandlerThread.start();
            sHandler = new Handler(sHandlerThread.getLooper());
        }
        MainApp.bus().register(this);
        loopSuspendedTill = SP.getLong("loopSuspendedTill", 0L);
        isSuperBolus = SP.getBoolean("isSuperBolus", false);
    }

    @Override
    public String getFragmentClass() {
        return LoopFragment.class.getName();
    }

    @Override
    public int getType() {
        return PluginBase.LOOP;
    }

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.loop);
    }

    @Override
    public String getNameShort() {
        String name = MainApp.sResources.getString(R.string.loop_shortname);
        if (!name.trim().isEmpty()){
            //only if translation exists
            return name;
        }
        // use long name as fallback
        return getName();
    }

    @Override
    public boolean isEnabled(int type) {
        return type == LOOP && fragmentEnabled && MainApp.getConfigBuilder().getPumpDescription().isTempBasalCapable;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return type == LOOP && fragmentVisible && MainApp.getConfigBuilder().getPumpDescription().isTempBasalCapable;
    }

    @Override
    public boolean canBeHidden(int type) {
        return true;
    }

    @Override
    public boolean hasFragment() {
        return true;
    }

    @Override
    public boolean showInList(int type) {
        return true;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        if (type == LOOP) this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        if (type == LOOP) this.fragmentVisible = fragmentVisible;
    }

    @Subscribe
    public void onStatusEvent(final EventTreatmentChange ev) {
        invoke("EventTreatmentChange", true);
    }

    @Subscribe
    public void onStatusEvent(final EventNewBG ev) {
        invoke("EventNewBG", true);
    }

    public long suspendedTo() {
        return loopSuspendedTill;
    }

    public void suspendTo(long endTime) {
        loopSuspendedTill = endTime;
        isSuperBolus = false;
        SP.putLong("loopSuspendedTill", loopSuspendedTill);
    }

    public void superBolusTo(long endTime) {
        loopSuspendedTill = endTime;
        isSuperBolus = true;
        SP.putLong("loopSuspendedTill", loopSuspendedTill);
    }

    public int minutesToEndOfSuspend() {
        if (loopSuspendedTill == 0)
            return 0;

        long now = System.currentTimeMillis();
        long msecDiff = loopSuspendedTill - now;

        if (loopSuspendedTill <= now) { // time exceeded
            suspendTo(0L);
            return 0;
        }

        return (int) (msecDiff / 60d / 1000d);
    }

    public boolean isSuspended() {
        if (loopSuspendedTill == 0)
            return false;

        long now = System.currentTimeMillis();

        if (loopSuspendedTill <= now) { // time exceeded
            suspendTo(0L);
            return false;
        }

        return true;
    }

   public boolean isSuperBolus() {
        if (loopSuspendedTill == 0)
            return false;

        long now = System.currentTimeMillis();

        if (loopSuspendedTill <= now) { // time exceeded
            suspendTo(0L);
            return false;
        }

        return isSuperBolus;
    }
    public boolean treatmentLast5min(){
        //TreatmentsInterface treatmentsInterface = ConfigBuilderPlugin.getActiveTreatments();
        List<Treatment> recentTreatments;
        recentTreatments = MainApp.getConfigBuilder().getTreatments5MinBackFromHistory(System.currentTimeMillis());
        if(recentTreatments.size() != 0){
            // There is treatment 
            return true;
        }
        return false;
    }

    public void invoke(String initiator, boolean allowNotification) {
        try {
            if (Config.logFunctionCalls)
                log.debug("invoke from " + initiator);
            ConstraintsInterface constraintsInterface = MainApp.getConfigBuilder();
            if (!constraintsInterface.isLoopEnabled()) {
                log.debug(MainApp.sResources.getString(R.string.loopdisabled));
                MainApp.bus().post(new EventLoopSetLastRunGui(MainApp.sResources.getString(R.string.loopdisabled)));
                return;
            }
            final ConfigBuilderPlugin configBuilder = MainApp.getConfigBuilder();
            APSResult result = null;

            if (configBuilder == null || !isEnabled(PluginBase.LOOP))
                return;

            if (isSuspended()) {
                log.debug(MainApp.sResources.getString(R.string.loopsuspended));
                MainApp.bus().post(new EventLoopSetLastRunGui(MainApp.sResources.getString(R.string.loopsuspended)));
                return;
            }

            if (configBuilder.isSuspended()) {
                log.debug(MainApp.sResources.getString(R.string.pumpsuspended));
                MainApp.bus().post(new EventLoopSetLastRunGui(MainApp.sResources.getString(R.string.pumpsuspended)));
                return;
            }

            if (configBuilder.getProfile() == null) {
                log.debug(MainApp.sResources.getString(R.string.noprofileselected));
                MainApp.bus().post(new EventLoopSetLastRunGui(MainApp.sResources.getString(R.string.noprofileselected)));
                return;
            }

            // Check if pump info is loaded
            if (configBuilder.getBaseBasalRate() < 0.01d) return;

            APSInterface usedAPS = configBuilder.getActiveAPS();
            Double smb_value = 0.0;                                                                               
            if (usedAPS != null && ((PluginBase) usedAPS).isEnabled(PluginBase.APS)) {
                usedAPS.invoke(initiator);
                result = usedAPS.getLastAPSResult();
                smb_value = usedAPS.smbValue();
            }
    
            // Check if we have any result
            if (result == null) {
                MainApp.bus().post(new EventLoopSetLastRunGui(MainApp.sResources.getString(R.string.noapsselected)));
                return;
            }

            // check rate for constraints
            final APSResult resultAfterConstraints = result.clone();
            resultAfterConstraints.rate = constraintsInterface.applyBasalConstraints(resultAfterConstraints.rate);
            
            if (lastRun == null) lastRun = new LastRun();
            lastRun.request = result;
            lastRun.constraintsProcessed = resultAfterConstraints;
            lastRun.lastAPSRun = new Date();
            lastRun.source = ((PluginBase) usedAPS).getName();
            // Added by Rumen for SMB in Loop
            
            //Getting last connection to pump
            boolean noConnectionLast15Min = false;
            PumpInterface activePump = ConfigBuilderPlugin.getActivePump();
            if(activePump!=null){
                log.debug("Activepump date is:"+activePump.lastDataTime());
                long lastConnection = activePump.lastDataTime().getTime();
                int lastConnectionMin = (int) (System.currentTimeMillis()-lastConnection)/(1000*60);
                if(lastConnectionMin>14) noConnectionLast15Min = true;
                log.debug("Last connection was "+lastConnectionMin+" min ago");
                //TODO Getting remaining insulin in pump reservoir => Works with Virtual and DanaR drivers only!
                if(activePump.getPumpDescription().reservoir != 0 ){
                    log.debug("Pump reservoir is:"+activePump.getPumpDescription().reservoir);
                } else log.debug("Pump reservoir is not in getPumpDescription()");
            }

            
            // If APS source s rumen's plugin
            boolean SMB_enable = false;
            if(SP.getBoolean("key_smb", false)){
                SMB_enable = true;
            } 
            // check if SMB is enabled from preferences
            if(lastRun.source.equals("Rumen SMB") && SMB_enable ){
                
                if(smb_value>0){ 
                    // Gett SMB by direct call of function
                    lastRun.smb = smb_value;
                } else {
                    // always ending here!!!
                    //lastRun.smb = usedAPS.smbValue();//smbPlugin.smbValue();
                    lastRun.smb = 0.0;//smbPlugin.smbValue();                    
                }
            } else {
                log.debug("Plugin is not Rumen SMB or SMB disabled in preferences");
                lastRun.smb = 0.0;
            }                                
            lastRun.setByPump = null;
            if(lastRun.smb == null) lastRun.smb = 0.0;
            //is there enough insulin in reservoir
            if((activePump.getPumpDescription().reservoir - lastRun.smb) < 10){
                log.debug("Error:Insulin in reservoir is not enough ( less than 10U! ");
                lastRun.smb = 0.0;
            } 
            // now SMB is here but needs to go afte closed loop check :)'
            log.debug("SMB vlalue is "+lastRun.smb);
            //lastRun.smb = 0.5;
            if(lastRun.smb > 0){
                // enacting SMB result but first check for treatment
                
                boolean treamentExists = treatmentLast5min();
                if(lastRun.lastEnact != null){
                    Long agoMsec = new Date().getTime() - lastRun.lastEnact.getTime();
                    int agoSec = (int) (agoMsec / 1000d);
                    if(agoSec > 300) smbEnacted = false;
                
                }
                log.debug("initiator is"+initiator+"\nSMB treatmentExists is "+treamentExists+" and smbEnacted is:"+smbEnacted);
                //if(!treamentExists && !smbEnacted){
                
                if(initiator == "EventNewBG" || initiator == "Loop button"){
                    log.debug("SMB entering after invoke from Loop button or new BG.");
                    // Testing Notification for SMB
                    boolean notificationForSMB = false;
                    if(notificationForSMB){
                        NotificationCompat.Builder builder =
                        new NotificationCompat.Builder(MainApp.instance().getApplicationContext());
                        builder.setSmallIcon(R.drawable.notif_icon)
                            .setContentTitle("New SMB Notification")
                            .setContentText("Requested SMB is "+lastRun.smb)
                            .setAutoCancel(true)
                            .setPriority(Notification.PRIORITY_HIGH)
                            .setCategory(Notification.CATEGORY_ALARM)
                            .setVisibility(Notification.VISIBILITY_PUBLIC);
                    
                        // Creates an explicit intent for an Activity in your app
                        Intent resultIntent = new Intent(MainApp.instance().getApplicationContext(), MainActivity.class);

                        // The stack builder object will contain an artificial back stack for the
                        // started Activity.
                        // This ensures that navigating backward from the Activity leads out of
                        // your application to the Home screen.
                        TaskStackBuilder stackBuilder = TaskStackBuilder.create(MainApp.instance().getApplicationContext());
                        stackBuilder.addParentStack(MainActivity.class);
                        // Adds the Intent that starts the Activity to the top of the stack
                        stackBuilder.addNextIntent(resultIntent);
                        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
                        builder.setContentIntent(resultPendingIntent);
                        builder.setVibrate(new long[]{1000, 1000, 1000, 1000, 1000});
                        NotificationManager mNotificationManager =
                            (NotificationManager) MainApp.instance().getSystemService(Context.NOTIFICATION_SERVICE);
                        // mId allows you to update the notification later on.
                        mNotificationManager.notify(Constants.notificationID, builder.build());
                        MainApp.bus().post(new EventNewOpenLoopNotification());
                    }// End of notification test
                    
                    final ConfigBuilderPlugin pump = MainApp.getConfigBuilder();
                    //PumpInterface activePump;
                    //activePump = ConfigBuilderPlugin.getActivePump();
                    PumpEnactResult enactResult;
                    log.debug("SMB just before setting  basal!");
                    Integer nullCarbs = 0;
                    Double smbFinalValue = lastRun.smb;
                    //DetailedBolusInfo detailedBolusInfo = new DetailedBolusInfo();
                    //detailedBolusInfo.insulin = smbFinalValue;
                    //PumpEnactResult result;
                    final int carbTime = 0;
                    DetailedBolusInfo detailedBolusInfo = new DetailedBolusInfo();
                    detailedBolusInfo.eventType = "SMB";
                    detailedBolusInfo.insulin = smbFinalValue;
                    detailedBolusInfo.carbs = 0;
                    detailedBolusInfo.context = null;
                    detailedBolusInfo.glucose = 0;
                    detailedBolusInfo.glucoseType = "Manual";
                    detailedBolusInfo.carbTime = 0;
                    //detailedBolusInfo.boluscalc = boluscalcJSON;
                    detailedBolusInfo.source = 3; // 3 is Source.USER
                    enactResult = pump.deliverTreatment(detailedBolusInfo);
                    if (!enactResult.success) {
                        log.debug("SMB of "+smbFinalValue+" failed!");
                        //OKDialog.show(getActivity(), MainApp.sResources.getString(R.string.treatmentdeliveryerror), result.comment, null);
                    } else log.debug("SMB of "+smbFinalValue+" done!");
                        if (result.changeRequested && result.rate > -1d && result.duration > -1) {
                            log.debug("Pubp basal is:"+pump.getBaseBasalRate());
                            log.debug("Entering closedLoop after SMB - rate is "+result.rate+" and duration is "+result.duration);  
                            log.debug("There is temp basal:"+configBuilder.isTempBasalInProgress());
                            final PumpEnactResult waiting = new PumpEnactResult();
                            final PumpEnactResult previousResult = lastRun.setByPump;
                            waiting.queued = true;
                            lastRun.setByPump = waiting;
                            MainApp.bus().post(new EventLoopUpdateGui());
                            sHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    final PumpEnactResult applyResult = configBuilder.applyAPSRequest(resultAfterConstraints);
                                    if (applyResult.enacted || applyResult.success) {
                                        lastRun.setByPump = applyResult;
                                        lastRun.lastEnact = lastRun.lastAPSRun;
                                    } else {
                                        lastRun.setByPump = previousResult;
                                    }
                                MainApp.bus().post(new EventLoopUpdateGui());
                                }
                            });
                        } else {
                            lastRun.setByPump = null;
                            lastRun.source = null;
                        }
                        /*
                        enactResult = pump.deliverTreatment(detailedBolusInfo);
                        if (enactResult.success) {
                            smbEnacted = true;
                            lastRun.lastEnact = new Date();
                            log.debug("SMB of "+smbFinalValue+" done!");
                        }*/
                    //}
                }
            

            } else if (constraintsInterface.isClosedModeEnabled()) {
                log.debug("No connection in last 15 min:"+noConnectionLast15Min+" change requested "+result.changeRequested);
                // Added by Rumen on 01.08.2017 to ensure change is done every 15 mins
                result.changeRequested = result.changeRequested || noConnectionLast15Min;
                log.debug("Change requested after merge:"+result.changeRequested);
                if (result.changeRequested && result.rate > -1d && result.duration > -1) {
                    log.debug("Entering closedLoop and rate is "+result.rate+" and duration is "+result.duration);      
                    log.debug("No connection in last 15 min:"+noConnectionLast15Min);
                    final PumpEnactResult waiting = new PumpEnactResult();
                    final PumpEnactResult previousResult = lastRun.setByPump;
                    waiting.queued = true;
                    lastRun.setByPump = waiting;
                    MainApp.bus().post(new EventLoopUpdateGui());
                    sHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            final PumpEnactResult applyResult = configBuilder.applyAPSRequest(resultAfterConstraints);
                            if (applyResult.enacted || applyResult.success) {
                                lastRun.setByPump = applyResult;
                                lastRun.lastEnact = lastRun.lastAPSRun;
                            } else {
                                lastRun.setByPump = previousResult;
                            }
                            MainApp.bus().post(new EventLoopUpdateGui());
                        }
                    });
                } else {
                    lastRun.setByPump = null;
                    lastRun.source = null;
                    lastRun.comment = "Change requested:"+result.changeRequested+" Rate should be:"+result.rate+" Duration should be: "+result.duration;
                }
            } else {
                if (result.changeRequested && allowNotification) {
                    NotificationCompat.Builder builder =
                            new NotificationCompat.Builder(MainApp.instance().getApplicationContext());
                    builder.setSmallIcon(R.drawable.notif_icon)
                            .setContentTitle(MainApp.sResources.getString(R.string.openloop_newsuggestion))
                            .setContentText(resultAfterConstraints.toString())
                            .setAutoCancel(true)
                            .setPriority(Notification.PRIORITY_HIGH)
                            .setCategory(Notification.CATEGORY_ALARM)
                            .setVisibility(Notification.VISIBILITY_PUBLIC);

                    // Creates an explicit intent for an Activity in your app
                    Intent resultIntent = new Intent(MainApp.instance().getApplicationContext(), MainActivity.class);

                    // The stack builder object will contain an artificial back stack for the
                    // started Activity.
                    // This ensures that navigating backward from the Activity leads out of
                    // your application to the Home screen.
                    TaskStackBuilder stackBuilder = TaskStackBuilder.create(MainApp.instance().getApplicationContext());
                    stackBuilder.addParentStack(MainActivity.class);
                    // Adds the Intent that starts the Activity to the top of the stack
                    stackBuilder.addNextIntent(resultIntent);
                    PendingIntent resultPendingIntent =
                            stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
                    builder.setContentIntent(resultPendingIntent);
                    builder.setVibrate(new long[]{1000, 1000, 1000, 1000, 1000});
                    NotificationManager mNotificationManager =
                            (NotificationManager) MainApp.instance().getSystemService(Context.NOTIFICATION_SERVICE);
                    // mId allows you to update the notification later on.
                    mNotificationManager.notify(Constants.notificationID, builder.build());
                    MainApp.bus().post(new EventNewOpenLoopNotification());
                }
            }

            MainApp.bus().post(new EventLoopUpdateGui());
            NSUpload.uploadDeviceStatus();
        } finally {
            if (Config.logFunctionCalls)
                log.debug("invoke end");
        }
    }

}
