package com.example.acer.appusagetracker.lockscreen;

import android.app.KeyguardManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.couchbase.lite.Database;
import com.couchbase.lite.DatabaseOptions;
import com.couchbase.lite.Emitter;
import com.couchbase.lite.Manager;
import com.couchbase.lite.Mapper;
import com.couchbase.lite.android.AndroidContext;
import com.example.acer.appusagetracker.GlobalApplication;
import com.example.acer.appusagetracker.MainActivity;
import com.example.acer.appusagetracker.R;
import com.example.acer.appusagetracker.usagetracker.appUsage.AppUsageStatisticsFragment;
import com.example.acer.appusagetracker.usagetracker.appUsage.CustomUsageStats;
import com.example.acer.appusagetracker.usagetracker.appUsage.UsageListAdapter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static android.database.sqlite.SQLiteDatabase.openDatabase;
import static com.example.acer.appusagetracker.MainActivity.DATABASE_NAME;
import static com.example.acer.appusagetracker.MainActivity.TAG;
import static com.example.acer.appusagetracker.MainActivity.byDateViewName;
import static com.example.acer.appusagetracker.MainActivity.designDocName;
import static java.security.AccessController.getContext;

public class PhoneUnlockedReceiver extends BroadcastReceiver {
    Context context = GlobalApplication.getAppContext();
    protected static Manager manager;
    public static Database database;
    UsageListAdapter mUsageListAdapter;

    @Override
    public void onReceive(Context context, Intent intent) {

        KeyguardManager keyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguardManager.isKeyguardSecure()) {
            try {
                startCBLite();
            } catch (Exception e) {
                String exception = e.toString();
                Toast toast = Toast.makeText(context, "some error", Toast.LENGTH_SHORT);
            }
        }
    }

    public void startCBLite() throws Exception {

        manager = new Manager(new AndroidContext(context), Manager.DEFAULT_OPTIONS);
        DatabaseOptions options = new DatabaseOptions();
        options.setCreate(true);
        database = manager.openDatabase(DATABASE_NAME, options);
        com.couchbase.lite.View viewItemsByDate = database.getView(String.format("%s/%s", designDocName, byDateViewName));
        viewItemsByDate.setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                Object createdAt = document.get("created_at");
                if (createdAt != null) {
                    emitter.emit(createdAt.toString(), null);
                }
            }
        }, "1.0");

        UsageStatsManager mUsageStatsManager;
        mUsageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -1);
        cal.set(Calendar.MILLISECOND, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        Map<String, UsageStats> queryUsageStatsMap = mUsageStatsManager
                .queryAndAggregateUsageStats(cal.getTimeInMillis(),
                        System.currentTimeMillis());
        List<UsageStats> queryUsageStats = new ArrayList<UsageStats>();
        for (Map.Entry<String, UsageStats> stat : queryUsageStatsMap.entrySet()) {
            queryUsageStats.add(stat.getValue());
        }
        Collections.sort(queryUsageStats, new timeInForegroundComparator());
        List<CustomUsageStats> customUsageStatsList = new ArrayList<>();
        for (int i = 0; i < queryUsageStats.size(); i++) {
            CustomUsageStats customUsageStats = new CustomUsageStats();
            customUsageStats.usageStats = queryUsageStats.get(i);
//            try {
//                Drawable appIcon = context.getPackageManager()
//                        .getApplicationIcon(customUsageStats.usageStats.getPackageName());
//                customUsageStats.appIcon = appIcon;
//            } catch (PackageManager.NameNotFoundException e) {
//                Log.w(TAG, String.format("App Icon is not found for %s",
//                        customUsageStats.usageStats.getPackageName()));
//                customUsageStats.appIcon = context
//                        .getDrawable(R.drawable.ic_default_app_launcher);
//            }
            customUsageStatsList.add(customUsageStats);
        }
        final PackageManager pm = context.getPackageManager();
        ApplicationInfo ai = null;
        DateFormat dateFormat = SimpleDateFormat.getDateTimeInstance();
        String DB_PATH = "/data/user/0/com.example.acer.appusagetracker/databases/";
        String DB_NAME = "unique_value.db";
        SQLiteDatabase sampleDB =  SQLiteDatabase.openDatabase(DB_PATH + DB_NAME, null, SQLiteDatabase.CREATE_IF_NECESSARY);
        Cursor cursor = sampleDB.rawQuery("select * from uniqueTable", null);
        try {

            cursor.moveToFirst();
            String unique_string = cursor.getString(0);

            final String[] arr = new String[500000];
            int i;
            for (i = 0; i < customUsageStatsList.size(); i++) {
                try {
                    double total = totalTime(customUsageStatsList);
                    ai = pm.getApplicationInfo(customUsageStatsList.get(i).usageStats.getPackageName(), 0);
                    String last_used = dateFormat.format(new Date(customUsageStatsList.get(i).usageStats.getLastTimeUsed()));
                    last_used = last_used.replace(',', ' ');
                    final long timeInForeground = customUsageStatsList.get(i).usageStats.getTotalTimeInForeground();
                    String time_in_sec = calculateTime(timeInForeground);
                    String percentage = calculatePercent(timeInForeground, total);
                    arr[i] = pm.getApplicationLabel(ai).toString().replaceAll("(^\\h*)|(\\h*$)", "") + "," + last_used.replaceAll("(^\\h*)|(\\h*$)", "") + "," + time_in_sec.replaceAll("(^\\h*)|(\\h*$)", "") + "," + percentage.replaceAll("(^\\h*)|(\\h*$)", "") + "," + unique_string.replaceAll("(^\\h*)|(\\h*$)", "") + "," + customUsageStatsList.get(i).usageStats.getPackageName().toString().replaceAll("(^\\h*)|(\\h*$)", "");

                } catch (Exception e) {
                    System.out.println("App not Found   " + Integer.toString(i));
                    arr[i] = "";
                }

            }
            final int array_count = i;

            ////REQUEST PASSAGE

            final RequestQueue queue = Volley.newRequestQueue(context);
            String url = "http://192.168.1.3:8000/assistant/update_stats_list/";
            StringRequest postRequest = new StringRequest(Request.Method.POST, url,
                    new Response.Listener<String>()
                    {
                        @Override
                        public void onResponse(String response) {

                            Log.d("Successfull","Successfull");

                        }
                    },
                    new Response.ErrorListener()
                    {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            // error
                            Log.d("Error.Response", error.toString());
                        }
                    }
            ) {
                @Override
                protected Map<String, String> getParams()
                {
                    Map<String, String>  params = new HashMap<String, String>();
                    for(int j=0;j<array_count;j++){
                        String j_string = Integer.toString(j);
                        params.put("update_list_"+j_string,arr[j].toString());
                    }

                    return params;

                }
            };
            queue.add(postRequest);

            /////END OF REQUESTS

        }
        finally {
            cursor.close();
        }





    }

    class timeInForegroundComparator implements Comparator<UsageStats> {

        @Override
        public int compare(UsageStats left, UsageStats right) {
            return Long.compare(right.getTotalTimeInForeground(), left.getTotalTimeInForeground());
        }
    }

    private String calculateTime(long ms) {
        String total = "";
        long sec = ms / 1000;
        long day;
        long hour;
        long min;
        if (sec >= (86400)) {
            day = sec / 86400;
            sec = sec % 86400;
            total = total + day + "d";
        }
        if (sec >= 3600) {
            hour = sec / 3600;
            sec = sec % 3600;
            total = total + hour + ":";
        }
        if (sec >= 60) {
            min = sec / 60;
            sec = sec % 60;
            total = total + min + ":";
        }
        if (sec > 0) {
            total = total + sec + ":";
        }
        return total;
    }

    private String calculatePercent(long ms, double total_time) {
        DecimalFormat f = new DecimalFormat("##.00");
        return f.format(ms * 100.0 / (double) total_time) + "%";
    }

    private long totalTime(List<CustomUsageStats> list) {
        long total = 0;
        for (CustomUsageStats app : list) {
            total += app.usageStats.getTotalTimeInForeground();
        }
        return total;
    }

}