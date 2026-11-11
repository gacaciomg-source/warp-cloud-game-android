package com.limelight.warp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.limelight.PcView;
import com.limelight.R;
import com.limelight.preferences.StreamSettings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Catálogo do Cloud Game: os jogos cadastrados no painel da WARP.
 *
 * Tocar num jogo liberado abre a sessão (fila, conexão e o jogo). Jogo "em
 * breve" aparece, mas não abre. As capas vêm do painel; vídeo de capa aparece
 * só com a cor do jogo, para o catálogo continuar leve.
 */
public class WarpJogosActivity extends Activity {
    private final List<JSONObject> jogos = new ArrayList<>();
    private final LruCache<String, Bitmap> capas = new LruCache<>(24);
    private final ExecutorService baixador = Executors.newFixedThreadPool(3);

    private Adaptador adaptador;
    private ProgressBar carregando;
    private TextView aviso;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.warp_jogos);

        carregando = findViewById(R.id.warp_carregando);
        aviso = findViewById(R.id.warp_aviso);
        GridView grade = findViewById(R.id.warp_grade);
        adaptador = new Adaptador();
        grade.setAdapter(adaptador);
        grade.setOnItemClickListener((pai, v, posicao, id) -> tocarJogo(jogos.get(posicao)));

        findViewById(R.id.warp_menu).setOnClickListener(v -> abrirMenu());
        findViewById(R.id.warp_atualizar).setOnClickListener(v -> carregar());
    }

    @Override
    protected void onResume() {
        super.onResume();
        carregar();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        baixador.shutdownNow();
    }

    private void carregar() {
        carregando.setVisibility(View.VISIBLE);
        aviso.setVisibility(View.GONE);
        new Thread(() -> {
            List<JSONObject> lista = new ArrayList<>();
            String falha = null;
            try {
                JSONArray a = WarpApi.get(this).jogos();
                // Jogáveis primeiro; "em breve" no fim.
                for (int pass = 0; pass < 2; pass++) {
                    for (int i = 0; i < a.length(); i++) {
                        JSONObject j = a.getJSONObject(i);
                        if (j.optBoolean("jogavel") == (pass == 0)) lista.add(j);
                    }
                }
            } catch (Exception e) {
                falha = e instanceof WarpApi.ErroApi ? e.getMessage() : "Sem conexão com a WARP.";
            }
            final String mensagem = falha;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                carregando.setVisibility(View.GONE);
                jogos.clear();
                jogos.addAll(lista);
                adaptador.notifyDataSetChanged();
                if (mensagem != null) {
                    aviso.setText(mensagem);
                    aviso.setVisibility(View.VISIBLE);
                } else if (jogos.isEmpty()) {
                    aviso.setText("Nenhum jogo liberado ainda. Volte em breve.");
                    aviso.setVisibility(View.VISIBLE);
                }
            });
        }).start();
    }

    private void tocarJogo(JSONObject jogo) {
        if (!jogo.optBoolean("jogavel")) {
            new AlertDialog.Builder(this)
                    .setTitle(jogo.optString("title"))
                    .setMessage("Este jogo chega em breve na WARP.")
                    .setPositiveButton("Ok", null)
                    .show();
            return;
        }
        Intent i = new Intent(this, WarpSessaoActivity.class);
        i.putExtra(WarpSessaoActivity.EXTRA_SLUG, jogo.optString("slug"));
        i.putExtra(WarpSessaoActivity.EXTRA_TITULO, jogo.optString("title"));
        i.putExtra(WarpSessaoActivity.EXTRA_CAPA, WarpApi.urlAbsoluta(jogo.optString("coverUrl", null)));
        startActivity(i);
    }

    private void abrirMenu() {
        String[] opcoes = {"Qualidade de vídeo e controles", "Modo avançado (Moonlight)", "Sair da conta"};
        new AlertDialog.Builder(this)
                .setItems(opcoes, (d, qual) -> {
                    if (qual == 0) {
                        startActivity(new Intent(this, StreamSettings.class));
                    } else if (qual == 1) {
                        startActivity(new Intent(this, PcView.class));
                    } else {
                        new Thread(() -> {
                            WarpApi.get(this).sairDaConta();
                            runOnUiThread(() -> {
                                startActivity(new Intent(this, WarpEntrarActivity.class));
                                finish();
                            });
                        }).start();
                    }
                })
                .show();
    }

    private static int corDoJogo(JSONObject jogo) {
        try {
            return Color.parseColor(jogo.optString("accentColor", "#15304a"));
        } catch (IllegalArgumentException e) {
            return Color.parseColor("#15304a");
        }
    }

    private static boolean ehVideo(String url) {
        String u = url.toLowerCase();
        return u.contains(".mp4") || u.contains(".webm");
    }

    private void baixarCapa(String url, ImageView destino) {
        destino.setTag(url);
        Bitmap pronta = capas.get(url);
        if (pronta != null) {
            destino.setImageBitmap(pronta);
            return;
        }
        destino.setImageDrawable(null);
        baixador.execute(() -> {
            Bitmap b = null;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(15000);
                try (InputStream in = c.getInputStream()) {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inSampleSize = 2;
                    b = BitmapFactory.decodeStream(in, null, o);
                } finally {
                    c.disconnect();
                }
            } catch (IOException ignored) {
                // sem capa: fica a cor do jogo
            }
            if (b == null) return;
            capas.put(url, b);
            final Bitmap img = b;
            runOnUiThread(() -> {
                if (url.equals(destino.getTag())) destino.setImageBitmap(img);
            });
        });
    }

    private final class Adaptador extends BaseAdapter {
        @Override
        public int getCount() {
            return jogos.size();
        }

        @Override
        public Object getItem(int i) {
            return jogos.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int posicao, View reaproveitada, ViewGroup pai) {
            View v = reaproveitada != null
                    ? reaproveitada
                    : LayoutInflater.from(WarpJogosActivity.this).inflate(R.layout.warp_item_jogo, pai, false);
            JSONObject jogo = jogos.get(posicao);

            ImageView capa = v.findViewById(R.id.warp_capa);
            TextView titulo = v.findViewById(R.id.warp_titulo);
            TextView selo = v.findViewById(R.id.warp_selo);

            capa.setBackgroundColor(corDoJogo(jogo));
            String url = WarpApi.urlAbsoluta(jogo.optString("coverUrl", null));
            if (url != null && !ehVideo(url)) {
                baixarCapa(url, capa);
            } else {
                capa.setTag(null);
                capa.setImageDrawable(null);
            }

            titulo.setText(jogo.optString("title"));
            boolean jogavel = jogo.optBoolean("jogavel");
            selo.setText(jogavel ? "JOGAR" : "EM BREVE");
            selo.setBackgroundResource(jogavel ? R.drawable.warp_selo_jogar : R.drawable.warp_selo_breve);
            v.setAlpha(jogavel ? 1f : 0.6f);
            return v;
        }
    }
}
