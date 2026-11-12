package com.limelight.warp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Outline;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limelight.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A tela de um jogo, antes de jogar: arte, descrição, em qual loja a pessoa
 * tem o jogo (Steam, Epic…), como se joga, a qualidade que vai ser usada e o
 * botão de jogar ("Continuar jogando" para quem já jogou neste aparelho).
 */
public class WarpJogoActivity extends Activity {
    public static final String EXTRA_JOGO = "warp_jogo";

    private JSONObject jogo;
    private String slug;
    private Button jogar;
    private WarpQualidadeView qualidade;
    private final List<TextView> chipsDeLoja = new ArrayList<>();

    /* ------------------------------------------------ dados do jogo */

    /** Lojas do jogo, como o painel cadastrou ("Steam, Epic" vira duas). */
    static List<String> lojas(JSONObject jogo) {
        List<String> r = new ArrayList<>();
        String s = jogo.optString("store", "");
        if (s.isEmpty() || "null".equals(s)) return r;
        for (String parte : s.split("[,/;|]")) {
            String l = parte.trim();
            if (!l.isEmpty()) r.add(l.substring(0, 1).toUpperCase() + l.substring(1));
        }
        return r;
    }

    /** controle, teclado, toque. Sem nada cadastrado, vale controle. */
    static List<String> modosDeControle(JSONObject jogo) {
        List<String> r = new ArrayList<>();
        String s = jogo.optString("controls", "");
        if (!s.isEmpty() && !"null".equals(s)) {
            for (String parte : s.split(",")) {
                if (!parte.trim().isEmpty()) r.add(parte.trim());
            }
        }
        if (r.isEmpty()) r.add("controle");
        return r;
    }

    static String nomeDoModo(String modo) {
        switch (modo) {
            case "teclado": return "Teclado e mouse";
            case "toque": return "Toque na tela";
            default: return "Controle";
        }
    }

    static int iconeDoModo(String modo) {
        switch (modo) {
            case "teclado": return R.drawable.warp_ic_teclado;
            case "toque": return R.drawable.warp_ic_toque;
            default: return R.drawable.warp_ic_controle;
        }
    }

    /** Abre a sessão do jogo (fila, conexão e o jogo). */
    static void jogarAgora(Activity de, JSONObject jogo) {
        if (!jogo.optBoolean("jogavel")) return;
        String slug = jogo.optString("slug");
        List<String> lojas = lojas(jogo);
        Intent i = new Intent(de, WarpSessaoActivity.class);
        i.putExtra(WarpSessaoActivity.EXTRA_SLUG, slug);
        i.putExtra(WarpSessaoActivity.EXTRA_TITULO, jogo.optString("title"));
        i.putExtra(WarpSessaoActivity.EXTRA_CAPA, WarpApi.urlAbsoluta(jogo.optString("coverUrl", null)));
        i.putExtra(WarpSessaoActivity.EXTRA_COR, WarpJogosActivity.corDoJogo(jogo));
        if (!lojas.isEmpty()) {
            i.putExtra(WarpSessaoActivity.EXTRA_LOJA, WarpPreferencias.loja(de, slug, lojas.get(0)));
        }
        de.startActivity(i);
    }

    /* ------------------------------------------------------------ tela */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            jogo = new JSONObject(getIntent().getStringExtra(EXTRA_JOGO));
        } catch (JSONException | NullPointerException e) {
            finish();
            return;
        }
        slug = jogo.optString("slug");
        montarTela();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (jogo == null) return;
        if (qualidade != null) qualidade.atualizar();
        if (jogar != null && jogo.optBoolean("jogavel")) {
            jogar.setText(WarpPreferencias.jogadoEm(this, slug) > 0 ? "Continuar jogando" : "Jogar");
        }
    }

    private void montarTela() {
        boolean largo = WarpUi.telaLarga(this);
        int margem = WarpUi.dp(this, WarpUi.tablet(this) ? 48 : 22);
        boolean jogavel = jogo.optBoolean("jogavel");

        FrameLayout raiz = new FrameLayout(this);
        raiz.setBackgroundColor(WarpUi.FUNDO);

        ImageView fundo = new ImageView(this);
        fundo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        fundo.setBackgroundColor(WarpJogosActivity.corDoJogo(jogo));
        raiz.addView(fundo, new FrameLayout.LayoutParams(-1, -1));
        WarpImagens.carregar(fundo, jogo.optString("coverUrl", null),
                getResources().getDisplayMetrics().widthPixels / 2, 0.5f);
        View esquerda = new View(this);
        esquerda.setBackgroundResource(R.drawable.warp_degrade_esquerda);
        raiz.addView(esquerda, new FrameLayout.LayoutParams(-1, -1));
        View baixo = new View(this);
        baixo.setBackgroundResource(R.drawable.warp_degrade_baixo);
        raiz.addView(baixo, new FrameLayout.LayoutParams(-1, -1));

        ScrollView rolagem = new ScrollView(this);
        rolagem.setVerticalScrollBarEnabled(false);
        rolagem.setFillViewport(true);
        LinearLayout pagina = WarpUi.coluna(this);
        pagina.setPadding(margem, WarpUi.dp(this, 14), margem, WarpUi.dp(this, 40));

        // Topo: voltar e configurações.
        LinearLayout topo = WarpUi.linha(this);
        ImageButton voltar = WarpUi.botaoIcone(this, R.drawable.warp_ic_voltar, "Voltar");
        voltar.setOnClickListener(v -> finish());
        topo.addView(voltar);
        TextView marca = WarpUi.texto(this, "Jogos", 15, WarpUi.TEXTO_2, true);
        marca.setPadding(WarpUi.dp(this, 12), 0, 0, 0);
        topo.addView(marca, new LinearLayout.LayoutParams(0, -2, 1));
        ImageButton ajustes = WarpUi.botaoIcone(this, R.drawable.warp_ic_ajustes, "Configurações");
        ajustes.setOnClickListener(v -> startActivity(new Intent(this, WarpConfigActivity.class)));
        topo.addView(ajustes);
        pagina.addView(topo);

        LinearLayout corpo = largo ? WarpUi.linha(this) : WarpUi.coluna(this);
        corpo.setGravity(Gravity.TOP);
        LinearLayout info = WarpUi.coluna(this);
        corpo.addView(info, largo ? new LinearLayout.LayoutParams(0, -2, 1) : new LinearLayout.LayoutParams(-1, -2));
        if (largo) corpo.addView(montarCapa(), WarpUi.margens(this, WarpUi.dp(this, 230), WarpUi.dp(this, 306), 40, 28, 0, 0));
        pagina.addView(corpo, WarpUi.margens(this, -1, -2, 0, largo ? 18 : 28, 0, 0));

        // Nome, selos e descrição.
        String genero = jogo.optString("genre", "");
        info.addView(WarpUi.rotulo(this, genero.isEmpty() || "null".equals(genero) ? "Cloud Game" : genero));
        TextView titulo = WarpUi.titulo(this, jogo.optString("title"), WarpUi.tablet(this) ? 46 : 36);
        info.addView(titulo, WarpUi.margens(this, -2, -2, 0, 8, 0, 0));

        LinearLayout selos = WarpUi.linha(this);
        if (jogo.optBoolean("soAdmin")) {
            selos.addView(selo("Só administrador", WarpUi.AMARELO));
            selos.addView(WarpUi.espaco(this, 8, 1));
        }
        selos.addView(selo(jogavel ? "Disponível agora" : "Em breve", jogavel ? WarpUi.VERDE : WarpUi.AMARELO));
        selos.addView(WarpUi.espaco(this, 8, 1));
        selos.addView(selo("Progresso salvo na nuvem", WarpUi.TEXTO_2));
        info.addView(selos, WarpUi.margens(this, -2, -2, 0, 14, 0, 0));

        String descricao = jogo.optString("description", "");
        if (descricao.isEmpty() || "null".equals(descricao)) {
            descricao = "Jogue " + jogo.optString("title") + " direto da nuvem da WARP, sem instalar nada e sem "
                    + "precisar de um computador potente. Entre com a sua conta da loja e continue de onde parou.";
        }
        TextView desc = WarpUi.texto(this, descricao, 16, WarpUi.TEXTO_2, false);
        desc.setMaxWidth(WarpUi.dp(this, 620));
        info.addView(desc, WarpUi.margens(this, -2, -2, 0, 16, 0, 0));

        // Botões.
        LinearLayout botoes = WarpUi.linha(this);
        jogar = WarpUi.botao(this, !jogavel ? "Em breve"
                : WarpPreferencias.jogadoEm(this, slug) > 0 ? "Continuar jogando" : "Jogar",
                jogavel ? R.drawable.warp_ic_jogar : 0, true);
        jogar.setEnabled(jogavel);
        jogar.setOnClickListener(v -> jogarAgora(this, jogo));
        botoes.addView(jogar);
        Button configurar = WarpUi.botao(this, "Qualidade e controles", R.drawable.warp_ic_ajustes, false);
        configurar.setOnClickListener(v -> startActivity(new Intent(this, WarpConfigActivity.class)));
        botoes.addView(configurar, WarpUi.margens(this, -2, -2, 12, 0, 0, 0));
        info.addView(botoes, WarpUi.margens(this, -2, -2, 0, 24, 0, 0));

        // Loja.
        List<String> lojas = lojas(jogo);
        if (!lojas.isEmpty()) {
            info.addView(secao(lojas.size() > 1 ? "Onde você tem o jogo" : "Loja"));
            LinearLayout linhaLojas = WarpUi.linha(this);
            String escolhida = WarpPreferencias.loja(this, slug, lojas.get(0));
            if (!lojas.contains(escolhida)) escolhida = lojas.get(0);
            for (final String loja : lojas) {
                final TextView chip = WarpUi.chip(this, loja);
                chip.setSelected(loja.equals(escolhida));
                chip.setOnClickListener(v -> {
                    WarpPreferencias.definirLoja(this, slug, loja);
                    for (TextView c : chipsDeLoja) c.setSelected(c == chip);
                });
                chipsDeLoja.add(chip);
                linhaLojas.addView(chip, WarpUi.margens(this, -2, -2, 0, 0, 10, 0));
            }
            info.addView(linhaLojas, WarpUi.margens(this, -2, -2, 0, 10, 0, 0));
            TextView ajuda = WarpUi.texto(this, "O jogo precisa estar na sua biblioteca. Quando ele pedir, entre "
                    + "com a sua conta da loja — a WARP não guarda essa senha.", 13.5f, WarpUi.APAGADO, false);
            ajuda.setMaxWidth(WarpUi.dp(this, 560));
            info.addView(ajuda, WarpUi.margens(this, -2, -2, 0, 8, 0, 0));
        }

        // Como jogar.
        info.addView(secao("Como jogar"));
        LinearLayout modos = WarpUi.linha(this);
        for (String modo : modosDeControle(jogo)) {
            LinearLayout m = WarpUi.linha(this);
            m.setBackground(WarpUi.fundo(0x1FFFFFFF, 12, this));
            m.setPadding(WarpUi.dp(this, 12), WarpUi.dp(this, 9), WarpUi.dp(this, 14), WarpUi.dp(this, 9));
            m.addView(WarpUi.imagemDeIcone(this, iconeDoModo(modo), WarpUi.VERDE, 20));
            TextView t = WarpUi.texto(this, nomeDoModo(modo), 14, WarpUi.TEXTO, true);
            t.setPadding(WarpUi.dp(this, 8), 0, 0, 0);
            m.addView(t);
            modos.addView(m, WarpUi.margens(this, -2, -2, 0, 0, 10, 0));
        }
        info.addView(modos, WarpUi.margens(this, -2, -2, 0, 10, 0, 0));

        // Qualidade e salvamento.
        LinearLayout caixas = largo ? WarpUi.linha(this) : WarpUi.coluna(this);
        caixas.setGravity(Gravity.TOP);
        qualidade = new WarpQualidadeView(this, true);
        caixas.addView(qualidade, largo ? new LinearLayout.LayoutParams(0, -2, 1) : new LinearLayout.LayoutParams(-1, -2));
        View salvar = caixaDeSalvamento(lojas.isEmpty() ? null : lojas.get(0));
        caixas.addView(salvar, largo ? WarpUi.margens(this, 0, -2, 14, 0, 0, 0) : WarpUi.margens(this, -1, -2, 0, 14, 0, 0));
        if (largo) ((LinearLayout.LayoutParams) salvar.getLayoutParams()).weight = 1;
        pagina.addView(caixas, WarpUi.margens(this, -1, -2, 0, 30, 0, 0));

        rolagem.addView(pagina);
        raiz.addView(rolagem, new FrameLayout.LayoutParams(-1, -1));
        setContentView(raiz);

        if (jogavel) jogar.requestFocus();
        else voltar.requestFocus();
    }

    private View montarCapa() {
        FrameLayout capa = new FrameLayout(this);
        capa.setBackground(WarpUi.fundo(WarpJogosActivity.corDoJogo(jogo), 18, this));
        capa.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), WarpUi.dp(WarpJogoActivity.this, 18));
            }
        });
        capa.setClipToOutline(true);
        capa.setElevation(WarpUi.dp(this, 14));
        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        capa.addView(img, new FrameLayout.LayoutParams(-1, -1));
        WarpImagens.carregar(img, jogo.optString("coverUrl", null), WarpUi.dp(this, 230), -1);
        return capa;
    }

    private TextView secao(String nome) {
        TextView t = WarpUi.texto(this, nome, 15, WarpUi.TEXTO, true);
        t.setPadding(0, WarpUi.dp(this, 26), 0, 0);
        return t;
    }

    private TextView selo(String texto, int cor) {
        TextView t = WarpUi.texto(this, texto, 12.5f, cor, true);
        t.setBackground(WarpUi.fundo((cor & 0x00FFFFFF) | 0x24000000, 8, this));
        t.setPadding(WarpUi.dp(this, 10), WarpUi.dp(this, 5), WarpUi.dp(this, 10), WarpUi.dp(this, 5));
        return t;
    }

    private View caixaDeSalvamento(String loja) {
        LinearLayout caixa = WarpUi.coluna(this);
        caixa.setBackgroundResource(R.drawable.warp_caixa);
        int p = WarpUi.dp(this, 18);
        caixa.setPadding(p, p, p, p);
        caixa.addView(WarpUi.rotulo(this, "Seu progresso"));
        LinearLayout l = WarpUi.linha(this);
        l.setGravity(Gravity.TOP);
        l.addView(WarpUi.imagemDeIcone(this, R.drawable.warp_ic_nuvem, WarpUi.VERDE, 26));
        String nuvem = loja == null ? "a nuvem da loja" : "Steam".equalsIgnoreCase(loja) ? "a Steam Cloud"
                : "a nuvem da " + loja;
        TextView t = WarpUi.texto(this, "Fica salvo na sua conta. Quando você sai, a WARP fecha o jogo do jeito "
                + "certo e espera " + nuvem + " terminar de enviar o save antes de liberar a máquina.",
                14, WarpUi.TEXTO_2, false);
        t.setPadding(WarpUi.dp(this, 12), 0, 0, 0);
        l.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        caixa.addView(l, WarpUi.margens(this, -1, -2, 0, 10, 0, 0));
        return caixa;
    }
}
