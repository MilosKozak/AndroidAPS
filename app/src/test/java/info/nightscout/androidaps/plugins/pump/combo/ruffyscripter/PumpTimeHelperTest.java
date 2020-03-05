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

        int offset = pumpTimeHelper.getOffset();

        assertEquals(0, offset);
    }

    @Test
    public void getTimezoneOffsetUTC_BST() {
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

        int offset = pumpTimeHelper.getOffset();

        assertEquals(1, offset);
    }

    @Test
    public void getTimezoneOffsetUTC_Minus1() {
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

        int offset = pumpTimeHelper.getOffset();

        assertEquals(-1, offset);
    }

    /*
        @Test
        public void pumpDateTimeToPhoneUTC() {
            AAPSMocker.mockMainApp();
            AAPSMocker.mockSP();

            when(SP.getBoolean(eq(R.string.key_pump_uses_utc), anyBoolean())).thenReturn(true);

            TimezoneOffset timezoneOffset = Mockito.spy(new TimezoneOffset());

            Calendar pumpCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            pumpCalendar.set(2001, 5, 10, 20, 20, 30);
            Mockito.doReturn(pumpCalendar).when(timezoneOffset).getPumpCalendar();

            Calendar phoneCalendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+01:00"));
            phoneCalendar.set(2001, 5, 10, 21, 20, 30);
            Mockito.doReturn(phoneCalendar).when(timezoneOffset).getPhoneCalendar();

            Calendar testCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            testCalendar.set(2020, 6, 2, 3, 40, 50);

            Calendar resultCalendar = timezoneOffset.convertToPhoneTimezone(testCalendar);

            assertEquals(2020, resultCalendar.get(Calendar.YEAR));
            assertEquals(6, resultCalendar.get(Calendar.MONTH));
            assertEquals(2, resultCalendar.get(Calendar.DAY_OF_MONTH));
            assertEquals(4, resultCalendar.get(Calendar.HOUR_OF_DAY));
            assertEquals(40, resultCalendar.get(Calendar.MINUTE));
            assertEquals(50, resultCalendar.get(Calendar.SECOND));
        }
    */
    @Test
    public void calTest() {
        Calendar pumpCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        pumpCalendar.set(2001, 5, 10, 14, 20, 30);
        long originalMillis = pumpCalendar.getTimeInMillis();
        assertEquals(14, pumpCalendar.get(Calendar.HOUR_OF_DAY));
        pumpCalendar.setTimeZone(TimeZone.getTimeZone("GMT+01:00"));
        long newMillis = pumpCalendar.getTimeInMillis();
        assertEquals(15, pumpCalendar.get(Calendar.HOUR_OF_DAY));

        assertEquals(originalMillis, newMillis);

    }

    @Test
    public void pumpTime() {
        Calendar pumpCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        assertEquals(pumpCalendar.getTimeInMillis(), System.currentTimeMillis());
    }
}