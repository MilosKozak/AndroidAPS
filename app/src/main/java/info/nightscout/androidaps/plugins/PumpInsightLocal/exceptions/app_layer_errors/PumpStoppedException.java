package info.nightscout.androidaps.plugins.PumpInsightLocal.exceptions.app_layer_errors;

public class PumpStoppedException extends AppLayerErrorException {

    public PumpStoppedException(int errorCode) {
        super(errorCode);
    }
}
