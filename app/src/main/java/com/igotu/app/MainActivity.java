package com.igotu.app;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements LocationListener {

    private static final int PERMISSION_REQ_CODE = 1001;

    private MaterialCardView cardStatus;
    private ImageView imgStatusIcon;
    private TextView tvVerdict;
    private TextView tvVerdictDesc;
    private TextView tvMockFlag;
    private TextView tvCoords;
    private TextView tvProvider;
    private TextView tvAccuracy;
    private TextView tvMockApps;
    private TextView tvServiceStatus;
    private TextView tvBadgeLive;
    private SwitchMaterial switchSentinel;
    private MaterialButton btnScan;
    private MaterialButton btnNeutralize;

    // Anti-Bypass Views
    private TextView tvRootStatus;
    private TextView tvHookStatus;

    // Incident Log Views
    private TextView tvIncidentCount;
    private TextView tvRecentIncidents;
    private MaterialButton btnExportCsv;
    private MaterialButton btnClearLogs;

    private LocationManager locationManager;
    private IncidentDbHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cardStatus = findViewById(R.id.card_status);
        imgStatusIcon = findViewById(R.id.img_status_icon);
        tvVerdict = findViewById(R.id.tv_verdict);
        tvVerdictDesc = findViewById(R.id.tv_verdict_desc);
        tvMockFlag = findViewById(R.id.tv_mock_flag);
        tvCoords = findViewById(R.id.tv_coords);
        tvProvider = findViewById(R.id.tv_provider);
        tvAccuracy = findViewById(R.id.tv_accuracy);
        tvMockApps = findViewById(R.id.tv_mock_apps);
        tvServiceStatus = findViewById(R.id.tv_service_status);
        tvBadgeLive = findViewById(R.id.tv_badge_live);
        switchSentinel = findViewById(R.id.switch_sentinel);
        btnScan = findViewById(R.id.btn_scan);
        btnNeutralize = findViewById(R.id.btn_neutralize);

        tvRootStatus = findViewById(R.id.tv_root_status);
        tvHookStatus = findViewById(R.id.tv_hook_status);
        tvIncidentCount = findViewById(R.id.tv_incident_count);
        tvRecentIncidents = findViewById(R.id.tv_recent_incidents);
        btnExportCsv = findViewById(R.id.btn_export_csv);
        btnClearLogs = findViewById(R.id.btn_clear_logs);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        dbHelper = IncidentDbHelper.getInstance(this);

        // Auto-neutralize fake GPS apps on launch
        triggerNeutralizeAction(false);

        btnScan.setOnClickListener(v -> performFullScan());
        btnExportCsv.setOnClickListener(v -> exportAndShareCsv());
        btnClearLogs.setOnClickListener(v -> clearLogs());

        btnNeutralize.setOnClickListener(v -> triggerNeutralizeAction(true));

        // Quick copy coordinates on tap
        tvCoords.setOnClickListener(v -> {
            String coords = tvCoords.getText().toString();
            if (!coords.contains("Acquiring")) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Coordinates", coords);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Coordinates copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        });

        switchSentinel.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                startSentinelService();
                tvServiceStatus.setText("Active: Guarding Google Maps, WhatsApp & system apps");
            } else {
                stopSentinelService();
                tvServiceStatus.setText("Disabled: Tap switch to enable background protection");
            }
        });

        checkPermissionsAndStart();
    }

    private void triggerNeutralizeAction(boolean userClicked) {
        AppNeutralizer.NeutralizeResult res = AppNeutralizer.neutralize(this);
        if (userClicked) {
            String msg = "Neutralized " + res.killedCount + " mock process(es). Injected test providers cleared.";
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            
            // If mock location persists, take user directly to settings to disable it
            Location last = null;
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
            boolean stillMocked = (last != null && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? last.isMock() : last.isFromMockProvider()));
            if (stillMocked) {
                AppNeutralizer.openDeveloperSettings(this);
            }
            performFullScan();
        }
    }

    private void checkPermissionsAndStart() {
        List<String> neededPermissions = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            neededPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            neededPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (!neededPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, neededPermissions.toArray(new String[0]), PERMISSION_REQ_CODE);
        } else {
            performFullScan();
            startSentinelService();
        }
    }

    private void startSentinelService() {
        Intent serviceIntent = new Intent(this, SentinelService.class);
        serviceIntent.setAction(SentinelService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void stopSentinelService() {
        Intent serviceIntent = new Intent(this, SentinelService.class);
        serviceIntent.setAction(SentinelService.ACTION_STOP);
        startService(serviceIntent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQ_CODE && grantResults.length > 0) {
            performFullScan();
            startSentinelService();
        } else {
            Toast.makeText(this, "Permissions needed for full protection", Toast.LENGTH_SHORT).show();
        }
    }

    private void performFullScan() {
        scanForMockApps();
        scanAntiBypassIntegrity();
        refreshIncidentLogView();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            tvVerdict.setText("ACQUIRING SENSORS...");
            tvVerdict.setTextColor(Color.parseColor("#00F2FE"));

            Location lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location lastNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            Location best = (lastGps != null) ? lastGps : lastNetwork;

            if (best != null) {
                inspectLocation(best);
            }

            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0, this);
        }
    }

    private void scanAntiBypassIntegrity() {
        int redColor = Color.parseColor("#EF4444");
        int greenColor = Color.parseColor("#10B981");

        boolean isRooted = SecurityEngine.isRooted(this);
        if (isRooted) {
            List<String> threats = SecurityEngine.getRootThreats(this);
            String threatStr = threats.isEmpty() ? "Root binaries present" : threats.get(0);
            tvRootStatus.setText("⚠ ROOT DETECTED: " + threatStr);
            tvRootStatus.setTextColor(redColor);
        } else {
            tvRootStatus.setText("Clean (No Root)");
            tvRootStatus.setTextColor(greenColor);
        }

        boolean hasHook = SecurityEngine.isHookFrameworkDetected(this);
        if (hasHook) {
            List<String> threats = SecurityEngine.getHookThreats(this);
            String threatStr = threats.isEmpty() ? "Hook bridge active" : threats.get(0);
            tvHookStatus.setText("⚠ HOOK ACTIVE: " + threatStr);
            tvHookStatus.setTextColor(redColor);
        } else {
            tvHookStatus.setText("Clean (No Hooks)");
            tvHookStatus.setTextColor(greenColor);
        }
    }

    private void refreshIncidentLogView() {
        int count = dbHelper.getIncidentCount();
        tvIncidentCount.setText(count + " Incident" + (count == 1 ? "" : "s"));

        if (count == 0) {
            tvRecentIncidents.setText("No spoofing incidents detected yet. Clean record.");
            tvRecentIncidents.setTextColor(Color.parseColor("#64748B"));
        } else {
            List<IncidentDbHelper.IncidentItem> recent = dbHelper.getRecentIncidents(3);
            StringBuilder sb = new StringBuilder();
            for (IncidentDbHelper.IncidentItem item : recent) {
                sb.append("• [").append(item.dateTime).append("]\n")
                        .append("  Fake: ").append(String.format("%.4f, %.4f", item.fakeLat, item.fakeLon))
                        .append(" | ").append(item.threatType).append("\n\n");
            }
            tvRecentIncidents.setText(sb.toString().trim());
            tvRecentIncidents.setTextColor(Color.parseColor("#EF4444"));
        }
    }

    private void exportAndShareCsv() {
        try {
            int count = dbHelper.getIncidentCount();
            if (count == 0) {
                Toast.makeText(this, "No incidents to export yet.", Toast.LENGTH_SHORT).show();
                return;
            }

            File csvFile = dbHelper.exportToCsv(this);
            Uri contentUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", csvFile);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/csv");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "IgotU Security Audit Report");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Attached is the IgotU Fake GPS Incident Audit Report (" + count + " records).");
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Share Security Audit Report"));
        } catch (Exception e) {
            Toast.makeText(this, "Export error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void clearLogs() {
        dbHelper.clearAll();
        refreshIncidentLogView();
        Toast.makeText(this, "Incident logs cleared.", Toast.LENGTH_SHORT).show();
    }

    private void inspectLocation(Location location) {
        if (location == null) return;

        boolean isMock = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            isMock = location.isMock();
        } else {
            isMock = location.isFromMockProvider();
        }

        tvCoords.setText(String.format("%.6f, %.6f", location.getLatitude(), location.getLongitude()));
        tvProvider.setText("Provider: " + location.getProvider().toUpperCase());
        tvAccuracy.setText(String.format("Accuracy: ±%.1f m", location.getAccuracy()));

        int redColor = Color.parseColor("#EF4444");
        int greenColor = Color.parseColor("#10B981");

        if (isMock) {
            cardStatus.setStrokeColor(redColor);
            cardStatus.setStrokeWidth(4);
            imgStatusIcon.setImageResource(R.drawable.ic_shield_alert);

            tvVerdict.setText("MOCK LOCATION DETECTED!");
            tvVerdict.setTextColor(redColor);
            tvVerdictDesc.setText("Android OS confirms location is injected by a fake GPS / mock provider.");

            tvMockFlag.setText("isMock: TRUE [ALERT]");
            tvMockFlag.setTextColor(redColor);

            tvBadgeLive.setText("● THREAT");
            tvBadgeLive.setTextColor(redColor);
            tvBadgeLive.setBackgroundResource(R.drawable.bg_badge_danger);

            btnNeutralize.setText("Kill Fake GPS & Revert to Real GPS");
            btnNeutralize.setBackgroundColor(Color.parseColor("#EF4444"));

            refreshIncidentLogView();
        } else {
            cardStatus.setStrokeColor(Color.parseColor("#1E293B"));
            cardStatus.setStrokeWidth(2);
            imgStatusIcon.setImageResource(R.drawable.ic_shield_secure);

            tvVerdict.setText("AUTHENTIC PHYSICAL LOCATION");
            tvVerdict.setTextColor(greenColor);
            tvVerdictDesc.setText("Location verified from genuine physical hardware satellites/towers.");

            tvMockFlag.setText("isMock: FALSE (Clean)");
            tvMockFlag.setTextColor(greenColor);

            tvBadgeLive.setText("● SECURED");
            tvBadgeLive.setTextColor(greenColor);
            tvBadgeLive.setBackgroundResource(R.drawable.bg_badge_secure);

            btnNeutralize.setText("Neutralizer Active (Guarding)");
            btnNeutralize.setBackgroundColor(Color.parseColor("#1E293B"));
        }
    }

    private void scanForMockApps() {
        PackageManager pm = getPackageManager();
        List<String> mockAppsFound = new ArrayList<>();

        try {
            List<PackageInfo> packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);
            for (PackageInfo pkg : packages) {
                boolean isSystem = (pkg.applicationInfo != null && (pkg.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
                if (isSystem || pkg.packageName.equals(getPackageName())) continue;

                if (pkg.requestedPermissions != null) {
                    for (String perm : pkg.requestedPermissions) {
                        if ("android.permission.ACCESS_MOCK_LOCATION".equals(perm)) {
                            String appName = pkg.applicationInfo.loadLabel(pm).toString();
                            mockAppsFound.add(appName + " (" + pkg.packageName + ")");
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (mockAppsFound.isEmpty()) {
            tvMockApps.setText("✔ No mock location / fake GPS apps found installed.");
            tvMockApps.setTextColor(Color.parseColor("#10B981"));
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("⚠ ").append(mockAppsFound.size()).append(" Mock GPS app(s) found:\n");
            for (String app : mockAppsFound) {
                sb.append("• ").append(app).append("\n");
            }
            tvMockApps.setText(sb.toString().trim());
            tvMockApps.setTextColor(Color.parseColor("#EF4444"));
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        inspectLocation(location);
    }

    @Override
    public void onProviderEnabled(@NonNull String provider) {}

    @Override
    public void onProviderDisabled(@NonNull String provider) {}

    @Override
    protected void onResume() {
        super.onResume();
        refreshIncidentLogView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
    }
}
