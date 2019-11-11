package info.nightscout.androidaps.plugins.general.maintenance;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.database.BlockingAppRepository;
import info.nightscout.androidaps.database.transactions.ResetDatabaseTransaction;
import info.nightscout.androidaps.plugins.general.maintenance.activities.LogSettingActivity;

/**
 *
 */
public class MaintenanceFragment extends Fragment {

    private Fragment f;

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
                    BlockingAppRepository.INSTANCE.runTransaction(new ResetDatabaseTransaction());
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


        return view;
    }

}
