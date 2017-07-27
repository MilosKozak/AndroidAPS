package info.nightscout.androidaps.plugins.NSClientInternal.broadcasts;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.Services.Intents;

/**
 * Created by mike on 28.02.2016.
 */
public class BroadcastQueueStatus {
    public static void handleNewStatus(int size, Context context) {
<<<<<<< HEAD
=======

        if(!SP.getBoolean(R.string.key_nsclient_localbroadcasts, true)) return;

>>>>>>> 39d41e8e7d841a7b5a22e0b4e14acce3394a7878
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "sendQueue");
        wakeLock.acquire();
        try {
            Bundle bundle = new Bundle();
            bundle.putInt("size", size);
            Intent intent = new Intent(Intents.ACTION_QUEUE_STATUS);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent);
        } finally {
            wakeLock.release();
        }
    }
}
