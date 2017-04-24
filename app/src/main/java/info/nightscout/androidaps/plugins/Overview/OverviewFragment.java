package info.nightscout.androidaps.plugins.Overview;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.ContextMenu;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.LabelFormatter;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.PointsGraphSeries;
import com.squareup.otto.Subscribe;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.GlucoseStatus;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.TempBasal;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.events.EventInitializationChanged;
import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.events.EventNewBasalProfile;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.events.EventRefreshGui;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.events.EventTreatmentChange;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.Careportal.Dialogs.NewNSTreatmentDialog;
import info.nightscout.androidaps.plugins.Careportal.OptionsToShow;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.ConstraintsObjectives.ObjectivesPlugin;
import info.nightscout.androidaps.plugins.Loop.LoopPlugin;
import info.nightscout.androidaps.plugins.Loop.events.EventNewOpenLoopNotification;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSProfile;
import info.nightscout.androidaps.plugins.OpenAPSAMA.DetermineBasalResultAMA;
import info.nightscout.androidaps.plugins.OpenAPSAMA.OpenAPSAMAPlugin;
import info.nightscout.androidaps.plugins.Overview.Dialogs.CalibrationDialog;
import info.nightscout.androidaps.plugins.Overview.Dialogs.NewTreatmentDialog;
import info.nightscout.androidaps.plugins.Overview.Dialogs.WizardDialog;
import info.nightscout.androidaps.plugins.Overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.AreaGraphSeries;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.DoubleDataPoint;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.PointsWithLabelGraphSeries;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.TimeAsXAxisLabelFormatter;
import info.nightscout.androidaps.plugins.SourceXdrip.SourceXdripPlugin;
import info.nightscout.androidaps.plugins.TempTargetRange.TempTargetRangePlugin;
import info.nightscout.androidaps.plugins.TempTargetRange.events.EventTempTargetRangeChange;
import info.nightscout.utils.BolusWizard;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.Round;
import info.nightscout.utils.SP;
import info.nightscout.utils.ToastUtils;


public class OverviewFragment extends Fragment implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {
    private static Logger log = LoggerFactory.getLogger(OverviewFragment.class);

    private static OverviewPlugin overviewPlugin = new OverviewPlugin();

    public static OverviewPlugin getPlugin() {
        return overviewPlugin;
    }

    TextView bgView;
    TextView arrowView;
    TextView timeAgoView;
    TextView deltaView;
    TextView avgdeltaView;
    TextView runningTempView;
    TextView baseBasalView;
    LinearLayout basalLayout;
    TextView activeProfileView;
    TextView iobView;
    TextView apsModeView;
    TextView tempTargetView;
    TextView pumpStatusView;
    LinearLayout loopStatusLayout;
    LinearLayout pumpStatusLayout;
    GraphView bgGraph;
    GraphView iobGraph;
    RelativeLayout iobGraphLayout;

    CheckBox showPredictionView;
    CheckBox showBasalsView;
    CheckBox showIobView;
    CheckBox showCobView;

    RecyclerView notificationsView;
    LinearLayoutManager llm;

    LinearLayout cancelTempLayout;
    LinearLayout acceptTempLayout;
    Button cancelTempButton;
    Button treatmentButton;
    Button wizardButton;
    Button calibrationButton;
    Button acceptTempButton;
    Button quickWizardButton;

    Handler sLoopHandler = new Handler();
    Runnable sRefreshLoop = null;

    private static Handler sHandler;
    private static HandlerThread sHandlerThread;

    public OverviewFragment() {
        super();
        if (sHandlerThread == null) {
            sHandlerThread = new HandlerThread(OverviewFragment.class.getSimpleName());
            sHandlerThread.start();
            sHandler = new Handler(sHandlerThread.getLooper());
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.overview_fragment, container, false);

        bgView = (TextView) view.findViewById(R.id.overview_bg);
        arrowView = (TextView) view.findViewById(R.id.overview_arrow);
        timeAgoView = (TextView) view.findViewById(R.id.overview_timeago);
        deltaView = (TextView) view.findViewById(R.id.overview_delta);
        avgdeltaView = (TextView) view.findViewById(R.id.overview_avgdelta);
        runningTempView = (TextView) view.findViewById(R.id.overview_runningtemp);
        baseBasalView = (TextView) view.findViewById(R.id.overview_basebasal);
        basalLayout = (LinearLayout) view.findViewById(R.id.overview_basallayout);
        activeProfileView = (TextView) view.findViewById(R.id.overview_activeprofile);
        pumpStatusView = (TextView) view.findViewById(R.id.overview_pumpstatus);
        loopStatusLayout = (LinearLayout) view.findViewById(R.id.overview_looplayout);
        pumpStatusLayout = (LinearLayout) view.findViewById(R.id.overview_pumpstatuslayout);

        iobView = (TextView) view.findViewById(R.id.overview_iob);
        apsModeView = (TextView) view.findViewById(R.id.overview_apsmode);
        tempTargetView = (TextView) view.findViewById(R.id.overview_temptarget);

        bgGraph = (GraphView) view.findViewById(R.id.overview_bggraph);
        iobGraph = (GraphView) view.findViewById(R.id.overview_iobgraph);
        iobGraphLayout = (RelativeLayout) view.findViewById(R.id.overview_iobgraphlayout);

        cancelTempButton = (Button) view.findViewById(R.id.overview_canceltempbutton);
        cancelTempButton.setOnClickListener(this);
        treatmentButton = (Button) view.findViewById(R.id.overview_treatmentbutton);
        treatmentButton.setOnClickListener(this);
        wizardButton = (Button) view.findViewById(R.id.overview_wizardbutton);
        wizardButton.setOnClickListener(this);
        cancelTempButton = (Button) view.findViewById(R.id.overview_canceltempbutton);
        cancelTempButton.setOnClickListener(this);
        acceptTempButton = (Button) view.findViewById(R.id.overview_accepttempbutton);
        acceptTempButton.setOnClickListener(this);
        quickWizardButton = (Button) view.findViewById(R.id.overview_quickwizardbutton);
        quickWizardButton.setOnClickListener(this);
        calibrationButton = (Button) view.findViewById(R.id.overview_calibrationbutton);
        calibrationButton.setOnClickListener(this);

        cancelTempLayout = (LinearLayout) view.findViewById(R.id.overview_canceltemplayout);
        acceptTempLayout = (LinearLayout) view.findViewById(R.id.overview_accepttemplayout);

        showPredictionView = (CheckBox) view.findViewById(R.id.overview_showprediction);
        showPredictionView.setOnCheckedChangeListener(this);
        showBasalsView = (CheckBox) view.findViewById(R.id.overview_showbasals);
        showBasalsView.setOnCheckedChangeListener(this);
        showIobView = (CheckBox) view.findViewById(R.id.overview_showiob);
        showIobView.setOnCheckedChangeListener(this);
        showCobView = (CheckBox) view.findViewById(R.id.overview_showcob);
        showCobView.setOnCheckedChangeListener(this);

        notificationsView = (RecyclerView) view.findViewById(R.id.overview_notifications);
        notificationsView.setHasFixedSize(true);
        llm = new LinearLayoutManager(view.getContext());
        notificationsView.setLayoutManager(llm);

        showPredictionView.setChecked(SP.getBoolean("showprediction", false));
        showBasalsView.setChecked(SP.getBoolean("showbasals", false));
        showIobView.setChecked(SP.getBoolean("showiob", false));
        showCobView.setChecked(SP.getBoolean("showcob", false));

        bgGraph.getGridLabelRenderer().setGridColor(Color.rgb(0x75, 0x75, 0x75));
        bgGraph.getGridLabelRenderer().reloadStyles();
        iobGraph.getGridLabelRenderer().setGridColor(Color.rgb(0x75, 0x75, 0x75));
        iobGraph.getGridLabelRenderer().reloadStyles();
        iobGraph.getGridLabelRenderer().setHorizontalLabelsVisible(false);
        bgGraph.getGridLabelRenderer().setLabelVerticalWidth(50);
        iobGraph.getGridLabelRenderer().setLabelVerticalWidth(50);
        iobGraph.getGridLabelRenderer().setNumVerticalLabels(5);

        return view;
    }


    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        final LoopPlugin activeloop = MainApp.getConfigBuilder().getActiveLoop();
        if (activeloop == null)
            return;
        menu.setHeaderTitle(MainApp.sResources.getString(R.string.loop));
        if (activeloop.isEnabled(PluginBase.LOOP)) {
            menu.add(MainApp.sResources.getString(R.string.disableloop));
            if (!activeloop.isSuspended()) {
                menu.add(MainApp.sResources.getString(R.string.suspendloopfor1h));
                menu.add(MainApp.sResources.getString(R.string.suspendloopfor2h));
                menu.add(MainApp.sResources.getString(R.string.suspendloopfor3h));
                menu.add(MainApp.sResources.getString(R.string.suspendloopfor10h));
                menu.add(MainApp.sResources.getString(R.string.disconnectpumpfor30m));
                menu.add(MainApp.sResources.getString(R.string.disconnectpumpfor1h));
                menu.add(MainApp.sResources.getString(R.string.disconnectpumpfor2h));
                menu.add(MainApp.sResources.getString(R.string.disconnectpumpfor3h));
            } else {
                menu.add(MainApp.sResources.getString(R.string.resume));
            }
        }
        if (!activeloop.isEnabled(PluginBase.LOOP))
            menu.add(MainApp.sResources.getString(R.string.enableloop));
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        switch (buttonView.getId()) {
            case R.id.overview_showprediction:
                SP.putBoolean("showprediction", showPredictionView.isChecked());
                updateGUI("onPredictionCheckedChanged");
                break;
            case R.id.overview_showbasals:
                SP.putBoolean("showbasals", showPredictionView.isChecked());
                updateGUI("onBasalsCheckedChanged");
                break;
            case R.id.overview_showiob:
                SP.putBoolean("showiob", showIobView.isChecked());
                updateGUI("onIobCheckedChanged");
                break;
            case R.id.overview_showcob:
                SP.putBoolean("showcob", showCobView.isChecked());
                updateGUI("onCobCheckedChanged");
                break;
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        final LoopPlugin activeloop = MainApp.getConfigBuilder().getActiveLoop();
        if (item.getTitle().equals(MainApp.sResources.getString(R.string.disableloop))) {
            activeloop.setFragmentEnabled(PluginBase.LOOP, false);
            activeloop.setFragmentVisible(PluginBase.LOOP, false);
            MainApp.getConfigBuilder().storeSettings();
            return true;
        } else if (item.getTitle().equals(MainApp.sResources.getString(R.string.enableloop))) {
            activeloop.setFragmentEnabled(PluginBase.LOOP, true);
            activeloop.setFragmentVisible(PluginBase.LOOP, true);
            MainApp.getConfigBuilder().storeSettings();
            return true;
        } else if (item.getTitle().equals(MainApp.sResources.getString(R.string.resume))) {
            activeloop.suspendTo(0L);
            sHandler.post(new Runnable() {
                @Override
                public void run() {
                    MainApp.bus().post(new EventRefreshGui(false));
                    PumpEnactResult result = MainApp.getConfigBuilder().cancelTempBasal();
                    if (!result.success) {
                        ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.tempbasaldeliveryerror));
                    }
                }
            });
            return true;
        } else if (item.getTitle().equals(MainApp.sResources.getString(R.string.suspendloopfor1h))) {
            activeloop.suspendTo(new Date().getTime() + 60L * 60 * 1000);
            return true;
        } else if (item.getTitle().equals(MainApp.sResources.getString(R.string.suspendloopfor2h))) {
            activeloop.suspendTo(new Date().getTime() + 2 * 60L * 60 * 1000);
            return true;
        } else if (item.getTitle().equals(MainApp.sResources.getString(R.string.suspendloopfor3h))) {
            activeloop.suspendTo(new Date().getTime() + 3 * 60L * 60 * 1000);
            return true;
        } else if (item.getTitle().equals(MainApp.sResources.getString(R.string.suspendloopfor10h))) {
            activeloop.suspendTo(new Date().getTime() + 10 * 60L * 60 * 1000);
            return true;
        } else if (item.getTitle().equals(MainApp.sResources.getString(R.string.disconnectpumpfor30m))) {
            activeloop.suspendTo(new Date().getTime() + 30L * 60 * 1000);
            sHandler.post(new Runnable() {
                @Override
                public void run() {
                    PumpEnactResult result = MainApp.getConfigBuilder().setTempBasalAbsolute(0d, 30);
                    if (!result.success) {
                        ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.tempbasaldeliveryerror));
                    }
                }
            });
            return true;
        } else if (item.getTitle().equals(MainApp.sResources.getString(R.string.disconnectpumpfor1h))) {
            activeloop.suspendTo(new Date().getTime() + 1 * 60L * 60 * 1000);
            sHandler.post(new Runnable() {
                @Override
                public void run() {
                    MainApp.bus().post(new EventRefreshGui(false));
                    PumpEnactResult result = MainApp.getConfigBuilder().setTempBasalAbsolute(0d, 60);
                    if (!result.success) {
                        ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.tempbasaldeliveryerror));
                    }
                }
            });
            return true;
        } else if (item.getTitle().equals(MainApp.sResources.getString(R.string.disconnectpumpfor2h))) {
            activeloop.suspendTo(new Date().getTime() + 2 * 60L * 60 * 1000);
            sHandler.post(new Runnable() {
                @Override
                public void run() {
                    MainApp.bus().post(new EventRefreshGui(false));
                    PumpEnactResult result = MainApp.getConfigBuilder().setTempBasalAbsolute(0d, 2 * 60);
                    if (!result.success) {
                        ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.tempbasaldeliveryerror));
                    }
                }
            });
            return true;
        } else if (item.getTitle().equals(MainApp.sResources.getString(R.string.disconnectpumpfor3h))) {
            activeloop.suspendTo(new Date().getTime() + 3 * 60L * 60 * 1000);
            sHandler.post(new Runnable() {
                @Override
                public void run() {
                    MainApp.bus().post(new EventRefreshGui(false));
                    PumpEnactResult result = MainApp.getConfigBuilder().setTempBasalAbsolute(0d, 3 * 60);
                    if (!result.success) {
                        ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.tempbasaldeliveryerror));
                    }
                }
            });
            return true;
        }

        return super.onContextItemSelected(item);
    }

    @Override
    public void onClick(View v) {
        FragmentManager manager = getFragmentManager();
        switch (v.getId()) {
            case R.id.overview_accepttempbutton:
                onClickAcceptTemp();
                break;
            case R.id.overview_quickwizardbutton:
                onClickQuickwizard();
                break;
            case R.id.overview_wizardbutton:
                WizardDialog wizardDialog = new WizardDialog();
                wizardDialog.show(manager, "WizardDialog");
                break;
            case R.id.overview_calibrationbutton:
                CalibrationDialog calibrationDialog = new CalibrationDialog();
                calibrationDialog.show(manager, "CalibrationDialog");
                break;
            case R.id.overview_treatmentbutton:
                NewTreatmentDialog treatmentDialogFragment = new NewTreatmentDialog();
                treatmentDialogFragment.show(manager, "TreatmentDialog");
                break;
            case R.id.overview_canceltempbutton:
                final PumpInterface pump = MainApp.getConfigBuilder();
                if (pump.isTempBasalInProgress()) {
                    sHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            pump.cancelTempBasal();
                            Answers.getInstance().logCustom(new CustomEvent("CancelTemp"));
                        }
                    });
                }
                break;
            case R.id.overview_pumpstatus:
                if (MainApp.getConfigBuilder().isSuspended() || !MainApp.getConfigBuilder().isInitialized())
                    sHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            MainApp.getConfigBuilder().refreshDataFromPump("RefreshClicked");
                        }
                    });
                break;
        }

    }

    private void onClickAcceptTemp() {
        if (ConfigBuilderPlugin.getActiveLoop() != null) {
            ConfigBuilderPlugin.getActiveLoop().invoke("Accept temp button", false);
            final LoopPlugin.LastRun finalLastRun = LoopPlugin.lastRun;
            if (finalLastRun != null && finalLastRun.lastAPSRun != null && finalLastRun.constraintsProcessed.changeRequested) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle(getContext().getString(R.string.confirmation));
                builder.setMessage(getContext().getString(R.string.setbasalquestion) + "\n" + finalLastRun.constraintsProcessed);
                builder.setPositiveButton(getContext().getString(R.string.ok), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        sHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                hideTempRecommendation();
                                PumpEnactResult applyResult = MainApp.getConfigBuilder().applyAPSRequest(finalLastRun.constraintsProcessed);
                                if (applyResult.enacted) {
                                    finalLastRun.setByPump = applyResult;
                                    finalLastRun.lastEnact = new Date();
                                    finalLastRun.lastOpenModeAccept = new Date();
                                    MainApp.getConfigBuilder().uploadDeviceStatus();
                                    ObjectivesPlugin objectivesPlugin = (ObjectivesPlugin) MainApp.getSpecificPlugin(ObjectivesPlugin.class);
                                    if (objectivesPlugin != null) {
                                        objectivesPlugin.manualEnacts++;
                                        objectivesPlugin.saveProgress();
                                    }
                                }
                                updateGUIIfVisible("onClickAcceptTemp");
                            }
                        });
                        Answers.getInstance().logCustom(new CustomEvent("AcceptTemp"));
                    }
                });
                builder.setNegativeButton(getContext().getString(R.string.cancel), null);
                builder.show();
            }
        }
    }

    void onClickQuickwizard() {
        final BgReading actualBg = GlucoseStatus.actualBg();
        if (MainApp.getConfigBuilder() == null || ConfigBuilderPlugin.getActiveProfile() == null) // app not initialized yet
            return;
        final NSProfile profile = ConfigBuilderPlugin.getActiveProfile().getProfile();

        QuickWizard.QuickWizardEntry quickWizardEntry = getPlugin().quickWizard.getActive();
        if (quickWizardEntry != null && actualBg != null) {
            quickWizardButton.setVisibility(View.VISIBLE);
            String text = MainApp.sResources.getString(R.string.bolus) + ": " + quickWizardEntry.buttonText();
            BolusWizard wizard = new BolusWizard();
            wizard.doCalc(profile.getDefaultProfile(), quickWizardEntry.carbs(), 0d, actualBg.valueToUnits(profile.getUnits()), 0d, true, true, false, true);

            final JSONObject boluscalcJSON = new JSONObject();
            try {
                boluscalcJSON.put("eventTime", DateUtil.toISOString(new Date()));
                boluscalcJSON.put("targetBGLow", wizard.targetBGLow);
                boluscalcJSON.put("targetBGHigh", wizard.targetBGHigh);
                boluscalcJSON.put("isf", wizard.sens);
                boluscalcJSON.put("ic", wizard.ic);
                boluscalcJSON.put("iob", -(wizard.insulingFromBolusIOB + wizard.insulingFromBasalsIOB));
                boluscalcJSON.put("bolusiobused", true);
                boluscalcJSON.put("basaliobused", true);
                boluscalcJSON.put("bg", actualBg.valueToUnits(profile.getUnits()));
                boluscalcJSON.put("insulinbg", wizard.insulinFromBG);
                boluscalcJSON.put("insulinbgused", true);
                boluscalcJSON.put("bgdiff", wizard.bgDiff);
                boluscalcJSON.put("insulincarbs", wizard.insulinFromCarbs);
                boluscalcJSON.put("carbs", quickWizardEntry.carbs());
                boluscalcJSON.put("othercorrection", 0d);
                boluscalcJSON.put("insulintrend", wizard.insulinFromTrend);
                boluscalcJSON.put("insulin", wizard.calculatedTotalInsulin);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if (wizard.calculatedTotalInsulin > 0d && quickWizardEntry.carbs() > 0d) {
                DecimalFormat formatNumber2decimalplaces = new DecimalFormat("0.00");
                String confirmMessage = getString(R.string.entertreatmentquestion);

                Double insulinAfterConstraints = MainApp.getConfigBuilder().applyBolusConstraints(wizard.calculatedTotalInsulin);
                Integer carbsAfterConstraints = MainApp.getConfigBuilder().applyCarbsConstraints(quickWizardEntry.carbs());

                confirmMessage += "\n" + getString(R.string.bolus) + ": " + formatNumber2decimalplaces.format(insulinAfterConstraints) + "U";
                confirmMessage += "\n" + getString(R.string.carbs) + ": " + carbsAfterConstraints + "g";

                if (insulinAfterConstraints - wizard.calculatedTotalInsulin != 0 || carbsAfterConstraints != quickWizardEntry.carbs()) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setTitle(MainApp.sResources.getString(R.string.treatmentdeliveryerror));
                    builder.setMessage(getString(R.string.constraints_violation) + "\n" + getString(R.string.changeyourinput));
                    builder.setPositiveButton(MainApp.sResources.getString(R.string.ok), null);
                    builder.show();
                    return;
                }

                final Double finalInsulinAfterConstraints = insulinAfterConstraints;
                final Integer finalCarbsAfterConstraints = carbsAfterConstraints;

                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle(MainApp.sResources.getString(R.string.confirmation));
                builder.setMessage(confirmMessage);
                builder.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        if (finalInsulinAfterConstraints > 0 || finalCarbsAfterConstraints > 0) {
                            final ConfigBuilderPlugin pump = MainApp.getConfigBuilder();
                            sHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    PumpEnactResult result = pump.deliverTreatmentFromBolusWizard(
                                            MainApp.getConfigBuilder().getActiveInsulin(),
                                            getContext(),
                                            finalInsulinAfterConstraints,
                                            finalCarbsAfterConstraints,
                                            actualBg.valueToUnits(profile.getUnits()),
                                            "Manual",
                                            0,
                                            boluscalcJSON
                                    );
                                    if (!result.success) {
                                        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                                        builder.setTitle(MainApp.sResources.getString(R.string.treatmentdeliveryerror));
                                        builder.setMessage(result.comment);
                                        builder.setPositiveButton(MainApp.sResources.getString(R.string.ok), null);
                                        builder.show();
                                    }
                                }
                            });
                            Answers.getInstance().logCustom(new CustomEvent("QuickWizard"));
                        }
                    }
                });
                builder.setNegativeButton(getString(R.string.cancel), null);
                builder.show();
            }
        }

    }

    @Override
    public void onPause() {
        super.onPause();
        MainApp.bus().unregister(this);
        sLoopHandler.removeCallbacksAndMessages(null);
        unregisterForContextMenu(apsModeView);
    }

    @Override
    public void onResume() {
        super.onResume();
        MainApp.bus().register(this);
        sRefreshLoop = new Runnable() {
            @Override
            public void run() {
                updateGUIIfVisible("refreshLoop");
                sLoopHandler.postDelayed(sRefreshLoop, 60 * 1000L);
            }
        };
        sLoopHandler.postDelayed(sRefreshLoop, 60 * 1000L);
        registerForContextMenu(apsModeView);
        updateGUIIfVisible("onResume");
    }

    @Subscribe
    public void onStatusEvent(final EventInitializationChanged ev) {
        updateGUIIfVisible("EventInitializationChanged");
    }

    @Subscribe
    public void onStatusEvent(final EventPreferenceChange ev) {
        updateGUIIfVisible("EventPreferenceChange");
    }

    @Subscribe
    public void onStatusEvent(final EventRefreshGui ev) {
        updateGUIIfVisible("EventRefreshGui");
    }

    @Subscribe
    public void onStatusEvent(final EventTreatmentChange ev) {
        updateGUIIfVisible("EventTreatmentChange");
    }

    @Subscribe
    public void onStatusEvent(final EventTempBasalChange ev) {
        updateGUIIfVisible("EventTempBasalChange");
    }

    @Subscribe
    public void onStatusEvent(final EventNewBG ev) {
        updateGUIIfVisible("EventTempBasalChange");
    }

    @Subscribe
    public void onStatusEvent(final EventNewOpenLoopNotification ev) {
        updateGUIIfVisible("EventNewOpenLoopNotification");
    }

    @Subscribe
    public void onStatusEvent(final EventNewBasalProfile ev) {
        updateGUIIfVisible("EventNewBasalProfile");
    }

    @Subscribe
    public void onStatusEvent(final EventTempTargetRangeChange ev) {
        updateGUIIfVisible("EventTempTargetRangeChange");
    }

    @Subscribe
    public void onStatusEvent(final EventNewNotification n) {
        updateNotifications();
    }

    @Subscribe
    public void onStatusEvent(final EventDismissNotification n) {
        updateNotifications();
    }

    @Subscribe
    public void onStatusEvent(final EventPumpStatusChanged s) {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updatePumpStatus(s.textStatus());
                }
            });
    }

    private void hideTempRecommendation() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    acceptTempLayout.setVisibility(View.GONE);
                }
            });
    }

    private void updateGUIIfVisible(final String from) {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateGUI(from);
                }
            });
    }

    private void updatePumpStatus(String status) {
        PumpInterface pump = MainApp.getConfigBuilder();
        if (!status.equals("")) {
            pumpStatusView.setText(status);
            pumpStatusLayout.setVisibility(View.VISIBLE);
            loopStatusLayout.setVisibility(View.GONE);
        } else {
            wizardButton.setVisibility(View.VISIBLE);
            treatmentButton.setVisibility(View.VISIBLE);
            pumpStatusLayout.setVisibility(View.GONE);
            loopStatusLayout.setVisibility(View.VISIBLE);
        }
    }

    @SuppressLint("SetTextI18n")
    public void updateGUI(String from) {
        log.debug("updateGUI entered from: " + from);
        updateNotifications();
        BgReading actualBG = GlucoseStatus.actualBg();
        BgReading lastBG = GlucoseStatus.lastBg();

        if (MainApp.getConfigBuilder() == null || MainApp.getConfigBuilder().getActiveProfile() == null || MainApp.getConfigBuilder().getActiveProfile().getProfile() == null) {// app not initialized yet
            pumpStatusView.setText(R.string.noprofileset);
            pumpStatusLayout.setVisibility(View.VISIBLE);
            loopStatusLayout.setVisibility(View.GONE);
            return;
        } else {
            pumpStatusLayout.setVisibility(View.GONE);
            loopStatusLayout.setVisibility(View.VISIBLE);
        }

        PumpInterface pump = MainApp.getConfigBuilder();

        // Skip if not initialized yet
        if (bgGraph == null)
            return;

        if (getActivity() == null)
            return;

        // open loop mode
        final LoopPlugin.LastRun finalLastRun = LoopPlugin.lastRun;
        if (Config.APS && MainApp.getConfigBuilder().getPumpDescription().isTempBasalCapable) {
            apsModeView.setVisibility(View.VISIBLE);
            apsModeView.setBackgroundResource(R.drawable.loopmodeborder);
            apsModeView.setTextColor(Color.BLACK);
            final LoopPlugin activeloop = MainApp.getConfigBuilder().getActiveLoop();
            if (activeloop != null && activeloop.isEnabled(activeloop.getType()) && activeloop.isSuperBolus()) {
                apsModeView.setBackgroundResource(R.drawable.loopmodesuspendedborder);
                apsModeView.setText(String.format(MainApp.sResources.getString(R.string.loopsuperbolusfor), activeloop.minutesToEndOfSuspend()));
                apsModeView.setTextColor(Color.WHITE);
            } else if (activeloop != null && activeloop.isEnabled(activeloop.getType()) && activeloop.isSuspended()) {
                apsModeView.setBackgroundResource(R.drawable.loopmodesuspendedborder);
                apsModeView.setText(String.format(MainApp.sResources.getString(R.string.loopsuspendedfor), activeloop.minutesToEndOfSuspend()));
                apsModeView.setTextColor(Color.WHITE);
            } else if (pump.isSuspended()) {
                apsModeView.setBackgroundResource(R.drawable.loopmodesuspendedborder);
                apsModeView.setText(MainApp.sResources.getString(R.string.pumpsuspended));
                apsModeView.setTextColor(Color.WHITE);
            } else if (activeloop != null && activeloop.isEnabled(activeloop.getType())) {
                if (MainApp.getConfigBuilder().isClosedModeEnabled()) {
                    apsModeView.setText(MainApp.sResources.getString(R.string.closedloop));
                } else {
                    apsModeView.setText(MainApp.sResources.getString(R.string.openloop));
                }
            } else {
                apsModeView.setBackgroundResource(R.drawable.loopmodedisabledborder);
                apsModeView.setText(MainApp.sResources.getString(R.string.disabledloop));
                apsModeView.setTextColor(Color.WHITE);
            }
        } else {
            apsModeView.setVisibility(View.GONE);
        }

        // temp target
        NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
        TempTargetRangePlugin tempTargetRangePlugin = (TempTargetRangePlugin) MainApp.getSpecificPlugin(TempTargetRangePlugin.class);
        if (Config.APS && tempTargetRangePlugin != null && tempTargetRangePlugin.isEnabled(PluginBase.GENERAL)) {
            TempTarget tempTarget = tempTargetRangePlugin.getTempTargetInProgress(new Date().getTime());
            if (tempTarget != null) {
                tempTargetView.setTextColor(Color.BLACK);
                tempTargetView.setBackgroundResource(R.drawable.temptargetborder);
                tempTargetView.setVisibility(View.VISIBLE);
                tempTargetView.setText(NSProfile.toUnitsString(tempTarget.low, NSProfile.fromMgdlToUnits(tempTarget.low, profile.getUnits()), profile.getUnits()) + " - " + NSProfile.toUnitsString(tempTarget.high, NSProfile.fromMgdlToUnits(tempTarget.high, profile.getUnits()), profile.getUnits()));
            } else {

                Double maxBgDefault = Constants.MAX_BG_DEFAULT_MGDL;
                Double minBgDefault = Constants.MIN_BG_DEFAULT_MGDL;
                if (!profile.getUnits().equals(Constants.MGDL)) {
                    maxBgDefault = Constants.MAX_BG_DEFAULT_MMOL;
                    minBgDefault = Constants.MIN_BG_DEFAULT_MMOL;
                }
                tempTargetView.setTextColor(Color.WHITE);
                tempTargetView.setBackgroundResource(R.drawable.temptargetborderdisabled);
                tempTargetView.setText(SP.getDouble("openapsma_min_bg", minBgDefault) + " - " + SP.getDouble("openapsma_max_bg", maxBgDefault));
                tempTargetView.setVisibility(View.VISIBLE);
            }
        } else {
            tempTargetView.setVisibility(View.GONE);
        }

        // **** Temp button ****
        boolean showAcceptButton = !MainApp.getConfigBuilder().isClosedModeEnabled(); // Open mode needed
        showAcceptButton = showAcceptButton && finalLastRun != null && finalLastRun.lastAPSRun != null; // aps result must exist
        showAcceptButton = showAcceptButton && (finalLastRun.lastOpenModeAccept == null || finalLastRun.lastOpenModeAccept.getTime() < finalLastRun.lastAPSRun.getTime()); // never accepted or before last result
        showAcceptButton = showAcceptButton && finalLastRun.constraintsProcessed.changeRequested; // change is requested

        if (showAcceptButton && pump.isInitialized() && !pump.isSuspended() && ConfigBuilderPlugin.getActiveLoop() != null) {
            acceptTempLayout.setVisibility(View.VISIBLE);
            acceptTempButton.setText(getContext().getString(R.string.setbasalquestion) + "\n" + finalLastRun.constraintsProcessed);
        } else {
            acceptTempLayout.setVisibility(View.GONE);
        }

        // **** Calibration button ****
        if (MainApp.getSpecificPlugin(SourceXdripPlugin.class).isEnabled(PluginBase.BGSOURCE) && profile != null && GlucoseStatus.actualBg() != null) {
            calibrationButton.setVisibility(View.VISIBLE);
        } else {
            calibrationButton.setVisibility(View.GONE);
        }

        TempBasal activeTemp = pump.getTempBasal();
        if (pump.isTempBasalInProgress()) {
            cancelTempLayout.setVisibility(View.VISIBLE);
            cancelTempButton.setText(MainApp.instance().getString(R.string.cancel) + ": " + activeTemp.toString());
            runningTempView.setVisibility(View.VISIBLE);
            runningTempView.setText(activeTemp.toString());
        } else {
            cancelTempLayout.setVisibility(View.GONE);
            runningTempView.setVisibility(View.GONE);
        }

        if (pump.getPumpDescription().isTempBasalCapable) {
            basalLayout.setVisibility(View.VISIBLE);
            baseBasalView.setText(DecimalFormatter.to2Decimal(pump.getBaseBasalRate()) + " U/h");
        } else {
            basalLayout.setVisibility(View.GONE);
        }

        if (profile != null && profile.getActiveProfile() != null)
            activeProfileView.setText(profile.getActiveProfile());

        activeProfileView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                NewNSTreatmentDialog newDialog = new NewNSTreatmentDialog();
                final OptionsToShow profileswitch = new OptionsToShow(R.id.careportal_profileswitch, R.string.careportal_profileswitch, true, false, false, false, false, false, false, true, false, false);
                profileswitch.executeProfileSwitch = true;
                newDialog.setOptions(profileswitch);
                newDialog.show(getFragmentManager(), "NewNSTreatmentDialog");
                return true;
            }
        });
        activeProfileView.setLongClickable(true);


        tempTargetView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                NewNSTreatmentDialog newTTDialog = new NewNSTreatmentDialog();
                final OptionsToShow temptarget = new OptionsToShow(R.id.careportal_temporarytarget, R.string.careportal_temporarytarget, false, false, false, false, true, false, false, false, false, true);
                temptarget.executeTempTarget = true;
                newTTDialog.setOptions(temptarget);
                newTTDialog.show(getFragmentManager(), "NewNSTreatmentDialog");
                return true;
            }
        });
        tempTargetView.setLongClickable(true);

        // QuickWizard button
        QuickWizard.QuickWizardEntry quickWizardEntry = getPlugin().quickWizard.getActive();
        if (quickWizardEntry != null && lastBG != null && pump.isInitialized() && !pump.isSuspended()) {
            quickWizardButton.setVisibility(View.VISIBLE);
            String text = MainApp.sResources.getString(R.string.bolus) + ": " + quickWizardEntry.buttonText() + " " + DecimalFormatter.to0Decimal(quickWizardEntry.carbs()) + "g";
            BolusWizard wizard = new BolusWizard();
            wizard.doCalc(profile.getDefaultProfile(), quickWizardEntry.carbs(), 0d, lastBG.valueToUnits(profile.getUnits()), 0d, true, true, false, true);
            text += " " + DecimalFormatter.to2Decimal(wizard.calculatedTotalInsulin) + "U";
            quickWizardButton.setText(text);
            if (wizard.calculatedTotalInsulin <= 0)
                quickWizardButton.setVisibility(View.GONE);
        } else
            quickWizardButton.setVisibility(View.GONE);

        String units = profile.getUnits();

        // **** BG value ****
        if (lastBG != null) {
            bgView.setText(lastBG.valueToUnitsToString(profile.getUnits()));
            arrowView.setText(lastBG.directionToSymbol());
            GlucoseStatus glucoseStatus = GlucoseStatus.getGlucoseStatusData();
            if (glucoseStatus != null) {
                deltaView.setText("Δ " + NSProfile.toUnitsString(glucoseStatus.delta, glucoseStatus.delta * Constants.MGDL_TO_MMOLL, units) + " " + units);
                avgdeltaView.setText("øΔ15m: " + NSProfile.toUnitsString(glucoseStatus.short_avgdelta, glucoseStatus.short_avgdelta * Constants.MGDL_TO_MMOLL, units) +
                        "  øΔ40m: " + NSProfile.toUnitsString(glucoseStatus.long_avgdelta, glucoseStatus.long_avgdelta * Constants.MGDL_TO_MMOLL, units));
            } else {
                deltaView.setText("Δ " + MainApp.sResources.getString(R.string.notavailable));
                avgdeltaView.setText("");
            }

            BgReading.units = profile.getUnits();
        } else
            return;

        Integer flag = bgView.getPaintFlags();
        if (actualBG == null) {
            flag |= Paint.STRIKE_THRU_TEXT_FLAG;
        } else
            flag &= ~Paint.STRIKE_THRU_TEXT_FLAG;
        bgView.setPaintFlags(flag);

        Long agoMsec = new Date().getTime() - lastBG.timeIndex;
        int agoMin = (int) (agoMsec / 60d / 1000d);
        timeAgoView.setText(String.format(MainApp.sResources.getString(R.string.minago), agoMin));

        // iob
        ConfigBuilderPlugin.getActiveTreatments().updateTotalIOB();
        IobTotal bolusIob = ConfigBuilderPlugin.getActiveTreatments().getLastCalculation().round();
        IobTotal basalIob = new IobTotal(new Date().getTime());
        if (ConfigBuilderPlugin.getActiveTempBasals() != null) {
            ConfigBuilderPlugin.getActiveTempBasals().updateTotalIOB();
            basalIob = ConfigBuilderPlugin.getActiveTempBasals().getLastCalculation().round();
        }

        String iobtext = getString(R.string.treatments_iob_label_string) + " " + DecimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob) + "U ("
                + getString(R.string.bolus) + ": " + DecimalFormatter.to2Decimal(bolusIob.iob) + "U "
                + getString(R.string.basal) + ": " + DecimalFormatter.to2Decimal(basalIob.basaliob) + "U)";
        iobView.setText(iobtext);

        boolean showPrediction = showPredictionView.isChecked() && finalLastRun != null && finalLastRun.constraintsProcessed.getClass().equals(DetermineBasalResultAMA.class);
        if (MainApp.getSpecificPlugin(OpenAPSAMAPlugin.class) != null && MainApp.getSpecificPlugin(OpenAPSAMAPlugin.class).isEnabled(PluginBase.APS)) {
            showPredictionView.setVisibility(View.VISIBLE);
        } else {
            showPredictionView.setVisibility(View.GONE);
        }

        // ****** GRAPH *******

        // allign to hours
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(new Date().getTime());
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.add(Calendar.HOUR, 1);

        int hoursToFetch;
        long toTime;
        long fromTime;
        long endTime;
        if (showPrediction) {
            int predHours = (int) (Math.ceil(((DetermineBasalResultAMA) finalLastRun.constraintsProcessed).getLatestPredictionsTime() - new Date().getTime()) / (60 * 60 * 1000));
            predHours = Math.min(2, predHours);
            predHours = Math.max(0, predHours);
            hoursToFetch = (int) (6 - predHours);
            toTime = calendar.getTimeInMillis() + 100000; // little bit more to avoid wrong rounding
            fromTime = toTime - hoursToFetch * 60 * 60 * 1000L;
            endTime = toTime + predHours * 60 * 60 * 1000L;
        } else {
            hoursToFetch = 6;
            toTime = calendar.getTimeInMillis() + 100000; // little bit more to avoid wrong rounding
            fromTime = toTime - hoursToFetch * 60 * 60 * 1000L;
            endTime = toTime;
        }

        Double lowLine = SP.getDouble("low_mark", 0d);
        Double highLine = SP.getDouble("high_mark", 0d);

        if (lowLine < 1) {
            lowLine = NSProfile.fromMgdlToUnits(OverviewPlugin.bgTargetLow, units);
        }

        if (highLine < 1) {
            highLine = NSProfile.fromMgdlToUnits(OverviewPlugin.bgTargetHigh, units);
        }

        LineGraphSeries<DataPoint> basalsLineSeries = null;
        LineGraphSeries<DataPoint> baseBasalsSeries = null;
        LineGraphSeries<DataPoint> tempBasalsSeries = null;
        AreaGraphSeries<DoubleDataPoint> areaSeries;
        LineGraphSeries<DataPoint> seriesNow, seriesNow2;
        PointsGraphSeries<BgReading> seriesInRage;
        PointsGraphSeries<BgReading> seriesOutOfRange;
        PointsGraphSeries<BgReading> predSeries;
        PointsWithLabelGraphSeries<Treatment> seriesTreatments;

        // **** TEMP BASALS graph ****
        Double maxBasalValueFound = 0d;

        long now = new Date().getTime();
        if (pump.getPumpDescription().isTempBasalCapable && showBasalsView.isChecked()) {
            List<DataPoint> baseBasalArray = new ArrayList<>();
            List<DataPoint> tempBasalArray = new ArrayList<>();
            List<DataPoint> basalLineArray = new ArrayList<>();
            double lastLineBasal = 0;
            double lastBaseBasal = 0;
            double lastTempBasal = 0;
            for (long time = fromTime; time < now; time += 5 * 60 * 1000L) {
                TempBasal tb = MainApp.getConfigBuilder().getTempBasal(new Date(time));
                double baseBasalValue = profile.getBasal(NSProfile.secondsFromMidnight(new Date(time)));
                double baseLineValue = baseBasalValue;
                double tempBasalValue = 0;
                double basal = 0d;
                if (tb != null) {
                    tempBasalValue = tb.tempBasalConvertedToAbsolute(new Date(time));
                    if (tempBasalValue != lastTempBasal) {
                        tempBasalArray.add(new DataPoint(time, lastTempBasal));
                        tempBasalArray.add(new DataPoint(time, basal = tempBasalValue));
                    }
                    if (lastBaseBasal != 0d) {
                        baseBasalArray.add(new DataPoint(time, lastBaseBasal));
                        baseBasalArray.add(new DataPoint(time, 0d));
                        lastBaseBasal = 0d;
                    }
                } else {
                    if (baseBasalValue != lastBaseBasal) {
                        baseBasalArray.add(new DataPoint(time, lastBaseBasal));
                        baseBasalArray.add(new DataPoint(time, basal = baseBasalValue));
                        lastBaseBasal = baseBasalValue;
                    }
                    if (lastTempBasal != 0) {
                        tempBasalArray.add(new DataPoint(time, lastTempBasal));
                        tempBasalArray.add(new DataPoint(time, 0d));
                    }
                }

                if (baseLineValue != lastLineBasal) {
                    basalLineArray.add(new DataPoint(time, lastLineBasal));
                    basalLineArray.add(new DataPoint(time, baseLineValue));
                }

                lastLineBasal = baseLineValue;
                lastTempBasal = tempBasalValue;
                maxBasalValueFound = Math.max(maxBasalValueFound, basal);
            }
            basalLineArray.add(new DataPoint(now, lastLineBasal));
            baseBasalArray.add(new DataPoint(now, lastBaseBasal));
            tempBasalArray.add(new DataPoint(now, lastTempBasal));

            DataPoint[] baseBasal = new DataPoint[baseBasalArray.size()];
            baseBasal = baseBasalArray.toArray(baseBasal);
            baseBasalsSeries = new LineGraphSeries<>(baseBasal);
            baseBasalsSeries.setDrawBackground(true);
            baseBasalsSeries.setBackgroundColor(Color.argb(200, 0x3F, 0x51, 0xB5));
            baseBasalsSeries.setThickness(0);

            DataPoint[] tempBasal = new DataPoint[tempBasalArray.size()];
            tempBasal = tempBasalArray.toArray(tempBasal);
            tempBasalsSeries = new LineGraphSeries<>(tempBasal);
            tempBasalsSeries.setDrawBackground(true);
            tempBasalsSeries.setBackgroundColor(Color.argb(200, 0x03, 0xA9, 0xF4));
            tempBasalsSeries.setThickness(0);

            DataPoint[] basalLine = new DataPoint[basalLineArray.size()];
            basalLine = basalLineArray.toArray(basalLine);
            basalsLineSeries = new LineGraphSeries<>(basalLine);
            Paint paint = new Paint();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2);
            paint.setPathEffect(new DashPathEffect(new float[]{2, 4}, 0));
            paint.setColor(Color.CYAN);
            basalsLineSeries.setCustomPaint(paint);
        }

        // **** IOB COB graph ****
        LineGraphSeries<DataPoint> iobSeries;
        LineGraphSeries<DataPoint> cobSeries;
        Double maxIobValueFound = 0d;
        Double maxCobValueFound = 0d;
        Long lastTime = 0L;

        if (showIobView.isChecked() || showCobView.isChecked()) {
            List<DataPoint> iobArray = new ArrayList<>();
            List<DataPoint> cobArray = new ArrayList<>();
            for (long time = fromTime; time <= endTime; time += 5 * 60 * 1000L) {
                if (showIobView.isChecked()) {
                    IobTotal iob = IobTotal.calulateFromTreatmentsAndTemps(time);
                    iobArray.add(new DataPoint(time, iob.iob));
                    maxIobValueFound = Math.max(maxIobValueFound, Math.abs(iob.iob));
                }
                if (showCobView.isChecked()) {
                    //MealData mealData = MainApp.getConfigBuilder().getActiveTreatments().getMealData();
                    //cobArray.add(new DataPoint(time, mealData.mealCOB));
                    //maxCobValueFound = Math.max(maxCobValueFound, mealData.mealCOB);
                }
                lastTime = time;
            }
            iobArray.add(new DataPoint(lastTime, 0)); // close the path
            iobArray.add(new DataPoint(fromTime, 0)); // close the path
            DataPoint[] iobData = new DataPoint[iobArray.size()];
            iobData = iobArray.toArray(iobData);
            iobSeries = new LineGraphSeries<>(iobData);
            iobSeries.setDrawBackground(true);
            iobSeries.setBackgroundColor(0x80FFFFFF & MainApp.sResources.getColor(R.color.ioborange)); //50%
            iobSeries.setColor(MainApp.sResources.getColor(R.color.ioborange));
            iobSeries.setThickness(3);
            iobSeries.setTitle("IOB");
            iobGraph.getGridLabelRenderer().setVerticalLabelsAlign(Paint.Align.LEFT);

            DataPoint[] cobData = new DataPoint[cobArray.size()];
            cobData = cobArray.toArray(cobData);
            cobSeries = new LineGraphSeries<>(cobData);
            cobSeries.setDrawBackground(true);
            cobSeries.setBackgroundColor(Color.RED);
            cobSeries.setThickness(0);

            iobGraph.removeAllSeries();

            if (showIobView.isChecked()) {
                iobGraph.addSeries(iobSeries);
            }
            if (showCobView.isChecked()) {
                iobGraph.getSecondScale().addSeries(cobSeries);
                iobGraph.getSecondScale().setLabelFormatter(new LabelFormatter() {
                    @Override
                    public String formatLabel(double value, boolean isValueX) {
                        return "";
                    }

                    @Override
                    public void setViewport(Viewport viewport) {

                    }
                });
            }
            iobGraphLayout.setVisibility(View.VISIBLE);
        } else {
            iobGraphLayout.setVisibility(View.GONE);
        }

        // remove old data from graph
        bgGraph.getSecondScale().getSeries().clear();
        bgGraph.removeAllSeries();

        // **** HIGH and LOW targets graph ****
        DoubleDataPoint[] areaDataPoints = new DoubleDataPoint[]{
                new DoubleDataPoint(fromTime, lowLine, highLine),
                new DoubleDataPoint(endTime, lowLine, highLine)
        };
        bgGraph.addSeries(areaSeries = new AreaGraphSeries<>(areaDataPoints));
        areaSeries.setColor(0);
        areaSeries.setDrawBackground(true);
        areaSeries.setBackgroundColor(Color.argb(40, 0, 255, 0));

        // set manual x bounds to have nice steps
        bgGraph.getViewport().setMaxX(endTime);
        bgGraph.getViewport().setMinX(fromTime);
        bgGraph.getViewport().setXAxisBoundsManual(true);
        bgGraph.getGridLabelRenderer().setLabelFormatter(new TimeAsXAxisLabelFormatter(getActivity(), "HH"));
        bgGraph.getGridLabelRenderer().setNumHorizontalLabels(7); // only 7 because of the space
        iobGraph.getViewport().setMaxX(endTime);
        iobGraph.getViewport().setMinX(fromTime);
        iobGraph.getViewport().setXAxisBoundsManual(true);
        iobGraph.getGridLabelRenderer().setLabelFormatter(new TimeAsXAxisLabelFormatter(getActivity(), "HH"));
        iobGraph.getGridLabelRenderer().setNumHorizontalLabels(7); // only 7 because of the space

        // **** BG graph ****
        List<BgReading> bgReadingsArray = MainApp.getDbHelper().getBgreadingsDataFromTime(fromTime, true);
        List<BgReading> inRangeArray = new ArrayList<BgReading>();
        List<BgReading> outOfRangeArray = new ArrayList<BgReading>();

        if (bgReadingsArray.size() == 0)
            return;

        Iterator<BgReading> it = bgReadingsArray.iterator();
        Double maxBgValue = 0d;
        while (it.hasNext()) {
            BgReading bg = it.next();
            if (bg.value > maxBgValue) maxBgValue = bg.value;
            if (bg.valueToUnits(units) < lowLine || bg.valueToUnits(units) > highLine)
                outOfRangeArray.add(bg);
            else
                inRangeArray.add(bg);
        }
        maxBgValue = NSProfile.fromMgdlToUnits(maxBgValue, units);
        maxBgValue = units.equals(Constants.MGDL) ? Round.roundTo(maxBgValue, 40d) + 80 : Round.roundTo(maxBgValue, 2d) + 4;
        if (highLine > maxBgValue) maxBgValue = highLine;
        Integer numOfHorizLines = units.equals(Constants.MGDL) ? (int) (maxBgValue / 40 + 1) : (int) (maxBgValue / 2 + 1);

        BgReading[] inRange = new BgReading[inRangeArray.size()];
        BgReading[] outOfRange = new BgReading[outOfRangeArray.size()];
        inRange = inRangeArray.toArray(inRange);
        outOfRange = outOfRangeArray.toArray(outOfRange);


        if (inRange.length > 0) {
            bgGraph.addSeries(seriesInRage = new PointsGraphSeries<BgReading>(inRange));
            seriesInRage.setShape(PointsGraphSeries.Shape.POINT);
            seriesInRage.setSize(5);
            seriesInRage.setColor(Color.GREEN);
        }

        if (outOfRange.length > 0) {
            bgGraph.addSeries(seriesOutOfRange = new PointsGraphSeries<BgReading>(outOfRange));
            seriesOutOfRange.setShape(PointsGraphSeries.Shape.POINT);
            seriesOutOfRange.setSize(5);
            seriesOutOfRange.setColor(Color.RED);
        }

        if (showPrediction) {
            DetermineBasalResultAMA amaResult = (DetermineBasalResultAMA) finalLastRun.constraintsProcessed;
            List<BgReading> predArray = amaResult.getPredictions();
            BgReading[] pred = new BgReading[predArray.size()];
            pred = predArray.toArray(pred);
            if (pred.length > 0) {
                bgGraph.addSeries(predSeries = new PointsGraphSeries<BgReading>(pred));
                predSeries.setShape(PointsGraphSeries.Shape.POINT);
                predSeries.setSize(4);
                predSeries.setColor(Color.MAGENTA);
            }
        }

        // **** NOW line ****
        DataPoint[] nowPoints = new DataPoint[]{
                new DataPoint(now, 0),
                new DataPoint(now, maxBgValue)
        };
        bgGraph.addSeries(seriesNow = new LineGraphSeries<>(nowPoints));
        seriesNow.setDrawDataPoints(false);
        DataPoint[] nowPoints2 = new DataPoint[]{
                new DataPoint(now, 0),
                new DataPoint(now, maxIobValueFound)
        };
        iobGraph.addSeries(seriesNow2 = new LineGraphSeries<>(nowPoints2));
        seriesNow2.setDrawDataPoints(false);
        //seriesNow.setThickness(1);
        // custom paint to make a dotted line
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        paint.setPathEffect(new DashPathEffect(new float[]{10, 20}, 0));
        paint.setColor(Color.WHITE);
        seriesNow.setCustomPaint(paint);
        seriesNow2.setCustomPaint(paint);


        // Treatments
        List<Treatment> treatments = MainApp.getConfigBuilder().getActiveTreatments().getTreatments();
        List<Treatment> filteredTreatments = new ArrayList<Treatment>();

        for (int tx = 0; tx < treatments.size(); tx++) {
            Treatment t = treatments.get(tx);
            if (t.getTimeIndex() < fromTime || t.getTimeIndex() > now) continue;
            t.setYValue(bgReadingsArray);
            filteredTreatments.add(t);
        }
        Treatment[] treatmentsArray = new Treatment[filteredTreatments.size()];
        treatmentsArray = filteredTreatments.toArray(treatmentsArray);
        if (treatmentsArray.length > 0) {
            bgGraph.addSeries(seriesTreatments = new PointsWithLabelGraphSeries<Treatment>(treatmentsArray));
            seriesTreatments.setShape(PointsWithLabelGraphSeries.Shape.TRIANGLE);
            seriesTreatments.setSize(10);
            seriesTreatments.setColor(Color.CYAN);
        }

        // set manual y bounds to have nice steps
        bgGraph.getViewport().setMaxY(maxBgValue);
        bgGraph.getViewport().setMinY(0);
        bgGraph.getViewport().setYAxisBoundsManual(true);
        bgGraph.getGridLabelRenderer().setNumVerticalLabels(numOfHorizLines);

        // set second scale
        if (pump.getPumpDescription().isTempBasalCapable && showBasalsView.isChecked()) {
            bgGraph.getSecondScale().addSeries(baseBasalsSeries);
            bgGraph.getSecondScale().addSeries(tempBasalsSeries);
            bgGraph.getSecondScale().addSeries(basalsLineSeries);
            bgGraph.getSecondScale().setMinY(0);
            bgGraph.getSecondScale().setMaxY(maxBgValue / lowLine * maxBasalValueFound * 1.2d);
        }
        bgGraph.getSecondScale().setLabelFormatter(new LabelFormatter() {
            @Override
            public String formatLabel(double value, boolean isValueX) {
                return "";
            }

            @Override
            public void setViewport(Viewport viewport) {

            }
        });


    }

    //Notifications
    public static class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.NotificationsViewHolder> {

        List<Notification> notificationsList;

        RecyclerViewAdapter(List<Notification> notificationsList) {
            this.notificationsList = notificationsList;
        }

        @Override
        public NotificationsViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.overview_notification_item, viewGroup, false);
            NotificationsViewHolder notificationsViewHolder = new NotificationsViewHolder(v);
            return notificationsViewHolder;
        }

        @Override
        public void onBindViewHolder(NotificationsViewHolder holder, int position) {
            Notification notification = notificationsList.get(position);
            holder.dismiss.setTag(notification);
            holder.text.setText(notification.text);
            holder.time.setText(DateUtil.timeString(notification.date));
            if (notification.level == Notification.URGENT)
                holder.cv.setBackgroundColor(ContextCompat.getColor(MainApp.instance(), R.color.notificationUrgent));
            else if (notification.level == Notification.NORMAL)
                holder.cv.setBackgroundColor(ContextCompat.getColor(MainApp.instance(), R.color.notificationNormal));
            else if (notification.level == Notification.LOW)
                holder.cv.setBackgroundColor(ContextCompat.getColor(MainApp.instance(), R.color.notificationLow));
            else if (notification.level == Notification.INFO)
                holder.cv.setBackgroundColor(ContextCompat.getColor(MainApp.instance(), R.color.notificationInfo));
        }

        @Override
        public int getItemCount() {
            return notificationsList.size();
        }

        @Override
        public void onAttachedToRecyclerView(RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
        }

        public static class NotificationsViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            CardView cv;
            TextView time;
            TextView text;
            Button dismiss;

            NotificationsViewHolder(View itemView) {
                super(itemView);
                cv = (CardView) itemView.findViewById(R.id.notification_cardview);
                time = (TextView) itemView.findViewById(R.id.notification_time);
                text = (TextView) itemView.findViewById(R.id.notification_text);
                dismiss = (Button) itemView.findViewById(R.id.notification_dismiss);
                dismiss.setOnClickListener(this);
            }

            @Override
            public void onClick(View v) {
                Notification notification = (Notification) v.getTag();
                switch (v.getId()) {
                    case R.id.notification_dismiss:
                        MainApp.bus().post(new EventDismissNotification(notification.id));
                        break;
                }
            }
        }
    }

    void updateNotifications() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    NotificationStore nstore = getPlugin().notificationStore;
                    nstore.removeExpired();
                    if (nstore.store.size() > 0) {
                        RecyclerViewAdapter adapter = new RecyclerViewAdapter(nstore.store);
                        notificationsView.setAdapter(adapter);
                        notificationsView.setVisibility(View.VISIBLE);
                    } else {
                        notificationsView.setVisibility(View.GONE);
                    }
                }
            });
    }


}
