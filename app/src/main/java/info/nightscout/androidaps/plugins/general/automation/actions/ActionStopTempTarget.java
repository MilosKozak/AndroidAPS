package info.nightscout.androidaps.plugins.general.automation.actions;

import com.google.common.base.Optional;

import org.json.JSONException;
import org.json.JSONObject;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.database.BlockingAppRepository;
import info.nightscout.androidaps.database.entities.TemporaryTarget;
import info.nightscout.androidaps.database.transactions.CancelTemporaryTargetTransaction;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.utils.JsonHelper;

public class ActionStopTempTarget extends Action {
    String reason = "";
    private TemporaryTarget tempTarget;

    public ActionStopTempTarget() {
    }

    @Override
    public int friendlyName() {
        return R.string.stoptemptarget;
    }

    @Override
    public String shortDescription() {
        return MainApp.gs(R.string.stoptemptarget);
    }

    @Override
    public void doAction(Callback callback) {
        BlockingAppRepository.INSTANCE.runTransaction(new CancelTemporaryTargetTransaction());
        if (callback != null)
            callback.result(new PumpEnactResult().success(true).comment(R.string.ok)).run();
    }

    @Override
    public boolean hasDialog() {
        return false;
    }

    @Override
    public String toJSON() {
        JSONObject o = new JSONObject();
        try {
            o.put("type", ActionStopTempTarget.class.getName());
            JSONObject data = new JSONObject();
            data.put("reason", reason);
            o.put("data", data);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return o.toString();
    }

    @Override
    public Action fromJSON(String data) {
        try {
            JSONObject d = new JSONObject(data);
            reason = JsonHelper.safeGetString(d, "reason");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return this;
    }

    @Override
    public Optional<Integer> icon() {
        return Optional.of(R.drawable.ic_stop_24dp);
    }
}
