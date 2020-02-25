package info.nightscout.androidaps.plugins.pump.combo.ruffyscripter;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.utils.SP;

public class TimezoneOffset {

    public String getLocalTimeOffset() {
        DateFormat dateFormat = new SimpleDateFormat("Z");
        return dateFormat.format(System.currentTimeMillis());
    }

    public int getOffset() {

        if (!isAbsolutePumpTimezone()) return 0;

        String pumpTimezoneOffset = SP.getString(R.string.key_combo_timezone, "");

        String localTime = getLocalTimeOffset();

        int pumpOffset = Integer.parseInt(pumpTimezoneOffset);
        int phoneOffset = Integer.parseInt(localTime)/100;

        return pumpOffset - phoneOffset;
    }

    public boolean isAbsolutePumpTimezone() {
        String pumpTimezoneOffset = SP.getString(R.string.key_combo_timezone, "");
        return ! (pumpTimezoneOffset.isEmpty() || pumpTimezoneOffset.equalsIgnoreCase("phone"));
    }

}
