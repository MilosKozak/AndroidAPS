package info.nightscout.androidaps.plugins.general.nsclient;

import androidx.annotation.Nullable;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.db.*;

import java.util.Date;

/**
 * @author tanja
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

    void uploadCareportalBgCheck(BgReading reading, String source);

    void uploadCareportalBgCheck(String createdAt, String createdBy, String glucoseType, Number glucose, String units, String device);

    void uploadCareportalNote(String createdAt, String createdBy, String note, String device);

    void uploadInsulinChangeEvent(String createdBy, String createdAt, String note, String device);

    void uploadBatteryChanged(String createdBy, String createdAt, String note, String device);

    void uploadComboBolus(String createdAt, String enteredBy, String device, Double insulin, Integer duration,
                          Double relative, Integer splitNow, Integer splitExt);

    void uploadCareportalMealBolus(String createdAt, String enteredBy, String pumpSignature, Double insulin, Double carbs);


    void removeCareportalEntryFromNS(String _id);

    boolean isIdValid(String _id);

    void uploadError(String error);

    void uploadError(String error, Date date);

    void uploadEvent(String careportalEvent, long time, @Nullable String notes);

    void removeFoodFromNS(String _id);

    void uploadSensorChange(String enteredBy, String created_at, String device);

    void uploadSiteChange(String enteredBy, String created_at, String device);

    void uploadTempBasal(String createdAt, String createdBy, String device, Integer duration, Double absolute);

}
