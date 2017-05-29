package info.nightscout.androidaps.db;

import android.content.Context;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.Nullable;

import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.dao.CloseableIterator;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.Where;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.events.EventCareportalEventChange;
import info.nightscout.androidaps.events.EventExtendedBolusChange;
import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.events.EventReloadTempBasalData;
import info.nightscout.androidaps.events.EventReloadTreatmentData;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.events.EventTempTargetChange;
import info.nightscout.androidaps.events.EventTreatmentChange;
import info.nightscout.androidaps.plugins.IobCobCalculator.events.EventNewHistoryData;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSProfile;
import info.nightscout.androidaps.plugins.PumpDanaR.History.DanaRNSHistorySync;

public class DatabaseHelper extends OrmLiteSqliteOpenHelper {
    private static Logger log = LoggerFactory.getLogger(DatabaseHelper.class);

    public static final String DATABASE_NAME = "AndroidAPSDb";
    public static final String DATABASE_BGREADINGS = "BgReadings";
    public static final String DATABASE_TEMPORARYBASALS = "TemporaryBasals";
    public static final String DATABASE_EXTENDEDBOLUSES = "ExtendedBoluses";
    public static final String DATABASE_TEMPTARGETS = "TempTargets";
    public static final String DATABASE_TREATMENTS = "Treatments";
    public static final String DATABASE_DANARHISTORY = "DanaRHistory";
    public static final String DATABASE_DBREQUESTS = "DBRequests";
    public static final String DATABASE_CAREPORTALEVENTS = "CareportalEvents";

    private static final int DATABASE_VERSION = 7;

    private static Long latestTreatmentChange = null;

    private static final ScheduledExecutorService bgWorker = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduledBgPost = null;

    private static final ScheduledExecutorService treatmentsWorker = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduledTratmentPost = null;

    private static final ScheduledExecutorService tempBasalsWorker = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduledTemBasalsPost = null;

    private static final ScheduledExecutorService tempTargetWorker = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduledTemTargetPost = null;

    private static final ScheduledExecutorService extendedBolusWorker = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduledExtendedBolusPost = null;

    private static final ScheduledExecutorService careportalEventWorker = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduledCareportalEventPost = null;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        onCreate(getWritableDatabase(), getConnectionSource());
    }

    @Override
    public void onCreate(SQLiteDatabase database, ConnectionSource connectionSource) {
        try {
            log.info("onCreate");
            TableUtils.createTableIfNotExists(connectionSource, TempTarget.class);
            TableUtils.createTableIfNotExists(connectionSource, Treatment.class);
            TableUtils.createTableIfNotExists(connectionSource, BgReading.class);
            TableUtils.createTableIfNotExists(connectionSource, DanaRHistoryRecord.class);
            TableUtils.createTableIfNotExists(connectionSource, DbRequest.class);
            TableUtils.createTableIfNotExists(connectionSource, TemporaryBasal.class);
            TableUtils.createTableIfNotExists(connectionSource, ExtendedBolus.class);
            TableUtils.createTableIfNotExists(connectionSource, CareportalEvent.class);
        } catch (SQLException e) {
            log.error("Can't create database", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, ConnectionSource connectionSource, int oldVersion, int newVersion) {
        try {
            log.info(DatabaseHelper.class.getName(), "onUpgrade");
            TableUtils.dropTable(connectionSource, TempTarget.class, true);
            TableUtils.dropTable(connectionSource, Treatment.class, true);
            TableUtils.dropTable(connectionSource, BgReading.class, true);
            TableUtils.dropTable(connectionSource, DanaRHistoryRecord.class, true);
            TableUtils.dropTable(connectionSource, DbRequest.class, true);
            TableUtils.dropTable(connectionSource, TemporaryBasal.class, true);
            TableUtils.dropTable(connectionSource, ExtendedBolus.class, true);
            TableUtils.dropTable(connectionSource, CareportalEvent.class, true);
            onCreate(database, connectionSource);
        } catch (SQLException e) {
            log.error("Can't drop databases", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Close the database connections and clear any cached DAOs.
     */
    @Override
    public void close() {
        super.close();
    }

    public void cleanUpDatabases() {
        // TODO: call it somewhere
        log.debug("Before BgReadings size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_BGREADINGS));
        getWritableDatabase().delete(DATABASE_BGREADINGS, "date" + " < '" + (new Date().getTime() - Constants.hoursToKeepInDatabase * 60 * 60 * 1000L) + "'", null);
        log.debug("After BgReadings size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_BGREADINGS));

        log.debug("Before TempTargets size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_TEMPTARGETS));
        getWritableDatabase().delete(DATABASE_TEMPTARGETS, "date" + " < '" + (new Date().getTime() - Constants.hoursToKeepInDatabase * 60 * 60 * 1000L) + "'", null);
        log.debug("After TempTargets size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_TEMPTARGETS));

        log.debug("Before Treatments size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_TREATMENTS));
        getWritableDatabase().delete(DATABASE_TREATMENTS, "date" + " < '" + (new Date().getTime() - Constants.hoursToKeepInDatabase * 60 * 60 * 1000L) + "'", null);
        log.debug("After Treatments size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_TREATMENTS));

        log.debug("Before History size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_DANARHISTORY));
        getWritableDatabase().delete(DATABASE_DANARHISTORY, "recordDate" + " < '" + (new Date().getTime() - Constants.daysToKeepHistoryInDatabase * 24 * 60 * 60 * 1000L) + "'", null);
        log.debug("After History size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_DANARHISTORY));

        log.debug("Before TemporaryBasals size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_TEMPORARYBASALS));
        getWritableDatabase().delete(DATABASE_TEMPORARYBASALS, "recordDate" + " < '" + (new Date().getTime() - Constants.daysToKeepHistoryInDatabase * 24 * 60 * 60 * 1000L) + "'", null);
        log.debug("After TemporaryBasals size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_TEMPORARYBASALS));

        log.debug("Before ExtendedBoluses size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_EXTENDEDBOLUSES));
        getWritableDatabase().delete(DATABASE_EXTENDEDBOLUSES, "recordDate" + " < '" + (new Date().getTime() - Constants.daysToKeepHistoryInDatabase * 24 * 60 * 60 * 1000L) + "'", null);
        log.debug("After ExtendedBoluses size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_EXTENDEDBOLUSES));

        log.debug("Before CareportalEvent size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_CAREPORTALEVENTS));
        getWritableDatabase().delete(DATABASE_CAREPORTALEVENTS, "recordDate" + " < '" + (new Date().getTime() - Constants.daysToKeepHistoryInDatabase * 24 * 60 * 60 * 1000L) + "'", null);
        log.debug("After CareportalEvent size: " + DatabaseUtils.queryNumEntries(getReadableDatabase(), DATABASE_CAREPORTALEVENTS));
    }

    public long size(String database) {
        return DatabaseUtils.queryNumEntries(getReadableDatabase(), database);
    }

    // --------------------- DB resets ---------------------

    public void resetDatabases() {
        try {
            TableUtils.dropTable(connectionSource, TempTarget.class, true);
            TableUtils.dropTable(connectionSource, Treatment.class, true);
            TableUtils.dropTable(connectionSource, BgReading.class, true);
            TableUtils.dropTable(connectionSource, DanaRHistoryRecord.class, true);
            TableUtils.dropTable(connectionSource, DbRequest.class, true);
            TableUtils.dropTable(connectionSource, TemporaryBasal.class, true);
            TableUtils.dropTable(connectionSource, ExtendedBolus.class, true);
            TableUtils.dropTable(connectionSource, CareportalEvent.class, true);
            TableUtils.createTableIfNotExists(connectionSource, TempTarget.class);
            TableUtils.createTableIfNotExists(connectionSource, Treatment.class);
            TableUtils.createTableIfNotExists(connectionSource, BgReading.class);
            TableUtils.createTableIfNotExists(connectionSource, DanaRHistoryRecord.class);
            TableUtils.createTableIfNotExists(connectionSource, DbRequest.class);
            TableUtils.createTableIfNotExists(connectionSource, TemporaryBasal.class);
            TableUtils.createTableIfNotExists(connectionSource, ExtendedBolus.class);
            TableUtils.createTableIfNotExists(connectionSource, CareportalEvent.class);
            updateLatestTreatmentChange(0);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        scheduleBgChange(); // trigger refresh
        scheduleTemporaryBasalChange();
        scheduleTreatmentChange();
        scheduleExtendedBolusChange();
        scheduleTemporaryTargetChange();
        scheduleCareportalEventChange();
    }

    public void resetTreatments() {
        try {
            TableUtils.dropTable(connectionSource, Treatment.class, true);
            TableUtils.createTableIfNotExists(connectionSource, Treatment.class);
            updateLatestTreatmentChange(0);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        scheduleTreatmentChange();
    }

    public void resetTempTargets() {
        try {
            TableUtils.dropTable(connectionSource, TempTarget.class, true);
            TableUtils.createTableIfNotExists(connectionSource, TempTarget.class);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        scheduleTemporaryTargetChange();
    }

    public void resetTemporaryBasals() {
        try {
            TableUtils.dropTable(connectionSource, TemporaryBasal.class, true);
            TableUtils.createTableIfNotExists(connectionSource, TemporaryBasal.class);
            updateLatestTreatmentChange(0);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        scheduleTemporaryBasalChange();
    }

    public void resetExtededBoluses() {
        try {
            TableUtils.dropTable(connectionSource, ExtendedBolus.class, true);
            TableUtils.createTableIfNotExists(connectionSource, ExtendedBolus.class);
            updateLatestTreatmentChange(0);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        scheduleExtendedBolusChange();
    }

    public void resetCareportalEvents() {
        try {
            TableUtils.dropTable(connectionSource, CareportalEvent.class, true);
            TableUtils.createTableIfNotExists(connectionSource, CareportalEvent.class);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        scheduleCareportalEventChange();
    }

    // ------------------ getDao -------------------------------------------

    private Dao<TempTarget, Long> getDaoTempTargets() throws SQLException {
        return getDao(TempTarget.class);
    }

    private Dao<Treatment, Long> getDaoTreatments() throws SQLException {
        return getDao(Treatment.class);
    }

    private Dao<BgReading, Long> getDaoBgReadings() throws SQLException {
        return getDao(BgReading.class);
    }

    private Dao<DanaRHistoryRecord, String> getDaoDanaRHistory() throws SQLException {
        return getDao(DanaRHistoryRecord.class);
    }

    private Dao<DbRequest, String> getDaoDbRequest() throws SQLException {
        return getDao(DbRequest.class);
    }

    private Dao<TemporaryBasal, Long> getDaoTemporaryBasal() throws SQLException {
        return getDao(TemporaryBasal.class);
    }

    private Dao<ExtendedBolus, Long> getDaoExtendedBolus() throws SQLException {
        return getDao(ExtendedBolus.class);
    }

    private Dao<CareportalEvent, Long> getDaoCareportalEvents() throws SQLException {
        return getDao(CareportalEvent.class);
    }

    // -------------------  BgReading handling -----------------------

    public void createIfNotExists(BgReading bgReading) {
        bgReading.date = bgReading.date - bgReading.date % 1000;
        try {
            getDaoBgReadings().createIfNotExists(bgReading);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        scheduleBgChange();
    }

    static public void scheduleBgChange() {
        class PostRunnable implements Runnable {
            public void run() {
                log.debug("Firing EventNewBg");
                MainApp.bus().post(new EventNewBG());
                scheduledBgPost = null;
            }
        }
        // prepare task for execution in 1 sec
        // cancel waiting task to prevent sending multiple posts
        if (scheduledBgPost != null)
            scheduledBgPost.cancel(false);
        Runnable task = new PostRunnable();
        final int sec = 1;
        scheduledBgPost = bgWorker.schedule(task, sec, TimeUnit.SECONDS);

    }

    /*
         * Return last BgReading from database or null if db is empty
         */
    @Nullable
    public static BgReading lastBg() {
        List<BgReading> bgList = null;

        try {
            Dao<BgReading, Long> daoBgReadings = MainApp.getDbHelper().getDaoBgReadings();
            QueryBuilder<BgReading, Long> queryBuilder = daoBgReadings.queryBuilder();
            queryBuilder.orderBy("date", false);
            queryBuilder.limit(1L);
            queryBuilder.where().gt("value", 38);
            PreparedQuery<BgReading> preparedQuery = queryBuilder.prepare();
            bgList = daoBgReadings.query(preparedQuery);

        } catch (SQLException e) {
            log.debug(e.getMessage(), e);
        }
        if (bgList != null && bgList.size() > 0)
            return bgList.get(0);
        else
            return null;
    }

    /*
         * Return bg reading if not old ( <9 min )
         * or null if older
         */
    @Nullable
    public static BgReading actualBg() {
        BgReading lastBg = lastBg();

        if (lastBg == null)
            return null;

        if (lastBg.date > new Date().getTime() - 9 * 60 * 1000)
            return lastBg;

        return null;
    }


    public List<BgReading> getBgreadingsDataFromTime(long mills, boolean ascending) {
        try {
            Dao<BgReading, Long> daoBgreadings = getDaoBgReadings();
            List<BgReading> bgReadings;
            QueryBuilder<BgReading, Long> queryBuilder = daoBgreadings.queryBuilder();
            queryBuilder.orderBy("date", ascending);
            Where where = queryBuilder.where();
            where.ge("date", mills).and().gt("value", 38);
            PreparedQuery<BgReading> preparedQuery = queryBuilder.prepare();
            bgReadings = daoBgreadings.query(preparedQuery);
            return bgReadings;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new ArrayList<BgReading>();
    }

    // ------------- DbRequests handling -------------------

    public void create(DbRequest dbr) {
        try {
            getDaoDbRequest().create(dbr);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public int delete(DbRequest dbr) {
        try {
            return getDaoDbRequest().delete(dbr);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int deleteDbRequest(String nsClientId) {
        try {
            return getDaoDbRequest().deleteById(nsClientId);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int deleteDbRequestbyMongoId(String action, String id) {
        try {
            QueryBuilder<DbRequest, String> queryBuilder = getDaoDbRequest().queryBuilder();
            Where where = queryBuilder.where();
            where.eq("_id", id).and().eq("action", action);
            queryBuilder.limit(10L);
            PreparedQuery<DbRequest> preparedQuery = queryBuilder.prepare();
            List<DbRequest> dbList = getDaoDbRequest().query(preparedQuery);
            if (dbList.size() != 1) {
                log.error("deleteDbRequestbyMongoId query size: " + dbList.size());
            } else {
                //log.debug("Treatment findTreatmentById found: " + trList.get(0).log());
                return delete(dbList.get(0));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public void deleteAllDbRequests() {
        try {
            TableUtils.clearTable(connectionSource, DbRequest.class);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public CloseableIterator getDbRequestInterator() {
        try {
            return getDaoDbRequest().closeableIterator();
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    //  -------------------- TREATMENT HANDLING -------------------

    public boolean changeAffectingIobCob(Treatment t) {
        Treatment existing = findTreatmentByTimeIndex(t.date);
        if (existing == null)
            return true;
        if (existing.insulin == t.insulin && existing.carbs == t.carbs)
            return false;
        return true;
    }

    public Dao.CreateOrUpdateStatus createOrUpdate(Treatment treatment) {
        treatment.date = treatment.date - treatment.date % 1000;
        Dao.CreateOrUpdateStatus status = null;
        try {
            boolean historyChange = changeAffectingIobCob(treatment);
            status = getDaoTreatments().createOrUpdate(treatment);
            if (historyChange)
                updateLatestTreatmentChange(treatment.date);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        scheduleTreatmentChange();
        return status;
    }

    public void delete(Treatment treatment) {
        try {
            getDaoTreatments().delete(treatment);
            updateLatestTreatmentChange(treatment.date);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        scheduleTreatmentChange();
    }

    public void deleteTreatmentById(String _id) {
        Treatment stored = findTreatmentById(_id);
        if (stored != null) {
            log.debug("Removing TempTarget record from database: " + stored.log());
            delete(stored);
        } else {
            log.debug("Treatment not found database: " + _id);
        }
    }

    @Nullable
    public Treatment findTreatmentById(String _id) {
        try {
            Dao<Treatment, Long> daoTreatments = getDaoTreatments();
            QueryBuilder<Treatment, Long> queryBuilder = daoTreatments.queryBuilder();
            Where where = queryBuilder.where();
            where.eq("_id", _id);
            queryBuilder.limit(10L);
            PreparedQuery<Treatment> preparedQuery = queryBuilder.prepare();
            List<Treatment> trList = daoTreatments.query(preparedQuery);
            if (trList.size() != 1) {
                //log.debug("Treatment findTreatmentById query size: " + trList.size());
                return null;
            } else {
                //log.debug("Treatment findTreatmentById found: " + trList.get(0).log());
                return trList.get(0);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Nullable
    public Treatment findTreatmentByTimeIndex(Long timeIndex) {
        try {
            QueryBuilder<Treatment, String> qb = null;
            Dao<Treatment, Long> daoTreatments = getDaoTreatments();
            QueryBuilder<Treatment, Long> queryBuilder = daoTreatments.queryBuilder();
            Where where = queryBuilder.where();
            where.eq("date", timeIndex);
            queryBuilder.limit(10L);
            PreparedQuery<Treatment> preparedQuery = queryBuilder.prepare();
            List<Treatment> trList = daoTreatments.query(preparedQuery);
            if (trList.size() != 1) {
                log.debug("Treatment findTreatmentByTimeIndex query size: " + trList.size());
                return null;
            } else {
                log.debug("Treatment findTreatmentByTimeIndex found: " + trList.get(0).log());
                return trList.get(0);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    void updateLatestTreatmentChange(long newDate) {
        if (latestTreatmentChange == null) {
            latestTreatmentChange = newDate;
            return;
        }
        if (newDate < latestTreatmentChange) {
            latestTreatmentChange = newDate;
        }
    }

    static public void scheduleTreatmentChange() {
        class PostRunnable implements Runnable {
            public void run() {
                log.debug("Firing EventTreatmentChange");
                MainApp.bus().post(new EventReloadTreatmentData());
                MainApp.bus().post(new EventTreatmentChange());
                if (latestTreatmentChange != null)
                    MainApp.bus().post(new EventNewHistoryData(latestTreatmentChange));
                latestTreatmentChange = null;
                scheduledTratmentPost = null;
            }
        }
        // prepare task for execution in 1 sec
        // cancel waiting task to prevent sending multiple posts
        if (scheduledTratmentPost != null)
            scheduledTratmentPost.cancel(false);
        Runnable task = new PostRunnable();
        final int sec = 1;
        scheduledTratmentPost = treatmentsWorker.schedule(task, sec, TimeUnit.SECONDS);

    }

    public List<Treatment> getTreatmentDataFromTime(long mills, boolean ascending) {
        try {
            Dao<Treatment, Long> daoTreatments = getDaoTreatments();
            List<Treatment> treatments;
            QueryBuilder<Treatment, Long> queryBuilder = daoTreatments.queryBuilder();
            queryBuilder.orderBy("date", ascending);
            Where where = queryBuilder.where();
            where.ge("date", mills);
            PreparedQuery<Treatment> preparedQuery = queryBuilder.prepare();
            treatments = daoTreatments.query(preparedQuery);
            return treatments;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new ArrayList<Treatment>();
    }

    public void createTreatmentFromJsonIfNotExists(JSONObject trJson) {
        try {
            QueryBuilder<Treatment, Long> queryBuilder = null;
            queryBuilder = getDaoTreatments().queryBuilder();
            Where where = queryBuilder.where();
            where.eq("_id", trJson.getString("_id")).or().eq("date", trJson.getLong("mills"));
            PreparedQuery<Treatment> preparedQuery = queryBuilder.prepare();
            List<Treatment> list = getDaoTreatments().query(preparedQuery);
            Treatment treatment;
            if (list.size() == 0) {
                treatment = new Treatment();
                treatment.source = Source.NIGHTSCOUT;
                if (Config.logIncommingData)
                    log.debug("Adding Treatment record to database: " + trJson.toString());
                // Record does not exists. add
            } else if (list.size() == 1) {
                treatment = list.get(0);
                if (Config.logIncommingData)
                    log.debug("Updating Treatment record in database: " + trJson.toString());
            } else {
                log.error("Something went wrong");
                return;
            }
            treatment.date = trJson.getLong("mills");
            treatment.carbs = trJson.has("carbs") ? trJson.getDouble("carbs") : 0;
            treatment.insulin = trJson.has("insulin") ? trJson.getDouble("insulin") : 0d;
            treatment._id = trJson.getString("_id");
            if (trJson.has("eventType")) {
                treatment.mealBolus = true;
                if (trJson.get("eventType").equals("Correction Bolus"))
                    treatment.mealBolus = false;
                double carbs = treatment.carbs;
                if (trJson.has("boluscalc")) {
                    JSONObject boluscalc = trJson.getJSONObject("boluscalc");
                    if (boluscalc.has("carbs")) {
                        carbs = Math.max(boluscalc.getDouble("carbs"), carbs);
                    }
                }
                if (carbs <= 0)
                    treatment.mealBolus = false;
            }
            createOrUpdate(treatment);
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // ---------------- TempTargets handling ---------------

    public List<TempTarget> getTemptargetsDataFromTime(long mills, boolean ascending) {
        try {
            Dao<TempTarget, Long> daoTempTargets = getDaoTempTargets();
            List<TempTarget> tempTargets;
            QueryBuilder<TempTarget, Long> queryBuilder = daoTempTargets.queryBuilder();
            queryBuilder.orderBy("date", ascending);
            Where where = queryBuilder.where();
            where.ge("date", mills);
            PreparedQuery<TempTarget> preparedQuery = queryBuilder.prepare();
            tempTargets = daoTempTargets.query(preparedQuery);
            return tempTargets;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new ArrayList<TempTarget>();
    }

    public void createOrUpdate(TempTarget tempTarget) {
        tempTarget.date = tempTarget.date - tempTarget.date % 1000;
        try {
            getDaoTempTargets().createOrUpdate(tempTarget);
            scheduleTemporaryTargetChange();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void delete(TempTarget tempTarget) {
        try {
            getDaoTempTargets().delete(tempTarget);
            scheduleTemporaryTargetChange();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    static public void scheduleTemporaryTargetChange() {
        class PostRunnable implements Runnable {
            public void run() {
                log.debug("Firing EventTempTargetChange");
                MainApp.bus().post(new EventTempTargetChange());
                scheduledTemTargetPost = null;
            }
        }
        // prepare task for execution in 1 sec
        // cancel waiting task to prevent sending multiple posts
        if (scheduledTemTargetPost != null)
            scheduledTemTargetPost.cancel(false);
        Runnable task = new PostRunnable();
        final int sec = 1;
        scheduledTemTargetPost = tempTargetWorker.schedule(task, sec, TimeUnit.SECONDS);

    }

 /*
 {
    "_id": "58795998aa86647ba4d68ce7",
    "enteredBy": "",
    "eventType": "Temporary Target",
    "reason": "Eating Soon",
    "targetTop": 80,
    "targetBottom": 80,
    "duration": 120,
    "created_at": "2017-01-13T22:50:00.782Z",
    "carbs": null,
    "insulin": null
}
  */

    public void createTemptargetFromJsonIfNotExists(JSONObject trJson) {
        try {
            QueryBuilder<TempTarget, Long> queryBuilder = null;
            queryBuilder = getDaoTempTargets().queryBuilder();
            Where where = queryBuilder.where();
            where.eq("_id", trJson.getString("_id")).or().eq("date", trJson.getLong("mills"));
            PreparedQuery<TempTarget> preparedQuery = queryBuilder.prepare();
            List<TempTarget> list = getDaoTempTargets().query(preparedQuery);
            NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
            if (profile == null) return; // no profile data, better ignore than do something wrong
            String units = profile.getUnits();
            TempTarget tempTarget;
            if (list.size() == 0) {
                tempTarget = new TempTarget();
                if (Config.logIncommingData)
                    log.debug("Adding TempTarget record to database: " + trJson.toString());
                // Record does not exists. add
            } else if (list.size() == 1) {
                tempTarget = list.get(0);
                if (Config.logIncommingData)
                    log.debug("Updating TempTarget record in database: " + trJson.toString());
            } else {
                log.error("Something went wrong");
                return;
            }
            tempTarget.date = trJson.getLong("mills");
            tempTarget.durationInMinutes = trJson.getInt("duration");
            tempTarget.low = NSProfile.toMgdl(trJson.getDouble("targetBottom"), units);
            tempTarget.high = NSProfile.toMgdl(trJson.getDouble("targetTop"), units);
            tempTarget.reason = trJson.getString("reason");
            tempTarget._id = trJson.getString("_id");
            createOrUpdate(tempTarget);
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void deleteTempTargetById(String _id) {
        try {
            QueryBuilder<TempTarget, Long> queryBuilder = getDaoTempTargets().queryBuilder();
            Where where = queryBuilder.where();
            where.eq("_id", _id);
            PreparedQuery<TempTarget> preparedQuery = queryBuilder.prepare();
            List<TempTarget> list = getDaoTempTargets().query(preparedQuery);

            if (list.size() == 1) {
                TempTarget record = list.get(0);
                if (Config.logIncommingData)
                    log.debug("Removing TempTarget record from database: " + record.log());
                delete(record);
            } else {
                if (Config.logIncommingData)
                    log.debug("TempTarget not found database: " + _id);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ----------------- DanaRHistory handling --------------------

    public void createOrUpdate(DanaRHistoryRecord record) {
        try {
            getDaoDanaRHistory().createOrUpdate(record);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<DanaRHistoryRecord> getDanaRHistoryRecordsByType(byte type) {
        List<DanaRHistoryRecord> historyList;
        try {
            QueryBuilder<DanaRHistoryRecord, String> queryBuilder = getDaoDanaRHistory().queryBuilder();
            queryBuilder.orderBy("recordDate", false);
            Where where = queryBuilder.where();
            where.eq("recordCode", type);
            queryBuilder.limit(200L);
            PreparedQuery<DanaRHistoryRecord> preparedQuery = queryBuilder.prepare();
            historyList = getDaoDanaRHistory().query(preparedQuery);
        } catch (SQLException e) {
            e.printStackTrace();
            historyList = new ArrayList<>();
        }
        return historyList;
    }

    public void updateDanaRHistoryRecordId(JSONObject trJson) {
        try {
            QueryBuilder<DanaRHistoryRecord, String> queryBuilder = getDaoDanaRHistory().queryBuilder();
            Where where = queryBuilder.where();
            where.ge("bytes", trJson.get(DanaRNSHistorySync.DANARSIGNATURE));
            PreparedQuery<DanaRHistoryRecord> preparedQuery = queryBuilder.prepare();
            List<DanaRHistoryRecord> list = getDaoDanaRHistory().query(preparedQuery);
            if (list.size() == 0) {
                // Record does not exists. Ignore
            } else if (list.size() == 1) {
                DanaRHistoryRecord record = list.get(0);
                if (record._id == null || !record._id.equals(trJson.getString("_id"))) {
                    if (Config.logIncommingData)
                        log.debug("Updating _id in DanaR history database: " + trJson.getString("_id"));
                    record._id = trJson.getString("_id");
                    getDaoDanaRHistory().update(record);
                } else {
                    // already set
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // ------------ TemporaryBasal handling ---------------

    public void createOrUpdate(TemporaryBasal tempBasal) {
        tempBasal.date = tempBasal.date - tempBasal.date % 1000;
        try {
            getDaoTemporaryBasal().createOrUpdate(tempBasal);
            updateLatestTreatmentChange(tempBasal.date);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        scheduleTemporaryBasalChange();
    }

    public void delete(TemporaryBasal tempBasal) {
        try {
            getDaoTemporaryBasal().delete(tempBasal);
            updateLatestTreatmentChange(tempBasal.date);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        scheduleTemporaryBasalChange();
    }

    public List<TemporaryBasal> getTemporaryBasalsDataFromTime(long mills, boolean ascending) {
        try {
            List<TemporaryBasal> tempbasals;
            QueryBuilder<TemporaryBasal, Long> queryBuilder = getDaoTemporaryBasal().queryBuilder();
            queryBuilder.orderBy("date", ascending);
            Where where = queryBuilder.where();
            where.ge("date", mills);
            PreparedQuery<TemporaryBasal> preparedQuery = queryBuilder.prepare();
            tempbasals = getDaoTemporaryBasal().query(preparedQuery);
            return tempbasals;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new ArrayList<TemporaryBasal>();
    }

    static public void scheduleTemporaryBasalChange() {
        class PostRunnable implements Runnable {
            public void run() {
                log.debug("Firing EventTempBasalChange");
                MainApp.bus().post(new EventReloadTempBasalData());
                MainApp.bus().post(new EventTempBasalChange());
                scheduledTemBasalsPost = null;
            }
        }
        // prepare task for execution in 1 sec
        // cancel waiting task to prevent sending multiple posts
        if (scheduledTemBasalsPost != null)
            scheduledTemBasalsPost.cancel(false);
        Runnable task = new PostRunnable();
        final int sec = 1;
        scheduledTemBasalsPost = tempBasalsWorker.schedule(task, sec, TimeUnit.SECONDS);

    }

    /*
    {
        "_id": "59232e1ddd032d04218dab00",
        "eventType": "Temp Basal",
        "duration": 60,
        "percent": -50,
        "created_at": "2017-05-22T18:29:57Z",
        "enteredBy": "AndroidAPS",
        "notes": "Basal Temp Start 50% 60.0 min",
        "NSCLIENT_ID": 1495477797863,
        "mills": 1495477797000,
        "mgdl": 194.5,
        "endmills": 1495481397000
    }
    */

    public void createTempBasalFromJsonIfNotExists(JSONObject trJson) {
        try {
            if (trJson.has("originalExtendedAmount")) { // extended bolus uploaded as temp basal
                QueryBuilder<ExtendedBolus, Long> queryBuilder = null;
                queryBuilder = getDaoExtendedBolus().queryBuilder();
                Where where = queryBuilder.where();
                where.eq("_id", trJson.getString("_id")).or().eq("date", trJson.getLong("mills"));
                PreparedQuery<ExtendedBolus> preparedQuery = queryBuilder.prepare();
                List<ExtendedBolus> list = getDaoExtendedBolus().query(preparedQuery);
                ExtendedBolus extendedBolus;
                if (list.size() == 0) {
                    extendedBolus = new ExtendedBolus();
                    extendedBolus.source = Source.NIGHTSCOUT;
                    if (Config.logIncommingData)
                        log.debug("Adding ExtendedBolus record to database: " + trJson.toString());
                    // Record does not exists. add
                } else if (list.size() == 1) {
                    extendedBolus = list.get(0);
                    if (Config.logIncommingData)
                        log.debug("Updating ExtendedBolus record in database: " + trJson.toString());
                } else {
                    log.error("Something went wrong");
                    return;
                }
                extendedBolus.date = trJson.getLong("mills");
                extendedBolus.durationInMinutes = trJson.getInt("duration");
                extendedBolus.insulin = trJson.getDouble("originalExtendedAmount");
                extendedBolus._id = trJson.getString("_id");
                createOrUpdate(extendedBolus);
            } else if (trJson.has("isFakedTempBasal")) { // extended bolus end uploaded as temp basal end
                QueryBuilder<ExtendedBolus, Long> queryBuilder = null;
                queryBuilder = getDaoExtendedBolus().queryBuilder();
                Where where = queryBuilder.where();
                where.eq("_id", trJson.getString("_id")).or().eq("date", trJson.getLong("mills"));
                PreparedQuery<ExtendedBolus> preparedQuery = queryBuilder.prepare();
                List<ExtendedBolus> list = getDaoExtendedBolus().query(preparedQuery);
                ExtendedBolus extendedBolus;
                if (list.size() == 0) {
                    extendedBolus = new ExtendedBolus();
                    extendedBolus.source = Source.NIGHTSCOUT;
                    if (Config.logIncommingData)
                        log.debug("Adding ExtendedBolus record to database: " + trJson.toString());
                    // Record does not exists. add
                } else if (list.size() == 1) {
                    extendedBolus = list.get(0);
                    if (Config.logIncommingData)
                        log.debug("Updating ExtendedBolus record in database: " + trJson.toString());
                } else {
                    log.error("Something went wrong");
                    return;
                }
                extendedBolus.date = trJson.getLong("mills");
                extendedBolus.durationInMinutes = 0;
                extendedBolus.insulin = 0;
                extendedBolus._id = trJson.getString("_id");
                createOrUpdate(extendedBolus);
            } else {
                QueryBuilder<TemporaryBasal, Long> queryBuilder = null;
                queryBuilder = getDaoTemporaryBasal().queryBuilder();
                Where where = queryBuilder.where();
                where.eq("_id", trJson.getString("_id")).or().eq("date", trJson.getLong("mills"));
                PreparedQuery<TemporaryBasal> preparedQuery = queryBuilder.prepare();
                List<TemporaryBasal> list = getDaoTemporaryBasal().query(preparedQuery);
                TemporaryBasal tempBasal;
                if (list.size() == 0) {
                    tempBasal = new TemporaryBasal();
                    tempBasal.source = Source.NIGHTSCOUT;
                    if (Config.logIncommingData)
                        log.debug("Adding TemporaryBasal record to database: " + trJson.toString());
                    // Record does not exists. add
                } else if (list.size() == 1) {
                    tempBasal = list.get(0);
                    if (Config.logIncommingData)
                        log.debug("Updating TemporaryBasal record in database: " + trJson.toString());
                } else {
                    log.error("Something went wrong");
                    return;
                }
                tempBasal.date = trJson.getLong("mills");
                if (trJson.has("duration")) {
                    tempBasal.durationInMinutes = trJson.getInt("duration");
                }
                if (trJson.has("percent")) {
                    tempBasal.percentRate = trJson.getInt("percent") + 100;
                    tempBasal.isAbsolute = false;
                }
                if (trJson.has("absolute")) {
                    tempBasal.absoluteRate = trJson.getDouble("absolute");
                    tempBasal.isAbsolute = true;
                }
                tempBasal._id = trJson.getString("_id");
                createOrUpdate(tempBasal);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void deleteTempBasalById(String _id) {
        try {
            QueryBuilder<TemporaryBasal, Long> queryBuilder = null;
            queryBuilder = getDaoTemporaryBasal().queryBuilder();
            Where where = queryBuilder.where();
            where.eq("_id", _id);
            PreparedQuery<TemporaryBasal> preparedQuery = queryBuilder.prepare();
            List<TemporaryBasal> list = getDaoTemporaryBasal().query(preparedQuery);

            if (list.size() == 1) {
                TemporaryBasal record = list.get(0);
                if (Config.logIncommingData)
                    log.debug("Removing TempBasal record from database: " + record.log());
                delete(record);
            } else {
                if (Config.logIncommingData)
                    log.debug("TempBasal not found database: " + _id);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ------------ ExtendedBolus handling ---------------

    public void createOrUpdate(ExtendedBolus extendedBolus) {
        extendedBolus.date = extendedBolus.date - extendedBolus.date % 1000;
        try {
            getDaoExtendedBolus().createOrUpdate(extendedBolus);
            updateLatestTreatmentChange(extendedBolus.date);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        scheduleExtendedBolusChange();
    }

    public void delete(ExtendedBolus extendedBolus) {
        try {
            getDaoExtendedBolus().delete(extendedBolus);
            updateLatestTreatmentChange(extendedBolus.date);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        scheduleExtendedBolusChange();
    }

    public List<ExtendedBolus> getExtendedBolusDataFromTime(long mills, boolean ascending) {
        try {
            List<ExtendedBolus> extendedBoluses;
            QueryBuilder<ExtendedBolus, Long> queryBuilder = getDaoExtendedBolus().queryBuilder();
            queryBuilder.orderBy("date", ascending);
            Where where = queryBuilder.where();
            where.ge("date", mills);
            PreparedQuery<ExtendedBolus> preparedQuery = queryBuilder.prepare();
            extendedBoluses = getDaoExtendedBolus().query(preparedQuery);
            return extendedBoluses;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new ArrayList<ExtendedBolus>();
    }

    public void deleteExtendedBolusById(String _id) {
        try {
            QueryBuilder<ExtendedBolus, Long> queryBuilder = null;
            queryBuilder = getDaoExtendedBolus().queryBuilder();
            Where where = queryBuilder.where();
            where.eq("_id", _id);
            PreparedQuery<ExtendedBolus> preparedQuery = queryBuilder.prepare();
            List<ExtendedBolus> list = getDaoExtendedBolus().query(preparedQuery);

            if (list.size() == 1) {
                ExtendedBolus record = list.get(0);
                if (Config.logIncommingData)
                    log.debug("Removing ExtendedBolus record from database: " + record.log());
                delete(record);
            } else {
                if (Config.logIncommingData)
                    log.debug("ExtendedBolus not found database: " + _id);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /*
{
    "_id": "5924898d577eb0880e355337",
    "eventType": "Combo Bolus",
    "duration": 120,
    "splitNow": 0,
    "splitExt": 100,
    "enteredinsulin": 1,
    "relative": 1,
    "created_at": "2017-05-23T19:12:14Z",
    "enteredBy": "AndroidAPS",
    "NSCLIENT_ID": 1495566734628,
    "mills": 1495566734000,
    "mgdl": 106
}
     */

    public void createExtendedBolusFromJsonIfNotExists(JSONObject trJson) {
        try {
            QueryBuilder<ExtendedBolus, Long> queryBuilder = null;
            queryBuilder = getDaoExtendedBolus().queryBuilder();
            Where where = queryBuilder.where();
            where.eq("_id", trJson.getString("_id")).or().eq("date", trJson.getLong("mills"));
            PreparedQuery<ExtendedBolus> preparedQuery = queryBuilder.prepare();
            List<ExtendedBolus> list = getDaoExtendedBolus().query(preparedQuery);
            ExtendedBolus extendedBolus;
            if (list.size() == 0) {
                extendedBolus = new ExtendedBolus();
                extendedBolus.source = Source.NIGHTSCOUT;
                if (Config.logIncommingData)
                    log.debug("Adding ExtendedBolus record to database: " + trJson.toString());
                // Record does not exists. add
            } else if (list.size() == 1) {
                extendedBolus = list.get(0);
                if (Config.logIncommingData)
                    log.debug("Updating ExtendedBolus record in database: " + trJson.toString());
            } else {
                log.error("Something went wrong");
                return;
            }
            extendedBolus.date = trJson.getLong("mills");
            extendedBolus.durationInMinutes = trJson.getInt("duration");
            extendedBolus.insulin = trJson.getDouble("relative");
            extendedBolus._id = trJson.getString("_id");
            createOrUpdate(extendedBolus);
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    static public void scheduleExtendedBolusChange() {
        class PostRunnable implements Runnable {
            public void run() {
                log.debug("Firing EventExtendedBolusChange");
                MainApp.bus().post(new EventReloadTreatmentData());
                MainApp.bus().post(new EventExtendedBolusChange());
                scheduledExtendedBolusPost = null;
            }
        }
        // prepare task for execution in 1 sec
        // cancel waiting task to prevent sending multiple posts
        if (scheduledExtendedBolusPost != null)
            scheduledExtendedBolusPost.cancel(false);
        Runnable task = new PostRunnable();
        final int sec = 1;
        scheduledExtendedBolusPost = extendedBolusWorker.schedule(task, sec, TimeUnit.SECONDS);

    }


    // ------------ CareportalEvent handling ---------------

    public void createOrUpdate(CareportalEvent careportalEvent) {
        careportalEvent.date = careportalEvent.date - careportalEvent.date % 1000;
        try {
            getDaoCareportalEvents().createOrUpdate(careportalEvent);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        scheduleCareportalEventChange();
    }

    public void delete(CareportalEvent careportalEvent) {
        try {
            getDaoCareportalEvents().delete(careportalEvent);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        scheduleCareportalEventChange();
    }

    @Nullable
    public CareportalEvent getLastCareportalEvent(String event) {
        try {
            List<CareportalEvent> careportalEvents;
            QueryBuilder<CareportalEvent, Long> queryBuilder = getDaoCareportalEvents().queryBuilder();
            queryBuilder.orderBy("date", false);
            Where where = queryBuilder.where();
            where.eq("eventType", event);
            queryBuilder.limit(1L);
            PreparedQuery<CareportalEvent> preparedQuery = queryBuilder.prepare();
            careportalEvents = getDaoCareportalEvents().query(preparedQuery);
            if (careportalEvents.size() == 1)
                return careportalEvents.get(0);
            else
                return null;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void deleteCareportalEventById(String _id) {
        try {
            QueryBuilder<CareportalEvent, Long> queryBuilder = null;
            queryBuilder = getDaoCareportalEvents().queryBuilder();
            Where where = queryBuilder.where();
            where.eq("_id", _id);
            PreparedQuery<CareportalEvent> preparedQuery = queryBuilder.prepare();
            List<CareportalEvent> list = getDaoCareportalEvents().query(preparedQuery);

            if (list.size() == 1) {
                CareportalEvent record = list.get(0);
                if (Config.logIncommingData)
                    log.debug("Removing CareportalEvent record from database: " + record.log());
                delete(record);
            } else {
                if (Config.logIncommingData)
                    log.debug("CareportalEvent not found database: " + _id);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void createCareportalEventFromJsonIfNotExists(JSONObject trJson) {
        try {
            QueryBuilder<CareportalEvent, Long> queryBuilder = null;
            queryBuilder = getDaoCareportalEvents().queryBuilder();
            Where where = queryBuilder.where();
            where.eq("_id", trJson.getString("_id")).or().eq("date", trJson.getLong("mills"));
            PreparedQuery<CareportalEvent> preparedQuery = queryBuilder.prepare();
            List<CareportalEvent> list = getDaoCareportalEvents().query(preparedQuery);
            CareportalEvent careportalEvent;
            if (list.size() == 0) {
                careportalEvent = new CareportalEvent();
                careportalEvent.source = Source.NIGHTSCOUT;
                if (Config.logIncommingData)
                    log.debug("Adding CareportalEvent record to database: " + trJson.toString());
                // Record does not exists. add
            } else if (list.size() == 1) {
                careportalEvent = list.get(0);
                if (Config.logIncommingData)
                    log.debug("Updating CareportalEvent record in database: " + trJson.toString());
            } else {
                log.error("Something went wrong");
                return;
            }
            careportalEvent.date = trJson.getLong("mills");
            careportalEvent.eventType = trJson.getString("eventType");
            careportalEvent.json = trJson.toString();
            careportalEvent._id = trJson.getString("_id");
            createOrUpdate(careportalEvent);
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    static public void scheduleCareportalEventChange() {
        class PostRunnable implements Runnable {
            public void run() {
                log.debug("Firing scheduleCareportalEventChange");
                MainApp.bus().post(new EventCareportalEventChange());
                scheduledCareportalEventPost = null;
            }
        }
        // prepare task for execution in 1 sec
        // cancel waiting task to prevent sending multiple posts
        if (scheduledCareportalEventPost != null)
            scheduledCareportalEventPost.cancel(false);
        Runnable task = new PostRunnable();
        final int sec = 1;
        scheduledCareportalEventPost = careportalEventWorker.schedule(task, sec, TimeUnit.SECONDS);

    }

}
