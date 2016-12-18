package xyz.osei.sensor;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity implements SensorRecorder.Listener {

    static ListeningManager listeningManager;

    private TextView text, errorText;
    private boolean hasPermissions = false;

    private final boolean LISTEN_BACKGROUND = true;
    private static final int MY_PERMISSIONS_REQUEST_ACCESS_LOCATION = 666;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        System.out.println("onCreate");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.sample_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSIONS_REQUEST_ACCESS_LOCATION);
        } else {
            hasPermissions = true;
        }

        if (listeningManager == null) {
            listeningManager = new ListeningManager(this);
        } else {
            listeningManager.setActivity(this);
        }

        Button button = (Button) findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                listeningManager.recordButton();
            }
        });

        text = (TextView) findViewById(R.id.counterText);
        errorText = (TextView) findViewById(R.id.errorText);

        onResume();
    }

    @Override
    protected void onResume() {
        System.out.println("onResume");
        super.onResume();
        if (hasPermissions) listeningManager.startListening();
    }


    @Override
    protected void onPause() {
        System.out.println("onPause");
        super.onPause();
        if (!LISTEN_BACKGROUND) listeningManager.stopListening();
    }

    private long prevKb = -1;

    @Override
    public void onEvent(long nEvents, long nBytes) {
        long nKb = nBytes / 1024;
        if (nKb != prevKb) {
            text.setText(nKb+" kB / "+nEvents+" events");
            prevKb = nKb;
        }
    }

    @Override
    public void onError(Throwable err) {
        listeningManager.stopListening();
        errorText.setText(err.getMessage());
    }
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_ACCESS_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    hasPermissions = true;
                    listeningManager.startListening();

                } else {
                    onError(new RuntimeException("permission denied"));
                }
                return;
            }
        }
    }
}
