package info.nightscout.androidaps.plugins.general.maintenance.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.utils.ToastUtils;

public class LoginActivity extends AppCompatActivity {
    private Button buttonRegister;
    private EditText emailText;
    private EditText passwordText; // at least 6 characters
    private TextView login;
    private FirebaseAuth firebaseAuth;
    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    private static final Logger log = LoggerFactory.getLogger(L.CORE);


    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);

        firebaseAuth = FirebaseAuth.getInstance();

        if(firebaseAuth.getCurrentUser() != null){
            // User is already logged in
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.gs(R.string.firebase_logged));
            startActivity(new Intent(this, FirebaseFunctions.class));
            finish();

        } else {
            setContentView(R.layout.activity_login);
            buttonRegister = (Button) findViewById(R.id.buttonRegister);
            buttonRegister.setOnClickListener(view1 -> {
                // try to login
                loginUser();
            });
            emailText = (EditText) findViewById(R.id.loginEmail);
            passwordText = (EditText) findViewById(R.id.loginPassword);
            login = (TextView) findViewById(R.id.login);
            login.setOnClickListener(view1 -> {
                // Will open signup activity
                startActivity(new Intent(this, SignUpActivity.class));
            });
        }
    }

    private void loginUser(){
        String email = emailText.getText().toString().trim();
        String password = passwordText.getText().toString();

        if(TextUtils.isEmpty(email)){
            //email is empty
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.gs(R.string.enter_email));
            return;
        }

        if(TextUtils.isEmpty(password) || password.length() < 6){
            //Password is empty
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.gs(R.string.password_length));
            return;
        }

        firebaseAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                if(task.isSuccessful()){
                    ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.gs(R.string.login_successful));
                    startActivity(new Intent(MainApp.instance().getApplicationContext(), FirebaseFunctions.class));
                    finish();
                } else {
                    ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.gs(R.string.login_failed));
                    log.debug("Error logging in: "+task.getException());
                    log.debug("Name of app: " + FirebaseApp.getInstance().getOptions().getProjectId());
                }
            }
        });
    }

    private boolean checkPlayServices() {
        GoogleApiAvailability googleAPI = GoogleApiAvailability.getInstance();
        int result = googleAPI.isGooglePlayServicesAvailable(this);
        if(result != ConnectionResult.SUCCESS) {
            if(googleAPI.isUserResolvableError(result)) {
                googleAPI.getErrorDialog(this, result,
                        PLAY_SERVICES_RESOLUTION_REQUEST).show();
            }

            return false;
        }

        return true;
    }
}
