package info.nightscout.androidaps.plugins.CircadianPercentageProfile;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioButton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.FragmentBase;
import info.nightscout.utils.SafeParse;

public class CircadianPercentageProfileFragment extends Fragment implements FragmentBase {
    private static Logger log = LoggerFactory.getLogger(CircadianPercentageProfileFragment.class);

    private static CircadianPercentageProfilePlugin circadianPercentageProfilePlugin = new CircadianPercentageProfilePlugin();

    public static CircadianPercentageProfilePlugin getPlugin() {
        return circadianPercentageProfilePlugin;
    }

    EditText diaView;
    RadioButton mgdlView;
    RadioButton mmolView;
    EditText icView;
    EditText isfView;
    EditText carView;
    EditText basalView;
    EditText targetlowView;
    EditText targethighView;
    EditText percentageView;
    EditText timeshiftView;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.circadianpercentageprofile_fragment, container, false);
        diaView = (EditText) layout.findViewById(R.id.simpleprofile_dia);
        mgdlView = (RadioButton) layout.findViewById(R.id.simpleprofile_mgdl);
        mmolView = (RadioButton) layout.findViewById(R.id.simpleprofile_mmol);
        icView = (EditText) layout.findViewById(R.id.simpleprofile_ic);
        isfView = (EditText) layout.findViewById(R.id.simpleprofile_isf);
        carView = (EditText) layout.findViewById(R.id.simpleprofile_car);
        targetlowView = (EditText) layout.findViewById(R.id.simpleprofile_targetlow);
        targethighView = (EditText) layout.findViewById(R.id.simpleprofile_targethigh);
        percentageView = (EditText) layout.findViewById(R.id.circadianpercentageprofile_percentage);
        timeshiftView = (EditText) layout.findViewById(R.id.circadianpercentageprofile_timeshift);


        mgdlView.setChecked(circadianPercentageProfilePlugin.mgdl);
        mmolView.setChecked(circadianPercentageProfilePlugin.mmol);
        diaView.setText(circadianPercentageProfilePlugin.dia.toString());
        icView.setText(circadianPercentageProfilePlugin.ic.toString());
        isfView.setText(circadianPercentageProfilePlugin.isf.toString());
        carView.setText(circadianPercentageProfilePlugin.car.toString());
        targetlowView.setText(circadianPercentageProfilePlugin.targetLow.toString());
        targethighView.setText(circadianPercentageProfilePlugin.targetHigh.toString());
        percentageView.setText("" + circadianPercentageProfilePlugin.percentage);
        timeshiftView.setText("" + circadianPercentageProfilePlugin.timeshift);

        mgdlView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                circadianPercentageProfilePlugin.mgdl = mgdlView.isChecked();
                circadianPercentageProfilePlugin.mmol = !circadianPercentageProfilePlugin.mgdl;
                mmolView.setChecked(circadianPercentageProfilePlugin.mmol);
                circadianPercentageProfilePlugin.storeSettings();
            }
        });
        mmolView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                circadianPercentageProfilePlugin.mmol = mmolView.isChecked();
                circadianPercentageProfilePlugin.mgdl = !circadianPercentageProfilePlugin.mmol;
                mgdlView.setChecked(circadianPercentageProfilePlugin.mgdl);
                circadianPercentageProfilePlugin.storeSettings();
            }
        });

        TextWatcher textWatch = new TextWatcher() {

            @Override
            public void afterTextChanged(Editable s) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start,
                                      int before, int count) {
                circadianPercentageProfilePlugin.dia = SafeParse.stringToDouble(diaView.getText().toString());
                circadianPercentageProfilePlugin.ic = SafeParse.stringToDouble(icView.getText().toString());
                circadianPercentageProfilePlugin.isf = SafeParse.stringToDouble(isfView.getText().toString());
                circadianPercentageProfilePlugin.car = SafeParse.stringToDouble(carView.getText().toString());
                circadianPercentageProfilePlugin.targetLow = SafeParse.stringToDouble(targetlowView.getText().toString());
                circadianPercentageProfilePlugin.targetHigh = SafeParse.stringToDouble(targethighView.getText().toString());
                circadianPercentageProfilePlugin.timeshift = SafeParse.stringToInt(timeshiftView.getText().toString());
                circadianPercentageProfilePlugin.percentage = SafeParse.stringToInt(percentageView.getText().toString());
                circadianPercentageProfilePlugin.storeSettings();
            }
        };

        diaView.addTextChangedListener(textWatch);
        icView.addTextChangedListener(textWatch);
        isfView.addTextChangedListener(textWatch);
        carView.addTextChangedListener(textWatch);
        targetlowView.addTextChangedListener(textWatch);
        targethighView.addTextChangedListener(textWatch);
        percentageView.addTextChangedListener(textWatch);
        timeshiftView.addTextChangedListener(textWatch);

        return layout;
    }

}
