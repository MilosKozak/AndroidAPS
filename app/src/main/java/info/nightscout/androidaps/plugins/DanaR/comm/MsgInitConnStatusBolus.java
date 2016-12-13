package info.nightscout.androidaps.plugins.DanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.DanaR.DanaRPlugin;
import info.nightscout.androidaps.plugins.DanaR.DanaRPump;

/**
 * Created by mike on 28.05.2016.
 */
public class MsgInitConnStatusBolus extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgInitConnStatusBolus.class);

    public MsgInitConnStatusBolus() {
        SetCommand(0x0302);
    }

    @Override
    public void handleMessage(byte[] bytes) {
        if (bytes.length - 10 > 12) {
            return;
        }
        DanaRPump pump = DanaRPlugin.getDanaRPump();
        int bolusConfig = intFromBuff(bytes, 0, 1);
        pump.isExtendedBolusEnabled = (bolusConfig & 0x01) != 0;

        pump.bolusStep = intFromBuff(bytes, 1, 1) / 100d;
        pump.maxBolus = intFromBuff(bytes, 2, 2) / 100d;
        //int bolusRate = intFromBuff(bytes, 4, 8);

        if (Config.logDanaMessageDetail) {
            log.debug("Is Extended bolus enabled: " + pump.isExtendedBolusEnabled);
            log.debug("Bolus increment: " + pump.bolusStep);
            log.debug("Bolus max: " + pump.maxBolus);
        }
    }
}
