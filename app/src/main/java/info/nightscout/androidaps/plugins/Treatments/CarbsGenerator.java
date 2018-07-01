package info.nightscout.androidaps.plugins.Treatments;

import android.content.Intent;
import android.support.annotation.Nullable;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Overview.Dialogs.ErrorHelperActivity;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.utils.T;

import static info.nightscout.utils.DateUtil.now;

public class CarbsGenerator {
    public static void generateCarbs(int amount, long startTime, int duration, @Nullable String notes) {
        long remainingCarbs = amount;
        int ticks = (duration * 4); //duration guaranteed to be integer greater or equal zero
        BgReading lastBg = DatabaseHelper.lastBg();
        if(lastBg != null){
            //align start time to 2.5 minute offset after loop-triggering BG time
            double modHelper = 0d;
            startTime += (modHelper = (-startTime + lastBg.date + T.secs(150).msecs())%T.mins(5).msecs()) >= 0 ? modHelper : modHelper+T.mins(5).msecs();
        }
        for (int i = 0; i < ticks; i++){
            long carbTime = startTime + i * 15 * 60 * 1000;
            int smallCarbAmount = (int) Math.round((1d * remainingCarbs) / (ticks-i));  //on last iteration (ticks-i) is 1 -> smallCarbAmount == remainingCarbs
            remainingCarbs -= smallCarbAmount;
            if (smallCarbAmount > 0)
                createCarb(smallCarbAmount, carbTime, CareportalEvent.MEALBOLUS, notes);
        }
    }

    public static void createCarb(int carbs, long time, String eventType, @Nullable String notes) {
        DetailedBolusInfo carbInfo = new DetailedBolusInfo();
        carbInfo.date = time;
        carbInfo.eventType = eventType;
        carbInfo.carbs = carbs;
        carbInfo.context = MainApp.instance();
        carbInfo.source = Source.USER;
        carbInfo.notes = notes;
        if (ConfigBuilderPlugin.getActivePump().getPumpDescription().storesCarbInfo && (Math.abs(carbInfo.date - now()) < T.mins(1).msecs())) {
            ConfigBuilderPlugin.getCommandQueue().bolus(carbInfo, new Callback() {
                @Override
                public void run() {
                    if (!result.success) {
                        Intent i = new Intent(MainApp.instance(), ErrorHelperActivity.class);
                        i.putExtra("soundid", R.raw.boluserror);
                        i.putExtra("status", result.comment);
                        i.putExtra("title", MainApp.gs(R.string.treatmentdeliveryerror));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        MainApp.instance().startActivity(i);
                    }
                }
            });
        } else {
            TreatmentsPlugin.getPlugin().addToHistoryTreatment(carbInfo, false);
        }
    }
}
