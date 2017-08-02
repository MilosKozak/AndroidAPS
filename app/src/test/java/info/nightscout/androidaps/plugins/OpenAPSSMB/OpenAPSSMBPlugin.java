package info.nightscout.androidaps.plugins.OpenAPSSMB;

import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.GlucoseStatus;
import info.nightscout.androidaps.data.MealData;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.interfaces.APSInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.IobCobCalculator.AutosensResult;
import info.nightscout.androidaps.plugins.IobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.Loop.APSResult;
import info.nightscout.androidaps.plugins.Loop.ScriptReader;
import info.nightscout.androidaps.plugins.OpenAPSSMB.DetermineBasalResultSMB;
import info.nightscout.androidaps.plugins.OpenAPSSMB.DetermineBasalAdapterSMBJS;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.plugins.OpenAPSMA.events.EventOpenAPSUpdateGui;
import info.nightscout.androidaps.plugins.OpenAPSMA.events.EventOpenAPSUpdateResultGui;
//import info.nightscout.androidaps.plugins.TempTargetRange.TempTargetRangePlugin;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.Profiler;
import info.nightscout.utils.Round;
import info.nightscout.utils.SP;
import info.nightscout.utils.SafeParse;
import info.nightscout.utils.ToastUtils;
import info.nightscout.utils.NSUpload;
// Added by Rumen for SMB
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.db.DatabaseHelper;  
//import info.nightscout.androidaps.Constants;
import java.util.ArrayList; 
import java.util.List;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.interfaces.InsulinInterface;
import android.content.Context;
import android.view.ViewGroup;
import android.app.Activity;
// Added by Rumen for testing
import org.junit.Test;
import static org.junit.Assert.*;


/**
 * Created by mike on 05.08.2016.
 */
public class OpenAPSSMBPlugin implements PluginBase, APSInterface {
    public OpenAPSSMBPlugin(){
        super();
    }

    private static Logger log = LoggerFactory.getLogger(OpenAPSSMBPlugin.class);

    // last values
    DetermineBasalAdapterSMBJS lastDetermineBasalAdapterAMAJS = null;
    Date lastAPSRun = null;
    DetermineBasalResultSMB lastAPSResult = null;
    AutosensResult lastAutosensResult = null;

    boolean fragmentEnabled = false;
    boolean fragmentVisible = true;
	// Added by Rumen on 19.05.2017
	Double smb = Double.valueOf(0);
    //boolean smbDone = this.bolusSMB(0.5);
    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.openapssmb);
    }

    @Override
    public String getNameShort() {
        String name = MainApp.sResources.getString(R.string.smb_shortname);
        if (!name.trim().isEmpty()){
            //only if translation exists
            return name;
        }
        // use long name as fallback
        return getName();
    }

    @Override
    public boolean isEnabled(int type) {
        return type == APS && fragmentEnabled && MainApp.getConfigBuilder().getPumpDescription().isTempBasalCapable;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return type == APS && fragmentVisible && MainApp.getConfigBuilder().getPumpDescription().isTempBasalCapable;
    }

    @Override
    public boolean canBeHidden(int type) {
        return true;
    }

    @Override
    public boolean hasFragment() {
        return true;
    }

    @Override
    public boolean showInList(int type) {
        return true;
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        if (type == APS) this.fragmentVisible = fragmentVisible;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        if (type == APS) this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public int getType() {
        return PluginBase.APS;
    }

    @Override
    public String getFragmentClass() {
        return OpenAPSSMBFragment.class.getName();
    }

    @Override
    public APSResult getLastAPSResult() {
        return lastAPSResult;
    }
    //@Override
    public Double getSMB() {
        return smb;
    }
	
    @Override
    public Date getLastAPSRun() {
        return lastAPSRun;
    }

    @Override
    public void invoke(String initiator) {
        log.debug("invoke from " + initiator);
		
        lastAPSResult = null;
        DetermineBasalAdapterSMBJS determineBasalAdapterAMAJS = null;
        try {
            determineBasalAdapterAMAJS = new DetermineBasalAdapterSMBJS(new ScriptReader(MainApp.instance().getBaseContext()));
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            return;
        }

        GlucoseStatus glucoseStatus = GlucoseStatus.getGlucoseStatusData();
        Profile profile = MainApp.getConfigBuilder().getProfile();
        PumpInterface pump = MainApp.getConfigBuilder();

        if (!isEnabled(PluginBase.APS)) {
            MainApp.bus().post(new EventOpenAPSUpdateResultGui(MainApp.instance().getString(R.string.openapsma_disabled)));
            if (Config.logAPSResult)
                log.debug(MainApp.instance().getString(R.string.openapsma_disabled));
            return;
        }

        if (glucoseStatus == null) {
            MainApp.bus().post(new EventOpenAPSUpdateResultGui(MainApp.instance().getString(R.string.openapsma_noglucosedata)));
            if (Config.logAPSResult)
                log.debug(MainApp.instance().getString(R.string.openapsma_noglucosedata));
            return;
        }

        if (profile == null || profile.getIc(Profile.secondsFromMidnight()) == null || profile.getIsf(Profile.secondsFromMidnight()) == null || profile.getBasal(Profile.secondsFromMidnight()) == null ) {
            MainApp.bus().post(new EventOpenAPSUpdateResultGui(MainApp.instance().getString(R.string.openapsma_noprofile)));
            if (Config.logAPSResult)
                log.debug(MainApp.instance().getString(R.string.openapsma_noprofile));
            return;
        }

        if (pump == null) {
            MainApp.bus().post(new EventOpenAPSUpdateResultGui(MainApp.instance().getString(R.string.openapsma_nopump)));
            if (Config.logAPSResult)
                log.debug(MainApp.instance().getString(R.string.openapsma_nopump));
            return;
        }

        String units = profile.getUnits();

        Double maxBgDefault = Constants.MAX_BG_DEFAULT_MGDL;
        Double minBgDefault = Constants.MIN_BG_DEFAULT_MGDL;
        Double targetBgDefault = Constants.TARGET_BG_DEFAULT_MGDL;
        if (!units.equals(Constants.MGDL)) {
            maxBgDefault = Constants.MAX_BG_DEFAULT_MMOL;
            minBgDefault = Constants.MIN_BG_DEFAULT_MMOL;
            targetBgDefault = Constants.TARGET_BG_DEFAULT_MMOL;
        }

        Date now = new Date();

        double maxIob = SP.getDouble("openapsma_max_iob", 1.5d);
        double maxBasal = SP.getDouble("openapsma_max_basal", 1d);
        double minBg = Profile.toMgdl(SP.getDouble("openapsma_min_bg", minBgDefault), units);
        double maxBg = Profile.toMgdl(SP.getDouble("openapsma_max_bg", maxBgDefault), units);
        double targetBg = Profile.toMgdl(SP.getDouble("openapsma_target_bg", targetBgDefault), units);

        minBg = Round.roundTo(minBg, 0.1d);
        maxBg = Round.roundTo(maxBg, 0.1d);

        Date start = new Date();
        Date startPart = new Date();
        IobTotal[] iobArray = IobCobCalculatorPlugin.calculateIobArrayInDia();
        Profiler.log(log, "calculateIobArrayInDia()", startPart);

        startPart = new Date();
        MealData mealData = MainApp.getConfigBuilder().getMealData();
        Profiler.log(log, "getMealData()", startPart);

        maxIob = MainApp.getConfigBuilder().applyMaxIOBConstraints(maxIob);

        minBg = verifyHardLimits(minBg, "minBg", Constants.VERY_HARD_LIMIT_MIN_BG[0], Constants.VERY_HARD_LIMIT_MIN_BG[1]);
        maxBg = verifyHardLimits(maxBg, "maxBg", Constants.VERY_HARD_LIMIT_MAX_BG[0], Constants.VERY_HARD_LIMIT_MAX_BG[1]);
        targetBg = verifyHardLimits(targetBg, "targetBg", Constants.VERY_HARD_LIMIT_TARGET_BG[0], Constants.VERY_HARD_LIMIT_TARGET_BG[1]);

        boolean isTempTarget = false;
        TempTarget tempTarget = MainApp.getConfigBuilder().getTempTargetFromHistory(new Date().getTime());
        if (tempTarget != null) {
            isTempTarget = true;
            minBg = verifyHardLimits(tempTarget.low, "minBg", Constants.VERY_HARD_LIMIT_TEMP_MIN_BG[0], Constants.VERY_HARD_LIMIT_TEMP_MIN_BG[1]);
            maxBg = verifyHardLimits(tempTarget.high, "maxBg", Constants.VERY_HARD_LIMIT_TEMP_MAX_BG[0], Constants.VERY_HARD_LIMIT_TEMP_MAX_BG[1]);
            targetBg = verifyHardLimits((tempTarget.low + tempTarget.high) / 2, "targetBg", Constants.VERY_HARD_LIMIT_TEMP_TARGET_BG[0], Constants.VERY_HARD_LIMIT_TEMP_TARGET_BG[1]);
        }

        maxIob = verifyHardLimits(maxIob, "maxIob", 0, 7);
        maxBasal = verifyHardLimits(maxBasal, "max_basal", 0.1, 10);

        if (!checkOnlyHardLimits(profile.getDia(), "dia", 2, 7)) return;
        if (!checkOnlyHardLimits(profile.getIc(profile.secondsFromMidnight()), "carbratio", 2, 100)) return;
        if (!checkOnlyHardLimits(Profile.toMgdl(profile.getIsf(Profile.secondsFromMidnight()).doubleValue(), units), "sens", 2, 900)) return;
        if (!checkOnlyHardLimits(profile.getMaxDailyBasal(), "max_daily_basal", 0.1, 10)) return;
        if (!checkOnlyHardLimits(pump.getBaseBasalRate(), "current_basal", 0.01, 5)) return;

        long oldestDataAvailable = MainApp.getConfigBuilder().oldestDataAvailable();
        long getBGDataFrom = Math.max(oldestDataAvailable, (long) (new Date().getTime() - 60 * 60 * 1000L * (24 + profile.getDia())));
        log.debug("Limiting data to oldest available temps: " + new Date(oldestDataAvailable).toString());

        startPart = new Date();
        if(MainApp.getConfigBuilder().isAMAModeEnabled()){
            //lastAutosensResult = Autosens.detectSensitivityandCarbAbsorption(getBGDataFrom, null);
            lastAutosensResult = IobCobCalculatorPlugin.detectSensitivityWithLock(IobCobCalculatorPlugin.oldestDataAvailable(), System.currentTimeMillis()); 
        } else {
            lastAutosensResult = new AutosensResult();
        }
        Profiler.log(log, "detectSensitivityandCarbAbsorption()", startPart);
        Profiler.log(log, "AMA data gathering", start);

        start = new Date();
        determineBasalAdapterAMAJS.setData(profile, maxIob, maxBasal, minBg, maxBg, targetBg, pump, iobArray, glucoseStatus, mealData,
                lastAutosensResult.ratio, //autosensDataRatio
                isTempTarget,
                SafeParse.stringToDouble(SP.getString("openapsama_min_5m_carbimpact", "3.0"))//min_5m_carbimpact
                );


        DetermineBasalResultSMB determineBasalResultAMA = determineBasalAdapterAMAJS.invoke();
        Profiler.log(log, "SMB calculation", start);
        // Fix bug determine basal
        if (determineBasalResultAMA.rate == 0d && determineBasalResultAMA.duration == 0 && !MainApp.getConfigBuilder().isTempBasalInProgress())
            determineBasalResultAMA.changeRequested = false;
        // limit requests on openloop mode
        if (!MainApp.getConfigBuilder().isClosedModeEnabled()) {
            if (MainApp.getConfigBuilder().isTempBasalInProgress() && Math.abs(determineBasalResultAMA.rate - MainApp.getConfigBuilder().getTempBasalAbsoluteRateHistory()) < 0.1)
                determineBasalResultAMA.changeRequested = false;
            if (!MainApp.getConfigBuilder().isTempBasalInProgress() && Math.abs(determineBasalResultAMA.rate - MainApp.getConfigBuilder().getBaseBasalRate()) < 0.1)
                determineBasalResultAMA.changeRequested = false;
        }
		
		
        determineBasalResultAMA.iob = iobArray[0];
		
        determineBasalAdapterAMAJS.release();

        try {
            determineBasalResultAMA.json.put("timestamp", DateUtil.toISOString(now));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        lastDetermineBasalAdapterAMAJS = determineBasalAdapterAMAJS;
        lastAPSResult = determineBasalResultAMA;
        lastAPSRun = now;
		// Trying here
		smb = smbValue(lastAPSResult);
        MainApp.bus().post(new EventOpenAPSUpdateGui());
		// call smbValue()
		//smb = smbValue();
		//if(bolusSMB(smb)){
			// SMB bolused
		//}
		//deviceStatus.suggested = determineBasalResultAMA.json;
    }

	//added by Rumen
	public double smbValue(DetermineBasalResultSMB APSResult){
		// Added by Rumen on 19.05.2017
		// Trying to add SMB calculations here
		boolean SMB_enable = false;
		if(SP.getBoolean("key_smb", false)){
			SMB_enable = true;
		}

		IobTotal bolusIob = MainApp.getConfigBuilder().getCalculationToTimeTreatments(new Date().getTime()).round();
		IobTotal basalIob = new IobTotal(new Date().getTime());
		double maxIob = SP.getDouble("openapsma_max_iob", 1.5d);
		double iob_difference = maxIob - bolusIob.iob;
		//APSResult lastAPSResult = null;
		//APSInterface usedAPS = ConfigBuilderPlugin.getActiveAPS();
		
		
		
		//double lastAPSRate = lastAPSRate;
		Profile profile = MainApp.getConfigBuilder().getProfile();

		//Calculate SMB - getting the smallest of IOB left and check for negative SMB value
		double smb_value;
		smb_value = iob_difference;
		
		if(lastAPSResult != null){
			if(smb_value > APSResult.rate/6){
				smb_value = APSResult.rate/6;
			}
		}
		
		

		if(smb_value> (profile.getBasal(Profile.secondsFromMidnight())/2)){
			smb_value = profile.getBasal(Profile.secondsFromMidnight()) / 2;
		}
		if(smb_value<0.1){
			smb_value = 0.0;
		}

		// get time of last BG 
		TreatmentsInterface activeTreatments;
		GlucoseStatus glucoseStatus = GlucoseStatus.getGlucoseStatusData();
		double lastBG = GlucoseStatus.getGlucoseStatusData().glucose;
		Double bg = Profile.fromMgdlToUnits(GlucoseStatus.getGlucoseStatusData() != null ? GlucoseStatus.getGlucoseStatusData().glucose : 0d, profile != null ? profile.getUnits() : Constants.MGDL); 
		BgReading lastBGReading = DatabaseHelper.actualBg(); 
		Long agoMsec = new Date().getTime() - lastBGReading.date;
		int agoMin = (int) (agoMsec / 60d / 1000d);
		//Get if there is a treatment for last 5 minutes
		boolean treamentExists = false;
		TreatmentsInterface treatmentsInterface = MainApp.getConfigBuilder(); 
		List<Treatment> recentTreatments = new ArrayList<Treatment>(); 
		recentTreatments = MainApp.getConfigBuilder().getTreatments5MinBackFromHistory(new Date().getTime());
		//List<Treatment> recentTreatments = new List<Treatment>;
		//recentTreatments = treatmentsInterface.getTreatments5MinBackFromHistory(new Date().getTime());
		if(recentTreatments.size() != 0){
			// There is treatment
			treamentExists = true;
		}
		//Getting COB
		MealData mealData = MainApp.getConfigBuilder().getMealData();
        //Check for COB available
		if(mealData.mealCOB > 0){
			// Check for positive delta (BG is rising)
			if(glucoseStatus.delta > 0){
				// Check for bolusIOB is that needed ?!?
				if(bolusIob.iob != 0){
					if((agoMin-5) < 5 && treamentExists){
						//There is a treatment less than 5 minutes ago so disable SMB to prevent double-triple bolusing
						SMB_enable = false;
					}
					// SMB is positive and enabled and no other treatmnt has been done so setting a value
					if(smb_value>0 && SMB_enable && !treamentExists){
						return smb_value;
					} return 0;
				} return 0;// there is bolusIob.iob
			} return 0;// delta is 0 or negative
		} return 0;// No cob */
	}
	public boolean bolusSMB(double smbValue){
		// If we get a positive smbValue, do some checks and do a bolus (return true)
		boolean SMB_enable = false;
		if(SP.getBoolean("key_smb", false)){
			SMB_enable = true;
		} else if(smbValue<0.1){
			return false;
		} else {
			// Set temp basal of 0 for 120 minutes and disable loop
			PumpEnactResult result;
			final PumpInterface pump = MainApp.getConfigBuilder();
			result = pump.setTempBasalPercent(0, 120);
			if (result.success) {
				
				final Context context = MainApp.instance().getApplicationContext();
				Integer nullCarbs = 0;
				Double smbFinalValue = smbValue;
				//JUST TO TEST INTERFACE
				//if(smb_value == 0){ smbFinalValue = 0.1;}

				InsulinInterface insulin = ConfigBuilderPlugin.getActiveInsulin();
				// Deliver smbFinalValue but not working in 1.50
				//result = pump.deliverTreatment(insulin, smbFinalValue, nullCarbs, context);
				if(result.success) return true;
			}
			return false;
		}
		return false;
	}
    // safety checks
    public static boolean checkOnlyHardLimits(Double value, String valueName, double lowLimit, double highLimit) {
        return value.equals(verifyHardLimits(value, valueName, lowLimit, highLimit));
    }
	//added by Rumen
	public Double smbValue(){
		DetermineBasalResultSMB lastAPSResult = this.lastAPSResult;
		if(lastAPSResult == null) return -0.3; else return lastAPSResult.smbValue;
	}
    public static Double verifyHardLimits(Double value, String valueName, double lowLimit, double highLimit) {
        Double newvalue = value;
        if (newvalue < lowLimit || newvalue > highLimit) {
            newvalue = Math.max(newvalue, lowLimit);
            newvalue = Math.min(newvalue, highLimit);
            String msg = String.format(MainApp.sResources.getString(R.string.openapsma_valueoutofrange), valueName);
            msg += ".\n";
            msg += String.format(MainApp.sResources.getString(R.string.openapsma_valuelimitedto), value, newvalue);
            log.error(msg);
            NSUpload.uploadError(msg);
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), msg, R.raw.error);
        }
        return newvalue;
    }

}
