package com.example.glaciersoft;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.AutocompleteSupportFragment;
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {
    private GoogleMap mMap;
    private Button btnShowRoute;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private LatLng sourceLatLng;
    private LatLng destLatLng;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), "AIzaSyCN3ggNnAOp-rLmDbWNFk4UuG2_lZ9K-CQ");
        }


        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);


        AutocompleteSupportFragment sourceAutoComplete = (AutocompleteSupportFragment)
                getSupportFragmentManager().findFragmentById(R.id.autocomplete_source);

        sourceAutoComplete.setPlaceFields(Arrays.asList(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG));
        sourceAutoComplete.setHint("Enter source location");

        sourceAutoComplete.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(@NonNull Place place) {
                sourceLatLng = place.getLatLng();
                if (sourceLatLng != null) {
                    mMap.addMarker(new MarkerOptions().position(sourceLatLng).title("Source"));
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(sourceLatLng, 15));
                }
            }

            @Override
            public void onError(@NonNull Status status) {
                Toast.makeText(MainActivity.this, "Error: " + status.getStatusMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });


        AutocompleteSupportFragment destAutoComplete = (AutocompleteSupportFragment)
                getSupportFragmentManager().findFragmentById(R.id.autocomplete_destination);

        destAutoComplete.setPlaceFields(Arrays.asList(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG));
        destAutoComplete.setHint("Enter destination location");

        destAutoComplete.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(@NonNull Place place) {
                destLatLng = place.getLatLng();
                if (destLatLng != null) {
                    mMap.addMarker(new MarkerOptions().position(destLatLng).title("Destination"));
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(destLatLng, 15));
                }
            }

            @Override
            public void onError(@NonNull Status status) {
                Toast.makeText(MainActivity.this, "Error: " + status.getStatusMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });


        btnShowRoute = findViewById(R.id.btnShowRoute);
        btnShowRoute.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (sourceLatLng != null && destLatLng != null) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            drawRoute();
                        }
                    }).start();
                } else {
                    Toast.makeText(MainActivity.this, "Please select both locations",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            mMap.setMyLocationEnabled(true);
        }
    }

    private void drawRoute() {
        try {

            String urlString = "https://maps.googleapis.com/maps/api/directions/json?" +
                    "origin=" + sourceLatLng.latitude + "," + sourceLatLng.longitude +
                    "&destination=" + destLatLng.latitude + "," + destLatLng.longitude +
                    "&key=AIzaSyCN3ggNnAOp-rLmDbWNFk4UuG2_lZ9K-CQ";

            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            // Read the response
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            // Parse JSON response
            JSONObject jsonResponse = new JSONObject(response.toString());
            JSONObject route = jsonResponse.getJSONArray("routes").getJSONObject(0);
            String encodedPolyline = route.getJSONObject("overview_polyline").getString("points");

            List<LatLng> decodedPath = decodePolyline(encodedPolyline);


            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mMap.clear();

                    PolylineOptions options = new PolylineOptions()
                            .addAll(decodedPath)
                            .color(Color.BLUE)
                            .width(10);
                    mMap.addPolyline(options);

                    mMap.addMarker(new MarkerOptions().position(sourceLatLng).title("Source"));
                    mMap.addMarker(new MarkerOptions().position(destLatLng).title("Destination"));

                    LatLngBounds.Builder builder = new LatLngBounds.Builder();
                    for (LatLng point : decodedPath) {
                        builder.include(point);
                    }
                    LatLngBounds bounds = builder.build();
                    mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "Error finding route: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private List<LatLng> decodePolyline(String encoded) {
        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            LatLng p = new LatLng((double) lat / 1E5, (double) lng / 1E5);
            poly.add(p);
        }
        return poly;
    }
}