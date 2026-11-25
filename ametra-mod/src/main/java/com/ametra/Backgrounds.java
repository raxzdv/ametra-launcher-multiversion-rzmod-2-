package com.ametra;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;

import org.w3c.dom.NodeList;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Fondos del menú principal. Cualquier cosa que pongas en  .minecraft/(instancia)/ametra/fondos/  aparece sola:
 *   - imágenes PNG / JPG / BMP (fondo fijo),
 *   - GIF animados,
 *   - carpetas con frames numerados (img_001.png, img_002.png...). Pon "@30" al final del nombre de la carpeta para 30 fps.
 * Además hay fondos integrados dibujados por código (Clásico, Aurora, Ondas, Estrellas).
 * Los GIF/frames se decodifican en un hilo aparte y se reducen (máx. 640 px de ancho) con un tope de memoria,
 * así que un GIF enorme no congela el juego: se queda con menos fotogramas, pero se ve fluido.
 */
public final class Backgrounds {
    private Backgrounds() {}

    static {
        // Solo usamos ImageIO para leer archivos (nada de ventanas AWT); así no molesta a GLFW en macOS.
        if (System.getProperty("java.awt.headless") == null) System.setProperty("java.awt.headless", "true");
    }

    private static final long BUDGET = 96L << 20;   // memoria máxima por fondo animado (bytes)
    private static final int MAXW_ANIM = 640, MAXW_IMG = 1280;

    // ---------------------------------------------------------------- lista de fondos
    public static final class Entry {
        public static final int BUILTIN = 0, IMAGE = 1, GIF = 2, FRAMES = 3;
        public final String id, name;
        public final int kind;
        final Path path;

        Entry(String id, String name, int kind, Path path) {
            this.id = id;
            this.name = name;
            this.kind = kind;
            this.path = path;
        }

        public boolean builtin() { return kind == BUILTIN; }

        public String tag() {
            return switch (kind) {
                case BUILTIN -> "INTEGRADO";
                case IMAGE -> "IMAGEN";
                case GIF -> "GIF";
                default -> "FRAMES";
            };
        }
    }

    private static final String[][] BUILTINS = {
        {"b:clasico", "Clásico"}, {"b:aurora", "Aurora"}, {"b:ondas", "Ondas"}, {"b:estrellas", "Estrellas"}};

    private static List<Entry> entries = new ArrayList<>();
    private static long scanned = 0L;

    /** Carpeta donde se sueltan los fondos (se crea sola). */
    public static Path dir() {
        return FabricLoader.getInstance().getGameDir().resolve("ametra").resolve("fondos");
    }

    public static synchronized List<Entry> list() {
        if (entries.isEmpty() || System.currentTimeMillis() - scanned > 3000L) rescan();
        return entries;
    }

    public static synchronized void rescan() {
        scanned = System.currentTimeMillis();
        List<Entry> out = new ArrayList<>();
        for (String[] b : BUILTINS) out.add(new Entry(b[0], b[1], Entry.BUILTIN, null));
        Path d = dir();
        try {
            Files.createDirectories(d);
            List<Path> ps = new ArrayList<>();
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(d)) {
                for (Path p : ds) ps.add(p);
            }
            ps.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)));
            for (Path p : ps) {
                String fn = p.getFileName().toString();
                if (fn.startsWith(".")) continue;
                if (Files.isDirectory(p)) {
                    if (frameFiles(p).size() >= 2) out.add(new Entry("d:" + fn, fn.replaceAll("@\\d+$", ""), Entry.FRAMES, p));
                } else {
                    String ext = ext(fn);
                    String base = fn.contains(".") ? fn.substring(0, fn.lastIndexOf('.')) : fn;
                    if (ext.equals("gif")) out.add(new Entry("f:" + fn, base, Entry.GIF, p));
                    else if (isImg(ext)) out.add(new Entry("f:" + fn, base, Entry.IMAGE, p));
                }
            }
        } catch (IOException ignored) { /* sin permisos: solo los integrados */ }
        entries = out;
    }

    private static String ext(String fn) {
        int i = fn.lastIndexOf('.');
        return i < 0 ? "" : fn.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean isImg(String ext) {
        return ext.equals("png") || ext.equals("jpg") || ext.equals("jpeg") || ext.equals("bmp");
    }

    private static List<Path> frameFiles(Path dir) {
        List<Path> out = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) if (!Files.isDirectory(p) && isImg(ext(p.getFileName().toString()))) out.add(p);
        } catch (IOException ignored) { /* carpeta ilegible */ }
        out.sort(Comparator.comparing(p -> natKey(p.getFileName().toString())));
        return out;
    }

    private static String natKey(String s) {
        String l = s.toLowerCase(Locale.ROOT);
        Matcher m = Pattern.compile("\\d+").matcher(l);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find()) {
            sb.append(l, last, m.start());
            String d = m.group();
            sb.append("0".repeat(Math.max(0, 9 - d.length()))).append(d);
            last = m.end();
        }
        return sb.append(l.substring(last)).toString();
    }

    public static Entry find(String id) {
        if (id == null) return null;
        for (Entry e : list()) if (e.id.equals(id)) return e;
        return null;
    }

    public static Entry current() {
        Entry e = find(AmetraConfig.background);
        return e != null ? e : find("b:clasico");
    }

    public static void select(String id) {
        AmetraConfig.background = id;
        AmetraConfig.save();
    }

    public static void openFolder() {
        try {
            Files.createDirectories(dir());
            net.minecraft.util.Util.getOperatingSystem().open(dir().toFile());
        } catch (Exception ignored) { /* no se pudo abrir: el usuario la encuentra en .minecraft/ametra/fondos */ }
    }

    /** Vuelve a leer la carpeta y descarta lo que había cargado (por si cambiaste un archivo). */
    public static void reload() {
        for (Anim a : CACHE.values()) a.free();
        CACHE.clear();
        rescan();
    }

    // ---------------------------------------------------------------- fondos con archivo (decodificados aparte)
    private static final class Anim {
        final Entry e;
        volatile int state = 0;        // 0 cargando, 1 listo, 2 error
        volatile boolean cancel = false;
        volatile String error = "";
        int w, h, n;
        int[][] px;                    // fotogramas en ABGR opaco (listos para NativeImage)
        int[] ends;                    // fin de cada fotograma en ms (acumulado)
        int total = 1;
        Identifier texId;
        NativeImageBackedTexture tex;
        int shown = -1;
        long start = 0L, used = 0L;

        Anim(Entry e) { this.e = e; }

        void free() {
            cancel = true;
            if (tex != null) {
                MinecraftClient.getInstance().getTextureManager().destroyTexture(texId);
                tex = null;
            }
            px = null;
        }

        int indexAt(long pos) {
            int lo = 0, hi = n - 1;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (ends[mid] > pos) hi = mid; else lo = mid + 1;
            }
            return lo;
        }
    }

    private static final Map<String, Anim> CACHE = new LinkedHashMap<>();
    private static int texCounter = 0;

    private static Anim get(Entry e) {
        Anim a = CACHE.get(e.id);
        if (a == null) {
            a = new Anim(e);
            CACHE.put(e.id, a);
            final Anim fa = a;
            Thread t = new Thread(() -> decode(fa), "ametra-fondos");
            t.setDaemon(true);
            t.start();
            evict(a);
        }
        a.used = System.nanoTime();
        return a;
    }

    private static void evict(Anim keep) {
        while (CACHE.size() > 3) {
            Anim old = null;
            for (Anim x : CACHE.values()) {
                if (x == keep || x.e.id.equals(AmetraConfig.background)) continue;
                if (old == null || x.used < old.used) old = x;
            }
            if (old == null) break;
            CACHE.remove(old.e.id);
            old.free();
        }
    }

    private static void decode(Anim a) {
        try {
            switch (a.e.kind) {
                case Entry.GIF -> decodeGif(a);
                case Entry.FRAMES -> decodeFrames(a);
                default -> decodeStill(a);
            }
        } catch (Throwable t) {
            String m = t.getMessage();
            a.error = m == null || m.isBlank() ? t.getClass().getSimpleName() : m;
            a.state = 2;
        }
    }

    private static int toInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    private static void decodeStill(Anim a) throws IOException {
        BufferedImage bi = ImageIO.read(a.e.path.toFile());
        if (bi == null) throw new IOException("formato no soportado");
        int sw = bi.getWidth(), sh = bi.getHeight();
        int[] src = bi.getRGB(0, 0, sw, sh, null, 0, sw);
        double sc = Math.min(1.0, MAXW_IMG / (double) sw);
        int tw = Math.max(1, (int) Math.round(sw * sc)), th = Math.max(1, (int) Math.round(sh * sc));
        List<int[]> out = new ArrayList<>();
        out.add(scale(src, sw, sh, tw, th));
        List<Integer> d = new ArrayList<>();
        d.add(1000);
        finish(a, tw, th, out, d);
    }

    private static void decodeGif(Anim a) throws Exception {
        try (ImageInputStream in = ImageIO.createImageInputStream(a.e.path.toFile())) {
            Iterator<ImageReader> it = ImageIO.getImageReadersByFormatName("gif");
            if (in == null || !it.hasNext()) throw new IOException("no se pudo abrir el GIF");
            ImageReader r = it.next();
            try {
                r.setInput(in, false, false);
                int n = r.getNumImages(true);
                if (n < 1) throw new IOException("GIF vacío");
                int sw = 0, sh = 0;
                IIOMetadata sm = r.getStreamMetadata();
                if (sm != null) {
                    IIOMetadataNode root = (IIOMetadataNode) sm.getAsTree("javax_imageio_gif_stream_1.0");
                    NodeList l = root.getElementsByTagName("LogicalScreenDescriptor");
                    if (l.getLength() > 0) {
                        IIOMetadataNode d = (IIOMetadataNode) l.item(0);
                        sw = toInt(d.getAttribute("logicalScreenWidth"));
                        sh = toInt(d.getAttribute("logicalScreenHeight"));
                    }
                }
                if (sw <= 0 || sh <= 0) {
                    BufferedImage f0 = r.read(0);
                    sw = f0.getWidth();
                    sh = f0.getHeight();
                }
                double sc = Math.min(1.0, MAXW_ANIM / (double) sw);
                int tw = Math.max(1, (int) Math.round(sw * sc)), th = Math.max(1, (int) Math.round(sh * sc));
                int cap = (int) Math.max(2L, BUDGET / ((long) tw * th * 4L));
                int stride = Math.max(1, (int) Math.ceil(n / (double) cap));

                int[] canvas = new int[sw * sh];
                int[] prev = null;
                List<int[]> out = new ArrayList<>();
                List<Integer> delays = new ArrayList<>();
                for (int i = 0; i < n && !a.cancel; i++) {
                    BufferedImage fr = r.read(i);
                    int left = 0, top = 0, delay = 10;
                    String disp = "none";
                    IIOMetadata im = r.getImageMetadata(i);
                    if (im != null) {
                        IIOMetadataNode root = (IIOMetadataNode) im.getAsTree("javax_imageio_gif_image_1.0");
                        NodeList l = root.getElementsByTagName("ImageDescriptor");
                        if (l.getLength() > 0) {
                            IIOMetadataNode d = (IIOMetadataNode) l.item(0);
                            left = toInt(d.getAttribute("imageLeftPosition"));
                            top = toInt(d.getAttribute("imageTopPosition"));
                        }
                        l = root.getElementsByTagName("GraphicControlExtension");
                        if (l.getLength() > 0) {
                            IIOMetadataNode d = (IIOMetadataNode) l.item(0);
                            disp = d.getAttribute("disposalMethod");
                            delay = toInt(d.getAttribute("delayTime"));
                        }
                    }
                    if (delay <= 1) delay = 10; // como los navegadores: 0 o 1 centésima se toma como 10
                    if (disp.equals("restoreToPrevious")) prev = canvas.clone();

                    int fw = fr.getWidth(), fh = fr.getHeight();
                    int[] px = fr.getRGB(0, 0, fw, fh, null, 0, fw);
                    for (int y = 0; y < fh; y++) {
                        int cy = top + y;
                        if (cy < 0 || cy >= sh) continue;
                        for (int x = 0; x < fw; x++) {
                            int cx = left + x;
                            if (cx < 0 || cx >= sw) continue;
                            int p = px[y * fw + x];
                            if ((p >>> 24) != 0) canvas[cy * sw + cx] = p | 0xFF000000;
                        }
                    }
                    if (i % stride == 0) {
                        out.add(scale(canvas, sw, sh, tw, th));
                        delays.add(delay * 10);
                    } else {
                        int last = delays.size() - 1;
                        delays.set(last, delays.get(last) + delay * 10);
                    }
                    if (disp.equals("restoreToBackgroundColor")) {
                        for (int y = 0; y < fh; y++) {
                            int cy = top + y;
                            if (cy < 0 || cy >= sh) continue;
                            for (int x = 0; x < fw; x++) {
                                int cx = left + x;
                                if (cx >= 0 && cx < sw) canvas[cy * sw + cx] = 0;
                            }
                        }
                    } else if (disp.equals("restoreToPrevious") && prev != null) {
                        canvas = prev;
                    }
                }
                if (a.cancel) return;
                finish(a, tw, th, out, delays);
            } finally {
                r.dispose();
            }
        }
    }

    private static void decodeFrames(Anim a) throws IOException {
        List<Path> files = frameFiles(a.e.path);
        if (files.size() < 2) throw new IOException("hacen falta 2 imágenes o más");
        Matcher m = Pattern.compile("@(\\d{1,3})$").matcher(a.e.path.getFileName().toString());
        int fps = m.find() ? Math.max(1, Math.min(60, toInt(m.group(1)))) : 20;

        BufferedImage first = ImageIO.read(files.get(0).toFile());
        if (first == null) throw new IOException("no se pudo leer " + files.get(0).getFileName());
        int sw = first.getWidth(), sh = first.getHeight();
        double sc = Math.min(1.0, MAXW_ANIM / (double) sw);
        int tw = Math.max(1, (int) Math.round(sw * sc)), th = Math.max(1, (int) Math.round(sh * sc));
        int cap = (int) Math.max(2L, BUDGET / ((long) tw * th * 4L));
        int stride = Math.max(1, (int) Math.ceil(files.size() / (double) cap));
        int ms = Math.max(1, Math.round(1000f * stride / fps));

        List<int[]> out = new ArrayList<>();
        List<Integer> delays = new ArrayList<>();
        for (int i = 0; i < files.size() && !a.cancel; i += stride) {
            BufferedImage bi = i == 0 ? first : ImageIO.read(files.get(i).toFile());
            if (bi == null) continue;
            int w = bi.getWidth(), h = bi.getHeight();
            out.add(scale(bi.getRGB(0, 0, w, h, null, 0, w), w, h, tw, th));
            delays.add(ms);
        }
        if (a.cancel) return;
        if (out.isEmpty()) throw new IOException("no se pudo leer ninguna imagen");
        finish(a, tw, th, out, delays);
    }

    /** Promedia por bloques (sin java.awt.Graphics2D) y deja el resultado opaco. */
    private static int[] scale(int[] src, int sw, int sh, int tw, int th) {
        int[] out = new int[tw * th];
        for (int y = 0; y < th; y++) {
            int y0 = (int) ((long) y * sh / th), y1 = Math.max(y0 + 1, (int) ((long) (y + 1) * sh / th));
            for (int x = 0; x < tw; x++) {
                int x0 = (int) ((long) x * sw / tw), x1 = Math.max(x0 + 1, (int) ((long) (x + 1) * sw / tw));
                long r = 0, g = 0, b = 0;
                int c = 0;
                for (int yy = y0; yy < y1 && yy < sh; yy++) {
                    for (int xx = x0; xx < x1 && xx < sw; xx++) {
                        int p = src[yy * sw + xx];
                        r += (p >> 16) & 255;
                        g += (p >> 8) & 255;
                        b += p & 255;
                        c++;
                    }
                }
                if (c == 0) c = 1;
                out[y * tw + x] = 0xFF000000 | ((int) (r / c) << 16) | ((int) (g / c) << 8) | (int) (b / c);
            }
        }
        return out;
    }

    private static void finish(Anim a, int tw, int th, List<int[]> frames, List<Integer> delays) {
        int n = frames.size();
        int[][] px = new int[n][];
        int[] ends = new int[n];
        int acc = 0;
        for (int i = 0; i < n; i++) {
            int[] f = frames.get(i);
            for (int k = 0; k < f.length; k++) { // ARGB -> ABGR (formato de NativeImage en 1.21.1)
                int p = f[k];
                f[k] = 0xFF000000 | ((p & 0xFF) << 16) | (p & 0xFF00) | ((p >> 16) & 0xFF);
            }
            px[i] = f;
            acc += Math.max(20, delays.get(i));
            ends[i] = acc;
        }
        a.w = tw;
        a.h = th;
        a.n = n;
        a.px = px;
        a.ends = ends;
        a.total = Math.max(1, acc);
        a.state = 1; // se publica al final: lo anterior ya está escrito
    }

    // ---------------------------------------------------------------- dibujo
    /** Fondo elegido a pantalla completa (menú principal y menú de Ametra sin mundo). */
    public static void drawFull(DrawContext ctx, int w, int h, float t) {
        drawEntry(ctx, current(), 0, 0, w, h, t, true);
    }

    /** Dibuja un fondo en un rectángulo. dim = aplicar el oscurecido configurado (imágenes y GIF). */
    public static void drawEntry(DrawContext ctx, Entry e, int x, int y, int w, int h, float t, boolean dim) {
        if (e == null || e.builtin()) {
            drawBuiltin(e == null ? "b:clasico" : e.id, ctx, x, y, w, h, t);
            return;
        }
        Anim a = get(e);
        if (a.state == 1) {
            drawAnim(ctx, a, x, y, w, h);
            if (dim && AmetraConfig.bgDim > 0.01f) Gfx.rect(ctx, x, y, w, h, 0x000000, AmetraConfig.bgDim);
            return;
        }
        ctx.fillGradient(x, y, x + w, y + h, 0xFF08080F, 0xFF151222);
        String m = a.state == 2 ? "No se pudo leer: " + a.error : "Cargando…";
        if (m.length() > 70) m = m.substring(0, 70) + "…";
        float ty = w > 300 ? y + h - 26 : y + h / 2f - 4;
        Gfx.text(ctx, m, x + Math.max(6f, (w - Gfx.width(m)) / 2f), ty, a.state == 2 ? 0xF1948A : 0x8E8E9E, 1f, true);
    }

    private static void drawAnim(DrawContext ctx, Anim a, int x, int y, int w, int h) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (a.tex == null) {
            a.tex = new NativeImageBackedTexture(new NativeImage(NativeImage.Format.RGBA, a.w, a.h, false));
            a.texId = Compat.id("ametra", "dyn/fondo_" + (texCounter++));
            mc.getTextureManager().registerTexture(a.texId, a.tex);
            a.tex.setFilter(true, false);
            a.shown = -1;
        }
        long now = System.currentTimeMillis();
        if (a.start == 0L) a.start = now;
        int idx = a.n > 1 ? a.indexAt((now - a.start) % a.total) : 0;
        if (idx != a.shown) upload(a, idx);

        float ia = a.w / (float) a.h, ta = w / (float) h;
        int rw = a.w, rh = a.h, u = 0, v = 0;
        if (ia > ta) { rw = Math.max(1, Math.round(a.h * ta)); u = (a.w - rw) / 2; }
        else { rh = Math.max(1, Math.round(a.w / ta)); v = (a.h - rh) / 2; }
        Compat.drawTex(ctx, a.texId, x, y, w, h, (float) u, (float) v, rw, rh, a.w, a.h);
    }

    private static void upload(Anim a, int idx) {
        NativeImage img = a.tex.getImage();
        if (img == null || a.px == null) return;
        int[] p = a.px[idx];
        int w = a.w, h = a.h;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) Compat.setPixel(img, x, y, p[y * w + x]);
        }
        a.tex.upload();
        a.shown = idx;
    }

    // ---------------------------------------------------------------- fondos integrados (dibujados por código)
    private static final int NS = 150;
    private static final float[] SX = new float[NS], SY = new float[NS], SZ = new float[NS], SP = new float[NS];

    static {
        Random r = new Random(99);
        for (int i = 0; i < NS; i++) {
            SX[i] = r.nextFloat();
            SY[i] = r.nextFloat();
            SZ[i] = r.nextFloat();
            SP[i] = r.nextFloat() * 6.28f;
        }
    }

    static void drawBuiltin(String id, DrawContext ctx, int x, int y, int w, int h, float t) {
        int ac = AmetraConfig.accent;
        MatrixStack ms = ctx.getMatrices();
        ms.push();
        ms.translate(x, y, 0f);
        switch (id) {
            case "b:aurora" -> aurora(ctx, w, h, t, ac);
            case "b:ondas" -> ondas(ctx, w, h, t, ac);
            case "b:estrellas" -> estrellas(ctx, w, h, t, ac);
            default -> clasico(ctx, w, h, t, ac);
        }
        ms.pop();
    }

    private static void clasico(DrawContext ctx, int w, int h, float t, int ac) {
        ctx.fillGradient(0, 0, w, h, 0xFF08080F, 0xFF151222);
        if (!AmetraConfig.titleFx) return;
        float gx1 = w * (0.30f + 0.08f * (float) Math.sin(t * 0.20f)), gy1 = h * (0.35f + 0.06f * (float) Math.cos(t * 0.25f));
        float gx2 = w * (0.72f + 0.08f * (float) Math.cos(t * 0.17f)), gy2 = h * (0.70f + 0.06f * (float) Math.sin(t * 0.22f));
        Gfx.glow(ctx, gx1, gy1, Math.min(w, h) * 0.45f, ac, 0.10f);
        Gfx.glow(ctx, gx2, gy2, Math.min(w, h) * 0.38f, Colors.lerp(ac, 0x6C7BFF, 0.5f), 0.08f);
        Fx.particles(ctx, w, h, t, 40);
    }

    private static void aurora(DrawContext ctx, int w, int h, float t, int ac) {
        ctx.fillGradient(0, 0, w, h, 0xFF05060F, 0xFF0B1022);
        int step = Math.max(2, w / 160);
        for (int x = 0; x < w; x += step) {
            float u = x / (float) w;
            for (int b = 0; b < 3; b++) {
                float cy = h * (0.30f + 0.13f * b) + (float) Math.sin(u * 5f + t * 0.4f + b * 1.7f) * h * 0.07f
                    + (float) Math.sin(u * 11f - t * 0.3f + b) * h * 0.03f;
                float bh = h * (0.20f + 0.05f * (float) Math.sin(u * 3f + t * 0.5f + b));
                int col = Colors.lerp(ac, Colors.hsv(0.50f + 0.12f * b + 0.05f * (float) Math.sin(t * 0.2f + u * 2f), 0.6f, 1f), 0.35f + 0.25f * b);
                int y0 = Math.round(cy - bh), y1 = Math.round(cy), y2 = Math.round(cy + bh * 0.45f);
                ctx.fillGradient(x, y0, x + step, y1, Colors.argb(col, 0f), Colors.argb(col, 0.26f));
                ctx.fillGradient(x, y1, x + step, y2, Colors.argb(col, 0.26f), Colors.argb(col, 0f));
            }
        }
    }

    private static void ondas(DrawContext ctx, int w, int h, float t, int ac) {
        ctx.fillGradient(0, 0, w, h, 0xFF070A14, 0xFF000000 | Colors.lerp(0x0A0E1C, ac, 0.16f));
        for (int l = 0; l < 4; l++) {
            int col = Colors.lerp(ac, 0x6C7BFF, l / 3f);
            float alpha = 0.07f + 0.04f * l;
            for (int x = 0; x < w; x += 2) {
                float yy = h * (0.50f + 0.12f * l) + (float) Math.sin(x * 0.018f * (1f + 0.35f * l) + t * (0.5f + 0.25f * l) + l * 1.3f) * h * 0.045f
                    + (float) Math.sin(x * 0.007f + t * 0.3f) * h * 0.03f;
                Gfx.rect(ctx, x, yy, 2, h - yy, col, alpha);
            }
        }
    }

    private static void estrellas(DrawContext ctx, int w, int h, float t, int ac) {
        ctx.fillGradient(0, 0, w, h, 0xFF04050C, 0xFF0A0B1A);
        Gfx.glow(ctx, w * (0.25f + 0.05f * (float) Math.sin(t * 0.13f)), h * 0.40f, Math.min(w, h) * 0.5f, ac, 0.07f);
        Gfx.glow(ctx, w * 0.78f, h * (0.65f + 0.04f * (float) Math.cos(t * 0.11f)), Math.min(w, h) * 0.4f, Colors.lerp(ac, 0x6C7BFF, 0.6f), 0.06f);
        for (int i = 0; i < NS; i++) {
            float sx = (((SX[i] - t * 0.012f * (0.3f + SZ[i])) % 1f) + 1f) % 1f;
            float tw = 0.5f + 0.5f * (float) Math.sin(t * (1.2f + SZ[i]) + SP[i]);
            int sz = SZ[i] > 0.82f ? 2 : 1;
            Gfx.rect(ctx, sx * w, SY[i] * h, sz, sz, i % 7 == 0 ? ac : 0xFFFFFF, 0.18f + 0.6f * tw * (0.4f + SZ[i] * 0.6f));
        }
    }
}
