package uk.ac.shef.oak.com4510;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polyline;
import java.text.DateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import uk.ac.shef.oak.com451.R;
import uk.ac.shef.oak.com4510.sensors.Accelerometer;
import uk.ac.shef.oak.com4510.sensors.Barometer;
import uk.ac.shef.oak.com4510.sensors.Thermometer;
import uk.ac.shef.oak.com4510.utilities.Notification;

/**
 * NVE Location Service is a never ending background service which tracks the location, temperature and barometric pressure
 * of the phone.
 * */
public class NVELocationService extends Service {
    // values
    private Location mCurrentLocation;
    private String mLastUpdateTime;
    private Float mCurrentTemp, mCurrentPress;
    private Polyline route;

    // list of values stored for when the app is removed/turned-off
    private List<Location> mSavedLocations;
    private List<String> mSavedUpdateTimes;
    private List<Float> mSavedTemp, mSavedPress;
    private List<LatLng> savedRoute;

    // sensors
    private Accelerometer accelerometer;
    private Barometer barometer;
    private Thermometer thermometer;
    private LocationRequest mLocationRequest;
    private FusedLocationProviderClient mFusedLocationClient;

    // notification related values
    protected static final int NOTIFICATION_ID = 1001;
    private static String TAG = "LocationService";
    private static Service mCurrentService;

    // booleans to set points tracked when the app wasn't running on the map
    private String tracking_mode;
    private Boolean trackingChecker;

    /**
     * On Create - Initializes variables.
     */
    @Override
    public void onCreate(){
        super.onCreate();
        // initializing sensors
        barometer = new Barometer(this);
        thermometer = new Thermometer(this);
        accelerometer = new Accelerometer(this, barometer, thermometer);
        // initializing location request and client
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(20000);
        mLocationRequest.setFastestInterval(5000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        // initialize value storages for when the app isn't on the screen
        mSavedLocations = new LinkedList<>();
        mSavedUpdateTimes = new LinkedList<>();
        // shouldn't update temp and press on restart
        if (mSavedTemp == null){
            mSavedTemp = new LinkedList<>();
        }
        if (mSavedPress == null) {
            mSavedPress = new LinkedList<>();
        }
        // shouldn't update the saved route on restart
        if (savedRoute == null) {
            savedRoute = new LinkedList<>();
        }
        trackingChecker = false;

        // if version higher than oreo then restart foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            restartForeground();
        }
        mCurrentService = this;
    }

    /**
     * On Start Command - when the service is started, start sensing.
     * */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.d(TAG, "restarting Service !!");

        // it has been killed by Android and now it is restarted. We must make sure to have reinitialised everything
        if (intent == null) {
            ProcessMainClass bck = new ProcessMainClass();
            bck.launchService(this);
        }

        // make sure you call the startForeground on onStartCommand because otherwise
        // when we hide the notification on onScreen it will not restart in Android 6 and 7
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            restartForeground();
        }

        // start sensing
        accelerometer.startAccelerometerRecording();
        barometer.startSensingPressure(accelerometer);
        thermometer.startSensingTemperature(accelerometer);
        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, null /* Looper */);

        return START_STICKY;
    }

    /**
     * Receives location updates and processes them.
     * */
    LocationCallback mLocationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            super.onLocationResult(locationResult);
            // get current location and time at which it was taken
            mCurrentLocation = locationResult.getLastLocation();
            mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());


            SharedPreferences prefs = getSharedPreferences("uk.ac.shef.oak.ServiceRunning", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            // try to get the sensor values, if not, it means that there is something that isn't working with
            // one of the sensors or we don't have access to one of them
            try {
                mCurrentPress = barometer.getCurrentPressure();
                editor.putFloat("current_pressure", mCurrentPress);
            }
            catch (Exception e){
            }
            try{
                mCurrentTemp = thermometer.getCurrentTemperature();
                editor.putFloat("current_temperature", mCurrentTemp);
            }
            catch (Exception e){
            }

            editor.putString("current_updateTime", mLastUpdateTime); // not necessary
            editor.apply();
            // store update times, pressure and temperature in list
            mSavedUpdateTimes.add(mLastUpdateTime);
            mSavedPress.add(mCurrentPress);
            mSavedTemp.add(mCurrentTemp);
//            Log.i("test",mCurrentLocation.toString());
            if (MapsActivity.getActivity()!=null)
                // if the activity is not null, then on callback
                // any modification of the user interface must be done on the UI Thread. The Intent Service is running
                // in its own thread, so it cannot communicate with the UI.
                MapsActivity.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            if (MapsActivity.getMap() != null) {
                                route = MapsActivity.getPolyline();
                                List<LatLng> points = route.getPoints();
                                // if the activity was null and tracking was running, when it is back, add the stored points to the polyline
                                // and store the temperature and barometric pressure
                                if (trackingChecker){
                                    for (Location listLocation:mSavedLocations){
                                        points.add(new LatLng(listLocation.getLatitude(), listLocation.getLongitude()));
                                        // update saved routes points
                                        savedRoute.add(new LatLng(listLocation.getLatitude(), listLocation.getLongitude()));
                                    }
                                }
                                points.add(new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude()));
                                // update saved routes points
                                savedRoute.add(new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude()));
                                route.setPoints(points);
                                // set starting marker
                                if (MapsActivity.isStartPoint()){
                                    MapsActivity.setMarker(new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude()),
                                            MapsActivity.getMap(), "Start of Trip", true, 14.0f);
                                    MapsActivity.stopStartPoint();
                                }

                                CameraUpdate zoom = CameraUpdateFactory.zoomTo(15);
                                // it centers the camera around the new location
                                MapsActivity.getMap().moveCamera(CameraUpdateFactory.newLatLng(new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude())));
                                // it moves the camera to the selected zoom
                                MapsActivity.getMap().animateCamera(zoom);
                            }
                        } catch (Exception e ){
                            Log.e("LocationService", "Error cannot write on map "+e.getMessage());
                        }
                    }
                });
            else{ // store values in lists to add them into the polyline later on
                if (!trackingChecker){
                    trackingChecker = true;
                    mSavedLocations = new LinkedList<>();
                }
                mSavedLocations.add(mCurrentLocation);
            }
        }
    };

    //////////////////////////////////////////////////
    //                                              //
    //          Background Process Functions        //
    //                                              //
    //////////////////////////////////////////////////


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * it starts the process in foreground. Normally this is done when screen goes off
     * THIS IS REQUIRED IN ANDROID 8 :
     * "The system allows apps to call Context.startForegroundService()
     * even while the app is in the background.
     * However, the app must call that service's startForeground() method within five seconds
     * after the service is created."
     */
    public void restartForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Log.i(TAG, "restarting foreground");
            try {
                Notification notification = new Notification();
                startForeground(NOTIFICATION_ID, notification.setNotification(this, "My Routes", "Currently Tracking.", R.drawable.ic_sleep));
                Log.i(TAG, "restarting foreground successful");
            } catch (Exception e) {
                Log.e(TAG, "Error in notification " + e.getMessage());
            }
        }
    }

    /**
     * Gets average of float list - can refactor to numeric if needed.
     **/
    private float getAverage(List<Float> fList){
        float avg = 0f;
        for (float add:fList){
            avg += add;
        }
        return avg/fList.size();
    }

    /**
     * On Destroy, if tracking was active, then restart the service, if not, don't, and do actions according to
     * tracking status.
     * */
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy called");
        // We don't want to keep the service running if it was destroyed on pause or on stop
        SharedPreferences prefs = getSharedPreferences("uk.ac.shef.oak.ServiceRunning", MODE_PRIVATE);
        tracking_mode = prefs.getString("tracking", "DEFAULT");
//        Log.i("Shared Preferences", tracking_mode);
        if (tracking_mode.equals("started")) {
            Intent broadcastIntent = new Intent(Globals.RESTART_INTENT);
            sendBroadcast(broadcastIntent);
        }
        // if stopped calculate the average temperature and pressure and store them in the shared preferences
        // also stop the service
        else if (tracking_mode.equals("stopped")){
            SharedPreferences.Editor editor = prefs.edit();
            if (mSavedTemp.size() > 0) {
                editor.putFloat("average_temperature", getAverage(mSavedTemp));
            }
            else {
                editor.remove("average_temperature");
            }
            if (mSavedPress.size() > 0) {
                editor.putFloat("average_pressure", getAverage(mSavedPress));
            }
            else{
                editor.remove("average_pressure");
            }
            editor.apply();
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
            accelerometer.stopAccelerometer();
        }
        // if paused, stop the service
        else {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
            accelerometer.stopAccelerometer();
        }
    }


    /**
     * this is called when the process is killed by Android
     *
     * @param rootIntent
     */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.i(TAG, "onTaskRemoved called");
        // restart the never ending service if tracking was active
        SharedPreferences prefs= getSharedPreferences("uk.ac.shef.oak.ServiceRunning", MODE_PRIVATE);
        tracking_mode = prefs.getString("tracking", "DEFAULT");
//        Log.i("Shared Preferences", tracking_mode);
        // if not stopped then store current polyline in preferences
        if (!tracking_mode.equals("stopped")) {
            String lats = "";
            String lngs = "";
            for (LatLng latlng : savedRoute) {
                lats += latlng.latitude + ";";
                lngs += latlng.longitude + ";";
            }
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("polyline_lats", lats);
            editor.putString("polyline_lngs", lngs);
            editor.apply();
        }

        // restart service if tracking
        if (tracking_mode.equals("started")) {
            Intent broadcastIntent = new Intent(Globals.RESTART_INTENT);
            sendBroadcast(broadcastIntent);
        }
        else {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
            accelerometer.stopAccelerometer();
        }
    }
}