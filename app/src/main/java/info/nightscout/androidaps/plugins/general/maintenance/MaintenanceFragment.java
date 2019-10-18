package info.nightscout.androidaps.plugins.general.maintenance;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.general.food.FoodPlugin;
import info.nightscout.androidaps.plugins.general.maintenance.activities.LogSettingActivity;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.ToastUtils;

/**
 *
 */
public class MaintenanceFragment extends Fragment {

    private Fragment f;
    private Context context = this.getContext();
    private static Logger log = LoggerFactory.getLogger(L.CORE);

    @Override
    public void onResume() {
        super.onResume();

        this.f = this;
    }

    @Override
    public void onPause() {
        super.onPause();

        this.f = null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.maintenance_fragment, container, false);

        final Fragment f = this;

        view.findViewById(R.id.log_send).setOnClickListener(view1 -> MaintenancePlugin.getPlugin().sendLogs());

        view.findViewById(R.id.log_delete).setOnClickListener(view1 -> MaintenancePlugin.getPlugin().deleteLogs());

        view.findViewById(R.id.nav_resetdb).setOnClickListener(view1 -> new AlertDialog.Builder(f.getContext())
                .setTitle(R.string.nav_resetdb)
                .setMessage(R.string.reset_db_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    MainApp.getDbHelper().resetDatabases();
                    // should be handled by Plugin-Interface and
                    // additional service interface and plugin registry
                    FoodPlugin.getPlugin().getService().resetFood();
                    TreatmentsPlugin.getPlugin().getService().resetTreatments();
                })
                .create()
                .show());

        view.findViewById(R.id.nav_export).setOnClickListener(view1 -> {
            // start activity for checking permissions...
            ImportExportPrefs.verifyStoragePermissions(f);
            ImportExportPrefs.exportSharedPreferences(f);
        });

        view.findViewById(R.id.nav_import).setOnClickListener(view1 -> {
            // start activity for checking permissions...
            ImportExportPrefs.verifyStoragePermissions(f);
            ImportExportPrefs.importSharedPreferences(f);
        });

        view.findViewById(R.id.nav_logsettings).setOnClickListener(view1 -> {
            startActivity(new Intent(getActivity(), LogSettingActivity.class));
        });

        view.findViewById(R.id.dropbox_export).setOnClickListener(view1 -> {
            // start activity for checking permissions...
            ImportExportPrefs.verifyStoragePermissions(f);
            sendPreferences();
        });

        return view;
    }

    public MaintenanceFragment getMain() {
        return this;
    }

    private void sendPreferences(){
        File path = f.getContext().getExternalFilesDir("exports");
        final File file = new File(path, MainApp.gs(R.string.app_name) + "Preferences");
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(f.getContext());
        try {
            FileWriter fw = new FileWriter(file);
            PrintWriter pw = new PrintWriter(fw);
            Map<String, ?> prefsMap = prefs.getAll();
            for (Map.Entry<String, ?> entry : prefsMap.entrySet()) {
                pw.println(entry.getKey() + "::" + entry.getValue().toString());
            }
            pw.close();
            fw.close();
            ToastUtils.showToastInUiThread(f.getContext(), MainApp.gs(R.string.exported));
        } catch (FileNotFoundException e) {
            ToastUtils.showToastInUiThread(f.getContext(), MainApp.gs(R.string.filenotfound) + " " + file);
            log.error("Unhandled exception", e);
        } catch (IOException e) {
            log.error("Unhandled exception", e);
        }
        try {
            Intent mIntent = new Intent(Intent.ACTION_SEND);
            mIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Context ctx = getMain().getContext();
            Uri uri = FileProvider.getUriForFile(getMain().getContext(), "info.nightscout.androidaps.fileprovider", file);

            mIntent.setType("text/*");
            mIntent.setData(uri);
            mIntent.putExtra("android.intent.extra.STREAM",uri);
            mIntent.setPackage("com.dropbox.android");
            ctx.startActivity(Intent.createChooser(mIntent, "Upload to Dropbox"));
//            ctx.startActivity(mIntent);
        } catch (Exception e) {
            //App not found
            e.printStackTrace();
        }
    }

}
