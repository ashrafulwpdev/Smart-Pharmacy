package com.oopgroup.smartpharmacy.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

public class NetworkUtils {
    private static final MutableLiveData<Boolean> networkStatus = new MutableLiveData<>();
    private static ConnectivityManager.NetworkCallback networkCallback;

    public static void initNetworkMonitoring(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                networkStatus.postValue(true);
            }

            @Override
            public void onLost(Network network) {
                networkStatus.postValue(false);
            }
        };

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
        networkStatus.postValue(isNetworkAvailable(context));
    }

    public static void stopNetworkMonitoring(Context context) {
        if (networkCallback != null) {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }
    }

    public static LiveData<Boolean> getNetworkStatus() {
        return networkStatus;
    }

    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = connectivityManager.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    public static void executeWithNetworkCheck(Context context, Runnable operation, Runnable onNoInternet) {
        if (isNetworkAvailable(context)) {
            operation.run();
        } else {
            onNoInternet.run();
        }
    }
}