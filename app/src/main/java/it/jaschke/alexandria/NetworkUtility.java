package it.jaschke.alexandria;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.lang.ref.WeakReference;

public class NetworkUtility {
    public static boolean isNetworkConnected(WeakReference<Context> weakReference){
        Context context = weakReference.get();
        ConnectivityManager systemService = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = systemService.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }
}
