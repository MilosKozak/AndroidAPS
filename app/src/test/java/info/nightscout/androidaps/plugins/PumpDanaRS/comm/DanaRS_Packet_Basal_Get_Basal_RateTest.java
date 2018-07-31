package info.nightscout.androidaps.plugins.PumpDanaRS.comm;


import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.utils.SP;
import info.nightscout.utils.ToastUtils;

import static org.junit.Assert.assertEquals;

/**
 * Created by mike on 20.11.2017.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class})
public class DanaRS_Packet_Basal_Get_Basal_RateTest extends DanaRS_Packet_Basal_Get_Basal_Rate {

    @Test
    public void runTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        DanaRS_Packet_Basal_Get_Basal_Rate test = new DanaRS_Packet_Basal_Get_Basal_Rate();
        // test message decoding
        test.handleMessage(new byte[]{(byte) 0, (byte) 0, (byte) 0});
        assertEquals(false, test.failed);
        test.handleMessage(new byte[]{(byte) 0, (byte) 0, (byte) 1});
        assertEquals(true, test.failed);

        assertEquals("BASAL__GET_BASAL_RATE", getFriendlyName());
    }

}
