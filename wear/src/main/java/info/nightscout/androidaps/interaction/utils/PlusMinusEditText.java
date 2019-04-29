package info.nightscout.androidaps.interaction.utils;

import android.os.Handler;
import android.os.Message;
import android.support.wearable.input.RotaryEncoder;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.text.NumberFormat;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/**
 * Created by mike on 28.06.2016.
 */
public class PlusMinusEditText implements View.OnKeyListener,
        View.OnTouchListener, View.OnClickListener {

    Integer editTextID;
    public TextView editText;
    ImageView minusImage;
    ImageView plusImage;

    Double value;
    Double minValue = 0d;
    Double maxValue = 1d;
    Double step = 1d;
    NumberFormat formater;
    boolean allowZero = false;
    boolean roundRobin;

    private Handler mHandler;
    private ScheduledExecutorService mUpdater;

    private class UpdateCounterTask implements Runnable {
        private boolean mInc;
        private int repeated = 0;
        private int multiplier = 1;

        private final int doubleLimit = 5;

        public UpdateCounterTask(boolean inc) {
            mInc = inc;
        }

        public void run() {
            Message msg = new Message();
            if (repeated % doubleLimit == 0) multiplier *= 2;
            repeated++;
            msg.arg1 = multiplier;
            msg.arg2 = repeated;
            if (mInc) {
                msg.what = MSG_INC;
            } else {
                msg.what = MSG_DEC;
            }
            mHandler.sendMessage(msg);
        }
    }

    private static final int MSG_INC = 0;
    private static final int MSG_DEC = 1;

    public PlusMinusEditText(View view, int editTextID, int plusID, int minusID, Double initValue, Double minValue, Double maxValue, Double step, NumberFormat formater, boolean allowZero) {
        this( view,  editTextID,  plusID,  minusID,  initValue,  minValue,  maxValue,  step,  formater,  allowZero, false);
    }

    public PlusMinusEditText(View view, int editTextID, int plusID, int minusID, Double initValue, Double minValue, Double maxValue, Double step, NumberFormat formater, boolean allowZero, boolean roundRobin) {
        editText = (TextView) view.findViewById(editTextID);
        minusImage = (ImageView) view.findViewById(minusID);
        plusImage = (ImageView) view.findViewById(plusID);

        this.value = initValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.step = step;
        this.formater = formater;
        this.allowZero = allowZero;
        this.roundRobin = roundRobin;

        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_INC:
                        inc(msg.arg1);
                        return;
                    case MSG_DEC:
                        dec(msg.arg1);
                        return;
                }
                super.handleMessage(msg);
            }
        };

        editText.setOnGenericMotionListener(new RotaryInputHandler());

        minusImage.setOnTouchListener(this);
        minusImage.setOnKeyListener(this);
        minusImage.setOnClickListener(this);
        plusImage.setOnTouchListener(this);
        plusImage.setOnKeyListener(this);
        plusImage.setOnClickListener(this);
        updateEditText();
    }

    public void setValue(Double value) {
        this.value = value;
        updateEditText();
    }

    public Double getValue() {
        return value;
    }

    public void setStep(Double step) {
        this.step = step;
    }
    private void inc(int multiplier) {
        value += step * multiplier;
        if (value > maxValue) {
            if(roundRobin){
                value = minValue;
            } else {
                value = maxValue;
                stopUpdating();
            }
        }
        updateEditText();
    }

    private void dec( int multiplier) {
        value -= step * multiplier;
        if (value < minValue) {
            if(roundRobin){
                value = maxValue;
            } else {
                value = minValue;
                stopUpdating();
            }
        }
        updateEditText();
    }

    private void updateEditText() {
        if (value == 0d && !allowZero)
            editText.setText("");
        else
            editText.setText(formater.format(value));
    }

    private void startUpdating(boolean inc) {
        if (mUpdater != null) {
            return;
        }
        mUpdater = Executors.newSingleThreadScheduledExecutor();
        mUpdater.scheduleAtFixedRate(new UpdateCounterTask(inc), 200, 200,
                TimeUnit.MILLISECONDS);
    }

    private void stopUpdating() {
        if (mUpdater != null) {
            mUpdater.shutdownNow();
            mUpdater = null;
        }
    }

    @Override
    public void onClick(View v) {
        if (mUpdater == null) {
            if (v == plusImage) {
                inc(1);
            } else {
                dec(1);
            }
        }
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        boolean isKeyOfInterest = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER;
        boolean isReleased = event.getAction() == KeyEvent.ACTION_UP;
        boolean isPressed = event.getAction() == KeyEvent.ACTION_DOWN
                && event.getAction() != KeyEvent.ACTION_MULTIPLE;

        if (isKeyOfInterest && isReleased) {
            stopUpdating();
        } else if (isKeyOfInterest && isPressed) {
            startUpdating(v == plusImage);
        }
        return false;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        boolean isReleased = event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL;
        boolean isPressed = event.getAction() == MotionEvent.ACTION_DOWN;

        if (isReleased) {
            stopUpdating();
        } else if (isPressed) {
            startUpdating(v == plusImage);
        }
        return false;
    }

    public void requestFocus() {
        editText.requestFocus();
    }

    private class RotaryInputHandler implements View.OnGenericMotionListener {
            private long lastRotaryActionTimestamp = 0;
            private float fastRotarySpeedThreshold = 3f;
            private float mediumRotatrySpeedThreshold = 0.2f;

            @Override
            public boolean onGenericMotion(View ignored, MotionEvent ev) {
                if (ev.getAction() == MotionEvent.ACTION_SCROLL && RotaryEncoder.isFromRotaryEncoder(ev)) {
                    float delta = -RotaryEncoder.getRotaryAxisValue(ev);
//                    System.out.println("Delta: " + delta);
                    long now = System.currentTimeMillis();
                    float speed = Math.abs(delta) * 1000 / (now - lastRotaryActionTimestamp);
//                    System.out.println("Speed: " + speed);
                    if (speed >= fastRotarySpeedThreshold) {
                        // rapid rotation, double steps
                        System.out.println("Fast   " + speed);
                        if (delta > 0) PlusMinusEditText.this.inc(2);
                        else PlusMinusEditText.this.dec(2);
                    } else if (speed >= mediumRotatrySpeedThreshold || lastRotaryActionTimestamp == 0) {
                        // medium speed, single step
                        System.out.println("Medium " + speed);
                        if (delta > 0) PlusMinusEditText.this.inc(1);
                        else PlusMinusEditText.this.dec(1);
                    } else {
                        // slow rotation, ignore
                        System.out.println("Slow   " + speed);
                        // with a pause between inputs, the next input will land here, detected as slow, but since
                        // the timestamp is updated, the next input will be dectecd as faster; thus the first
                        // input after a pause is ignored which might work nicely to prevent accidental changes via
                        // the back of the wearing hand or by moving the finger off the crown
                    }
                    lastRotaryActionTimestamp = now;
                    return true;
                }
                return false;
            }
    }
}
