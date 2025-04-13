package com.oopgroup.smartpharmacy.utils;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;

public class AppSignatureHelper {
    private static final String TAG = "AppSignatureHelper";

    public static ArrayList<String> getAppSignatures(Context context) {
        ArrayList<String> appCodes = new ArrayList<>();
        try {
            String packageName = context.getPackageName();
            PackageManager pm = context.getPackageManager();
            Signature[] signatures = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures;
            for (Signature sig : signatures) {
                String hash = hash(packageName, sig.toByteArray());
                if (hash != null) {
                    appCodes.add(String.format("%s", hash));
                }
            }
        } catch (PackageManager.NameNotFoundException | NoSuchAlgorithmException e) {
            Log.e(TAG, "Error getting app signature: " + e.getMessage(), e);
        }
        return appCodes;
    }

    private static String hash(String packageName, byte[] signature) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(packageName.getBytes(StandardCharsets.UTF_8));
        md.update(signature);
        byte[] digest = md.digest();
        return Base64.encodeToString(Arrays.copyOfRange(digest, 0, 9), Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }
}
