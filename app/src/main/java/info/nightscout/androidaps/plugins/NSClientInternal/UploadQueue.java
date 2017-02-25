package info.nightscout.androidaps.plugins.NSClientInternal;

import com.j256.ormlite.dao.CloseableIterator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.db.DbRequest;
import info.nightscout.androidaps.plugins.NSClientInternal.services.NSClientService;
import info.nightscout.utils.SP;

/**
 * Created by mike on 21.02.2016.
 */
public class UploadQueue {
    private static Logger log = LoggerFactory.getLogger(UploadQueue.class);

    public static String status() {
        return "QUEUE: " + MainApp.getDbHelper().size(DatabaseHelper.DATABASE_DBREQUESTS);
    }

    public static long size() {
        return MainApp.getDbHelper().size(DatabaseHelper.DATABASE_DBREQUESTS);
    }

    public static void add(final DbRequest dbr) {
        NSClientService.handler.post(new Runnable() {
            @Override
            public void run() {
                log.debug("QUEUE adding: " + dbr.data);
                MainApp.getDbHelper().create(dbr);
            }
        });
    }

    public static void clearQueue() {
        NSClientService.handler.post(new Runnable() {
            @Override
            public void run() {
                log.debug("QUEUE ClearQueue");
                MainApp.getDbHelper().deleteAllDbRequests();
                log.debug(status());
            }
        });
    }

    public static void removeID(final JSONObject record) {
        NSClientService.handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    String id;
                    if (record.has("NSCLIENT_ID")) {
                        id = record.getString("NSCLIENT_ID");
                    } else {
                        return;
                    }
                    if (MainApp.getDbHelper().deleteDbRequest(id) == 1) {
                        log.debug("Removed item from UploadQueue. " + UploadQueue.status());
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public static void removeID(final String action, final String _id) {
        NSClientService.handler.post(new Runnable() {
            @Override
            public void run() {
                MainApp.getDbHelper().deleteDbRequestbyMongoId(action, _id);
            }
        });
    }

    public String textList() {
        String result = "";
        CloseableIterator<DbRequest> iterator = null;
        try {
            iterator = MainApp.getDbHelper().getDaoDbRequest().closeableIterator();
            try {
                while (iterator.hasNext()) {
                    DbRequest dbr = iterator.next();
                    result += "<br>";
                    result += dbr.action.toUpperCase() + " ";
                    result += dbr.collection + ": ";
                    result += dbr.data;
                }
            } finally {
                iterator.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

}
