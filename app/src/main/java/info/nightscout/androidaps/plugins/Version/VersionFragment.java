package info.nightscout.androidaps.plugins.Version;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import info.nightscout.androidaps.R;

/**
 *
 */
public class VersionFragment extends Fragment {

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
        View view = inflater.inflate(R.layout.version_fragment, container, false);

        final Fragment f = this;

        view.findViewById(R.id.ver_chkversion).setOnClickListener(view1 -> VersionPlugin.getPlugin().checkVersion());

        return view;
    }

}
