package snet.tu_berlin.de.mycellservice;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.widget.Toast;

public class CellService extends Service {

    MobileNetworkHelper mobileNetworkHelper;

    public CellService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mobileNetworkHelper = new MobileNetworkHelper(this);
        Toast.makeText(this, "Service is created", Toast.LENGTH_LONG).show();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Toast.makeText(this, "Service is started", Toast.LENGTH_LONG).show();
        // catching data if it has been sent
        try {
            String message;
            message = intent.getStringExtra("key");
            Toast.makeText(this, "data sent to service was \"" + message + "\"", Toast.LENGTH_LONG).show();
            // otherwise catch exception
        } catch (Exception e) {
            Toast.makeText(this, "didn't receive any data, but caught exception "+e.toString(), Toast.LENGTH_LONG).show();
        }

        mobileNetworkHelper.listenForEvents();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mobileNetworkHelper.stopListening();
        mobileNetworkHelper = null;
        Toast.makeText(this, "Service is stopped", Toast.LENGTH_LONG).show();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

}
