package info.nightscout.androidaps.utils;

import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.app.backup.BackupManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.util.Pair;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

import info.nightscout.androidaps.logging.L;

public class aBackupAgent extends BackupAgent{
    private static Logger log = LoggerFactory.getLogger(L.CORE);
    public static final String RUN_SIZE_CHECK_ON_NEXT_BACKUP="runSizeCheckOnNextBackup";

    public static final String SIZE_CHECK_DATA_NUM="sizeCheckDataNum";
    public static final int SIZE_CHECK_DATA_NUM_DEFAULT=30;

    public static final String SIZE_CHECK_BYTE_NUM="sizeCheckByteNum";
    public static final int SIZE_CHECK_BYTE_NUM_DEFAULT=100000;
    public static final String LOGS="Logs";


    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data, ParcelFileDescriptor newState) {
        BackupManager backupManager = new BackupManager(getApplicationContext());
        backupManager.dataChanged();
    }

    @Override
    public void onRestore(BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState) throws IOException {
        log.debug("RESTORING!");
        SharedPreferences sp = getSharedPreferences("pref", Context.MODE_PRIVATE);
        HashSet<String> logLocal = new HashSet<>(sp.getStringSet(LOGS, new HashSet<String>()));

        SharedPreferences.Editor edit = sp.edit();
        try {
            log.debug("Restore called");
            ImmutableList.Builder<Pair<String, Integer>> keys = ImmutableList.builder();
            while (data.readNextHeader()) {
                String key = data.getKey();
                int dataSize = data.getDataSize();
                keys.add(Pair.create(key, dataSize));
                data.skipEntityData();
            }
            log.debug("Restore data contained [" + Joiner.on(",").join(FluentIterable.from(keys.build()).transform(new Function<Pair<String, Integer>, String>() {
                @Override
                public String apply(Pair<String, Integer> input) {
                    return input.first + "(" + input.second + ")";
                }
            })) + "]");
        } finally {
            edit.putStringSet(LOGS, logLocal);
            edit.apply();
        }
    }
}