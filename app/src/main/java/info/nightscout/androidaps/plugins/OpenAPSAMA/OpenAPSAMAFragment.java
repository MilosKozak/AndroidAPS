package info.nightscout.androidaps.plugins.OpenAPSAMA;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.squareup.otto.Subscribe;

import org.json.JSONArray;
import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.OpenAPSMA.events.EventOpenAPSUpdateGui;
import info.nightscout.androidaps.plugins.OpenAPSMA.events.EventOpenAPSUpdateResultGui;
import info.nightscout.utils.JSONFormatter;
//added for SMB calculations
import java.util.Date;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.androidaps.data.GlucoseStatus;
import info.nightscout.androidaps.data.MealData;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import java.util.List;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSProfile;
import info.nightscout.androidaps.plugins.Loop.LoopPlugin;
import info.nightscout.utils.SP;
import android.content.Context;
// for testing the pump interfaces
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.interfaces.InsulinInterface;




public class OpenAPSAMAFragment extends Fragment implements View.OnClickListener {
    //needed for SMB calculations
	public IobTotal iobTotal;
	private static Logger log = LoggerFactory.getLogger(OpenAPSAMAFragment.class);

    private static OpenAPSAMAPlugin openAPSAMAPlugin;

    public static OpenAPSAMAPlugin getPlugin() {
        if(openAPSAMAPlugin ==null){
            openAPSAMAPlugin = new OpenAPSAMAPlugin();
        }
        return openAPSAMAPlugin;
    }

    Button run;
    TextView lastRunView;
    TextView glucoseStatusView;
    TextView currentTempView;
    TextView iobDataView;
    TextView profileView;
    TextView mealDataView;
    TextView autosensDataView;
    TextView resultView;
    TextView scriptdebugView;
	TextView SMB_calc;
    TextView requestView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.openapsama_fragment, container, false);

        run = (Button) view.findViewById(R.id.openapsma_run);
        run.setOnClickListener(this);
        lastRunView = (TextView) view.findViewById(R.id.openapsma_lastrun);
        glucoseStatusView = (TextView) view.findViewById(R.id.openapsma_glucosestatus);
        currentTempView = (TextView) view.findViewById(R.id.openapsma_currenttemp);
        iobDataView = (TextView) view.findViewById(R.id.openapsma_iobdata);
        profileView = (TextView) view.findViewById(R.id.openapsma_profile);
        mealDataView = (TextView) view.findViewById(R.id.openapsma_mealdata);
        autosensDataView = (TextView) view.findViewById(R.id.openapsma_autosensdata);
        scriptdebugView = (TextView) view.findViewById(R.id.openapsma_scriptdebugdata);
		SMB_calc = (TextView) view.findViewById(R.id.openapsma_smb);
        resultView = (TextView) view.findViewById(R.id.openapsma_result);
        requestView = (TextView) view.findViewById(R.id.openapsma_request);

        updateGUI();
        return view;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.openapsma_run:
                getPlugin().invoke("OpenAPSAMA button");
                Answers.getInstance().logCustom(new CustomEvent("OpenAPS_AMA_Run"));
                break;
        }

    }

    @Override
    public void onPause() {
        super.onPause();
        MainApp.bus().unregister(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        MainApp.bus().register(this);
    }

    @Subscribe
    public void onStatusEvent(final EventOpenAPSUpdateGui ev) {
        updateGUI();
    }

    @Subscribe
    public void onStatusEvent(final EventOpenAPSUpdateResultGui ev) {
        updateResultGUI(ev.text);
    }

    void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    DetermineBasalResultAMA lastAPSResult = getPlugin().lastAPSResult;
                    if (lastAPSResult != null) {
                        resultView.setText(JSONFormatter.format(lastAPSResult.json));
                        requestView.setText(lastAPSResult.toSpanned());
                    }
                    DetermineBasalAdapterAMAJS determineBasalAdapterAMAJS = getPlugin().lastDetermineBasalAdapterAMAJS;
                    if (determineBasalAdapterAMAJS != null) {
                        glucoseStatusView.setText(JSONFormatter.format(determineBasalAdapterAMAJS.getGlucoseStatusParam()));
                        currentTempView.setText(JSONFormatter.format(determineBasalAdapterAMAJS.getCurrentTempParam()));
                        try {
                            JSONArray iobArray = new JSONArray(determineBasalAdapterAMAJS.getIobDataParam());
                            iobDataView.setText(String.format(MainApp.sResources.getString(R.string.array_of_elements), iobArray.length()) + "\n" + JSONFormatter.format(iobArray.getString(0)));
                        } catch (JSONException e) {
                            e.printStackTrace();
                            iobDataView.setText("JSONException");
                        }
                        profileView.setText(JSONFormatter.format(determineBasalAdapterAMAJS.getProfileParam()));
                        mealDataView.setText(JSONFormatter.format(determineBasalAdapterAMAJS.getMealDataParam()));
                        scriptdebugView.setText(determineBasalAdapterAMAJS.getScriptDebug());
						// Ok trying some calcs here getting iob, cob, delta - done
						// THIS WORKS ONLY WITH NS PROFILE!!!!
						// Find how to suspend loop and get loop status - Done by setting 0 temp bazal before SMB
						// Get time since last enact, and do not run another smb if time is less than 5 minutes
						// Maybe include SMB_enable in preferences
						boolean SMB_enable = false;
						// Single SMB amounts are limited by several factors.  The largest a single SMB bolus can be is the SMALLEST value of:
						//30 minutes of the current regular basal rate, or
						//1/3 of the Insulin Required amount, or
						//the remaining portion of your max-iob setting in preferences.
						
						
						//get current_basal
						//get max_iob
						IobTotal bolusIob = ConfigBuilderPlugin.getActiveTreatments().getLastCalculation().round();
						IobTotal basalIob = new IobTotal(new Date().getTime());
						double maxIob = SP.getDouble("openapsma_max_iob", 1.5d);
						double iob_difference = maxIob - bolusIob.iob;
						NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
						//Calculate SMB - getting the smallest of IOB left and check for negative SMB value
						double smb_value;
						smb_value = iob_difference;
						if(smb_value > lastAPSResult.rate/6){
							smb_value = lastAPSResult.rate/6;
						}
						if(smb_value> (profile.getBasal(NSProfile.secondsFromMidnight())/2)){
							smb_value = profile.getBasal(NSProfile.secondsFromMidnight()) / 2;
						}
						if(smb_value<0.1){ 
							smb_value = 0.0;
						}
						// get time of last BG 
						GlucoseStatus glucoseStatus = GlucoseStatus.getGlucoseStatusData();
						BgReading lastBG = GlucoseStatus.lastBg();
						Long agoMsec = new Date().getTime() - lastBG.timeIndex;
						int agoMin = (int) (agoMsec / 60d / 1000d);
						
						//Get if there is a treatment for last 5 minutes
						boolean treamentExists = false;
						TreatmentsInterface treatmentsInterface = ConfigBuilderPlugin.getActiveTreatments();
						List<Treatment> recentTreatments;
						recentTreatments = treatmentsInterface.getTreatments5MinBack(new Date().getTime());
						if(recentTreatments.size() != 0){
							// There is treatment 
							treamentExists = true;
						}
						
						// If there will be SMB -> TempBasal 0 ?!? 
						// VERY DANGEROUS !!! just to test pump interfaces setting temp 0 for 120 minutes
						String tempIsSet = "Already set to 0 U/hr. No treatment";
						if(treamentExists){
							tempIsSet = "Already 0 U/hr, but treatment exists";
						} 
						if(!SMB_enable){
							tempIsSet = "SMB disabled! Last BG was "+agoMin+" minutes ago";
						}
								
						//Getting COB 
						MealData mealData = MainApp.getConfigBuilder().getActiveTreatments().getMealData();
						//BgReading lastBG = GlucoseStatus.lastBg(); // This is a duplicate
						
						String iobtext = DecimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob) + "U ("+ getString(R.string.bolus) + ": " + DecimalFormatter.to2Decimal(bolusIob.iob) + "U " + getString(R.string.basal) + ": " + DecimalFormatter.to2Decimal(basalIob.basaliob) + "U)";
						
						if(mealData.mealCOB == 0){
							
							SMB_calc.setText("No COB's left for SMB");
							
						}	
						// check if delta < 0
						// delta is in mg/dl
						else if(glucoseStatus.delta < 0){
							SMB_calc.setText("Temp set: "+tempIsSet+"\nCalculated SMB: "+smb_value+"\nBasal is: "+profile.getBasal(NSProfile.secondsFromMidnight())+"\nMax IOB is: "+maxIob+"\nIOB difference:"+String.format( "%.2f",iob_difference)+"\n1/3 of suggested is:"+String.format( "%.2f", (lastAPSResult.rate/6))+" ("+lastAPSResult.rate+")"+"\nThere are COB left, but delta is " + glucoseStatus.delta+" mg/dl");
							//check for bolusIOB 
						} else if(bolusIob.iob == 0){
							// we have bolus IOB but is it enough to cover COB and delta ?!?
							//calculate delta for next 1 hr ?!?
							SMB_calc.setText("Temp set: "+tempIsSet+"\nCalculated SMB: "+smb_value+"\nBasal is: "+profile.getBasal(NSProfile.secondsFromMidnight())+"\nMax IOB is: "+maxIob+"\nIOB difference:"+String.format( "%.2f",iob_difference)+"\n1/3 of suggested is:"+String.format( "%.2f", (lastAPSResult.rate/6))+" ("+lastAPSResult.rate+")\nSMB_enabled: "+SMB_enable);
						} else {
							SMB_calc.setText("Temp set: "+tempIsSet+"\nCalculated SMB: "+smb_value+"\nBasal is: "+profile.getBasal(NSProfile.secondsFromMidnight())+"\nMax IOB is: "+maxIob+"\nIOB difference:"+String.format( "%.2f",iob_difference)+"\n1/3 of suggested is:"+String.format( "%.2f", (lastAPSResult.rate/6))+" ("+lastAPSResult.rate+")"+"\nMealCOB:"+mealData.mealCOB+"\nBG:"+glucoseStatus.glucose+"\ndelta:"+glucoseStatus.delta*0.1+"\nActive insulin:"+iobtext+"\n APS requested:"+lastAPSResult.rate/2+"\nSMB_enabled: "+SMB_enable);
							if(!SMB_enable){
								tempIsSet = "SMB disabled!";
							}
							if((agoMin-5) < 5 & treamentExists){
								SMB_enable = false;
								tempIsSet = "SMB disabled for next "+(5-agoMin)+" minutes. Treatment exists!";
							}
							//testing how many treatments will that create
							if(smb_value == 0) smb_value = 0.1;
							if(smb_value>0 & SMB_enable & !treamentExists){
								SMB_calc.setText("Entering temp");
								PumpEnactResult result;
								final PumpInterface pump = MainApp.getConfigBuilder();
								result = pump.setTempBasalPercent(0, 120);
								SMB_calc.setText("Cannot set temp of 0");
								if (result.success) {
									SMB_calc.setText("Temp set to 0 for 120 minutes!");
									final Context context = getContext();
									Integer nullCarbs = 0;
									Double smbFinalValue = smb_value;
									//JUST TO TEST INTERFACE
									if(smb_value == 0){ smbFinalValue = 0.1;}
							
									InsulinInterface insulin = ConfigBuilderPlugin.getActiveInsulin();
									result = pump.deliverTreatment(insulin, smbFinalValue, nullCarbs, context);
									if (result.success) {
										SMB_calc.setText("Temp set!SMB done");
									}
								}
								
							}
						}
						
                    }
                    if (getPlugin().lastAPSRun != null) {
                        lastRunView.setText(getPlugin().lastAPSRun.toLocaleString());
                    }
                    if (getPlugin().lastAutosensResult != null) {
                        autosensDataView.setText(JSONFormatter.format(getPlugin().lastAutosensResult.json()));
                    }
                }
            });
    }

    void updateResultGUI(final String text) {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    resultView.setText(text);
                    glucoseStatusView.setText("");
                    currentTempView.setText("");
                    iobDataView.setText("");
                    profileView.setText("");
                    mealDataView.setText("");
                    autosensDataView.setText("");
                    scriptdebugView.setText("");
                    requestView.setText("");
                    lastRunView.setText("");
                }
            });
    }
}
