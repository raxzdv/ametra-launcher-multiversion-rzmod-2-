package com.ametra.net;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Formato binario del canal de sincronización. Lógica pura (sin Minecraft). */
public final class Wire {
    private Wire() {}

    public static final int VERSION = 1;
    public static final int T_CHALLENGE = 1, T_HELLO = 2, T_TELEMETRY = 3;

    public static final class ModInfo {
        public final String id, version;
        public final byte[] hash16;

        public ModInfo(String id, String version, byte[] hash16) {
            this.id = id;
            this.version = version;
            this.hash16 = hash16;
        }
    }

    public static final class Challenge {
        public byte[] nonce;
        public final List<String> probes = new ArrayList<>();
    }

    public static byte[] hmac(byte[] key, byte[] data, int off, int len) {
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(key, "HmacSHA256"));
            m.update(data, off, len);
            return m.doFinal();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] sessionKey(byte[] secret, byte[] nonce) {
        byte[] d = new byte[1 + nonce.length];
        d[0] = 's';
        System.arraycopy(nonce, 0, d, 1, nonce.length);
        return hmac(secret, d, 0, d.length);
    }

    public static Challenge parseChallenge(byte[] d) throws IOException {
        java.io.DataInputStream in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(d));
        if (in.readUnsignedByte() != T_CHALLENGE) throw new IOException();
        Challenge c = new Challenge();
        c.nonce = new byte[16];
        in.readFully(c.nonce);
        int n = in.readUnsignedByte();
        for (int i = 0; i < n && i < 64; i++) {
            String s = in.readUTF();
            if (s.length() <= 200) c.probes.add(s);
        }
        return c;
    }

    public static byte[] hello(byte[] secret, byte[] nonce, byte[] jarHash32, int flags, List<String> agents,
                               List<ModInfo> mods, List<String> classHits) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream o = new DataOutputStream(b);
            o.writeByte(T_HELLO);
            o.writeByte(VERSION);
            o.write(nonce);
            o.write(jarHash32);
            o.writeByte(flags);
            int na = Math.min(8, agents.size());
            o.writeByte(na);
            for (int i = 0; i < na; i++) o.writeUTF(cut(agents.get(i)));
            int nm = Math.min(1000, mods.size());
            o.writeShort(nm);
            for (int i = 0; i < nm; i++) {
                ModInfo m = mods.get(i);
                o.writeUTF(cut(m.id));
                o.writeUTF(cut(m.version));
                o.write(m.hash16);
            }
            int nc = Math.min(64, classHits.size());
            o.writeByte(nc);
            for (int i = 0; i < nc; i++) o.writeUTF(cut(classHits.get(i)));
            byte[] body = b.toByteArray();
            byte[] mac = hmac(secret, body, 0, body.length);
            byte[] out = Arrays.copyOf(body, body.length + 32);
            System.arraycopy(mac, 0, out, body.length, 32);
            return out;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String cut(String s) {
        return s == null ? "" : (s.length() > 120 ? s.substring(0, 120) : s);
    }

    public static byte[] telemetry(byte[] sessionKey, int seq, int n, float[] yaw, float[] pitch, short[] mdx, short[] mdy,
                                   byte[] flags, int nClicks, int[] clickDtMicros) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream(16 + n * 13 + nClicks * 5);
            DataOutputStream o = new DataOutputStream(b);
            o.writeByte(T_TELEMETRY);
            o.writeInt(seq);
            o.writeShort(n);
            for (int i = 0; i < n; i++) {
                o.writeFloat(yaw[i]);
                o.writeFloat(pitch[i]);
                o.writeShort(mdx[i]);
                o.writeShort(mdy[i]);
                o.writeByte(flags[i]);
            }
            o.writeShort(nClicks);
            for (int i = 0; i < nClicks; i++) {
                o.writeByte(0);
                o.writeInt(clickDtMicros[i]);
            }
            byte[] body = b.toByteArray();
            byte[] mac = hmac(sessionKey, body, 0, body.length);
            byte[] out = Arrays.copyOf(body, body.length + 16);
            System.arraycopy(mac, 0, out, body.length, 16);
            return out;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
