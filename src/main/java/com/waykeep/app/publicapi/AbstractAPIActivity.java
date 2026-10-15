package com.waykeep.app.publicapi;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.waykeep.app.R;
import com.waykeep.app.services.TrackRecordingService;
import com.waykeep.app.services.TrackRecordingServiceConnection;
import com.waykeep.app.settings.PreferencesUtils;

abstract class AbstractAPIActivity extends AppCompatActivity {

    private final String TAG = AbstractAPIActivity.class.getSimpleName();

    private final TrackRecordingServiceConnection.Callback serviceConnectedCallback = (service, connection) -> {
        if (!isFinishing() && !isDestroyed()) {
            execute(service);
        }
        if (isPostExecuteStopService()) {
            connection.unbindAndStop(AbstractAPIActivity.this);
        } else {
            connection.unbind(AbstractAPIActivity.this);
        }
        finish();
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        splashScreen.setKeepOnScreenCondition(() -> true);

        if (PreferencesUtils.isPublicAPIenabled()) {
            Log.i(TAG, "Received and trying to execute requested action.");
            if (requiresForeground()) {
                TrackRecordingServiceConnection.executeForeground(this, serviceConnectedCallback);
            } else {
                TrackRecordingServiceConnection.execute(this, serviceConnectedCallback);
            }
        } else {
            Toast.makeText(this, getString(R.string.settings_public_api_disabled_toast), Toast.LENGTH_LONG).show();
            Log.w(TAG, "Public API is disabled; ignoring request.");
            finish();
        }
    }

    protected abstract void execute(TrackRecordingService service);

    protected abstract boolean isPostExecuteStopService();

    protected boolean requiresForeground() {
        return false;
    }
}
