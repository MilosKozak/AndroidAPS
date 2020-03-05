package info.nightscout.androidaps.plugins.pump.combo.ruffyscripter;

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

    protected int getPhoneToPumpOffsetHours() {
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

    public boolean isPumpUTC() {
        return SP.getBoolean(R.string.key_pump_uses_utc, false);
    }

    public int phoneHourToPumpHour(int hour) {
        int pumpHour = (hour- getPhoneToPumpOffsetHours()) % 24;
        if (pumpHour < 0) pumpHour += 24;
        return pumpHour;
    }

}
