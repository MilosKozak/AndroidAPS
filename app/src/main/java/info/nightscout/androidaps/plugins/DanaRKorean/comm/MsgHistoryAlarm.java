package info.nightscout.androidaps.plugins.DanaRKorean.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by mike on 20.07.2016.
 */
public class MsgHistoryAlarm extends MsgHistoryAll {
    private static Logger log = LoggerFactory.getLogger(MsgHistoryAlarm.class);
    public MsgHistoryAlarm() {
        SetCommand(0x3105);
    }
    // Handle message taken from MsgHistoryAll
}
