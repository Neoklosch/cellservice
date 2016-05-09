package de.tu_berlin.snet.cellservice.observer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

import de.tu_berlin.snet.cellservice.CellService;
import de.tu_berlin.snet.cellservice.model.record.CellInfo;
import de.tu_berlin.snet.cellservice.model.FakeCellInfo;
import de.tu_berlin.snet.cellservice.util.Constants;

/**
 * Created by Friedhelm Victor on 3/29/16.
 * Observes the mobile network cell the device is / was connected to.
 * It keeps a reference to these variables. It also provides an Interface that can be used to
 * listen to Location Updates (triggered when the LAC between Cells change)
 */

public class CellInfoObserver implements Observer {
    public interface CellInfoListener {
        void onCellLocationChanged(CellInfo oldCell, CellInfo newCell);
        void onLocationUpdate(CellInfo oldCell, CellInfo newCell);
    }

    private final static int fiveMinutes = 5 * 60 * 1000;

    private static CellInfoObserver instance;

    private List<CellInfoListener> listeners = new ArrayList<>();

    private CellInfo mPreviousCellInfo;
    private CellInfo mCurrentCellInfo;

    private PhoneStateListener mPhoneStateListener;
    private TelephonyManager mTelephonyManager;

    // See http://stackoverflow.com/questions/14057273/android-singleton-with-global-context/14057777#14057777
    // for reference / double check synchronization
    public static CellInfoObserver getInstance() {
        if (instance == null) {
            instance = getInstanceSync();
        }
        return instance;
    }

    private static synchronized CellInfoObserver getInstanceSync() {
        if (instance == null) {
            instance = new CellInfoObserver();
        }
        return instance;
    }

    private CellInfoObserver() {
        mTelephonyManager = (TelephonyManager) CellService.get().getSystemService(Context.TELEPHONY_SERVICE);
        mPhoneStateListener = setupPhoneStateListener();
    }

    @Override
    public void start() {
        initializeOrRestoreCellInfos();
        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CELL_LOCATION);
    }

    @Override
    public void stop() {
        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
    }

    // TODO: POSSIBLE PROBLEM: WHAT IF WE ARE NEVER REGISTERED TO A NETWORK? -> NO CELLINFO!
    private void initializeOrRestoreCellInfos() {
        // Restore preferences
        SharedPreferences settings = CellService.get().getSharedPreferences(Constants.SHARED_PREFERENCES_FILE_NAME, 0);
        Gson gson = new Gson();
        // retrieve the last timestamp. Default value is 2016-01-01 01:01:01 CET
        long lastTimestamp = settings.getLong(Constants.SHARED_PREFERENCES_LAST_TIMESTAMP,
                Constants.SHARED_PREFERENCES_LAST_TIMESTAMP_DEFAULT);

        long fiveMinsAgo = System.currentTimeMillis() - fiveMinutes;
        if (fiveMinsAgo < lastTimestamp) {
            Log.e("PERSISTENCE", "retrieving cellinfos");
            Log.e("PERSISTENCE", String.format("retrieving cellinfos previous: %s",
                            settings.getString(Constants.SHARED_PREFERENCES_PREVIOUS_CELL, "")));
            Log.e("PERSISTENCE", String.format("retrieving cellinfos current: %s",
                    settings.getString(Constants.SHARED_PREFERENCES_CURRENT_CELL, "")));
            setPreviousCellInfo(gson.fromJson(
                    settings.getString(Constants.SHARED_PREFERENCES_PREVIOUS_CELL, ""),
                    CellInfo.class));
            setCurrentCellInfo(gson.fromJson(
                    settings.getString(Constants.SHARED_PREFERENCES_CURRENT_CELL,""),
                    CellInfo.class));
        } else {
            setPreviousCellInfo(getNewCellInfo()); // TODO: WORKS?... SOMETIMES SETS TO FAKECELLINFO
            setCurrentCellInfo(getNewCellInfo());
        }
    }

    private void persistCellInfosToPreferences() {
        Log.e("PERSISTENCE", "saving cellinfos");
        SharedPreferences settings = CellService.get().getSharedPreferences(Constants.SHARED_PREFERENCES_FILE_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        Gson gson = new Gson();

        Log.e("PERSISTENCE", "previousCellInfoJson: "+gson.toJson(getPreviousCellInfo()));
        editor.putString(Constants.SHARED_PREFERENCES_PREVIOUS_CELL,
                gson.toJson(getPreviousCellInfo()));
        editor.putString(Constants.SHARED_PREFERENCES_CURRENT_CELL,
                gson.toJson(getCurrentCellInfo()));

        editor.putLong(Constants.SHARED_PREFERENCES_LAST_TIMESTAMP, System.currentTimeMillis());

        editor.apply();
    }

    public void addListener(CellInfoListener toAdd) {
        listeners.add(toAdd);
    }

    public void removeListener(CellInfoListener toRemove) {
        listeners.remove(toRemove);
    }

    private void setPreviousCellInfo(CellInfo cellInfo) { mPreviousCellInfo = cellInfo; }
    public CellInfo getPreviousCellInfo() {
        return mPreviousCellInfo;
    }

    private boolean isLocationUpdate(CellInfo oldCellInfo, CellInfo newCellInfo) {
        return !oldCellInfo.isFake() && !newCellInfo.isFake() &&
                oldCellInfo.getLac() != newCellInfo.getLac();
    }

    private void setCurrentCellInfo(CellInfo cellInfo) { mCurrentCellInfo = cellInfo; }
    public CellInfo getCurrentCellInfo() { return new CellInfo(mCurrentCellInfo); }

    public CellInfo getNewCellInfo() {
        try {
            TelephonyManager tm = (TelephonyManager) CellService.get().getSystemService(Context.TELEPHONY_SERVICE);
            GsmCellLocation location = (GsmCellLocation) tm.getCellLocation();

            /* Why I use this Bitmask:
             * https://stackoverflow.com/questions/9808396/android-cellid-not-available-on-all-carriers#12969638
             */
            int cellID = location.getCid();// & 0xffff;
            int lac = location.getLac();

            String networkOperator = tm.getNetworkOperator();
            int mcc = Integer.parseInt(networkOperator.substring(0, 3));
            int mnc = Integer.parseInt(networkOperator.substring(3));

            return new CellInfo(cellID, lac, mnc, mcc, tm.getNetworkType());
        } catch (Exception e) {
            return new FakeCellInfo();
        }
    }

    public PhoneStateListener setupPhoneStateListener() {
        return new PhoneStateListener() {
            /**
             * Callback invoked when device cell id / location changes.
             */
            @SuppressLint("NewApi")
            public void onCellLocationChanged(CellLocation location) {
                CellInfo newCellInfo = getNewCellInfo();
                if(newCellInfo.isFake()) {
                    return;
                }

                // if the cell changed, adjust current and previous values and persist
                if (!getCurrentCellInfo().equals(newCellInfo)) {
                    setPreviousCellInfo(getCurrentCellInfo());
                    setCurrentCellInfo(newCellInfo);
                    persistCellInfosToPreferences();
                } else {
                    return; // Cells are the same
                }

                if(!getPreviousCellInfo().isFake()) {
                    // At this point current and previous CellInfo should not be fake

                    for (CellInfoListener cil : listeners)
                        cil.onCellLocationChanged(getPreviousCellInfo(), getCurrentCellInfo());

                    if (isLocationUpdate(getPreviousCellInfo(), newCellInfo)) {
                        for (CellInfoListener cil : listeners)
                            cil.onLocationUpdate(getPreviousCellInfo(), newCellInfo);
                    }
                }

            }
        };
    }
}
