package uk.ac.shef.oak.com4510;

//////////////////////////////////////////////////
//                                              //
//                  Imports                     //
//                                              //
//////////////////////////////////////////////////

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;

import java.text.DateFormat;
import java.util.Date;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import uk.ac.shef.oak.com451.R;
import uk.ac.shef.oak.com4510.sensors.Accelerometer;
import uk.ac.shef.oak.com4510.sensors.Barometer;
import uk.ac.shef.oak.com4510.sensors.Thermometer;

public class MapsActivity extends AppCompatActivity implements GoogleMap.OnMyLocationButtonClickListener, OnMapReadyCallback {

    //////////////////////////////////////////////////
    //                                              //
    //          Initializing Variables              //
    //                                              //
    //////////////////////////////////////////////////

    // Activity Related Variables
    private static AppCompatActivity activity;
    private PendingIntent mLocationPendingIntent;
    public static AppCompatActivity getActivity() {
        return activity;
    }
    public static void setActivity(AppCompatActivity activity) {
        MapsActivity.activity = activity;
    }

    // View Related Variables
    private String mdate;
    private MapView mapView;
    private Button mButtonStart, mButtonPause, mButtonStop;

    // Google Maps Related Variables
    private static GoogleMap mMap;
    public static GoogleMap getMap() {
        return mMap;
    }
    private static Polyline polyline;
    private static PolylineOptions polylineOptions;
    public static PolylineOptions getPolylineOptions() {return polylineOptions;}
    public static Polyline getPolyline() {return polyline;}
    private static Boolean start_trip;
    static Boolean isStartPoint(){return start_trip;}
    static void stopStartPoint(){start_trip=false;}

    // Timer Related Variables
    private TextView timer ;
    private long MillisecondTime, StartTime, TimeBuff, UpdateTime = 0L ;
    private Handler handler;
    private int Seconds, Minutes, MilliSeconds ;

    // Location Related Variables
    private static final int ACCESS_FINE_LOCATION = 123;
    private LocationRequest mLocationRequest;
    private FusedLocationProviderClient mFusedLocationClient;
    private Location mCurrentLocation;
    private String mLastUpdateTime;

    // Sensor Related Variables
    private Barometer barometer;
    private Thermometer thermometer;
    private Accelerometer accelerometer;

    // Result tracking variables
//    private List<Double> lat_list, lng_list; // results storing for now
//    private List<String> time_list;

    //////////////////////////////////////////////////
    //                                              //
    //                Initialization                //
    //                                              //
    //////////////////////////////////////////////////

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        setActivity(this);

        // Initialize result tracking lists
//        lat_list = new LinkedList();
//        lng_list = new LinkedList();
//        time_list = new LinkedList();
        start_trip = true;

        // Initialize Sensors
        barometer= new Barometer(this);
        thermometer = new Thermometer(this);
        accelerometer= new Accelerometer(this, barometer);

        Bundle b = getIntent().getExtras();
        //Date from b.date
        if(b != null) {
            getSupportActionBar().setTitle(b.getString("name"));
            mdate = b.getString("date");
            Log.i("date: ", mdate);
            Log.i("route_name", b.getString("name"));
        }

        timer = findViewById(R.id.timer);
        handler = new Handler() ;

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        mButtonStart = findViewById(R.id.button_start);
        mButtonStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mButtonStart.isEnabled()) {
                    // start sensing
                    startLocationUpdates(getApplicationContext());
                    accelerometer.startAccelerometerRecording();
                    barometer.startSensingPressure(accelerometer);
                    thermometer.startSensingTemperature(accelerometer);
                    if (mButtonPause != null)
                        mButtonPause.setEnabled(true);
                    mButtonStart.setEnabled(false);

                    //Timer
                    StartTime = SystemClock.uptimeMillis();
                    handler.postDelayed(runnable, 0);
                }
            }
        });

        mButtonStart.setEnabled(true);

        mButtonPause = findViewById(R.id.button_pause);
        mButtonPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mButtonPause.isEnabled()) {
                    stopLocationUpdates();
                    accelerometer.stopAccelerometer();
                    barometer.stopBarometer();
                    thermometer.stopThermometer();
                    if (mButtonStart != null)
                        mButtonStart.setEnabled(true);
                    mButtonPause.setEnabled(false);

                    //Timer
                    TimeBuff += MillisecondTime;
                    handler.removeCallbacks(runnable);
                }
            }
        });

        mButtonPause.setEnabled(false);

        // Stop Button Initialization
        mButtonStop = (Button) findViewById(R.id.button_stop);
        // if Stop is clicked, ask the user if they're sure they want to stop
        // if they are then show them the end screen with a go Home/gallery button(s)
        mButtonStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mButtonStop.setEnabled(false);
                // show pop-up for 'are you sure?'
                // if accepted, then stop updates
                // then show activity with results - approximate distance moved, time taken, number of snapshots
                // average temp, average pressure, maybe select random snaps if present?
            }
        });
        mButtonStop.setEnabled(true);

        initLocations();
    }

    //////////////////////////////////////////////////
    //                                              //
    //               On Resume                      //
    //                                              //
    //////////////////////////////////////////////////

    @Override
    protected void onResume() {
        super.onResume();
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(20000);
        mLocationRequest.setFastestInterval(10000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

    }

    // track and show time
    public Runnable runnable = new Runnable() {

        public void run() {
            MillisecondTime = SystemClock.uptimeMillis() - StartTime;
            UpdateTime = TimeBuff + MillisecondTime;
            Seconds = (int) (UpdateTime / 1000);
            Minutes = Seconds / 60;
            Seconds = Seconds % 60;
            MilliSeconds = (int) (UpdateTime % 1000);
            String timeText = "" + Minutes + ":"
                    + String.format("%02d", Seconds) + ":"
                    + String.format("%03d", MilliSeconds);
            timer.setText(timeText);
            handler.postDelayed(this, 0);
        }

    };

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        polylineOptions = new PolylineOptions().clickable(true)
                .color(Color.BLUE)
                .width(10)
                .geodesic(true);
        polyline = mMap.addPolyline(polylineOptions);
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);
        mMap.setOnMyLocationButtonClickListener(this);

        // Add a marker in Sydney and move the camera
        //LatLng sydney = new LatLng(-34, 151);
        //mMap.addMarker(new MarkerOptions().position(sydney).title("Marker in Sydney"));
        //mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(sydney, 14.0f));
    }

    @Override
    public boolean onMyLocationButtonClick() {
        mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                mLocationCallback, null /* Looper */);
        return false;
    }

    //////////////////////////////////////////////////
    //                                              //
    //              Location Updates                //
    //                                              //
    //////////////////////////////////////////////////

    // make sure that we have permissions to access the location data
    private void initLocations() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        ACCESS_FINE_LOCATION);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }

            return;
        }
    }

    /**
     * Starts Location Updates
     */
    private void startLocationUpdates(Context context) {
        Intent intent = new Intent(context, LocationService.class);
        mLocationPendingIntent = PendingIntent.getService(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Task<Void> locationTask = mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                    mLocationPendingIntent);
            if (locationTask != null) {
                locationTask.addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        if (e instanceof ApiException) {
                            Log.w("MapsActivity", ((ApiException) e).getStatusMessage());
                        } else {
                            Log.w("MapsActivity", e.getMessage());
                        }
                    }
                });

                locationTask.addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        Log.d("MapsActivity", "restarting gps successful!");
                    }
                });
            }
        }
    }

    /**
     * it stops the location updates
     */
    private void stopLocationUpdates(){
        mFusedLocationClient.removeLocationUpdates(mLocationPendingIntent);
    }

    // Location callback gets the updates
    LocationCallback mLocationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            super.onLocationResult(locationResult);
            // get current location and time at which it was taken
            mCurrentLocation = locationResult.getLastLocation();
            mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
            if (start_trip){
                setMarker(new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude()),
                        mMap, "Start of Trip", true, 14.0f);
                start_trip = false;
            }
            // save in lists to add to rooms in the end
//            lat_list.add(mCurrentLocation.getLatitude());
//            lng_list.add(mCurrentLocation.getLongitude());
//            time_list.add(mLastUpdateTime);
            // track on console
            //Log.i("MAP", "new location " + mCurrentLocation.toString());
            // Move to current position on the map
            // Note: might be annoying for the user if they manually moved the map
//            if (mMap != null)
//                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude()), 14.0f));
        }
    };

    @SuppressLint("MissingPermission")
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                            mLocationCallback, null /* Looper */);
                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    /**
     * Sets Marker on Map
     * @param pos  - LatLong Position on where to put the marker
     * @param map - map on where to put the marker
     * @param title - title to add to marker
     */
    static void setMarker(LatLng pos, GoogleMap map, String title) {
        setMarker(pos, map, title, false);
    }

    /**
     * Sets Marker on Map
     * @param pos  - LatLong Position on where to put the marker
     * @param map - map on where to put the marker
     * @param title - title to add to marker
     * @param move_camera - if true sets camera on marker
     */
    static void setMarker(LatLng pos, GoogleMap map, String title, boolean move_camera) {
        setMarker(pos, map, title, move_camera, -1);
    }

    /**
     * Sets Marker on Map
     * @param pos  - LatLong Position on where to put the marker
     * @param map - map on where to put the marker
     * @param title - title to add to marker
     * @param move_camera - if true sets camera on marker
     * @param zoom - zoom of google maps view if move camera is set to true
     */
    static void setMarker(LatLng pos, GoogleMap map, String title, boolean move_camera, float zoom){
        map.addMarker(new MarkerOptions().position(pos).title(title));
        if (move_camera) {
            if (zoom >= 0) {
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, zoom));
            } else {
                mMap.moveCamera(CameraUpdateFactory.newLatLng(pos));
            }
        }
    }

}

