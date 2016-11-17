package info.nightscout.androidaps.plugins.Wear;

import android.content.Context;
import android.content.Intent;

import com.squareup.otto.Subscribe;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.events.EventNewBasalProfile;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventRefreshGui;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.events.EventTreatmentChange;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.Loop.events.EventNewOpenLoopNotification;
import info.nightscout.androidaps.plugins.Wear.wearintegration.WatchUpdaterService;

/**
 * Created by adrian on 17/11/16.
 */

public class WearPlugin implements PluginBase {

    static boolean fragmentEnabled = true;
    private final Context ctx;

    WearPlugin(Context ctx){
        this.ctx = ctx;
        MainApp.bus().register(this);
    }

    @Override
    public int getType() {
        return PluginBase.GENERAL;
    }

    @Override
    public String getFragmentClass() {
        return WearFragment.class.getName();
    }

    @Override
    public String getName() {
        return "WearPlugin";
    }

    @Override
    public boolean isEnabled(int type) {
        return fragmentEnabled;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return false;
    }

    @Override
    public boolean canBeHidden(int type) {
        return true;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        WearPlugin.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {

    }

    private void sendDataToWatch(){
        ctx.startService(new Intent(ctx, WatchUpdaterService.class));
    }


   /* @Subscribe
    public void onStatusEvent(final EventPreferenceChange ev) {
        sendDataToWatch();
    }

    @Subscribe
    public void onStatusEvent(final EventRefreshGui ev) {
        sendDataToWatch();
    }

    @Subscribe
    public void onStatusEvent(final EventTreatmentChange ev) {
        sendDataToWatch();
    }*/

    @Subscribe
    public void onStatusEvent(final EventTempBasalChange ev) {
        sendDataToWatch();
    }

    @Subscribe
    public void onStatusEvent(final EventNewBG ev) {
        sendDataToWatch();
    }

   /* @Subscribe
    public void onStatusEvent(final EventNewOpenLoopNotification ev) {
        sendDataToWatch();
    }*/

    @Subscribe
    public void onStatusEvent(final EventNewBasalProfile ev) { sendDataToWatch(); }





}
