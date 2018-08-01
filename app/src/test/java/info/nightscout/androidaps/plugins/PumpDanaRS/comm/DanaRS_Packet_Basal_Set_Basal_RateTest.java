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
 * Created by Rumen on 01.08.2018
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class})
public class DanaRS_Packet_Basal_Set_Basal_RateTest extends DanaRS_Packet_Basal_Set_Basal_Rate {

    @Test
    public void runTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        // test message decoding
        DanaRS_Packet_Basal_Set_Basal_Rate test = new DanaRS_Packet_Basal_Set_Basal_Rate(createArray(24, 5));
        byte[] requested = test.getRequestParams();
        byte lookingFor = (byte) ((5 * 100) & 0xff);
        assertEquals(lookingFor, requested[24]);
        lookingFor = (byte) ((500 >>> 8) & 0xff);
        assertEquals(lookingFor, requested[25]);
        handleMessage(createArray(3, (byte) 0));
        assertEquals(false, failed);
        handleMessage(createArray(3, (byte) 1));
        assertEquals(true, failed);

        assertEquals("BASAL__SET_BASAL_RATE", getFriendlyName());
    }

    double[] createArray(int length, double fillWith){
        double[] ret = new double[length];
        for(int i = 0; i<length; i++){
            ret[i] = fillWith;
        }
        return ret;
    }

    byte[] createArray(int length, byte fillWith){
        byte[] ret = new byte[length];
        for(int i = 0; i<length; i++){
            ret[i] = fillWith;
        }
        return ret;
    }
}
