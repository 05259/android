package eu.alefzero.owncloud;

import java.net.URI;
import java.net.URISyntaxException;

import eu.alefzero.owncloud.authenticator.AccountAuthenticatorService;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class PreferencesNewSession extends AccountAuthenticatorActivity implements OnClickListener {
  private Intent mReturnData;
  private final String TAG = "OwnCloudPreferencesNewSession";
  @Override
  public void onCreate(Bundle savedInstanceState){
    super.onCreate(savedInstanceState);
    //setContentView(R.layout.add_new_session);
    /*
    EditText et;// = (EditText) findViewById(R.id.newSession_sessionName);
    
    et = (EditText) findViewById(R.id.newSession_URL);
    if (getIntent().hasExtra("sessionURL")) {
      try {
        URI uri = new URI(getIntent().getStringExtra("sessionURL"));
        String url = uri.getHost();
        if (uri.getPort() != -1) {
          url += ":" + String.valueOf(uri.getPort());
        }
        if (uri.getPath() != null) {
          url += uri.getPath();
        } else {
          url += "/";
        }
        et.setText(url);
        et = (EditText) findViewById(R.id.newSession_username);
        if (uri.getAuthority() != null) {
          if (uri.getUserInfo().indexOf(':') != -1) {
            et.setText(uri.getUserInfo().substring(0, uri.getUserInfo().indexOf(':')));
            et = (EditText) findViewById(R.id.newSession_password);
            et.setText(uri.getUserInfo().substring(uri.getUserInfo().indexOf(':')+1));
          } else {
            et.setText(uri.getUserInfo());
          }
        }
        
      } catch (URISyntaxException e) {
        Log.e(TAG, "Incorrect URI syntax " + e.getLocalizedMessage());
      }
    }
    
    mReturnData = new Intent();
    setResult(Activity.RESULT_OK, mReturnData);
    ((Button) findViewById(R.id.button1)).setOnClickListener(this);
    ((Button) findViewById(R.id.button2)).setOnClickListener(this);*/
  }
  
  @Override
  protected void onResume() {
    super.onResume();
  }

  public void onClick(View v) {
   /* switch (v.getId()) {
      case R.id.button1:
        Intent intent = new Intent();
        if (getIntent().hasExtra("sessionId")) {
          intent.putExtra("sessionId", getIntent().getIntExtra("sessionId", -1));
        }
        //String sessionName = ((EditText) findViewById(R.id.newSession_sessionName)).getText().toString();
      //  if (sessionName.trim().equals("") || !isNameValid(sessionName)) {
     //    Toast.makeText(this, R.string.new_session_session_name_error, Toast.LENGTH_LONG).show();
     //     break;
       // }
        URI uri = prepareURI();
        if (uri != null) {
          //intent.putExtra("sessionName", sessionName);
          intent.putExtra("sessionURL", uri.toString());
          setResult(Activity.RESULT_OK, intent);
          AccountManager accMgr = AccountManager.get(this);
          Account a = new Account("OwnCloud", AccountAuthenticatorService.ACCOUNT_TYPE);
          accMgr.addAccountExplicitly(a, "asd", null);
          finish();
        }
        break;
      case R.id.button2:
        setResult(Activity.RESULT_CANCELED);
        finish();
        break;
    }*/
  }
  
  private URI prepareURI() {
    URI uri = null;
   /* String url = "";
    try {
      String username = ((EditText) findViewById(R.id.newSession_username)).getText().toString().trim();
      String password = ((EditText) findViewById(R.id.newSession_password)).getText().toString().trim();
      String hostname = ((EditText) findViewById(R.id.newSession_URL)).getText().toString().trim();
      String scheme;
      if (hostname.matches("[A-Za-z]://")) {
        scheme = hostname.substring(0, hostname.indexOf("://")+3);
        hostname = hostname.substring(hostname.indexOf("://")+3);
      } else {
        scheme = "http://";
      }
      if (!username.equals("")) {
        if (!password.equals("")) {
          username += ":" + password + "@";
        } else {
          username += "@";
        }
      }
      url = scheme + username + hostname;
      Log.i(TAG, url);
      uri = new URI(url);
    } catch (URISyntaxException e) {
      Log.e(TAG, "Incorrect URI syntax " + e.getLocalizedMessage());
      Toast.makeText(this, R.string.new_session_uri_error, Toast.LENGTH_LONG).show();
    }
    */return uri;
  }
  
  private boolean isNameValid(String string) {
    return string.matches("[A-Za-z0-9 _-]*");
  }
  
  @Override
  public void onBackPressed() {
    setResult(Activity.RESULT_CANCELED);
    super.onBackPressed();
  }
  
}
