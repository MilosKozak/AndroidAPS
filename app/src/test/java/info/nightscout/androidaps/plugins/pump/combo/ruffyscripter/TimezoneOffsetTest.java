package info.nightscout.androidaps.plugins.pump.combo.ruffyscripter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.text.DateFormat;
import java.text.FieldPosition;
import java.util.Date;
import java.util.TimeZone;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.utils.SP;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, DateFormat.class})
public class TimezoneOffsetTest {

    @Test
    public void getTimezoneOffsetDefault() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockSP();

        TimezoneOffset timezoneOffset = new TimezoneOffset();

        int offset = timezoneOffset.getOffset();

        assertEquals(0, offset);
    }

    @Test
    public void getTimezoneOffset0GMT() throws Exception {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockSP();
        when(SP.getString(eq(R.string.key_combo_timezone), anyString())).thenReturn("0");

        TimezoneOffset timezoneOffset = new TimezoneOffset();
        TimezoneOffset timezoneOffsetSpy = Mockito.spy(timezoneOffset);
        when(timezoneOffsetSpy.getLocalTimeOffset()).thenReturn("+0000");

        int offset = timezoneOffsetSpy.getOffset();

        assertEquals(0, offset);
    }

    @Test
    public void getTimezoneOffsetn1GMT() throws Exception {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockSP();
        when(SP.getString(eq(R.string.key_combo_timezone), anyString())).thenReturn("1");

        TimezoneOffset timezoneOffset = new TimezoneOffset();
        TimezoneOffset timezoneOffsetSpy = Mockito.spy(timezoneOffset);
        when(timezoneOffsetSpy.getLocalTimeOffset()).thenReturn("+0000");

        int offset = timezoneOffsetSpy.getOffset();

        assertEquals(1, offset);
    }

    @Test
    public void getTimezoneOffsetPump0PhoneBST() throws Exception {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockSP();
        when(SP.getString(eq(R.string.key_combo_timezone), anyString())).thenReturn("0");

        TimezoneOffset timezoneOffset = new TimezoneOffset();
        TimezoneOffset timezoneOffsetSpy = Mockito.spy(timezoneOffset);
        when(timezoneOffsetSpy.getLocalTimeOffset()).thenReturn("+0100");

        int offset = timezoneOffsetSpy.getOffset();

        assertEquals(-1, offset);
    }

    @Test
    public void getTimezoneOffsetPumpPlus1PhoneBST() throws Exception {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockSP();
        when(SP.getString(eq(R.string.key_combo_timezone), anyString())).thenReturn("1");

        TimezoneOffset timezoneOffset = new TimezoneOffset();
        TimezoneOffset timezoneOffsetSpy = Mockito.spy(timezoneOffset);
        when(timezoneOffsetSpy.getLocalTimeOffset()).thenReturn("+0100");

        int offset = timezoneOffsetSpy.getOffset();

        assertEquals(0, offset);
    }

}