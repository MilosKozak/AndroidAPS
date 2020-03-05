package info.nightscout.androidaps.plugins.pump.combo.ruffyscripter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.TimeZone;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.utils.SP;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, DateFormat.class})
public class PumpTimeHelperTest {

    @Test
    public void getTimezoneOffsetDefault() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockSP();

        PumpTimeHelper pumpTimeHelper = new PumpTimeHelper();

        int offset = pumpTimeHelper.getPhoneToPumpOffsetHours();

        assertEquals(0, offset);
        assertEquals(17, pumpTimeHelper.phoneHourToPumpHour(17));
    }

    @Test
    public void getOffset_GMTPlus1() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockSP();

        when(SP.getBoolean(eq(R.string.key_pump_uses_utc), anyBoolean())).thenReturn(true);

        PumpTimeHelper pumpTimeHelper = Mockito.spy(new PumpTimeHelper());

        Calendar pumpCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        pumpCalendar.set(2001, 5, 10, 20, 20, 30);
        Mockito.doReturn(pumpCalendar).when(pumpTimeHelper).getPumpCalendar();

        Calendar phoneCalendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+01:00"));
        phoneCalendar.set(2001, 5, 10, 21, 20, 30);
        Mockito.doReturn(phoneCalendar).when(pumpTimeHelper).getPhoneCalendar();

        int offset = pumpTimeHelper.getPhoneToPumpOffsetHours();

        assertEquals(-1, offset);
        assertEquals(18, pumpTimeHelper.phoneHourToPumpHour(17));
    }

    @Test
    public void getOffset_GMTMinus1() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockSP();

        when(SP.getBoolean(eq(R.string.key_pump_uses_utc), anyBoolean())).thenReturn(true);

        PumpTimeHelper pumpTimeHelper = Mockito.spy(new PumpTimeHelper());

        Calendar pumpCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        pumpCalendar.set(2001, 5, 10, 20, 20, 30);
        Mockito.doReturn(pumpCalendar).when(pumpTimeHelper).getPumpCalendar();

        Calendar phoneCalendar = Calendar.getInstance(TimeZone.getTimeZone("GMT-01:00"));
        phoneCalendar.set(2001, 5, 10, 19, 20, 30);
        Mockito.doReturn(phoneCalendar).when(pumpTimeHelper).getPhoneCalendar();

        int offset = pumpTimeHelper.getPhoneToPumpOffsetHours();

        assertEquals(1, offset);
        assertEquals(16, pumpTimeHelper.phoneHourToPumpHour(17));
    }

    @Test
    public void calendarHandlesTimezoneChange() {
        Calendar pumpCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        pumpCalendar.set(2001, 5, 10, 14, 20, 30);

        long originalMillis = pumpCalendar.getTimeInMillis();
        assertEquals(14, pumpCalendar.get(Calendar.HOUR_OF_DAY));
        pumpCalendar.setTimeZone(TimeZone.getTimeZone("GMT+01:00"));

        long newMillis = pumpCalendar.getTimeInMillis();
        assertEquals(15, pumpCalendar.get(Calendar.HOUR_OF_DAY));

        // In either TZ should return same millis
        assertEquals(originalMillis, newMillis, 1000);
    }

    @Test
    public void calendardAndSystemReturnSameMillis() {
        Calendar pumpCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        assertEquals(pumpCalendar.getTimeInMillis(), System.currentTimeMillis(), 1000);
    }
}