package org.monkey.d.ruffy.ruffy.driver.display.menu;

import java.util.Calendar;
import java.util.Locale;

/**
 * Created by fishermen21 on 24.05.17.
 */

public class MenuDate {
    private final int day;
    private final int month;


    public MenuDate(int day, int month) {
        this.day = day;
        this.month = month;
    }

    public MenuDate(String value) {
        String[] p = value.split("\\.");
        day = Integer.parseInt(p[0]);
        month = Integer.parseInt(p[1]);
    }

    public int getDay() {
        return day;
    }

    public int getMonth() {
        return month;
    }

    public long toTimestamp(int offsetHours) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.HOUR, -offsetHours);
        int year = calendar.get(Calendar.YEAR);
        if (month > calendar.get(Calendar.MONTH) + 1) {
            year -= 1;
        }
        calendar.set(year, month - 1, day, 0, 0, 0);
        calendar.add(Calendar.HOUR, offsetHours);

        // round to second
        return (calendar.getTimeInMillis() - calendar.getTimeInMillis() % 1000);

    }

    @Override
    public String toString() {
        return day+"."+String.format(Locale.ENGLISH, "%02d",month)+".";
    }
}
