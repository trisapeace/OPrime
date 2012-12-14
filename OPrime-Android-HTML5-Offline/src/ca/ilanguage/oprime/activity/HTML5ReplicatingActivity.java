package ca.ilanguage.oprime.activity;

import java.io.File;
import java.io.IOException;

import org.ektorp.CouchDbConnector;
import org.ektorp.CouchDbInstance;
import org.ektorp.DbAccessException;
import org.ektorp.ReplicationCommand;
import org.ektorp.android.util.EktorpAsyncTask;
import org.ektorp.http.HttpClient;
import org.ektorp.impl.StdCouchDbInstance;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import ca.ilanguage.oprime.R;

import com.couchbase.touchdb.TDServer;
import com.couchbase.touchdb.TDView;
import com.couchbase.touchdb.ektorp.TouchDBHttpClient;
import com.couchbase.touchdb.javascript.TDJavaScriptViewCompiler;
import com.couchbase.touchdb.listener.TDListener;
import com.couchbase.touchdb.router.TDURLStreamHandlerFactory;

public abstract class HTML5ReplicatingActivity extends HTML5Activity {

  protected String DATABASE_NAME = "dboprimesample";
  // constants sample for DB views
  protected String dDocName = "orpime-local";
  protected String dDocId = "_design/" + dDocName;
  protected String byDateViewName = "byDate";

  protected String mLocalTouchDBFileDir = "";
  protected String mRemoteCouchDBURL = "";

  // couch internals
  protected static TDServer server;
  protected static HttpClient httpClient;
  protected TDListener mLocalCouchDBListener;

  // ektorp impl
  protected CouchDbInstance dbInstance;
  protected CouchDbConnector couchDbConnector;
  protected ReplicationCommand pushReplicationCommand;
  protected ReplicationCommand pullReplicationCommand;

  // splash screen
  protected SplashScreenDialog splashDialog;

  int mBackPressedCount = 0;

  // static inializer to ensure that touchdb:// URLs are handled properly
  {
    TDURLStreamHandlerFactory.registerSelfIgnoreError();
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    beginReplicating();
    super.onCreate(savedInstanceState);

    // show splash and start couch
    // showSplashScreen();
    // removeSplashScreen();

  }

  protected abstract void beginReplicating();
//  {
//    mLocalTouchDBFileDir = this.getFilesDir().getAbsolutePath()
//        + File.separator;
//    startTouchDB();
//    startEktorp();
//    turnOnDatabase();
//  }

  protected void onPause() {
    if (D)
      Log.v(TAG, "HTML5 Replicating onPause");
    super.onPause();
  }

  protected void onDestroy() {
    if (D)
      Log.v(TAG, "HTML5 Replicating onDestroy");
    super.onDestroy();
  }

  @Override
  public void onBackPressed() {
    mBackPressedCount++;

    if (mBackPressedCount < 2) {
      Toast.makeText(this, "Press again to exit.", Toast.LENGTH_LONG).show();

      if (httpClient != null) {
        httpClient.shutdown();
        return;
      }

      if (server != null) {
        server.close();
        return;
      }
    }
    super.onBackPressed();
  }

  protected void startTouchDB() {
    (new File(mLocalTouchDBFileDir)).mkdirs();
    try {
      server = new TDServer(mLocalTouchDBFileDir);
    } catch (IOException e) {
      Log.e(TAG, "Error starting TDServer", e);
    }

    setupTouchDBViews();
  }

  /**
   * sets up a javascript compiler for the views, which means normal views can
   * be used. If they are too slow could consider using ektorp?
   */
  protected void setupTouchDBViews() {
    if (D)
      Log.d(TAG, "Setting TDView with a Javascript map reduce compiler,"
          + " this allows compiling of any views downloaded from couchapp.");
    TDView.setCompiler(new TDJavaScriptViewCompiler());
  }

  /**
   * This opens security holes as other apps and computers on the local network
   * can access the touchdb, with no credentials, modify things, and those
   * modifications will be pushed to the server with the users credentials
   */
  @Deprecated
  public void turnOnDatabase() {
    (new File(mLocalTouchDBFileDir)).mkdirs();

    TDServer server;
    try {
      server = new TDServer(mLocalTouchDBFileDir);
      mLocalCouchDBListener = new TDListener(server, 8128);
      mLocalCouchDBListener.start();
      if (D) {
        Log.i(TAG, "Started the local offline couchdb database listener.");
      }

    } catch (IOException e) {
      Log.e(TAG, "Unable to create TDServer", e);
    }
  }

  protected void startEktorp() {
    Log.v(TAG, "starting ektorp");

    if (httpClient != null) {
      httpClient.shutdown();
    }

    httpClient = new TouchDBHttpClient(server);
    dbInstance = new StdCouchDbInstance(httpClient);

    HTML5SyncEktorpAsyncTask startupTask = new HTML5SyncEktorpAsyncTask() {

      @Override
      protected void doInBackground() {
        couchDbConnector = dbInstance.createConnector(DATABASE_NAME, true);
      }

      @Override
      protected void onSuccess() {
        Log.v(TAG, "Ektorp has started");
        mWebView.loadUrl(mInitialAppServerUrl);

        startReplications();
      }
    };
    startupTask.execute();
  }

  public void startReplications() {
    SharedPreferences prefs = PreferenceManager
        .getDefaultSharedPreferences(getBaseContext());

    pushReplicationCommand = new ReplicationCommand.Builder()
        .source(DATABASE_NAME)
        .target(prefs.getString("sync_url", mRemoteCouchDBURL))
        .continuous(true).build();

    HTML5SyncEktorpAsyncTask pushReplication = new HTML5SyncEktorpAsyncTask() {

      @Override
      protected void doInBackground() {
        dbInstance.replicate(pushReplicationCommand);
      }
    };

    pushReplication.execute();

    pullReplicationCommand = new ReplicationCommand.Builder()
        .source(prefs.getString("sync_url", mRemoteCouchDBURL))
        .target(DATABASE_NAME).continuous(true).build();

    HTML5SyncEktorpAsyncTask pullReplication = new HTML5SyncEktorpAsyncTask() {

      @Override
      protected void doInBackground() {
        dbInstance.replicate(pullReplicationCommand);
      }
    };

    pullReplication.execute();
  }

  public void stopEktorp() {
  }

  public abstract class HTML5SyncEktorpAsyncTask extends EktorpAsyncTask {

    @Override
    protected void onDbAccessException(DbAccessException dbAccessException) {
      Log.e(TAG, "DbAccessException in background", dbAccessException);
    }

  }

  /**
   * Removes the Dialog that displays the splash screen
   */
  protected void removeSplashScreen() {
    if (splashDialog != null) {
      splashDialog.dismiss();
      splashDialog = null;
    }
  }

  /**
   * Shows the splash screen over the full Activity
   */
  protected void showSplashScreen() {
    splashDialog = new SplashScreenDialog(this);
    splashDialog.show();
  }

  public class SplashScreenDialog extends Dialog {

    protected ProgressBar splashProgressBar;
    protected TextView splashProgressMessage;

    public SplashScreenDialog(Context context) {
      super(context, R.style.SplashScreenStyle);

      setContentView(R.layout.splashscreen);
      setCancelable(false);
    }

  }

}
