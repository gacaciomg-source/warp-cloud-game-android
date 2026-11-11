package com.limelight.warp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Capas e artes dos jogos: baixa uma vez, guarda na memória e reduz para o
 * tamanho em que vão aparecer (uma capa de 2000 px numa miniatura de 150 dp
 * só gastaria memória). Vídeo de capa fica de fora: aparece a cor do jogo.
 */
public final class WarpImagens {
    private static final int LIMITE_DE_BYTES = 15 * 1024 * 1024;

    private static final LruCache<String, Bitmap> guardadas =
            new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 1024 / 8)) {
                @Override
                protected int sizeOf(String chave, Bitmap b) {
                    return b.getByteCount() / 1024;
                }
            };
    private static final ExecutorService fila = Executors.newFixedThreadPool(3);
    private static final Handler principal = new Handler(Looper.getMainLooper());

    private WarpImagens() {
    }

    public static boolean ehVideo(String url) {
        if (url == null) return false;
        String u = url.toLowerCase();
        return u.contains(".mp4") || u.contains(".webm");
    }

    /**
     * Mostra a imagem em `destino`, reduzida para cerca de `larguraPx`.
     * Com `alfaFinal` >= 0, aparece suave até essa transparência.
     */
    public static void carregar(final ImageView destino, String endereco, final int larguraPx, final float alfaFinal) {
        final String url = WarpApi.urlAbsoluta(endereco);
        destino.setTag(url);
        if (url == null || ehVideo(url)) {
            destino.setImageDrawable(null);
            return;
        }
        final String chave = url + "@" + larguraPx;
        Bitmap pronta = guardadas.get(chave);
        if (pronta != null) {
            destino.setImageBitmap(pronta);
            if (alfaFinal >= 0) destino.setAlpha(alfaFinal);
            return;
        }
        destino.setImageDrawable(null);
        fila.execute(() -> {
            final Bitmap b = baixar(url, larguraPx);
            if (b == null) return;
            guardadas.put(chave, b);
            principal.post(() -> {
                if (!url.equals(destino.getTag())) return;
                destino.setImageBitmap(b);
                if (alfaFinal >= 0) {
                    destino.setAlpha(0f);
                    destino.animate().alpha(alfaFinal).setDuration(300).start();
                }
            });
        });
    }

    private static Bitmap baixar(String url, int larguraPx) {
        byte[] dados;
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream saida = new ByteArrayOutputStream();
                byte[] buf = new byte[32 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    saida.write(buf, 0, n);
                    if (saida.size() > LIMITE_DE_BYTES) return null;
                }
                dados = saida.toByteArray();
            } finally {
                c.disconnect();
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }

        BitmapFactory.Options medidas = new BitmapFactory.Options();
        medidas.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(dados, 0, dados.length, medidas);
        if (medidas.outWidth <= 0) return null;

        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = 1;
        while (medidas.outWidth / (o.inSampleSize * 2) >= larguraPx) o.inSampleSize *= 2;
        try {
            return BitmapFactory.decodeByteArray(dados, 0, dados.length, o);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }
}
