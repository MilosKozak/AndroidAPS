package info.nightscout.androidaps.interfaces;

import java.util.Date;

import info.nightscout.androidaps.db.TempBasal;
import info.nightscout.androidaps.data.IobTotal;

/**
 * Created by mike on 14.06.2016.
 */
public interface TempBasalsInterface {
    void updateTotalIOBTempBasals();
    IobTotal getLastCalculationTempBasals();
    IobTotal getCalculationToTimeTempBasals(long time);

    TempBasal getTempBasal (Date time);
    TempBasal getExtendedBolus (Date time);

    long oldestDataAvaialable();
}
