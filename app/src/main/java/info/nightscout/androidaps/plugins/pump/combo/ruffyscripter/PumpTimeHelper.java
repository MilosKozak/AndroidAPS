package info.nightscout.androidaps.plugins.pump.combo.ruffyscripter;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.utils.SP;

public class PumpTimeHelper {

    public Calendar getPhoneCalendar() {
        return Calendar.getInstance();
    }

    public Calendar getPumpCalendar() {
        if (isPumpUTC()) {
            return Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        } else {
            return Calendar.getInstance();
        }
    }

    protected int getPhonePumpOffsetHours() {
        if (!isPumpUTC()) {
            return 0;
        }
        Calendar pumpCal = getPumpCalendar();
        Calendar phoneCal = getPhoneCalendar();
        phoneCal.set(pumpCal.get(Calendar.YEAR), pumpCal.get(Calendar.MONTH), pumpCal.get(Calendar.DAY_OF_MONTH), pumpCal.get(Calendar.HOUR_OF_DAY), pumpCal.get(Calendar.MINUTE), pumpCal.get(Calendar.SECOND));
        float diff = phoneCal.getTimeInMillis() - pumpCal.getTimeInMillis();
        diff = diff / (60 * 60 * 1000);
        return (int) Math.round(diff);
    }

    protected int getOffset() {
        if (!isPumpUTC()) return 0;
        return -getPhonePumpOffsetHours();
    }

    public boolean isPumpUTC() {
        return SP.getBoolean(R.string.key_pump_uses_utc, false);
    }
/*
    public Calendar convertToPhoneTimezone(Calendar cal) {
        Calendar phoneCal = getPhoneCalendar();

        // Accessing .get calls the internal complete() function
        // until complete() is called changing the timezone doesn't cause a re-calc
        cal.get(Calendar.HOUR_OF_DAY);

        cal.setTimeZone(phoneCal.getTimeZone());
        return cal;
    }

    public Calendar convertToPumpTimezone(Calendar cal) {
        Calendar pumpCal = getPhoneCalendar();

        // Accessing .get calls the internal complete() function
        // until complete() is called changing the timezone doesn't cause a re-calc
        cal.get(Calendar.HOUR_OF_DAY);

        cal.setTimeZone(pumpCal.getTimeZone());
        return cal;
    }
*/
    public int phoneHourToPumpHour(int hour) {
        int pumpHour = (hour-getOffset()) % 24;
        if (pumpHour < 0) pumpHour += 24;
        return pumpHour;
    }

}
