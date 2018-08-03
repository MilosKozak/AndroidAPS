package info.nightscout.androidaps.plugins.PumpDanaRS.comm;


import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.utils.SP;
import info.nightscout.utils.ToastUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.runner.Request.method;
import static org.powermock.api.support.membermodification.MemberModifier.suppress;

/**
 * Created by Rumen on 01.08.2018
 */

@RunWith(PowerMockRunner.class)
@SuppressStaticInitializationFor("info.nightscout.androidaps.logging.L")
@PrepareForTest({MainApp.class, SP.class, L.class, L.LogElement.class})
public class DanaRS_Packet_Basal_Get_Basal_RateTest extends DanaRS_Packet_Basal_Get_Basal_Rate {

    @Test
    public void runTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        AAPSMocker.mockBus();
        // test message decoding
        // rate is 0.01
        handleMessage(createArray(100, (byte) 1));
        assertEquals(false, failed);
        handleMessage(createArray(100, (byte) 5));
        assertEquals(true, failed);

        assertEquals("BASAL__GET_BASAL_RATE", getFriendlyName());
    }

    byte[] createArray(int length, byte fillWith){
        byte[] ret = new byte[length];
        for(int i = 0; i<length; i++){
            ret[i] = fillWith;
        }
        return ret;
    }
}
