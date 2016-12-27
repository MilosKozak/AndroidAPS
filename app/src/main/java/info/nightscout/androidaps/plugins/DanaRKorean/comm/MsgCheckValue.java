package info.nightscout.androidaps.plugins.DanaRKorean.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.DanaR.comm.MessageBase;
import info.nightscout.androidaps.plugins.DanaRKorean.DanaRKoreanPlugin;
import info.nightscout.androidaps.plugins.DanaRKorean.DanaRKoreanPump;
import info.nightscout.utils.ToastUtils;

/**
 * Created by mike on 30.06.2016.
 */
public class MsgCheckValue extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgCheckValue.class);

    public MsgCheckValue() {
        SetCommand(0xF0F1);
    }

    @Override
    public void handleMessage(byte[] bytes) {
        DanaRKoreanPump pump = DanaRKoreanPlugin.getDanaRPump();

        pump.model = intFromBuff(bytes, 0, 1);
        pump.protocol = intFromBuff(bytes, 1, 1);
        pump.productCode = intFromBuff(bytes, 2, 1);
        if (pump.model != DanaRKoreanPump.DOMESTIC_MODEL) {
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(),MainApp.sResources.getString(R.string.wrongpumpdriverselected), R.raw.error);
            ((DanaRKoreanPlugin)MainApp.getSpecificPlugin(DanaRKoreanPlugin.class)).doDisconnect("Wrong Model");
            log.debug("Wrong model selected");
        }

        if (Config.logDanaMessageDetail) {
            log.debug("Model: " + String.format("%02X ", pump.model));
            log.debug("Protocol: " + String.format("%02X ", pump.protocol));
            log.debug("Product Code: " + String.format("%02X ", pump.productCode));
        }
    }

}
