package info.nightscout.androidaps.plugins.OpenAPSSMB;

import com.eclipsesource.v8.JavaVoidCallback;
import com.eclipsesource.v8.V8;
import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Object;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.GlucoseStatus;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.MealData;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.IobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.Loop.ScriptReader;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.utils.SP;
//added by Rumen for SMB calculations
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import java.util.ArrayList;
import java.util.List;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.DatabaseHelper;  
import info.nightscout.androidaps.Constants;

public class DetermineBasalAdapterSMBJS {
    private static Logger log = LoggerFactory.getLogger(DetermineBasalAdapterSMBJS.class);


    private ScriptReader mScriptReader = null;
    V8 mV8rt;
    private V8Object mProfile;
    private V8Object mGlucoseStatus;
    private V8Array mIobData;
    private V8Object mMealData;
    private V8Object mCurrentTemp;
    private V8Object mAutosensData = null;

    private final String PARAM_currentTemp = "currentTemp";
    private final String PARAM_iobData = "iobData";
    private final String PARAM_glucoseStatus = "glucose_status";
    private final String PARAM_profile = "profile";
    private final String PARAM_meal_data = "meal_data";
    private final String PARAM_autosens_data = "autosens_data";
	private final int PARAM_reservoirData = 100;
	private final boolean PARAM_microBolusAllowed = true;
	

    private String storedCurrentTemp = null;
    private String storedIobData = null;
    private String storedGlucoseStatus = null;
    private String storedProfile = null;
    private String storedMeal_data = null;
    private String storedAutosens_data = null;

    private String scriptDebug = "";

    /**
     * Main code
     */

    public DetermineBasalAdapterSMBJS(ScriptReader scriptReader) throws IOException {
        mV8rt = V8.createV8Runtime();
        mScriptReader = scriptReader;

        initLogCallback();
        initProcessExitCallback();
        initModuleParent();
        loadScript();
    }

    public DetermineBasalResultSMB invoke() {

        log.debug(">>> Invoking detemine_basal_oref1 <<<");
        log.debug("Glucose status: " + (storedGlucoseStatus = mV8rt.executeStringScript("JSON.stringify(" + PARAM_glucoseStatus + ");")));
        log.debug("IOB data:       " + (storedIobData = mV8rt.executeStringScript("JSON.stringify(" + PARAM_iobData + ");")));
        log.debug("Current temp:   " + (storedCurrentTemp = mV8rt.executeStringScript("JSON.stringify(" + PARAM_currentTemp + ");")));
        log.debug("Profile:        " + (storedProfile = mV8rt.executeStringScript("JSON.stringify(" + PARAM_profile + ");")));
        log.debug("Meal data:      " + (storedMeal_data = mV8rt.executeStringScript("JSON.stringify(" + PARAM_meal_data + ");")));
        if (mAutosensData != null)
            log.debug("Autosens data:  " + (storedAutosens_data = mV8rt.executeStringScript("JSON.stringify(" + PARAM_autosens_data + ");")));
        else
            log.debug("Autosens data:  " + (storedAutosens_data = "undefined"));

        mV8rt.executeVoidScript(
                "var rT = determine_basal(" +
                        PARAM_glucoseStatus + ", " +
                        PARAM_currentTemp + ", " +
                        PARAM_iobData + ", " +
                        PARAM_profile + ", " +
                        PARAM_autosens_data + ", " +
                        PARAM_meal_data + ", " +
                        "tempBasalFunctions" + ", " +
						PARAM_microBolusAllowed  + ", " +
						PARAM_reservoirData +
                        ");");


        String ret = mV8rt.executeStringScript("JSON.stringify(rT);");
        log.debug("Result: " + ret);

        V8Object v8ObjectReuslt = mV8rt.getObject("rT");

        DetermineBasalResultSMB result = null;
        try {
            result = new DetermineBasalResultSMB(v8ObjectReuslt, new JSONObject(ret));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return result;
    }

    String getGlucoseStatusParam() {
        return storedGlucoseStatus;
    }

    String getCurrentTempParam() {
        return storedCurrentTemp;
    }

    String getIobDataParam() {
        return storedIobData;
    }

    String getProfileParam() {
        return storedProfile;
    }

    String getMealDataParam() {
        return storedMeal_data;
    }

    String getAutosensDataParam() {
        return storedAutosens_data;
    }

    String getScriptDebug() {
        return scriptDebug;
    }

    private void loadScript() throws IOException {
        mV8rt.executeVoidScript("var round_basal = function round_basal(basal, profile) { return basal; };");
        mV8rt.executeVoidScript("require = function() {return round_basal;};");

        mV8rt.executeVoidScript(readFile("OpenAPSSMB/basal-set-temp.js"), "OpenAPSSMB/basal-set-temp.js ", 0);
        mV8rt.executeVoidScript("var tempBasalFunctions = module.exports;");

        mV8rt.executeVoidScript(
                readFile("OpenAPSSMB/determine-basal.js"),
                "OpenAPSSMB/determine-basal.js",
                0);
        mV8rt.executeVoidScript("var determine_basal = module.exports;");
    }

    private void initModuleParent() {
        mV8rt.executeVoidScript("var module = {\"parent\":Boolean(1)};");
    }

    private void initProcessExitCallback() {
        JavaVoidCallback callbackProccessExit = new JavaVoidCallback() {
            @Override
            public void invoke(V8Object arg0, V8Array parameters) {
                if (parameters.length() > 0) {
                    Object arg1 = parameters.get(0);
                    log.error("ProccessExit " + arg1);
                }
            }
        };
        mV8rt.registerJavaMethod(callbackProccessExit, "proccessExit");
        mV8rt.executeVoidScript("var process = {\"exit\": function () { proccessExit(); } };");
    }

    private void initLogCallback() {
        JavaVoidCallback callbackLog = new JavaVoidCallback() {
            @Override
            public void invoke(V8Object arg0, V8Array parameters) {
                int i = 0;
                String s = "";
                while (i < parameters.length()) {
                    Object arg = parameters.get(i);
                    s += arg + " ";
                    i++;
                }
                if (!s.equals("") && Config.logAPSResult) {
                    log.debug("Script debug: " + s);
                    scriptDebug += s + "\n";
                }
            }
        };
        mV8rt.registerJavaMethod(callbackLog, "log");
        mV8rt.executeVoidScript("var console = {\"log\":log, \"error\":log};");
    }


    public void setData(Profile profile,
                        double maxIob,
                        double maxBasal,
                        double minBg,
                        double maxBg,
                        double targetBg,
                        PumpInterface pump,
                        IobTotal[] iobArray,
                        GlucoseStatus glucoseStatus,
                        MealData mealData,
                        double autosensDataRatio,
                        boolean tempTargetSet,
                        double min_5m_carbimpact) {

        String units = profile.getUnits();

        mProfile = new V8Object(mV8rt);
        mProfile.add("max_iob", maxIob);
        mProfile.add("dia", profile.getDia());
        mProfile.add("type", "current");
        mProfile.add("max_daily_basal", profile.getMaxDailyBasal());
        mProfile.add("max_basal", maxBasal);
        mProfile.add("min_bg", minBg);
        mProfile.add("max_bg", maxBg);
        mProfile.add("target_bg", targetBg);
        mProfile.add("carb_ratio", profile.getIc());
        mProfile.add("sens", Profile.toMgdl(profile.getIsf().doubleValue(), units));
        mProfile.add("max_daily_safety_multiplier", SP.getInt("openapsama_max_daily_safety_multiplier", 3));
        mProfile.add("current_basal_safety_multiplier", SP.getInt("openapsama_current_basal_safety_multiplier", 4));
        mProfile.add("skip_neutral_temps", true);
        mProfile.add("current_basal", pump.getBaseBasalRate());
        mProfile.add("temptargetSet", tempTargetSet);
        mProfile.add("autosens_adjust_targets", SP.getBoolean("openapsama_autosens_adjusttargets", true));
        mProfile.add("min_5m_carbimpact", SP.getDouble("openapsama_min_5m_carbimpact", 3d));
		// Enable SMB only when bolus IOB or COB active
		boolean enableSMB = false;
		// enable SMB with COB and preferences key
		if(mealData.mealCOB > 0 && SP.getBoolean("key_smb", false)){ 
			mProfile.add("enableSMB_with_COB", true);	
			enableSMB = true;
		}						
		// enable SMB with bolus and preferences key
		// TODO Fix correction blusses triggering SMB
		//mIobData = mV8rt.executeArrayScript(IobCobCalculatorPlugin.convertToJSONArray(iobArray).toString());
		IobTotal bolusIob = MainApp.getConfigBuilder().getCalculationToTimeTreatments(new Date().getTime()).round();
		// lines below should get if there's a mealbolus DIA hours ago
		boolean mealBolusLastDia = false;
		List<Treatment> recentTreatments = new ArrayList<Treatment>(); 
		double dia = MainApp.getConfigBuilder() == null ? Constants.defaultDIA : MainApp.getConfigBuilder().getProfile().getDia();
        long fromMills = (long) (System.currentTimeMillis() - 60 * 60 * 1000L * (dia));
        recentTreatments = MainApp.getDbHelper().getTreatmentDataFromTime(fromMills, false);
		log.debug("DIA is:"+dia);
		log.debug("DIA (long):"+(long) dia*3600*1000);
		log.debug("timeback is:"+ fromMills);
		if(recentTreatments.size() != 0){
			log.debug("Got treatments from last DIA: " + dia);
			// There is treatment 
			// check is treatment is mealBolus
			for (int ir = 0; ir < recentTreatments.size(); ir++) {
				if(recentTreatments.get(ir).mealBolus) {
					mealBolusLastDia = true; 
					log.debug("Mealbolus at:"+((System.currentTimeMillis() - recentTreatments.get(ir).date)/(1000*60))+" minutes ago" );
				}
            }

			
		} else {
			// There is no treatment for the last DIA isn't that strange ?!?
		}
		log.debug("Mealbolus for last DIA: " + mealBolusLastDia);
		if(bolusIob.iob > 0 && SP.getBoolean("key_smb", false) && mealBolusLastDia){ 
			mProfile.add("enableSMB_with_bolus", true);
			enableSMB = true;
		}
		// enable SMB with teptarget < 100  and preferences key
		if(tempTargetSet && targetBg < 90 && SP.getBoolean("key_smb", false)){
			mProfile.add("enableSMB_with_temptarget", true);
			enableSMB = true;
		} 
		mProfile.add("enableSMB", enableSMB); // enable smb
		mProfile.add("enableUAM", SP.getBoolean("key_uam", false)); 
        mV8rt.add(PARAM_profile, mProfile);

        mCurrentTemp = new V8Object(mV8rt);
        mCurrentTemp.add("temp", "absolute");
        mCurrentTemp.add("duration", MainApp.getConfigBuilder().getTempBasalRemainingMinutesFromHistory());
        mCurrentTemp.add("rate", MainApp.getConfigBuilder().getTempBasalAbsoluteRateHistory());


        // as we have non default temps longer than 30 mintues
        TemporaryBasal tempBasal = MainApp.getConfigBuilder().getTempBasalFromHistory(System.currentTimeMillis());
        if(tempBasal != null) {
            mCurrentTemp.add("minutesrunning", tempBasal.getRealDuration());
        }

        mV8rt.add(PARAM_currentTemp, mCurrentTemp);
		
        mIobData = mV8rt.executeArrayScript(IobCobCalculatorPlugin.convertToJSONArray(iobArray).toString());
		mV8rt.add(PARAM_iobData, mIobData);
        //Added by rumen to get latest treatment/bolus age
		boolean treamentExists = false;
		TreatmentsInterface treatmentsInterface = MainApp.getConfigBuilder(); 
		
		recentTreatments = MainApp.getConfigBuilder().getTreatments5MinBackFromHistory(System.currentTimeMillis());
		long currTime = new Date().getTime();
		long fiveMinsAgo = currTime - 300000;
		log.debug("currTime is: " + currTime);
		log.debug("5 mins ago : " + fiveMinsAgo);
		if(recentTreatments.size() != 0){
			// There is treatment 
			mIobData.add("lastBolusTime", currTime);
		} else currTime = fiveMinsAgo;
		//Added by rumen to get latest treatment/bolus age
		
        mGlucoseStatus = new V8Object(mV8rt);
        mGlucoseStatus.add("glucose", glucoseStatus.glucose);

        if(SP.getBoolean("always_use_shortavg", false)){
            mGlucoseStatus.add("delta", glucoseStatus.short_avgdelta);
        } else {
            mGlucoseStatus.add("delta", glucoseStatus.delta);
        }
        mGlucoseStatus.add("short_avgdelta", glucoseStatus.short_avgdelta);
        mGlucoseStatus.add("long_avgdelta", glucoseStatus.long_avgdelta);
        mV8rt.add(PARAM_glucoseStatus, mGlucoseStatus);

        mMealData = new V8Object(mV8rt);
        mMealData.add("carbs", mealData.carbs);
        mMealData.add("boluses", mealData.boluses);
        mMealData.add("mealCOB", mealData.mealCOB);
		//adding minDeviationSlope
		BgReading lastBGReading = DatabaseHelper.actualBg(); 
		long bgTime = lastBGReading.date;
		double avgDelta = (glucoseStatus.short_avgdelta+glucoseStatus.long_avgdelta)/2;
		IobTotal iob = IobCobCalculatorPlugin.calulateFromTreatmentsAndTemps(bgTime);
		double bgi = -iob.activity * Profile.toMgdl(profile.getIsf(bgTime), profile.getUnits()) * 5;
		double currentDeviation = ((glucoseStatus.delta - bgi)*1000)/1000; 
		double avgDeviation = 0d;
		double deviationSlope = 0d;
		double minDeviationSlope = 0d;
		if (mealData.ciTime > bgTime) {
			avgDeviation = ((avgDelta-bgi)*1000)/1000;
			deviationSlope = (avgDeviation-currentDeviation)/(bgTime-mealData.ciTime)*1000*60*5;
			if (avgDeviation > 0) {
				minDeviationSlope = Math.min(0, deviationSlope);
			} 
		}
		
		
		//Added by Rumen and changed determine-basal to get it from mealData not IOB_data
		mMealData.add("lastBolusTime", currTime);
		mMealData.add("ciTime", mealData.ciTime);
		mMealData.add("minDeviationSlope", minDeviationSlope);
        mV8rt.add(PARAM_meal_data, mMealData);
		mV8rt.add("microbolusallowed", PARAM_microBolusAllowed);
		mV8rt.add("reservoir_data", PARAM_reservoirData);
		
        if (MainApp.getConfigBuilder().isAMAModeEnabled()) {
            mAutosensData = new V8Object(mV8rt);
            mAutosensData.add("ratio", autosensDataRatio);
            mV8rt.add(PARAM_autosens_data, mAutosensData);
        } else {
            mV8rt.addUndefined(PARAM_autosens_data);
        }
    }


    public void release() {
        mProfile.release();
        mCurrentTemp.release();
        mIobData.release();
        mMealData.release();
        mGlucoseStatus.release();
        if (mAutosensData != null) {
            mAutosensData.release();
        }
        mV8rt.release();
    }

    public String readFile(String filename) throws IOException {
        byte[] bytes = mScriptReader.readFile(filename);
        String string = new String(bytes, "UTF-8");
        if (string.startsWith("#!/usr/bin/env node")) {
            string = string.substring(20);
        }
        return string;
    }

}
