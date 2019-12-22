package info.nightscout.androidaps.plugins.general.maintenance.activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.utils.ToastUtils;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;

public class SignUpActivity extends AppCompatActivity {
    private Button buttonRegister;
    private EditText emailText;
    private EditText passwordText;
    private TextView login;
    private FirebaseAuth firebaseAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);

        firebaseAuth = FirebaseAuth.getInstance();

        setContentView(R.layout.email_signup);
        buttonRegister = (Button) findViewById(R.id.buttonRegister);
        buttonRegister.setOnClickListener(view1 -> {
            // try to register account
            registerUser();
        });
        emailText = (EditText) findViewById(R.id.signupEmail);
        passwordText = (EditText) findViewById(R.id.signupPassword);
        login = (TextView) findViewById(R.id.login_text);
        login.setOnClickListener(view1 -> {
            // Go back to login
            finish();
        });
    }

    private void registerUser(){
        String email = emailText.getText().toString().trim();
        String password = passwordText.getText().toString();

        if(TextUtils.isEmpty(email)){
            //email is empty
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), "Enter email");
            return;
        }

        if(TextUtils.isEmpty(password)){
            //Password is empty
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), "Enter password");
            return;
        }

        firebaseAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                if(task.isSuccessful()){
                    ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), "Registration complete!");
                    // Go back to login
                    // TODO save username and pass to SharedPreferences to be able to autofill them
                    finish();
                } else {
                    ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), "Registration FAILED!");
                }
            }
        });
    }
}
