package app.yoru.vk;

import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

final class TokenVault {
    private static final String ALIAS="yoru-vk-token-v1";
    private final SharedPreferences prefs;
    private long revision;
    TokenVault(Context c){prefs=c.getSharedPreferences("vk-private",Context.MODE_PRIVATE);}
    synchronized boolean connected(){return prefs.contains("cipher");}
    synchronized String label(){return prefs.getString("label","Аккаунт ВК");}
    synchronized long revision(){return revision;}
    private SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return generator.generateKey();
    }
    synchronized void save(String token,String label,long expected) throws Exception {
        if(revision!=expected||Thread.currentThread().isInterrupted())throw new InterruptedException();
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());byte[] data=cipher.doFinal(token.getBytes("UTF-8"));
        if(!prefs.edit().putString("cipher",Base64.encodeToString(data,Base64.NO_WRAP)).putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).putString("label",label).commit())throw new IllegalStateException("Не удалось сохранить токен.");revision++;
    }
    synchronized String read() throws Exception {
        if(!connected())throw new IllegalStateException("Сначала подключите ВК своим пользовательским токеном.");
        try{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(prefs.getString("iv",""),Base64.NO_WRAP)));return new String(cipher.doFinal(Base64.decode(prefs.getString("cipher",""),Base64.NO_WRAP)),"UTF-8");}
        catch(Exception e){clear();throw new IllegalStateException("Защищённый токен не удалось прочитать. Подключите ВК заново.");}
    }
    synchronized void clear(){revision++;prefs.edit().clear().commit();try{KeyStore k=KeyStore.getInstance("AndroidKeyStore");k.load(null);if(k.containsAlias(ALIAS))k.deleteEntry(ALIAS);}catch(Exception ignored){}}
}
