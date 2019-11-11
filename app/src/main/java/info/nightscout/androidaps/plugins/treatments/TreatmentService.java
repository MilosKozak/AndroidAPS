package info.nightscout.androidaps.plugins.treatments;

import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.j256.ormlite.android.apptools.OrmLiteBaseService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import info.nightscout.androidaps.database.BlockingAppRepository;
import info.nightscout.androidaps.database.entities.Bolus;
import info.nightscout.androidaps.database.transactions.MergedBolus;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.db.ICallback;
import info.nightscout.androidaps.events.Event;
import info.nightscout.androidaps.events.EventReloadTreatmentData;
import info.nightscout.androidaps.events.EventTreatmentChange;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventNewHistoryData;
import info.nightscout.androidaps.utils.BolusCalculatorResultUtilKt;
import io.reactivex.disposables.CompositeDisposable;


/**
 * Created by mike on 24.09.2017.
 */

public class TreatmentService extends OrmLiteBaseService<DatabaseHelper> {
    private static Logger log = LoggerFactory.getLogger(L.DATATREATMENTS);
    private CompositeDisposable disposable = new CompositeDisposable();

    private static final ScheduledExecutorService treatmentEventWorker = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduledTreatmentEventPost = null;

    public TreatmentService() {
    }

    public void resetTreatments() {
    }


    /**
     * A place to centrally register events to be posted, if any data changed.
     * This should be implemented in an abstract service-class.
     * <p>
     * We do need to make sure, that ICallback is extended to be able to handle multiple
     * events, or handle a list of events.
     * <p>
     * on some methods the earliestDataChange event is handled separatly, in that it is checked if it is
     * set to null by another event already (eg. scheduleExtendedBolusChange).
     *
     * @param event
     * @param eventWorker
     * @param callback
     */
    private static void scheduleEvent(final Event event, ScheduledExecutorService eventWorker,
                                      final ICallback callback) {

        class PostRunnable implements Runnable {
            public void run() {
                if (L.isEnabled(L.DATATREATMENTS))
                    log.debug("Firing EventReloadTreatmentData");
                RxBus.INSTANCE.send(event);
                if (DatabaseHelper.earliestDataChange != null) {
                    if (L.isEnabled(L.DATATREATMENTS))
                        log.debug("Firing EventNewHistoryData");
                    RxBus.INSTANCE.send(new EventNewHistoryData(DatabaseHelper.earliestDataChange));
                }
                DatabaseHelper.earliestDataChange = null;
                callback.setPost(null);
            }
        }
        // prepare task for execution in 1 sec
        // cancel waiting task to prevent sending multiple posts
        ScheduledFuture<?> scheduledFuture = callback.getPost();
        if (scheduledFuture != null)
            scheduledFuture.cancel(false);
        Runnable task = new PostRunnable();
        final int sec = 1;
        callback.setPost(eventWorker.schedule(task, sec, TimeUnit.SECONDS));
    }

    /**
     * Schedule a foodChange Event.
     */
    public static void scheduleTreatmentChange(@Nullable final Treatment treatment) {
        scheduleEvent(new EventReloadTreatmentData(new EventTreatmentChange(treatment)), treatmentEventWorker, new ICallback() {
            @Override
            public void setPost(ScheduledFuture<?> post) {
                scheduledTreatmentEventPost = post;
            }

            @Override
            public ScheduledFuture<?> getPost() {
                return scheduledTreatmentEventPost;
            }
        });
    }

    public List<Treatment> getTreatmentDataFromTime(long mills, boolean ascending) {
        return getTreatmentDataFromTime(mills, Long.MAX_VALUE, ascending);
    }

    public List<Treatment> getTreatmentDataFromTime(long from, long to, boolean ascending) {
        List<Treatment> converted = new ArrayList<>();
        for (MergedBolus mergedBolus : BlockingAppRepository.INSTANCE.getMergedBolusData(from, to)) {
            Treatment treatment = new Treatment();
            treatment.backing = mergedBolus;
            if (mergedBolus.getCarbs() != null) {
                treatment.carbs = mergedBolus.getCarbs().getAmount();
                treatment.date = mergedBolus.getCarbs().getTimestamp();
            }
            if (mergedBolus.getBolus() != null) {
                treatment.insulin = mergedBolus.getBolus().getAmount();
                treatment.isValid = mergedBolus.getBolus().getType() != Bolus.Type.PRIMING;
                treatment.isSMB = mergedBolus.getBolus().getType() == Bolus.Type.SMB;
                treatment.date = mergedBolus.getBolus().getTimestamp();
            }
            if (mergedBolus.getBolusCalculatorResult() != null) {
                treatment.boluscalc = BolusCalculatorResultUtilKt.nsJSON(mergedBolus.getBolusCalculatorResult());
            }
            converted.add(treatment);
        }
        if (!ascending) Collections.reverse(converted);
        return converted;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public class UpdateReturn {
        public UpdateReturn(boolean success, boolean newRecord) {
            this.success = success;
            this.newRecord = newRecord;
        }

        boolean newRecord;
        boolean success;
    }

}
