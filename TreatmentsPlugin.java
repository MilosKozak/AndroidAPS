package info.nightscout.androidaps.plugins.Treatments;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Iob;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.events.EventTreatmentChange;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.plugins.OpenAPSMA.IobTotal;
import info.nightscout.client.data.NSProfile;

/**
 * Created by mike on 05.08.2016.
 */
 
 // Eddited by Rumen on 04.01.2017 to add COB to mealdata
public class TreatmentsPlugin implements PluginBase, TreatmentsInterface {
    private static Logger log = LoggerFactory.getLogger(TreatmentsPlugin.class);

    public static long lastCalculationTimestamp = 0;
    public static IobTotal lastCalculation;

    public static List<Treatment> treatments;

    private static boolean fragmentEnabled = true;
    private static boolean fragmentVisible = true;

    @Override
    public String getFragmentClass() {
        return TreatmentsFragment.class.getName();
    }

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.treatments);
    }

    @Override
    public boolean isEnabled(int type) {
        return fragmentEnabled;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return fragmentVisible;
    }

    @Override
    public boolean canBeHidden(int type) {
        return true;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        this.fragmentVisible = fragmentVisible;
    }

    @Override
    public int getType() {
        return PluginBase.TREATMENT;
    }

    public TreatmentsPlugin() {
        MainApp.bus().register(this);
        initializeData();
    }

    public void initializeData() {
        try {
            Dao<Treatment, Long> dao = MainApp.getDbHelper().getDaoTreatments();
            QueryBuilder<Treatment, Long> queryBuilder = dao.queryBuilder();
            queryBuilder.orderBy("timeIndex", false);
            queryBuilder.limit(30l);
            PreparedQuery<Treatment> preparedQuery = queryBuilder.prepare();
            treatments = dao.query(preparedQuery);
        } catch (SQLException e) {
            log.debug(e.getMessage(), e);
            treatments = new ArrayList<Treatment>();
        }
    }

    /*
     * Recalculate IOB if value is older than 1 minute
     */
    public void updateTotalIOBIfNeeded() {
        if (lastCalculationTimestamp > new Date().getTime() - 60 * 1000)
            return;
        updateTotalIOB();
    }

    @Override
    public IobTotal getLastCalculation() {
        return lastCalculation;
    }

    @Override
    public void updateTotalIOB() {
        IobTotal total = new IobTotal();

        if (MainApp.getConfigBuilder() == null || MainApp.getConfigBuilder().getActiveProfile() == null) // app not initialized yet
            return;
        NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
        if (profile == null) {
            lastCalculation = total;
            return;
        }

        Double dia = profile.getDia();

        Date now = new Date();
        for (Integer pos = 0; pos < treatments.size(); pos++) {
            Treatment t = treatments.get(pos);
            Iob tIOB = t.iobCalc(now, dia);
            total.iob += tIOB.iobContrib;
            total.activity += tIOB.activityContrib;
            Iob bIOB = t.iobCalc(now, dia / 2);
            total.bolussnooze += bIOB.iobContrib;
        }

        lastCalculationTimestamp = new Date().getTime();
        lastCalculation = total;
    }

    public class MealData {
        public double boluses = 0d;
        public double carbs = 0d;
		public double COB = 0d;
    }

    @Override
    public MealData getMealData() {
        MealData result = new MealData();
        NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
        if (profile == null)
            return result;
		double carbsAbsorptionRate = profile.getCarbAbsorbtionRate();
        for (Treatment treatment : treatments) {
            long now = new Date().getTime();
            long dia_ago = now - (new Double(profile.getDia() * 60 * 60 * 1000l)).longValue();
            long t = treatment.created_at.getTime();
			// int hours   = (int) ((milliseconds / (1000*60*60)) % 24);
			//now in hours
			int hours   = (int) ((now / (1000*60*60)) % 24);
			int t_hours  = (int) ((t / (1000*60*60)) % 24);
			int carbs_ago =  hours - t_hours ;
			
            if (t > dia_ago && t <= now) {
                if (treatment.carbs >= 1) {
                    result.carbs += treatment.carbs;
					// check for negatives 
						if (carbs_ago >= 0){
							result.COB +=  treatment.carbs - Math.round(carbsAbsorptionRate*carbs_ago);//Math.round((treatment.carbs - (treatment.carbs * carbs_ago)))
						}
                }
                if (treatment.insulin >= 0.1 && treatment.mealBolus) {
                    result.boluses += treatment.insulin;
                }
            }
        }
        return result;
    }

    @Override
    public List<Treatment> getTreatments() {
        return treatments;
    }

    @Subscribe
    public void onStatusEvent(final EventTreatmentChange ev) {
        initializeData();
        updateTotalIOB();
    }

}
