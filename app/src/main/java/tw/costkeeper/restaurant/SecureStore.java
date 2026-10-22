package tw.costkeeper.restaurant;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** Encrypted local-only JSON storage. No network permission, no cloud sync. */
public final class SecureStore {
    private static final String ALIAS = "costkeeper_local_v1";
    private static final byte[] MAGIC = "CCK1".getBytes(StandardCharsets.US_ASCII);
    private final Context app;
    public SecureStore(Context context) { app = context.getApplicationContext(); }

    public static JSONObject blank() {
        JSONObject db = new JSONObject();
        try {
            db.put("format", 1);
            db.put("ingredients", new JSONArray());
            db.put("recipes", new JSONArray());
            db.put("purchases", new JSONArray());
            db.put("sales", new JSONArray());
        } catch (Exception e) { throw new IllegalStateException(e); }
        return db;
    }
    private File dataFile() { return new File(app.getFilesDir(), "costkeeper-v1.dat"); }

    private SecretKey localKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(ALIAS)) return ((KeyStore.SecretKeyEntry) store.getEntry(ALIAS, null)).getSecretKey();
        KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build());
        return gen.generateKey();
    }
    private static byte[] readAll(File f) throws Exception {
        try (FileInputStream in = new FileInputStream(f); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) >= 0) out.write(b, 0, n);
            return out.toByteArray();
        }
    }
    public JSONObject load() throws Exception {
        if (!dataFile().exists()) return blank();
        byte[] full = readAll(dataFile());
        if (full.length < 13) throw new IllegalStateException("資料檔案長度異常");
        byte[] nonce = Arrays.copyOfRange(full, 0, 12);
        byte[] body = Arrays.copyOfRange(full, 12, full.length);
        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.DECRYPT_MODE, localKey(), new GCMParameterSpec(128, nonce));
        return validate(new JSONObject(new String(aes.doFinal(body), StandardCharsets.UTF_8)));
    }
    public void save(JSONObject db) throws Exception {
        validate(db);
        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, localKey());
        byte[] bytes = aes.doFinal(db.toString().getBytes(StandardCharsets.UTF_8));
        File tmp = new File(app.getFilesDir(), "costkeeper-v1.tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(aes.getIV()); out.write(bytes); out.getFD().sync();
        }
        if (!tmp.renameTo(dataFile())) throw new IllegalStateException("資料儲存失敗");
    }
    private static JSONObject validate(JSONObject db) throws Exception {
        if (db.optInt("format", -1) != 1) throw new IllegalArgumentException("不支援的備份版本");
        for (String name : new String[]{"ingredients", "recipes", "purchases", "sales"}) db.getJSONArray(name);
        return db;
    }
    private static SecretKey passwordKey(char[] password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, 210000, 256);
        try {
            byte[] raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            try { return new SecretKeySpec(raw, "AES"); } finally { Arrays.fill(raw, (byte)0); }
        } finally { spec.clearPassword(); }
    }
    /** Password-encrypted portable backups. MAGIC|salt(16)|nonce(12)|GCM ciphertext. */
    public byte[] makeBackup(JSONObject db, char[] password) throws Exception {
        validate(db);
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16], nonce = new byte[12];
        random.nextBytes(salt); random.nextBytes(nonce);
        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, passwordKey(password, salt), new GCMParameterSpec(128, nonce));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(MAGIC); out.write(salt); out.write(nonce);
        out.write(aes.doFinal(db.toString().getBytes(StandardCharsets.UTF_8)));
        return out.toByteArray();
    }
    public JSONObject restoreBackup(byte[] full, char[] password) throws Exception {
        if (full.length < 49 || full.length > 30_000_000) throw new IllegalArgumentException("檔案不是有效的成本管家備份");
        for (int i=0; i<4; i++) if (full[i] != MAGIC[i]) throw new IllegalArgumentException("備份格式不正確");
        byte[] salt = Arrays.copyOfRange(full,4,20), nonce=Arrays.copyOfRange(full,20,32);
        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.DECRYPT_MODE, passwordKey(password,salt), new GCMParameterSpec(128, nonce));
        return validate(new JSONObject(new String(aes.doFinal(Arrays.copyOfRange(full,32,full.length)),StandardCharsets.UTF_8)));
    }
}
