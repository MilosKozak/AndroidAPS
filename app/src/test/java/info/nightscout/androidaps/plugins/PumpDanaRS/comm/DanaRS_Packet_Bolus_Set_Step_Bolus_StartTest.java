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
 * Created by Rumen on 31.07.2018.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class})
public class DanaRS_Packet_Bolus_Set_Step_Bolus_StartTest extends DanaRS_Packet_Bolus_Set_Step_Bolus_Start {

    @Test
    public void runTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        // test message generation - fails with null pointer exc
//        DanaRS_Packet_Bolus_Set_Step_Bolus_Start testBolus = new DanaRS_Packet_Bolus_Set_Step_Bolus_Start(1.0d,0);
//        byte[] generatedCode = testBolus.getRequestParams();
//        assertEquals(3 , generatedCode.length);
//        assertEquals((byte) 1 , generatedCode[1]);
//        assertEquals((byte) 0, generatedCode[2]);

        // test message decoding
        handleMessage(new byte[]{(byte) 0, (byte) 0, (byte) 0});
        assertEquals(false, failed);
        handleMessage(new byte[]{(byte) 1, (byte) 1, (byte) 1, (byte) 1, (byte) 1, (byte) 1, (byte) 1, (byte) 1});
        assertEquals(true, failed);

        assertEquals("BOLUS__SET_STEP_BOLUS_START", getFriendlyName());
    }

}
