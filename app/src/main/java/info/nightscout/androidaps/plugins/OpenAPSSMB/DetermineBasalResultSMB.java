package info.nightscout.androidaps.plugins.OpenAPSSMB;

import android.os.Parcel;
import android.os.Parcelable;

import com.eclipsesource.v8.V8Object;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.plugins.Loop.APSResult;
import info.nightscout.androidaps.data.IobTotal;

public class DetermineBasalResultSMB extends APSResult {
	 private static Logger log = LoggerFactory.getLogger(DetermineBasalResultSMB.class);
    public Date date;
    public JSONObject json = new JSONObject();
    public double eventualBG;
    public double snoozeBG;
    public IobTotal iob;
	public double smbValue;
    public DetermineBasalResultSMB(V8Object result, JSONObject j) {
        date = new Date();
        json = j;
        if (result.contains("error")) {
            reason = result.getString("error");
            changeRequested = false;
            rate = -1;
            duration = -1;
        } else {
            log.debug("DetermineBasalResultSMB - no error - processing result");
			reason = result.getString("reason");
            if (result.contains("eventualBG")) eventualBG = result.getDouble("eventualBG");
            if (result.contains("snoozeBG")) snoozeBG = result.getDouble("snoozeBG");
			if (result.contains("rate")) {
                rate = result.getDouble("rate");
                if (rate < 0d) rate = 0d;
                changeRequested = true;
				
				log.debug("Rate is positive and change is requested");
            } else {
                rate = -1;
				changeRequested = false;
				//if(smbValue>0.0) changeRequested = true; else changeRequested = false;
            }
            if (result.contains("duration")) {
                duration = result.getInteger("duration");
                changeRequested = changeRequested;
            } else {
                duration = -1;
				//if(smbValue>0.0) changeRequested = true; else changeRequested = false;
				// Added by Rumen hope that fixes bolussnooze
				changeRequested = false;
            }
			if (result.contains("units")) {
				changeRequested = true;
				smbValue = result.getDouble("units");
				log.debug(">>>>>>> Setting smbValue to "+smbValue+" >>>> DetermineBasalResultSMB");
			} else {
				smbValue = -5.5;
				log.debug(">>>>>>> Setting smbValue to -5.5 >>>> DetermineBasalResultSMB");
				changeRequested = false;
			}
        }
        result.release();
    }

    public DetermineBasalResultSMB() {
    }

    @Override
    public DetermineBasalResultSMB clone() {
        DetermineBasalResultSMB newResult = new DetermineBasalResultSMB();
        newResult.reason = new String(reason);
        newResult.rate = rate;
        newResult.duration = duration;
        newResult.changeRequested = changeRequested;
        newResult.rate = rate;
		newResult.smbValue = smbValue;

        try {
            newResult.json = new JSONObject(json.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        newResult.eventualBG = eventualBG;
        newResult.snoozeBG = snoozeBG;
        newResult.date = date;
        return newResult;
    }

    @Override
    public JSONObject json() {
        try {
            JSONObject ret = new JSONObject(this.json.toString());
            return ret;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<BgReading> getPredictions() {
        List<BgReading> array = new ArrayList<>();
        try {
            long startTime = date.getTime();
            if (json.has("predBGs")) {
                JSONObject predBGs = json.getJSONObject("predBGs");
                if (predBGs.has("IOB")) {
                    JSONArray iob = predBGs.getJSONArray("IOB");
                    for (int i = 1; i < iob.length(); i ++) {
                        BgReading bg = new BgReading();
                        bg.value = iob.getInt(i);
                        bg.date = startTime + i * 5 * 60 * 1000L;
			bg.isPrediction = true;
                        array.add(bg);
                    }
                }
                if (predBGs.has("aCOB")) {
                    JSONArray iob = predBGs.getJSONArray("aCOB");
                    for (int i = 1; i < iob.length(); i ++) {
                        BgReading bg = new BgReading();
                        bg.value = iob.getInt(i);
                        bg.date = startTime + i * 5 * 60 * 1000L;
						bg.isPrediction = true;
                        array.add(bg);
                    }
                }
                if (predBGs.has("COB")) {
                    JSONArray iob = predBGs.getJSONArray("COB");
                    for (int i = 1; i < iob.length(); i ++) {
                        BgReading bg = new BgReading();
                        bg.value = iob.getInt(i);
                        bg.date = startTime + i * 5 * 60 * 1000L;
						bg.isPrediction = true;
                        array.add(bg);
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return array;
    }

    public long getLatestPredictionsTime() {
        long latest = 0;
        try {
            long startTime = date.getTime();
            if (json.has("predBGs")) {
                JSONObject predBGs = json.getJSONObject("predBGs");
                if (predBGs.has("IOB")) {
                    JSONArray iob = predBGs.getJSONArray("IOB");
                    latest = Math.max(latest, startTime + (iob.length() - 1) * 5 * 60 * 1000L);
                }
                if (predBGs.has("aCOB")) {
                    JSONArray iob = predBGs.getJSONArray("aCOB");
                    latest = Math.max(latest, startTime + (iob.length() - 1) * 5 * 60 * 1000L);
                }
                if (predBGs.has("COB")) {
                    JSONArray iob = predBGs.getJSONArray("COB");
                    latest = Math.max(latest, startTime + (iob.length() - 1) * 5 * 60 * 1000L);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return latest;
    }
}
