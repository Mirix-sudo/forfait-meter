package com.example.forfaitmeter;

import android.app.Activity;
import android.net.TrafficStats;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

public class MainActivity extends Activity {
    private final Handler handler = new Handler();
    private TextView text;
    private long startMb = -1;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long rx = TrafficStats.getMobileRxBytes();
            long tx = TrafficStats.getMobileTxBytes();
            if (rx == TrafficStats.UNSUPPORTED || tx == TrafficStats.UNSUPPORTED) {
                text.setText("Compteur mobile non disponible sur ce téléphone");
            } else {
                long mb = (rx + tx) / 1024 / 1024;
                if (startMb < 0) startMb = mb;
                text.setText("Mobile depuis l'ouverture : " + (mb - startMb) + " Mo\n"
                        + "Compteur total : " + mb + " Mo");
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        text = new TextView(this);
        text.setTextSize(22);
        text.setPadding(48, 96, 48, 48);
        setContentView(text);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }
}
