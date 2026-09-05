package app.yoru.mobile;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import java.security.MessageDigest;
import java.util.Locale;

public final class AppSecurity {
    private AppSecurity(){}
    public static boolean original(Context context){
        if(BuildConfig.DEBUG)return true;
        try{
            String expected=BuildConfig.EXPECTED_SIGNER_SHA256.toLowerCase(Locale.ROOT);
            if(expected.length()!=64||!"app.yoru.mobile".equals(context.getPackageName()))return false;
            Signature[] sig;
            if(Build.VERSION.SDK_INT>=28){PackageInfo p=context.getPackageManager().getPackageInfo(context.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);sig=p.signingInfo.getApkContentsSigners();}
            else{PackageInfo p=context.getPackageManager().getPackageInfo(context.getPackageName(),PackageManager.GET_SIGNATURES);sig=p.signatures;}
            for(Signature s:sig){byte[] hash=MessageDigest.getInstance("SHA-256").digest(s.toByteArray());StringBuilder out=new StringBuilder();for(byte b:hash)out.append(String.format(Locale.ROOT,"%02x",b&255));if(MessageDigest.isEqual(out.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII),expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))return true;}
        }catch(Exception ignored){}
        return false;
    }
}
