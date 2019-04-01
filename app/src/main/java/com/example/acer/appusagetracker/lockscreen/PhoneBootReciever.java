package com.example.acer.appusagetracker.lockscreen;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.acer.appusagetracker.MainActivity;

import java.util.HashMap;
import java.util.Map;


public class PhoneBootReciever extends BroadcastReceiver {
/* Recieves events when phone boot is completed */
        private RequestQueue queue;
        @Override
        public void onReceive(Context context, Intent intent) {

            Intent myIntent = new Intent(context,UnlockCountService.class);
            context.startService(myIntent);
        }
    }

