package info.nightscout.androidaps.db;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.events.EventFoodDatabaseChanged;

/**
 * Created by mike on 24.09.2017.
 */

public class FoodHelper {
    private static Logger log = LoggerFactory.getLogger(FoodHelper.class);

    ConnectionSource connectionSource;

    private static final ScheduledExecutorService foodEventWorker = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduledFoodEventPost = null;

    public FoodHelper(ConnectionSource connectionSource) {
        this.connectionSource = connectionSource;
    }

    public FoodDao getDao() {
        return FoodDao.with(this.connectionSource);
    }

    public void resetFood() {
        try {
            TableUtils.dropTable(connectionSource, Food.class, true);
            TableUtils.createTableIfNotExists(connectionSource, Food.class);
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        scheduleFoodChange();
    }

    // should be moved to an own class, together with all change methodes from the
    // databasehelper
    public static void scheduleFoodChange() {
        class PostRunnable implements Runnable {
            public void run() {
                log.debug("Firing EventFoodChange");
                MainApp.bus().post(new EventFoodDatabaseChanged());
                scheduledFoodEventPost = null;
            }
        }
        // prepare task for execution in 1 sec
        // cancel waiting task to prevent sending multiple posts
        if (scheduledFoodEventPost != null)
            scheduledFoodEventPost.cancel(false);
        Runnable task = new PostRunnable();
        final int sec = 1;
        scheduledFoodEventPost = foodEventWorker.schedule(task, sec, TimeUnit.SECONDS);
    }

    /*
    {
        "_id": "551ee3ad368e06e80856e6a9",
        "type": "food",
        "category": "Zakladni",
        "subcategory": "Napoje",
        "name": "Mleko",
        "portion": 250,
        "carbs": 12,
        "gi": 1,
        "created_at": "2015-04-14T06:59:16.500Z",
        "unit": "ml"
    }
     */
    public void createFoodFromJsonIfNotExists(JSONObject trJson) {
        try {
            Food food = Food.createFromJson(trJson);
            this.getDao().createOrUpdate(food);
        } catch (JSONException | SQLException e) {
            log.error("Unhandled exception", e);
        }
    }


}
