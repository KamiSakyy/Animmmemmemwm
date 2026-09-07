package app.tsuyu.mobile;

import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

public final class TsuyuCrypto {
    private static final String PREF="tsuyu.keys.v1", RING="ring", MASTER="tsuyu_local_master_aes";
    private final Context context;
    private final SharedPreferences prefs;
    private final ArrayList<Identity> ring=new ArrayList<>();
    public static final class Identity {String id,pub,enc,iv;long at;boolean active;}
    public TsuyuCrypto(Context c){context=c.getApplicationContext();prefs=context.getSharedPreferences(PREF,0);load();}

    public synchronized Identity active(){if(ring.isEmpty())generateLocked();for(Identity k:ring)if(k.active)return k;ring.get(0).active=true;save();return ring.get(0);} 
    public synchronized String publicKey(){return active().pub;}
    public synchronized String keyId(){return active().id;}
    public synchronized Identity rotate(){for(Identity k:ring)k.active=false;Identity n=generateLocked();n.active=true;save();return n;}
    public synchronized JSONArray publicRing(){JSONArray a=new JSONArray();try{for(Identity k:ring)a.put(new JSONObject().put("id",k.id).put("publicKey",k.pub).put("active",k.active).put("at",k.at));}catch(Exception ignored){}return a;}

    private void load(){ring.clear();try{JSONArray a=new JSONArray(prefs.getString(RING,"[]"));for(int i=0;i<a.length();i++){JSONObject j=a.optJSONObject(i);if(j==null)continue;Identity k=new Identity();k.id=j.optString("id");k.pub=j.optString("pub");k.enc=j.optString("enc");k.iv=j.optString("iv");k.at=j.optLong("at");k.active=j.optBoolean("active");if(!k.pub.isEmpty()&&!k.enc.isEmpty())ring.add(k);}}catch(Exception ignored){} }
    private void save(){JSONArray a=new JSONArray();try{for(Identity k:ring)a.put(new JSONObject().put("id",k.id).put("pub",k.pub).put("enc",k.enc).put("iv",k.iv).put("at",k.at).put("active",k.active));prefs.edit().putString(RING,a.toString()).apply();}catch(Exception ignored){} }
    private Identity generateLocked(){try{KeyPairGenerator g=KeyPairGenerator.getInstance("EC");g.initialize(new ECGenParameterSpec("secp256r1"),rng());KeyPair kp=g.generateKeyPair();Identity k=new Identity();k.pub=b64(kp.getPublic().getEncoded());String[] packed=masterEncrypt(kp.getPrivate().getEncoded());k.iv=packed[0];k.enc=packed[1];k.id=fingerprint(k.pub);k.at=System.currentTimeMillis();k.active=ring.isEmpty();ring.add(0,k);save();return k;}catch(Exception e){throw new IllegalStateException(e);}}
    private SecureRandom rng(){try{return SecureRandom.getInstanceStrong();}catch(Exception e){return new SecureRandom();}}

    public JSONObject encrypt(String chatId,String msgId,String peerPub,JSONObject payload)throws Exception{Identity me=active();SecretKeySpec key=derive(privateKey(me),peerPub,chatId,msgId);byte[] iv=new byte[12];rng().nextBytes(iv);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,iv));byte[] cipher=c.doFinal(payload.toString().getBytes(StandardCharsets.UTF_8));return new JSONObject().put("v",1).put("alg","ECDH-P256+HKDF-SHA256+AES-256-GCM").put("senderPub",me.pub).put("recipientPub",peerPub).put("keyId",me.id).put("iv",b64(iv)).put("cipher",b64(cipher));}
    public JSONObject decrypt(String chatId,String msgId,JSONObject row,String myUid)throws Exception{String sender=row.optString("sender"),senderPub=row.optString("senderPub"),recipientPub=row.optString("recipientPub");String own=sender.equals(myUid)?senderPub:recipientPub;String peer=sender.equals(myUid)?recipientPub:senderPub;ArrayList<Identity> tries=new ArrayList<>();Identity exact=privateFor(own);if(exact!=null)tries.add(exact);for(Identity k:ring)if(exact==null||!k.pub.equals(exact.pub))tries.add(k);Exception last=null;for(Identity k:tries){try{SecretKeySpec key=derive(privateKey(k),peer,chatId,msgId);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Base64.decode(row.optString("iv"),Base64.DEFAULT)));String plain=new String(c.doFinal(Base64.decode(row.optString("cipher"),Base64.DEFAULT)),StandardCharsets.UTF_8);return new JSONObject(plain);}catch(Exception e){last=e;}}throw last==null?new GeneralSecurityException("decrypt failed"):last;}
    private Identity privateFor(String pub){if(pub==null)return null;for(Identity k:ring)if(pub.equals(k.pub))return k;return null;}
    private PrivateKey privateKey(Identity k)throws Exception{return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(masterDecrypt(k.iv,k.enc)));}
    private PublicKey publicKey(String pub)throws Exception{return KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.decode(pub,Base64.DEFAULT)));}
    private SecretKeySpec derive(PrivateKey own,String peerPub,String chatId,String msgId)throws Exception{KeyAgreement ka=KeyAgreement.getInstance("ECDH");ka.init(own);ka.doPhase(publicKey(peerPub),true);byte[] secret=ka.generateSecret();MessageDigest md=MessageDigest.getInstance("SHA-256");byte[] salt=md.digest(("Tsuyu/v1/"+chatId).getBytes(StandardCharsets.UTF_8));byte[] info=("msg/"+msgId).getBytes(StandardCharsets.UTF_8);byte[] okm=hkdf(secret,salt,info,32);return new SecretKeySpec(okm,"AES");}
    private static byte[] hkdf(byte[] ikm,byte[] salt,byte[] info,int len)throws Exception{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(salt,"HmacSHA256"));byte[] prk=mac.doFinal(ikm);byte[] out=new byte[len],t=new byte[0];int p=0,c=1;while(p<len){mac.init(new SecretKeySpec(prk,"HmacSHA256"));mac.update(t);mac.update(info);mac.update((byte)c++);t=mac.doFinal();int n=Math.min(t.length,len-p);System.arraycopy(t,0,out,p,n);p+=n;}return out;}

    private SecretKey masterKey()throws Exception{KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);if(!ks.containsAlias(MASTER)){KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder(MASTER,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).setRandomizedEncryptionRequired(true).build());g.generateKey();}return ((KeyStore.SecretKeyEntry)ks.getEntry(MASTER,null)).getSecretKey();}
    private String[] masterEncrypt(byte[] raw)throws Exception{byte[] iv=new byte[12];rng().nextBytes(iv);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,masterKey(),new GCMParameterSpec(128,iv));return new String[]{b64(iv),b64(c.doFinal(raw))};}
    private byte[] masterDecrypt(String iv,String enc)throws Exception{Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,masterKey(),new GCMParameterSpec(128,Base64.decode(iv,Base64.DEFAULT)));return c.doFinal(Base64.decode(enc,Base64.DEFAULT));}

    public synchronized String exportEncrypted(String password)throws Exception{return exportBundle(password);} 
    public synchronized void importEncrypted(String json,String password)throws Exception{importBundle(json,password);} 
    public synchronized String exportBundle(String password)throws Exception{JSONArray keys=new JSONArray();for(Identity k:ring)keys.put(new JSONObject().put("id",k.id).put("pub",k.pub).put("private",b64(masterDecrypt(k.iv,k.enc))).put("at",k.at).put("active",k.active));JSONObject payload=new JSONObject().put("format","TsuyuKeyBundleV1").put("createdAt",System.currentTimeMillis()).put("keys",keys);byte[] salt=new byte[16],iv=new byte[12];rng().nextBytes(salt);rng().nextBytes(iv);SecretKeySpec key=pwd(password,salt);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,iv));return new JSONObject().put("format","TsuyuEncryptedKeyBundleV1").put("kdf","PBKDF2-HMAC-SHA256-180000").put("salt",b64(salt)).put("iv",b64(iv)).put("cipher",b64(c.doFinal(payload.toString().getBytes(StandardCharsets.UTF_8)))).toString(2);}
    public synchronized void importBundle(String json,String password)throws Exception{JSONObject box=new JSONObject(json);SecretKeySpec key=pwd(password,Base64.decode(box.optString("salt"),Base64.DEFAULT));Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Base64.decode(box.optString("iv"),Base64.DEFAULT)));JSONObject payload=new JSONObject(new String(c.doFinal(Base64.decode(box.optString("cipher"),Base64.DEFAULT)),StandardCharsets.UTF_8));JSONArray keys=payload.optJSONArray("keys");for(Identity old:ring)old.active=false;for(int i=0;keys!=null&&i<keys.length();i++){JSONObject j=keys.getJSONObject(i);Identity k=new Identity();k.id=j.optString("id");k.pub=j.optString("pub");String[] packed=masterEncrypt(Base64.decode(j.optString("private"),Base64.DEFAULT));k.iv=packed[0];k.enc=packed[1];k.at=j.optLong("at",System.currentTimeMillis());k.active=j.optBoolean("active",i==0);Identity old=privateFor(k.pub);if(old==null)ring.add(k);else{old.iv=k.iv;old.enc=k.enc;old.at=k.at;old.active=k.active;}}if(ring.isEmpty())generateLocked();boolean any=false;for(Identity k:ring)if(k.active)any=true;if(!any)ring.get(0).active=true;save();}
    private SecretKeySpec pwd(String password,byte[] salt)throws Exception{char[] p=(password==null?"":password).toCharArray();PBEKeySpec spec=new PBEKeySpec(p,salt,180000,256);byte[] key=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();return new SecretKeySpec(key,"AES");}
    private static String b64(byte[] b){return Base64.encodeToString(b,Base64.NO_WRAP);} 
    private static String fingerprint(String s)throws Exception{byte[] d=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(int i=0;i<8&&i<d.length;i++)out.append(String.format(Locale.ROOT,"%02x",d[i]&255));return out.toString();}
}
