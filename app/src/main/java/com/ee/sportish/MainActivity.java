package com.ee.sportish;

import android.Manifest;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.List;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, LocationListener {

    private final static String TAG = "MainActivity";

    private GoogleMap mGoogleMap;
    private Menu mOptionsMenu;
    private NotificationManager mNotificationManager;
    private BroadcastReceiver mBroadcastReceiver;
    private LocationManager locationManager;

    private String provider;

    private int markerCount = 0;
    private Location locationPrevious;
    private Location latestMarkerLocation;
    private Location initialLocation;
    private Location initialTrackLocation;
    private boolean markerUsed;
    private boolean firstTrackLocationUsed = false;

    private double trackDistance;
    private double wpDistance;
    private double totalDistance;

    private double trackLine;
    private double wpLine;
    private double totalLine;
    private double sessionTotalLine;

    private Polyline mPolyline;
    private PolylineOptions mPolylineOptions;


    private TextView textViewWPCount;
    private TextView textViewSpeed;
    private TextView accuracy;

    private TextView textviewTrackDistance;
    private TextView textviewWpDistance;

    private TextView textviewTotalDistance;
    private TextView textviewTrackLine;
    private TextView textviewWpLine;
    private TextView textviewTotalLine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);


        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);


        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        Criteria criteria = new Criteria();

        // get the location provider (GPS/CEL-towers, WIFI)
        provider = locationManager.getBestProvider(criteria, false);

        //Log.d(TAG, provider);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "No COARSE location permissions!");

        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "No FINE location permissions!");

        }

        locationPrevious = locationManager.getLastKnownLocation(provider);

        if (locationPrevious != null) {
            initialLocation = locationPrevious;
        }


        textViewWPCount = (TextView) findViewById(R.id.textview_wpcount);
        textViewSpeed = (TextView) findViewById(R.id.textview_speed);
        accuracy = (TextView) findViewById(R.id.accuracy_label);

        textviewTrackDistance = (TextView) findViewById(R.id.textview_track_distance);
        textviewWpDistance = (TextView) findViewById(R.id.textview_wp_distance);
        textviewTotalDistance = (TextView) findViewById(R.id.textview_total_distance);
        textviewTrackLine = (TextView) findViewById(R.id.textview_track_line);
        textviewWpLine = (TextView) findViewById(R.id.textview_wp_line);
        textviewTotalLine = (TextView) findViewById(R.id.textview_total_line);
        mainNotification();

        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String filtername = intent.getAction();
                if (filtername.equals("notification-broadcast-addwaypoint"))
                    buttonAddWayPointClicked(null);
                if (filtername.equals("notification-broadcast-resettotal"))
                    buttonCResetClicked(null);
            }
        };

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("notification-broadcast-addwaypoint");
        intentFilter.addAction("notification-broadcast-resettotal");
        registerReceiver(mBroadcastReceiver, intentFilter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        mOptionsMenu = menu;
        mOptionsMenu.findItem(R.id.menu_keepmapcentered).setChecked(true);
        mOptionsMenu.findItem(R.id.menu_mylocation).setChecked(true);
        updateMyLocation();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_mylocation:
                item.setChecked(!item.isChecked());
                updateMyLocation();
                return true;
            case R.id.menu_trackposition:
                item.setChecked(!item.isChecked());
                updateTrackPosition();
                return true;
            case R.id.menu_keepmapcentered:
                item.setChecked(!item.isChecked());
                return true;
            case R.id.menu_map_type_hybrid:
            case R.id.menu_map_type_none:
            case R.id.menu_map_type_normal:
            case R.id.menu_map_type_satellite:
            case R.id.menu_map_type_terrain:
                item.setChecked(true);
                updateMapType();
                return true;

            case R.id.menu_map_zoom_10:
            case R.id.menu_map_zoom_15:
            case R.id.menu_map_zoom_20:
            case R.id.menu_map_zoom_in:
            case R.id.menu_map_zoom_out:
            case R.id.menu_map_zoom_fittrack:
                updateMapZoomLevel(item.getItemId());
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void updateMapZoomLevel(int itemId) {
        if (!checkReady()) {
            return;
        }

        switch (itemId) {
            case R.id.menu_map_zoom_10:
                mGoogleMap.moveCamera(CameraUpdateFactory.zoomTo(10));
                break;
            case R.id.menu_map_zoom_15:
                mGoogleMap.moveCamera(CameraUpdateFactory.zoomTo(15));
                break;
            case R.id.menu_map_zoom_20:
                mGoogleMap.moveCamera(CameraUpdateFactory.zoomTo(20));
                break;
            case R.id.menu_map_zoom_in:
                mGoogleMap.moveCamera(CameraUpdateFactory.zoomIn());
                break;
            case R.id.menu_map_zoom_out:
                mGoogleMap.moveCamera(CameraUpdateFactory.zoomOut());
                break;
            case R.id.menu_map_zoom_fittrack:
                updateMapZoomFitTrack();
                break;
        }
    }

    private void updateMapZoomFitTrack() {
        if (mPolyline == null) {
            return;
        }

        List<LatLng> points = mPolyline.getPoints();

        if (points.size() <= 1) {
            return;
        }
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (LatLng point : points) {
            builder.include(point);
        }
        LatLngBounds bounds = builder.build();
        int padding = 2; // offset from edges of the map in pixels
        mGoogleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));

    }

    private void updateTrackPosition() {
        if (!checkReady()) {
            return;
        }

        if (mOptionsMenu.findItem(R.id.menu_trackposition).isChecked()) {
            mPolylineOptions = new PolylineOptions().width(5).color(Color.RED);
            mPolyline = mGoogleMap.addPolyline(mPolylineOptions);
        }


    }

    private void updateMapType() {
        if (!checkReady()) {
            return;
        }

        if (mOptionsMenu.findItem(R.id.menu_map_type_normal).isChecked()) {
            mGoogleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        } else if (mOptionsMenu.findItem(R.id.menu_map_type_hybrid).isChecked()) {
            mGoogleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
        } else if (mOptionsMenu.findItem(R.id.menu_map_type_none).isChecked()) {
            mGoogleMap.setMapType(GoogleMap.MAP_TYPE_NONE);
        } else if (mOptionsMenu.findItem(R.id.menu_map_type_satellite).isChecked()) {
            mGoogleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
        } else if (mOptionsMenu.findItem(R.id.menu_map_type_terrain).isChecked()) {
            mGoogleMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
        }

    }

    private boolean checkReady() {
        if (mGoogleMap == null) {
            Toast.makeText(this, R.string.map_not_ready, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void updateMyLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "No COARSE location permissions!");
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "No FINE location permissions!");
        }

        if (mOptionsMenu.findItem(R.id.menu_mylocation).isChecked()) {
            mGoogleMap.setMyLocationEnabled(true);
            return;
        }

        mGoogleMap.setMyLocationEnabled(false);
    }

    //adds new waypoint on map
    public void buttonAddWayPointClicked(View view) {
        if (locationPrevious == null) {
            return;
        }
        markerCount++;
        markerUsed = true;
        wpDistance = 0;
        wpLine = 0;
        latestMarkerLocation = locationPrevious;
        mGoogleMap.addMarker(new MarkerOptions().position(new LatLng(locationPrevious.getLatitude(), locationPrevious.getLongitude())).title(Integer.toString(markerCount)));
        textViewWPCount.setText(Integer.toString(markerCount));
    }

    //resets overall tracking
    public void buttonCResetClicked(View view) {
        totalDistance = 0;
        sessionTotalLine = 0;

        totalLine = 0;
        initialLocation = null;
    }

    //resets track and waypoint tracking
    public void buttonCResetAllClicked(View view) {
        mGoogleMap.clear();
        wpDistance = 0;
        wpLine = 0;
        trackDistance = 0;
        trackLine = 0;
        markerCount = 0;
        markerUsed = false;
        firstTrackLocationUsed = false;
        mOptionsMenu.findItem(R.id.menu_trackposition).setChecked(false);
        textViewWPCount.setText(Integer.toString(markerCount));


    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mGoogleMap = googleMap;

        //mGoogleMap.setMyLocationEnabled(false);

        //LatLng latLngITK = new LatLng(59.3954789, 24.6621282);
        //mGoogleMap.addMarker(new MarkerOptions().position(latLngITK).title("ITK"));
        //mGoogleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLngITK, 17));

        // set zoom level to 15 - street
        mGoogleMap.moveCamera(CameraUpdateFactory.zoomTo(17));

        // if there was initial location received, move map to it
        if (locationPrevious != null) {
            mGoogleMap.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(locationPrevious.getLatitude(), locationPrevious.getLongitude())));
        }
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();
    }

    @Override
    public void onLocationChanged(Location location) {
        LatLng newPoint = new LatLng(location.getLatitude(), location.getLongitude());

        if (mGoogleMap == null) return;
        double distanceToLastPoint = 0;
        try {
            distanceToLastPoint = location.distanceTo(locationPrevious);
        } catch (NullPointerException e) {
            Log.d(TAG, "No previous location found");
        }

        //tracks overall line distance
        if (initialLocation == null) {
            initialLocation = location;
        } else {
            sessionTotalLine = location.distanceTo(initialLocation) + totalLine;
            textviewTotalLine.setText(String.valueOf(Math.round(sessionTotalLine)));
        }

        //keeps map centered on position
        if (mOptionsMenu.findItem(R.id.menu_keepmapcentered).isChecked() || locationPrevious == null) {
            mGoogleMap.moveCamera(CameraUpdateFactory.newLatLng(newPoint));
        }

        //tracks polyline
        if (mOptionsMenu.findItem(R.id.menu_trackposition).isChecked()) {

            if (!firstTrackLocationUsed) {
                initialTrackLocation = location;
                firstTrackLocationUsed = true;
            }

            List<LatLng> points = mPolyline.getPoints();
            points.add(newPoint);
            mPolyline.setPoints(points);
            trackDistance = distanceToLastPoint + trackDistance;
            trackDistance = Math.round(trackDistance);
            textviewTrackDistance.setText(String.valueOf(trackDistance));
            trackLine = Math.round(location.distanceTo(initialTrackLocation));
            textviewTrackLine.setText(String.valueOf(trackLine));
        } else {
            firstTrackLocationUsed = false;
            trackDistance = 0;
        }
        //tracks overall distance
        totalDistance = distanceToLastPoint + totalDistance;
        totalDistance = Math.round(totalDistance);
        textviewTotalDistance.setText(String.valueOf(totalDistance));

        accuracy.setText("Accuracy: " + String.valueOf(Math.round(location.getAccuracy())) + " m");

        //tracks when marker has been placed
        if (markerUsed) {
            wpDistance = wpDistance + distanceToLastPoint;
            wpDistance = Math.round(wpDistance);
            textviewWpDistance.setText(String.valueOf(wpDistance));
            wpLine = location.distanceTo(latestMarkerLocation);
            wpLine = Math.round(wpLine);
            textviewWpLine.setText(String.valueOf(wpLine));
        }
        //calculates the tempo by using the time that was between latest two locations and converts seconds to min:sec
        double speedSeconds = 1000 / (distanceToLastPoint / 0.5);
        double min = speedSeconds / 60;
        double sec = speedSeconds % 60;
        textViewSpeed.setText(String.format("%s:%s /km", Math.round(min), Math.round(sec)));
        locationPrevious = location;
        mainNotification();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    @Override
    protected void onResume() {
        super.onResume();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "No COARSE location permissions!");
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "No FINE location permissions!");
        }

        if (locationManager != null) {
            locationManager.requestLocationUpdates(provider, 500, 1, this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mBroadcastReceiver);
        super.onDestroy();
    }

    public void mainNotification() {

        // gets the view layout
        RemoteViews remoteView = new RemoteViews(
                getPackageName(), R.layout.notification);

        // sets current numbers to the notification
        remoteView.setTextViewText(R.id.WP_distance_textView, Math.round(wpDistance) + " m");
        remoteView.setTextViewText(R.id.total_distance_textView, Math.round(totalDistance) + " m");

        // define intents
        PendingIntent pIntentAddWaypoint = PendingIntent.getBroadcast(
                this,
                0,
                new Intent("notification-broadcast-addwaypoint"),
                0
        );

        PendingIntent pIntentResetTotal = PendingIntent.getBroadcast(
                this,
                0,
                new Intent("notification-broadcast-resettotal"),
                0
        );
        // brings back already running activity
        PendingIntent pIntentOpenActivity = PendingIntent.getActivity(
                this,
                0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT
        );

        // attaches events to elements
        remoteView.setOnClickPendingIntent(R.id.new_marker, pIntentAddWaypoint);
        remoteView.setOnClickPendingIntent(R.id.reset_notif, pIntentResetTotal);
        remoteView.setOnClickPendingIntent(R.id.icon, pIntentOpenActivity);

        // build notification
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setContent(remoteView)
                        .setSmallIcon(R.drawable.ic_location)
                        .setOngoing(true);

        // notify the manager
        mNotificationManager.notify(0, mBuilder.build());

    }


}
