package info.nightscout.androidaps.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.Iob;
import info.nightscout.androidaps.interfaces.InsulinInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.DataPointWithLabelInterface;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSProfile;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.NSUpload;

@DatabaseTable(tableName = DatabaseHelper.DATABASE_TREATMENTS)
public class Treatment implements DataPointWithLabelInterface {
    private static Logger log = LoggerFactory.getLogger(Treatment.class);

    @DatabaseField(id = true)
    public long date;

    @DatabaseField
    public boolean isValid = true;

    @DatabaseField
    public int source = Source.NONE;
    @DatabaseField
    public String _id;

    @DatabaseField
    public Double insulin = 0d;
    @DatabaseField
    public Double carbs = 0d;
    @DatabaseField
    public boolean mealBolus = true; // true for meal bolus , false for correction bolus

    @DatabaseField
    public int insulinInterfaceID = InsulinInterface.FASTACTINGINSULIN;
    @DatabaseField
    public double dia = Constants.defaultDIA;

    public Treatment() {
    }

    public Treatment(InsulinInterface insulin) {
        insulinInterfaceID = insulin.getId();
        dia = insulin.getDia();
    }

    public void copyFrom(Treatment t) {
        this.date = t.date;
        this.isValid = t.isValid;
        this.source = t.source;
        this._id = t._id;
        this.insulin = t.insulin;
        this.carbs = t.carbs;
        this.mealBolus = t.mealBolus;
    }

    public long getMillisecondsFromStart() {
        return new Date().getTime() - date;
    }

    public String log() {
        return "Treatment{" +
                "date= " + date +
                ", date= " + DateUtil.dateAndTimeString(date) +
                ", isValid= " + isValid +
                ", _id= " + _id +
                ", insulin= " + insulin +
                ", carbs= " + carbs +
                ", mealBolus= " + mealBolus +
                "}";
    }

    //  ----------------- DataPointInterface --------------------
    @Override
    public double getX() {
        return date;
    }

    // default when no sgv around available
    private double yValue = 0;

    @Override
    public double getY() {
        return yValue;
    }

    @Override
    public String getLabel() {
        String label = "";
        if (insulin > 0) label += DecimalFormatter.to2Decimal(insulin) + "U";
        if (carbs > 0)
            label += (label.equals("") ? "" : " ") + DecimalFormatter.to0Decimal(carbs) + "g";
        return label;
    }

    public void setYValue(List<BgReading> bgReadingsArray) {
        NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
        if (profile == null) return;
        for (int r = bgReadingsArray.size() - 1; r >= 0; r--) {
            BgReading reading = bgReadingsArray.get(r);
            if (reading.date > date) continue;
            yValue = NSProfile.fromMgdlToUnits(reading.value, profile.getUnits());
            break;
        }
    }

    //  ----------------- DataPointInterface end --------------------

    public Iob iobCalc(long time, double dia) {
        InsulinInterface insulinInterface = MainApp.getInsulinIterfaceById(insulinInterfaceID);
        if (insulinInterface == null)
            insulinInterface = ConfigBuilderPlugin.getActiveInsulin();

        return insulinInterface.iobCalcForTreatment(this, time, dia);
    }
}
