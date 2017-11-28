package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import java.util.Date;

import com.cozmo.danar.util.BleCommandUtil;

public class DanaRS_Packet_History_Basal extends DanaRS_Packet_History_ {

    public DanaRS_Packet_History_Basal() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__BASAL;
    }

    public DanaRS_Packet_History_Basal(Date from) {
        super(from);
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__BASAL;
    }

    @Override
    public String getFriendlyName() {
        return "REVIEW__BASAL";
    }
}
