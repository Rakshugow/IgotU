package com.igotu.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class IncidentDbHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "igotu_incidents.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_NAME = "incidents";
    public static final String COL_ID = "id";
    public static final String COL_TIMESTAMP = "timestamp";
    public static final String COL_DATE_TIME = "date_time";
    public static final String COL_FAKE_LAT = "fake_lat";
    public static final String COL_FAKE_LON = "fake_lon";
    public static final String COL_REAL_LAT = "real_lat";
    public static final String COL_REAL_LON = "real_lon";
    public static final String COL_ACCURACY = "accuracy";
    public static final String COL_MOCK_APP = "mock_app";
    public static final String COL_THREAT_TYPE = "threat_type";

    private static IncidentDbHelper instance;

    public static synchronized IncidentDbHelper getInstance(Context context) {
        if (instance == null) {
            instance = new IncidentDbHelper(context.getApplicationContext());
        }
        return instance;
    }

    private IncidentDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_NAME + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_TIMESTAMP + " INTEGER, " +
                COL_DATE_TIME + " TEXT, " +
                COL_FAKE_LAT + " REAL, " +
                COL_FAKE_LON + " REAL, " +
                COL_REAL_LAT + " REAL, " +
                COL_REAL_LON + " REAL, " +
                COL_ACCURACY + " REAL, " +
                COL_MOCK_APP + " TEXT, " +
                COL_THREAT_TYPE + " TEXT" +
                ")";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    public synchronized long logIncident(double fakeLat, double fakeLon, double realLat, double realLon,
                                         float accuracy, String mockApp, String threatType) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        long now = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String formattedDate = sdf.format(new Date(now));

        values.put(COL_TIMESTAMP, now);
        values.put(COL_DATE_TIME, formattedDate);
        values.put(COL_FAKE_LAT, fakeLat);
        values.put(COL_FAKE_LON, fakeLon);
        values.put(COL_REAL_LAT, realLat);
        values.put(COL_REAL_LON, realLon);
        values.put(COL_ACCURACY, accuracy);
        values.put(COL_MOCK_APP, mockApp != null ? mockApp : "Unknown");
        values.put(COL_THREAT_TYPE, threatType != null ? threatType : "Mock Location");

        return db.insert(TABLE_NAME, null, values);
    }

    public static class IncidentItem {
        public long id;
        public String dateTime;
        public double fakeLat;
        public double fakeLon;
        public double realLat;
        public double realLon;
        public float accuracy;
        public String mockApp;
        public String threatType;
    }

    public synchronized List<IncidentItem> getRecentIncidents(int limit) {
        List<IncidentItem> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_NAME, null, null, null, null, null,
                COL_ID + " DESC", String.valueOf(limit));

        if (cursor != null && cursor.moveToFirst()) {
            do {
                IncidentItem item = new IncidentItem();
                item.id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID));
                item.dateTime = cursor.getString(cursor.getColumnIndexOrThrow(COL_DATE_TIME));
                item.fakeLat = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_FAKE_LAT));
                item.fakeLon = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_FAKE_LON));
                item.realLat = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_REAL_LAT));
                item.realLon = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_REAL_LON));
                item.accuracy = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_ACCURACY));
                item.mockApp = cursor.getString(cursor.getColumnIndexOrThrow(COL_MOCK_APP));
                item.threatType = cursor.getString(cursor.getColumnIndexOrThrow(COL_THREAT_TYPE));
                list.add(item);
            } while (cursor.moveToNext());
            cursor.close();
        }
        return list;
    }

    public synchronized int getIncidentCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_NAME, null);
        int count = 0;
        if (cursor != null) {
            if (cursor.moveToFirst()) count = cursor.getInt(0);
            cursor.close();
        }
        return count;
    }

    public synchronized void clearAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_NAME, null, null);
    }

    public File exportToCsv(Context context) throws IOException {
        List<IncidentItem> incidents = getRecentIncidents(1000);
        File exportDir = new File(context.getCacheDir(), "reports");
        if (!exportDir.exists()) exportDir.mkdirs();

        File csvFile = new File(exportDir, "IgotU_Security_Audit_Report.csv");
        FileWriter writer = new FileWriter(csvFile);

        // CSV Header
        writer.append("Incident ID,Date & Time,Injected Fake Lat,Injected Fake Lon,Last Real Lat,Last Real Lon,Accuracy (m),Detected Mock App,Threat Type\n");

        for (IncidentItem item : incidents) {
            writer.append(String.valueOf(item.id)).append(",")
                    .append("\"").append(item.dateTime).append("\",")
                    .append(String.valueOf(item.fakeLat)).append(",")
                    .append(String.valueOf(item.fakeLon)).append(",")
                    .append(String.valueOf(item.realLat)).append(",")
                    .append(String.valueOf(item.realLon)).append(",")
                    .append(String.valueOf(item.accuracy)).append(",")
                    .append("\"").append(item.mockApp.replace("\"", "\"\"")).append("\",")
                    .append("\"").append(item.threatType.replace("\"", "\"\"")).append("\"\n");
        }

        writer.flush();
        writer.close();
        return csvFile;
    }
}
