package info.nightscout.androidaps.events;

import android.support.annotation.NonNull;

import info.nightscout.androidaps.data.DetailedBolusInfo;

/**
 * Created by adrian on 07/02/17.
 */

public class EventBolusRequested extends Event {
    @NonNull
    public final DetailedBolusInfo detailedBolusInfo;

    public EventBolusRequested(DetailedBolusInfo detailedBolusInfo) {
        this.detailedBolusInfo = detailedBolusInfo;
    }
}
