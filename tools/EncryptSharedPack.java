import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class EncryptSharedPack {
    private static final String KIND = "paruchan.encrypted-quest-pack";
    private static final String KDF = "PBKDF2WithHmacSHA256";
    private static final String CIPHER = "AES-256-GCM";
    private static final int DEFAULT_ITERATIONS = 210_000;
    private static final int AES_KEY_BITS = 256;
    private static final int GCM_TAG_BITS = 128;
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12;

    private EncryptSharedPack() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException(
                "Usage: EncryptSharedPack <input-json> <output-json> <pack-id> <pack-version>"
            );
        }

        String password = env("PARUCHAN_SHARED_PACK_PASSWORD");
        int iterations = parseIterations(System.getenv("PARUCHAN_SHARED_PACK_ITERATIONS"));
        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);
        String packId = args[2].trim();
        String packVersion = args[3].trim();

        if (password.isBlank()) {
            throw new IllegalArgumentException("PARUCHAN_SHARED_PACK_PASSWORD is empty");
        }
        if (packId.isBlank() || packVersion.isBlank()) {
            throw new IllegalArgumentException("pack-id and pack-version are required");
        }

        byte[] plaintext = Files.readString(input, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_BYTES];
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(salt);
        random.nextBytes(iv);

        byte[] key = SecretKeyFactory.getInstance(KDF)
            .generateSecret(new PBEKeySpec(password.toCharArray(), salt, iterations, AES_KEY_BITS))
            .getEncoded();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] ciphertext = cipher.doFinal(plaintext);

        String json = "{\n"
            + "  \"kind\": " + quote(KIND) + ",\n"
            + "  \"schemaVersion\": 1,\n"
            + "  \"packId\": " + quote(packId) + ",\n"
            + "  \"packVersion\": " + quote(packVersion) + ",\n"
            + "  \"kdf\": " + quote(KDF) + ",\n"
            + "  \"iterations\": " + iterations + ",\n"
            + "  \"cipher\": " + quote(CIPHER) + ",\n"
            + "  \"salt\": " + quote(Base64.getEncoder().encodeToString(salt)) + ",\n"
            + "  \"iv\": " + quote(Base64.getEncoder().encodeToString(iv)) + ",\n"
            + "  \"ciphertext\": " + quote(Base64.getEncoder().encodeToString(ciphertext)) + "\n"
            + "}\n";

        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(output, json, StandardCharsets.UTF_8);
        System.out.println("Wrote encrypted shared pack: " + output);
    }

    private static String env(String name) {
        String value = System.getenv(name);
        if (value == null) {
            throw new IllegalArgumentException("Set " + name);
        }
        return value;
    }

    private static int parseIterations(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_ITERATIONS;
        }
        int value = Integer.parseInt(raw.trim());
        if (value < 100_000) {
            throw new IllegalArgumentException("PARUCHAN_SHARED_PACK_ITERATIONS must be at least 100000");
        }
        return value;
    }

    private static String quote(String value) {
        StringBuilder escaped = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            switch (ch) {
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\b':
                    escaped.append("\\b");
                    break;
                case '\f':
                    escaped.append("\\f");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    if (ch < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) ch));
                    } else {
                        escaped.append(ch);
                    }
                    break;
            }
        }
        return escaped.append('"').toString();
    }
}
