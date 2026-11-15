package com.example.forfaitmeter;

import android.app.Activity;
import android.content.SharedPreferences;
import android.net.TrafficStats;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final String PREFS = "forfait";
    private static final long MB = 1024L * 1024L;
    private static final long HOUR_MS = 3600000L;

    private final Handler handler = new Handler();
    private SharedPreferences prefs;
    private TextView status, detail;
    private EditText volumeInput, hoursInput;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            update();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 96, 48, 48);

        status = new TextView(this);
        status.setTextSize(28);
        root.addView(status);

        detail = new TextView(this);
        detail.setTextSize(18);
        root.addView(detail);

        volumeInput = new EditText(this);
        volumeInput.setHint("Volume du forfait en Mo, ex. 476");
        volumeInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(volumeInput);

        hoursInput = new EditText(this);
        hoursInput.setHint("Validité en heures (24 par défaut)");
        hoursInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(hoursInput);

        Button start = new Button(this);
        start.setText("Démarrer le forfait");
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startBundle();
            }
        });
        root.addView(start);

        setContentView(root);
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

    // Phone's total mobile bytes since last boot, or -1 if unsupported.
    private long rawTotal() {
        long rx = TrafficStats.getMobileRxBytes();
        long tx = TrafficStats.getMobileTxBytes();
        if (rx == TrafficStats.UNSUPPORTED || tx == TrafficStats.UNSUPPORTED) return -1;
        return rx + tx;
    }

    // Adds bytes used since the last read. Works after the app was closed,
    // and handles a reboot (counter drops back to zero).
    private long syncUsed() {
        long raw = rawTotal();
        long last = prefs.getLong("lastRaw", -1);
        long used = prefs.getLong("used", 0);
        long delta;
        if (last < 0) delta = 0;
        else if (raw >= last) delta = raw - last;
        else delta = raw;
        used += delta;
        prefs.edit().putLong("lastRaw", raw).putLong("used", used).apply();
        return used;
    }

    private void startBundle() {
        String v = volumeInput.getText().toString().trim();
        String h = hoursInput.getText().toString().trim();
        if (v.isEmpty()) {
            volumeInput.setError("Indiquez le volume en Mo");
            return;
        }
        long mb;
        long hours = 24;
        try {
            mb = Long.parseLong(v);
            if (!h.isEmpty()) hours = Long.parseLong(h);
        } catch (NumberFormatException e) {
            volumeInput.setError("Valeur invalide");
            return;
        }
        if (mb <= 0 || hours <= 0) {
            volumeInput.setError("Les valeurs doivent être supérieures à 0");
            return;
        }
        if (rawTotal() < 0) {
            status.setText("Compteur mobile non disponible");
            return;
        }
        prefs.edit()
                .putLong("bundleBytes", mb * MB)
                .putLong("startMs", System.currentTimeMillis())
                .putLong("hours", hours)
                .putLong("used", 0)
                .putLong("lastRaw", rawTotal())
                .apply();
        update();
    }

    private void update() {
        if (rawTotal() < 0) {
            status.setText("Compteur mobile non disponible");
            detail.setText("");
            return;
        }
        long bundleBytes = prefs.getLong("bundleBytes", 0);
        if (bundleBytes == 0) {
            status.setText("Aucun forfait actif");
            detail.setText("Entrez le volume et la validité, puis démarrez.");
            return;
        }

        long used = syncUsed();
        long left = Math.max(0, bundleBytes - used);
        long expires = prefs.getLong("startMs", 0) + prefs.getLong("hours", 24) * HOUR_MS;
        long msLeft = expires - System.currentTimeMillis();

        status.setText((left / MB) + " Mo restants");

        String time;
        if (msLeft <= 0) {
            time = "expiré";
        } else {
            time = (msLeft / HOUR_MS) + " h " + ((msLeft % HOUR_MS) / 60000) + " min restantes";
        }

        String warn = "";
        if (left == 0 || msLeft <= 0) warn = "\nAttention : forfait épuisé ou expiré";
        else if (left * 5 < bundleBytes) warn = "\nAttention : moins de 20 % restant";
        else if (msLeft < 3 * HOUR_MS) warn = "\nAttention : expire dans moins de 3 h";

        detail.setText("sur " + (bundleBytes / MB) + " Mo · " + time + warn);
    }
}
