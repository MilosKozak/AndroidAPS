package info.nightscout.utils;

import android.support.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static android.webkit.ConsoleMessage.MessageLevel.LOG;

/**
 * JSonHelper is a Helper class which contains several methods to safely get data from the ggiven JSONObject.
 *
 * Created by triplem on 04.01.18.
 */

public class JsonHelper {

    private static final Logger log = LoggerFactory.getLogger(JsonHelper.class);

    private JsonHelper() {};

    public static Object safeGetObject(JSONObject json, String fieldName, Object defaultValue) {
        Object result = defaultValue;

        if (json != null && json.has(fieldName)) {
            try {
                result = json.get(fieldName);
            } catch (JSONException ignored) {
            }
        }

        return result;
    }

    @Nullable
    public static String safeGetString(JSONObject json, String fieldName) {
        String result = null;
        return safeGetString(json, fieldName, result);
    }

    public static String safeGetString(JSONObject json, String fieldName, String defaultValue) {
        String result = defaultValue;

        if (json != null && json.has(fieldName)) {
            try {
                result = json.getString(fieldName);
            } catch (JSONException ignored) {
            }
        }

        return result;
    }

    public static double safeGetDouble(JSONObject json, String fieldName) {
        double result = 0d;

        if (json != null && json.has(fieldName)) {
            try {
                result = json.getDouble(fieldName);
            } catch (JSONException ignored) {
            }
        }

        return result;
    }

    public static int safeGetInt(JSONObject json, String fieldName) {
        int result = 0;

        if (json != null && json.has(fieldName)) {
            try {
                result = json.getInt(fieldName);
            } catch (JSONException ignored) {
            }
        }

        return result;
    }

    public static long safeGetLong(JSONObject json, String fieldName) {
        long result = 0;

        if (json != null && json.has(fieldName)) {
            try {
                result = json.getLong(fieldName);
            } catch (JSONException e) {
            }
        }

        return result;
    }

    public static boolean safeGetBoolean(JSONObject json, String fieldName) {
        boolean result = false;

        if (json != null && json.has(fieldName)) {
            try {
                result = json.getBoolean(fieldName);
            } catch (JSONException e) {
            }
        }

        return result;
    }

    @Nullable
    public static Date safeGetDate(JSONObject json, String fieldName) {
        DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        return safeGetDate(json, fieldName, sdf);
    }

    @Nullable
    public static Date safeGetDate(JSONObject json, String fieldName, DateFormat format) {
        String dateValue = safeGetString(json, fieldName);;

        Date date = null;
        if (dateValue != null) {
            try {
                date = format.parse(dateValue);
            } catch (ParseException e) {
                log.error("Cannot parse dateValue {} with given format", dateValue);
            }
        }

         return date;
    }
}
