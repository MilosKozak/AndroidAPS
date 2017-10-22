package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import com.cozmo.danar.util.BleCommandUtil;

public class DanaRS_Packet_Basal_Set_Temporary_Basal extends DanaRS_Packet {
    private static Logger log = LoggerFactory.getLogger(DanaRS_Packet_Basal_Set_Temporary_Basal.class);

    private int temporaryBasalRatio;
    private int temporaryBasalDuration;

    public DanaRS_Packet_Basal_Set_Temporary_Basal() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BASAL__SET_TEMPORARY_BASAL;
    }

    public DanaRS_Packet_Basal_Set_Temporary_Basal(int temporaryBasalRatio, int temporaryBasalDuration) {
        this();
        this.temporaryBasalRatio = temporaryBasalRatio;
        this.temporaryBasalDuration = temporaryBasalDuration;
        if (Config.logDanaMessageDetail) {
            log.debug("Setting temporary basal of " + temporaryBasalRatio + "% for " + temporaryBasalDuration + " hours");
        }
    }

    @Override
    public byte[] getRequestParams() {
        byte[] request = new byte[2];
        request[0] = (byte) (temporaryBasalRatio & 0xff);
        request[1] = (byte) (temporaryBasalDuration & 0xff);
        return request;
    }

    @Override
    public void handleMessage(byte[] data) {
        int result = intFromBuff(data, 0, 1);
        if (Config.logDanaMessageDetail) {
            if (result == 0)
                log.debug("Result OK");
            else
                log.error("Result Error: " + result);
        }
    }

    @Override
    public String getFriendlyName() {
        return "BASAL__SET_TEMPORARY_BASAL";
    }
}
