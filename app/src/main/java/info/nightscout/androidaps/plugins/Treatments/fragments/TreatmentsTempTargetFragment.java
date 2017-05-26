package info.nightscout.androidaps.plugins.Treatments.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
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
import android.widget.Button;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.Services.Intents;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.events.EventTempTargetChange;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSProfile;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.NSUpload;
import info.nightscout.utils.OverlappingIntervals;
import info.nightscout.utils.SP;

/**
 * Created by mike on 13/01/17.
 */

public class TreatmentsTempTargetFragment extends Fragment implements View.OnClickListener {

    RecyclerView recyclerView;
    LinearLayoutManager llm;
    Button refreshFromNS;

    Context context;

    public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.TempTargetsViewHolder> {

        OverlappingIntervals<TempTarget> tempTargetList;

        RecyclerViewAdapter(OverlappingIntervals<TempTarget> TempTargetList) {
            this.tempTargetList = TempTargetList;
        }

        @Override
        public TempTargetsViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.treatments_temptarget_item, viewGroup, false);
            TempTargetsViewHolder TempTargetsViewHolder = new TempTargetsViewHolder(v);
            return TempTargetsViewHolder;
        }

        @Override
        public void onBindViewHolder(TempTargetsViewHolder holder, int position) {
            NSProfile profile = ConfigBuilderPlugin.getActiveProfile().getProfile();
            if (profile == null) return;
            TempTarget tempTarget = tempTargetList.getReversed(position);
            if (!tempTarget.isEndingEvent()) {
                holder.date.setText(DateUtil.dateAndTimeString(tempTarget.date) + " - " + DateUtil.timeString(tempTargetList.get(position).originalEnd()));
                holder.duration.setText(DecimalFormatter.to0Decimal(tempTarget.durationInMinutes) + " min");
                holder.low.setText(tempTarget.lowValueToUnitsToString(profile.getUnits()));
                holder.high.setText(tempTarget.highValueToUnitsToString(profile.getUnits()));
                holder.reason.setText(tempTarget.reason);
            } else {
                holder.date.setText(DateUtil.dateAndTimeString(tempTarget.date));
                holder.duration.setText(R.string.cancel);
                holder.low.setText("");
                holder.high.setText("");
                holder.reason.setText("");
                holder.reasonLabel.setText("");
                holder.reasonColon.setText("");
            }
            if (tempTarget.isInProgress())
                holder.date.setTextColor(ContextCompat.getColor(MainApp.instance(), R.color.colorActive));
            else
                holder.date.setTextColor(holder.reasonColon.getCurrentTextColor());
            holder.remove.setTag(tempTarget);
        }

        @Override
        public int getItemCount() {
            return tempTargetList.size();
        }

        @Override
        public void onAttachedToRecyclerView(RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
        }

        public class TempTargetsViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            CardView cv;
            TextView date;
            TextView duration;
            TextView low;
            TextView high;
            TextView reason;
            TextView reasonLabel;
            TextView reasonColon;
            TextView remove;

            TempTargetsViewHolder(View itemView) {
                super(itemView);
                cv = (CardView) itemView.findViewById(R.id.temptargetrange_cardview);
                date = (TextView) itemView.findViewById(R.id.temptargetrange_date);
                duration = (TextView) itemView.findViewById(R.id.temptargetrange_duration);
                low = (TextView) itemView.findViewById(R.id.temptargetrange_low);
                high = (TextView) itemView.findViewById(R.id.temptargetrange_high);
                reason = (TextView) itemView.findViewById(R.id.temptargetrange_reason);
                reasonLabel = (TextView) itemView.findViewById(R.id.temptargetrange_reason_label);
                reasonColon = (TextView) itemView.findViewById(R.id.temptargetrange_reason_colon);
                remove = (TextView) itemView.findViewById(R.id.temptargetrange_remove);
                remove.setOnClickListener(this);
                remove.setPaintFlags(remove.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            }

            @Override
            public void onClick(View v) {
                final TempTarget tempTarget = (TempTarget) v.getTag();
                final Context finalContext = context;
                switch (v.getId()) {
                    case R.id.temptargetrange_remove:
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setTitle(MainApp.sResources.getString(R.string.confirmation));
                        builder.setMessage(MainApp.sResources.getString(R.string.removerecord) + "\n" + DateUtil.dateAndTimeString(tempTarget.date));
                        builder.setPositiveButton(MainApp.sResources.getString(R.string.ok), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                final String _id = tempTarget._id;
                                if (_id != null && !_id.equals("")) {
                                    NSUpload.removeCareportalEntryFromNS(_id);
                                }
                                MainApp.getDbHelper().delete(tempTarget);
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
        View view = inflater.inflate(R.layout.treatments_temptarget_fragment, container, false);

        recyclerView = (RecyclerView) view.findViewById(R.id.temptargetrange_recyclerview);
        recyclerView.setHasFixedSize(true);
        llm = new LinearLayoutManager(view.getContext());
        recyclerView.setLayoutManager(llm);

        RecyclerViewAdapter adapter = new RecyclerViewAdapter(MainApp.getConfigBuilder().getTempTargetsFromHistory());
        recyclerView.setAdapter(adapter);

        refreshFromNS = (Button) view.findViewById(R.id.temptargetrange_refreshfromnightscout);
        refreshFromNS.setOnClickListener(this);

        context = getContext();

        boolean nsUploadOnly = SP.getBoolean(R.string.key_ns_upload_only, false);
        if (nsUploadOnly)
            refreshFromNS.setVisibility(View.GONE);

        updateGUI();
        return view;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.temptargetrange_refreshfromnightscout:
                AlertDialog.Builder builder = new AlertDialog.Builder(this.getContext());
                builder.setTitle(this.getContext().getString(R.string.confirmation));
                builder.setMessage(this.getContext().getString(R.string.refreshtemptargetsfromnightscout));
                builder.setPositiveButton(this.getContext().getString(R.string.ok), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        MainApp.getDbHelper().resetTempTargets();
                        Intent restartNSClient = new Intent(Intents.ACTION_RESTART);
                        MainApp.instance().getApplicationContext().sendBroadcast(restartNSClient);
                    }
                });
                builder.setNegativeButton(this.getContext().getString(R.string.cancel), null);
                builder.show();
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
    public void onStatusEvent(final EventTempTargetChange ev) {
        updateGUI();
    }

    void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    recyclerView.swapAdapter(new RecyclerViewAdapter(MainApp.getConfigBuilder().getTempTargetsFromHistory()), false);
                }
            });
    }
}
