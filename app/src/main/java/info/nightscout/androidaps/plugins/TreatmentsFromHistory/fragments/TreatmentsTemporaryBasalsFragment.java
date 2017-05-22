package info.nightscout.androidaps.plugins.TreatmentsFromHistory.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.OverlappingIntervals;


public class TreatmentsTemporaryBasalsFragment extends Fragment {
    private static Logger log = LoggerFactory.getLogger(TreatmentsTemporaryBasalsFragment.class);

    RecyclerView recyclerView;
    LinearLayoutManager llm;

    TextView tempBasalTotalView;

    Context context;

    public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.TempBasalsViewHolder> {

        OverlappingIntervals<TemporaryBasal> tempBasalList;

        RecyclerViewAdapter(OverlappingIntervals<TemporaryBasal> tempBasalList) {
            this.tempBasalList = tempBasalList;
        }

        @Override
        public TempBasalsViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.treatments_tempbasals_item, viewGroup, false);
            return new TempBasalsViewHolder(v);
        }

        @Override
        public void onBindViewHolder(TempBasalsViewHolder holder, int position) {
            TemporaryBasal tempBasal = tempBasalList.getReversed(position);
            if (tempBasal.isInProgress()) {
                holder.date.setText(DateUtil.dateAndTimeString(tempBasal.date));
            } else {
                holder.date.setText(DateUtil.dateAndTimeString(tempBasal.date) + " - " + DateUtil.timeString(tempBasalList.get(position).end()));
            }
            holder.duration.setText(DecimalFormatter.to0Decimal(tempBasal.getRealDuration()) + " min");
            if (tempBasal.isAbsolute) {
                holder.absolute.setText(DecimalFormatter.to0Decimal(tempBasal.tempBasalConvertedToAbsolute(tempBasal.date)) + " U/h");
                holder.percent.setText("");
            } else {
                holder.absolute.setText("");
                holder.percent.setText(DecimalFormatter.to0Decimal(tempBasal.percentRate) + "%");
            }
            holder.realDuration.setText(DecimalFormatter.to0Decimal(tempBasal.getRealDuration()) + " min");
            IobTotal iob = tempBasal.iobCalc(new Date().getTime());
            holder.iob.setText(DecimalFormatter.to2Decimal(iob.basaliob) + " U");
            holder.netInsulin.setText(DecimalFormatter.to2Decimal(iob.netInsulin) + " U");
            holder.netRatio.setText(DecimalFormatter.to2Decimal(iob.netRatio) + " U/h");
            //holder.extendedFlag.setVisibility(tempBasal.isExtended ? View.VISIBLE : View.GONE);
            holder.extendedFlag.setVisibility(View.GONE);
            if (tempBasal.isInProgress())
                holder.date.setTextColor(ContextCompat.getColor(MainApp.instance(), R.color.colorActive));
            else
                holder.date.setTextColor(holder.netRatio.getCurrentTextColor());
            if (tempBasal.iobCalc(new Date().getTime()).basaliob != 0)
                holder.iob.setTextColor(ContextCompat.getColor(MainApp.instance(), R.color.colorActive));
            else
                holder.date.setTextColor(holder.netRatio.getCurrentTextColor());
            holder.remove.setTag(tempBasal);
        }

        @Override
        public int getItemCount() {
            return tempBasalList.size();
        }

        @Override
        public void onAttachedToRecyclerView(RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
        }

        public class TempBasalsViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            CardView cv;
            TextView date;
            TextView duration;
            TextView absolute;
            TextView percent;
            TextView realDuration;
            TextView netRatio;
            TextView netInsulin;
            TextView iob;
            TextView extendedFlag;
            TextView remove;

            TempBasalsViewHolder(View itemView) {
                super(itemView);
                cv = (CardView) itemView.findViewById(R.id.tempbasals_cardview);
                date = (TextView) itemView.findViewById(R.id.tempbasals_date);
                duration = (TextView) itemView.findViewById(R.id.tempbasals_duration);
                absolute = (TextView) itemView.findViewById(R.id.tempbasals_absolute);
                percent = (TextView) itemView.findViewById(R.id.tempbasals_percent);
                realDuration = (TextView) itemView.findViewById(R.id.tempbasals_realduration);
                netRatio = (TextView) itemView.findViewById(R.id.tempbasals_netratio);
                netInsulin = (TextView) itemView.findViewById(R.id.tempbasals_netinsulin);
                iob = (TextView) itemView.findViewById(R.id.tempbasals_iob);
                extendedFlag = (TextView) itemView.findViewById(R.id.tempbasals_extendedflag);
                remove = (TextView) itemView.findViewById(R.id.tempbasals_remove);
                remove.setOnClickListener(this);
                remove.setPaintFlags(remove.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            }

            @Override
            public void onClick(View v) {
                final TemporaryBasal tempBasal = (TemporaryBasal) v.getTag();
                switch (v.getId()) {
                    case R.id.tempbasals_remove:
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setTitle(MainApp.sResources.getString(R.string.confirmation));
                        builder.setMessage(MainApp.sResources.getString(R.string.removerecord) + "\n" + DateUtil.dateAndTimeString(tempBasal.date));
                        builder.setPositiveButton(MainApp.sResources.getString(R.string.ok), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                // TODO: handle this in NS too
                                //final String _id = tempBasal._id;
                                //if (_id != null && !_id.equals("")) {
                                //    MainApp.getConfigBuilder().removeCareportalEntryFromNS(_id);
                                //}
                                MainApp.getDbHelper().delete(tempBasal);
                                Answers.getInstance().logCustom(new CustomEvent("RemoveTempBasal"));
                            }
                        });
                        builder.setNegativeButton(MainApp.sResources.getString(R.string.cancel), null);
                        builder.show();
                        break;
                }
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.treatments_tempbasals_fragment, container, false);

        recyclerView = (RecyclerView) view.findViewById(R.id.tempbasals_recyclerview);
        recyclerView.setHasFixedSize(true);
        llm = new LinearLayoutManager(view.getContext());
        recyclerView.setLayoutManager(llm);

        RecyclerViewAdapter adapter = new RecyclerViewAdapter(MainApp.getConfigBuilder().getTemporaryBasals());
        recyclerView.setAdapter(adapter);

        tempBasalTotalView = (TextView) view.findViewById(R.id.tempbasals_totaltempiob);

        context = getContext();

        updateGUI();
        return view;
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
    public void onStatusEvent(final EventTempBasalChange ev) {
        updateGUI();
    }

    @Subscribe
    public void onStatusEvent(final EventNewBG ev) {
        updateGUI();
    }

    public void updateGUI() {
        Activity activity = getActivity();
        if (activity != null && recyclerView != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    recyclerView.swapAdapter(new RecyclerViewAdapter(MainApp.getConfigBuilder().getTemporaryBasals()), false);
                    if (MainApp.getConfigBuilder().getLastCalculationTempBasals() != null) {
                        String totalText = DecimalFormatter.to2Decimal(MainApp.getConfigBuilder().getLastCalculationTempBasals().basaliob) + " U";
                        tempBasalTotalView.setText(totalText);
                    }
                }
            });
    }

}
