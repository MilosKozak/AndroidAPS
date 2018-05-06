package info.nightscout.androidaps.plugins.Overview.graphExtensions;

import android.content.Context;

import com.jjoe64.graphview.DefaultLabelFormatter;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Created by mike on 09.06.2016.
 */
public class TimeAsXAxisLabelFormatter extends DefaultLabelFormatter {

    public TimeAsXAxisLabelFormatter() { }

    @Override
    public String formatLabel(double value, boolean isValueX) {
        if (isValueX) {
            // If the value falls within a few minutes of an hour, format as HH
            // If it falls within a few minutes of a half-hour, format as HH:30
            // Otherwise, format exactly as HH:mm
            double halfHours = value / (30 * 60 * 1000);
            double minutes = (halfHours - Math.floor(halfHours)) * 30;
            String format;

            if (minutes > 5) {
                format = "HH:mm";
            }
            else if ((Math.floor(halfHours) % 2) == 1) {
                format = "HH:30";
            }
            else {
                format = "HH";
            }
            DateFormat dateFormat = new SimpleDateFormat(format);

            return dateFormat.format((long) value);
        } else {
            return super.formatLabel(value, isValueX);
        }
    }
}
