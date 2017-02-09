package info.nightscout.androidaps.actions.wizard;


import android.app.Activity;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.wearable.view.DotsPageIndicator;
import android.support.wearable.view.GridPagerAdapter;
import android.support.wearable.view.GridViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.text.DecimalFormat;

import info.nightscout.androidaps.ListenerService;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.actions.utils.PlusMinusEditText;
import info.nightscout.androidaps.actions.utils.SafeParse;

/**
 * Created by adrian on 09/02/17.
 */


public class WizardActivity extends Activity {

    PlusMinusEditText editCarbs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.grid_layout);
        final Resources res = getResources();
        final GridViewPager pager = (GridViewPager) findViewById(R.id.pager);

        pager.setAdapter(new MyGridViewPagerAdapter());
        DotsPageIndicator dotsPageIndicator = (DotsPageIndicator) findViewById(R.id.page_indicator);
        dotsPageIndicator.setPager(pager);
    }


    @Override
    protected void onPause() {
        super.onPause();
        finish();
    }


    private class MyGridViewPagerAdapter extends GridPagerAdapter {
        @Override
        public int getColumnCount(int arg0) {
            return 2;
        }

        @Override
        public int getRowCount() {
            return 1;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int row, int col) {

            if(col == 0){
                final View view = LayoutInflater.from(getApplicationContext()).inflate(R.layout.action_editplusminus_item, container, false);
                final TextView textView = (TextView) view.findViewById(R.id.label);
                textView.setText("carbs");
                editCarbs = new PlusMinusEditText(view, R.id.amountfield, R.id.plusbutton, R.id.minusbutton, 0d, 0d, 100d, 1d, new DecimalFormat("0"), false);
                container.addView(view);
                return view;
            } else {

                final View view = LayoutInflater.from(getApplicationContext()).inflate(R.layout.action_send_item, container, false);
                final ImageView confirmbutton = (ImageView) view.findViewById(R.id.confirmbutton);
                confirmbutton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        //TODO: check if it can happen that the fagment is never created that hold data
                        // (you have to swipe past them anyways - but still)

                        String actionstring = "wizard " + SafeParse.stringToInt(editCarbs.editText.getText().toString());;
                        ListenerService.initiateAction(WizardActivity.this, actionstring);
                        finish();
                    }
                });
                container.addView(view);
                return view;
            }
        }

        @Override
        public void destroyItem(ViewGroup container, int row, int col, Object view) {
            //TODO: Handle this to get the data before the view is destroyed?
            container.removeView((View)view);
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view==object;
        }


    }
}