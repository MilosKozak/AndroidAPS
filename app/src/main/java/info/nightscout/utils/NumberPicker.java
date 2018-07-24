package info.nightscout.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.NumberFormat;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

public class NumberPicker extends TransientNumberPicker {
    public NumberPicker(Context context) {
        super(context);
    }

    public NumberPicker(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NumberPicker(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Nullable
    @Override
    protected Parcelable onSaveInstanceState() {
        super.onSaveInstanceState();
        return editText.onSaveInstanceState();
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        super.onRestoreInstanceState(state);
        editText.onRestoreInstanceState(state);
    }
}
