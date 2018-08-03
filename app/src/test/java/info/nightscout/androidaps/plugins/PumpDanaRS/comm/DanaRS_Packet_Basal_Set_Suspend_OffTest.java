package info.nightscout.androidaps.plugins.PumpDanaRS.comm;


import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.PumpDanaRS.DanaRSPlugin;
import info.nightscout.utils.SP;
import info.nightscout.utils.ToastUtils;

import static org.junit.Assert.assertEquals;

/**
 * Created by Rumen on 01.08.2018.
 */

@RunWith(PowerMockRunner.class)
@SuppressStaticInitializationFor("info.nightscout.androidaps.logging.L")
@PrepareForTest({MainApp.class, SP.class, L.class})
public class DanaRS_Packet_Basal_Set_Suspend_OffTest extends DanaRS_Packet_Basal_Set_Suspend_Off {

    @Test
    public void runTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        // test message decoding
        handleMessage(new byte[]{(byte) 0, (byte) 0, (byte) 0});
        assertEquals(false, failed);
        handleMessage(new byte[]{(byte) 0, (byte) 0, (byte) 1});
        assertEquals(true, failed);

        assertEquals("BASAL__SET_SUSPEND_OFF", getFriendlyName());
    }

}
