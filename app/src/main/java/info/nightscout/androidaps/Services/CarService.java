package info.nightscout.androidaps.Services;

/**
 * Created by cchunn on 1/30/18.
 */

import com.google.android.apps.auto.sdk.CarActivity;
import com.google.android.apps.auto.sdk.CarActivityService;
import info.nightscout.androidaps.MainCarActivity;

public class CarService extends CarActivityService {
    public Class<? extends CarActivity> getCarActivity() {
        return MainCarActivity.class;
    }
}