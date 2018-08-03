package info.nightscout.androidaps.plugins.PumpDanaRS.comm;


import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Treatments.Treatment;
import info.nightscout.utils.SP;
import info.nightscout.utils.ToastUtils;

import static org.junit.Assert.assertEquals;

/**
 * Created by Rumen on 31.07.2018.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, L.class})
public class DanaRS_Packet_Bolus_Set_Step_Bolus_StopTest extends DanaRS_Packet_Bolus_Set_Step_Bolus_Stop {

    @Test
    public void runTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        AAPSMocker.mockBus();

        DanaRS_Packet_Bolus_Set_Step_Bolus_Stop testPacket = new DanaRS_Packet_Bolus_Set_Step_Bolus_Stop(1d , new Treatment());
        // test message decoding
        testPacket.handleMessage(new byte[]{(byte) 0, (byte) 0, (byte) 0});
        assertEquals(false, testPacket.failed);
        testPacket.handleMessage(new byte[]{(byte) 1, (byte) 1, (byte) 1, (byte) 1, (byte) 1, (byte) 1, (byte) 1, (byte) 1});
        assertEquals(true, testPacket.failed);

        assertEquals("BOLUS__SET_STEP_BOLUS_STOP", getFriendlyName());
    }

}
