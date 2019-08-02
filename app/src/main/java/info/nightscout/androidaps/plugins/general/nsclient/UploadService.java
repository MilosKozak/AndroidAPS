package info.nightscout.androidaps.plugins.general.nsclient;

import androidx.annotation.Nullable;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.db.*;

import java.util.Date;

/**
 *  @author tanja
 */
public interface UploadService {

    //Handler getHandler();

    void uploadAppStart();

    void uploadTempBasalStartAbsolute(TemporaryBasal temporaryBasal, Double originalExtendedAmount);

    void uploadTempBasalStartPercent(TemporaryBasal basal);

    void uploadTempBasalEnd(long time, boolean isFakedTempBasal, long pumpId);

    void uploadExtendedBolus(ExtendedBolus extendedBolus);

    void uploadExtendedBolusEnd(long time, long pumpId);

    void uploadTreatmentRecord(DetailedBolusInfo detailedBolusInfo);

    void uploadTempTarget(TempTarget tempTarget);

    void uploadProfileSwitch(ProfileSwitch profileSwitch);

    void updateProfileSwitch(ProfileSwitch profileSwitch);

    void uploadDeviceStatus();

    void uploadOpenAPSOffline(int duration);

    void uploadBg(BgReading reading, String source);

    void uploadBg(String enteredBy, String createdAt, String glucoseType, Number glucose, String units);

    //void uploadCareportalEntryToNS(JSONObject data);

    void removeCareportalEntryFromNS(String _id);

    boolean isIdValid(String _id);

    void uploadError(String error);

    void uploadError(String error, Date date);

    void uploadEvent(String careportalEvent, long time, @Nullable String notes);

    void removeFoodFromNS(String _id);

    void uploadSensorChange(String enteredBy, String created_at);


}
