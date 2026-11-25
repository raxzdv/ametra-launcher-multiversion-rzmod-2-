package com.ametra.guard;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Formato binario del canal "ametra:sync". Lógica pura (sin Bukkit) para poder probarla. */
final class Protocol {
    private Protocol() {}

    static final String CHANNEL = "ametra:sync";
    static final int VERSION = 1;
    static final int T_CHALLENGE = 1, T_HELLO = 2, T_TELEMETRY = 3;
    static final int MAX_TICKS = 400, MAX_CLICKS = 400, MAX_MODS = 1024, MAX_STR = 64;

    static final class Bad extends Exception {
        Bad(String m) { super(m); }
    }

    static final class Mod {
        String id, version, hash;
    }

    static final class Hello {
        int protocol, flags;
        byte[] nonce;
        String jarHash;
        List<String> agents = new ArrayList<>();
        List<Mod> mods = new ArrayList<>();
        List<String> classHits = new ArrayList<>();
    }

    static final class Telemetry {
        long seq;
        int n, nClicks;
        float[] yaw, pitch;
        short[] mdx, mdy;
        byte[] flags;
        int[] clickDtMicros;
    }

    static byte[] hmac(byte[] key, byte[] data, int off, int len) {
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(key, "HmacSHA256"));
            m.update(data, off, len);
            return m.doFinal();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Clave de sesión = HMAC(secreto, "s" + nonce). La usa la telemetría para que no se pueda repetir de otra sesión. */
    static byte[] sessionKey(byte[] secret, byte[] nonce) {
        byte[] d = new byte[1 + nonce.length];
        d[0] = 's';
        System.arraycopy(nonce, 0, d, 1, nonce.length);
        return hmac(secret, d, 0, d.length);
    }

    static byte[] challenge(byte[] nonce, List<String> probes) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream o = new DataOutputStream(b);
            o.writeByte(T_CHALLENGE);
            o.write(nonce);
            int n = Math.min(64, probes.size());
            o.writeByte(n);
            for (int i = 0; i < n; i++) o.writeUTF(probes.get(i));
            return b.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02x", x));
        return s.toString();
    }

    private static String str(DataInputStream in) throws IOException, Bad {
        String s = in.readUTF();
        if (s.length() > 200) throw new Bad("cadena larga");
        return s;
    }

    static Hello parseHello(byte[] d, byte[] secret) throws Bad {
        try {
            if (d.length < 1 + 1 + 16 + 32 + 1 + 32) throw new Bad("corto");
            byte[] mac = hmac(secret, d, 0, d.length - 32);
            if (!MessageDigest.isEqual(mac, Arrays.copyOfRange(d, d.length - 32, d.length))) throw new Bad("firma");
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(d, 0, d.length - 32));
            if (in.readUnsignedByte() != T_HELLO) throw new Bad("tipo");
            Hello h = new Hello();
            h.protocol = in.readUnsignedByte();
            h.nonce = new byte[16];
            in.readFully(h.nonce);
            byte[] jar = new byte[32];
            in.readFully(jar);
            h.jarHash = hex(jar);
            h.flags = in.readUnsignedByte();
            int na = in.readUnsignedByte();
            for (int i = 0; i < na; i++) h.agents.add(str(in));
            int nm = in.readUnsignedShort();
            if (nm > MAX_MODS) throw new Bad("demasiados mods");
            for (int i = 0; i < nm; i++) {
                Mod m = new Mod();
                m.id = str(in);
                m.version = str(in);
                byte[] mh = new byte[16];
                in.readFully(mh);
                m.hash = hex(mh);
                h.mods.add(m);
            }
            int nc = in.readUnsignedByte();
            for (int i = 0; i < nc; i++) h.classHits.add(str(in));
            return h;
        } catch (IOException e) {
            throw new Bad("formato");
        }
    }

    static Telemetry parseTelemetry(byte[] d, byte[] sessionKey) throws Bad {
        try {
            if (d.length < 1 + 4 + 2 + 2 + 16) throw new Bad("corto");
            byte[] mac = Arrays.copyOf(hmac(sessionKey, d, 0, d.length - 16), 16);
            if (!MessageDigest.isEqual(mac, Arrays.copyOfRange(d, d.length - 16, d.length))) throw new Bad("firma");
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(d, 0, d.length - 16));
            if (in.readUnsignedByte() != T_TELEMETRY) throw new Bad("tipo");
            Telemetry t = new Telemetry();
            t.seq = in.readInt() & 0xFFFFFFFFL;
            t.n = in.readUnsignedShort();
            if (t.n > MAX_TICKS) throw new Bad("ticks");
            t.yaw = new float[t.n];
            t.pitch = new float[t.n];
            t.mdx = new short[t.n];
            t.mdy = new short[t.n];
            t.flags = new byte[t.n];
            for (int i = 0; i < t.n; i++) {
                t.yaw[i] = in.readFloat();
                t.pitch[i] = in.readFloat();
                t.mdx[i] = in.readShort();
                t.mdy[i] = in.readShort();
                t.flags[i] = in.readByte();
            }
            t.nClicks = in.readUnsignedShort();
            if (t.nClicks > MAX_CLICKS) throw new Bad("clics");
            t.clickDtMicros = new int[t.nClicks];
            for (int i = 0; i < t.nClicks; i++) {
                in.readByte(); // botón (reservado)
                t.clickDtMicros[i] = in.readInt();
            }
            return t;
        } catch (IOException e) {
            throw new Bad("formato");
        }
    }
}
