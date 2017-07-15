package info.nightscout.androidaps.data;


import android.support.annotation.Nullable;

import info.nightscout.androidaps.interfaces.Interval;

/**
 * Created by adrian on 15/07/17.
 */

public class OverlappingIntervals<T extends Interval> extends Intervals<T> {

    protected void merge() {
        for (int index = 0; index < rawData.size() - 1; index++) {
            Interval i = rawData.valueAt(index);
            long startOfNewer = rawData.valueAt(index + 1).start();
            if (i.originalEnd() > startOfNewer) {
                i.cutEndTo(startOfNewer);
            }
        }
    }

    @Nullable
    public T getValueByInterval(long time) {
        int index = binarySearch(time);
        if (index >= 0) return rawData.valueAt(index);
        return null;
    }

}
