package info.nightscout.androidaps.plugins.Overview.events;

import android.support.annotation.Nullable;

import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.events.Event;

/**
 * Created by adrian on 20/02/17.
 */

public class EventDismissBolusprogressIfRunning extends Event {
    @Nullable
    public final DetailedBolusInfo bolusInfo;
    @Nullable
    public final PumpEnactResult result;

    public EventDismissBolusprogressIfRunning(@Nullable DetailedBolusInfo bolusInfo, @Nullable PumpEnactResult result) {
        this.bolusInfo = bolusInfo;
        this.result = result;
    }
}
