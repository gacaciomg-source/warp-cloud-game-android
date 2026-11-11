package com.limelight.warp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Outline;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limelight.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Início do app: o jogo em destaque no topo, com a arte de fundo, e as
 * fileiras de jogos embaixo ("Continue jogando", "Jogue agora", "Em breve").
 *
 * No controle e na TV, o destaque acompanha o jogo com foco, como nos
 * consoles. Tocar (ou apertar A) num jogo abre a tela dele, com descrição,
 * loja e o botão de jogar.
 */
public class WarpJogosActivity extends Activity {
    private final List<JSONObject> jogos = new ArrayList<>();

    private FrameLayout arteDoDestaque;
    private ImageView arte;
    private ScrollView rolagem;
    private LinearLayout fileiras;
    private ProgressBar carregando;
    private LinearLayout estado;
    private TextView estadoTexto;

    private TextView destaqueRotulo, destaqueTitulo, destaqueMeta, destaqueDescricao;
    private Button destaqueJogar, destaqueDetalhes;
    private JSONObject destaque;
    private String arteAtual;

    private int margem;
    private int alturaDestaque;
    private String nomeDaConta, emailDaConta;
    private boolean carregou;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        montarTela();
    }

    @Override
    protected void onResume() {
        super.onResume();
        carregar();
    }

    /* ------------------------------------------------------------ tela */

    private void montarTela() {
        margem = WarpUi.dp(this, WarpUi.tablet(this) ? 48 : 22);
        int alturaTelaDp = getResources().getConfiguration().screenHeightDp;
        alturaDestaque = WarpUi.dp(this, Math.max(300, Math.min(520, alturaTelaDp * 0.62f)));

        FrameLayout raiz = new FrameLayout(this);
        raiz.setBackgroundColor(WarpUi.FUNDO);

        // Arte do jogo em destaque, atrás de tudo, com degradês para o texto.
        arteDoDestaque = new FrameLayout(this);
        arte = new ImageView(this);
        arte.setScaleType(ImageView.ScaleType.CENTER_CROP);
        arteDoDestaque.addView(arte, new FrameLayout.LayoutParams(-1, -1));
        View esquerda = new View(this);
        esquerda.setBackgroundResource(R.drawable.warp_degrade_esquerda);
        arteDoDestaque.addView(esquerda, new FrameLayout.LayoutParams(-1, -1));
        View baixo = new View(this);
        baixo.setBackgroundResource(R.drawable.warp_degrade_baixo);
        arteDoDestaque.addView(baixo, new FrameLayout.LayoutParams(-1, -1));
        View cima = new View(this);
        cima.setBackgroundResource(R.drawable.warp_degrade_cima);
        arteDoDestaque.addView(cima, new FrameLayout.LayoutParams(-1, WarpUi.dp(this, 110)));
        raiz.addView(arteDoDestaque, new FrameLayout.LayoutParams(-1, alturaDestaque + WarpUi.dp(this, 40)));

        rolagem = new ScrollView(this);
        rolagem.setVerticalScrollBarEnabled(false);
        rolagem.setFillViewport(true);
        LinearLayout conteudo = WarpUi.coluna(this);
        conteudo.addView(montarBarraDeCima());
        conteudo.addView(montarDestaque());
        fileiras = WarpUi.coluna(this);
        fileiras.setPadding(0, 0, 0, WarpUi.dp(this, 36));
        conteudo.addView(fileiras);
        rolagem.addView(conteudo);
        raiz.addView(rolagem, new FrameLayout.LayoutParams(-1, -1));

        // A arte sobe mais devagar que a lista (profundidade).
        rolagem.getViewTreeObserver().addOnScrollChangedListener(() ->
                arteDoDestaque.setTranslationY(-rolagem.getScrollY() * 0.45f));

        carregando = new ProgressBar(this);
        carregando.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(WarpUi.VERDE));
        FrameLayout.LayoutParams pc = new FrameLayout.LayoutParams(WarpUi.dp(this, 44), WarpUi.dp(this, 44));
        pc.gravity = Gravity.CENTER;
        raiz.addView(carregando, pc);

        estado = WarpUi.coluna(this);
        estado.setGravity(Gravity.CENTER);
        estado.setPadding(margem, 0, margem, 0);
        estadoTexto = WarpUi.texto(this, "", 16, WarpUi.TEXTO_2, false);
        estadoTexto.setGravity(Gravity.CENTER);
        estado.addView(estadoTexto);
        Button tentar = WarpUi.botao(this, "Tentar de novo", R.drawable.warp_ic_atualizar, false);
        tentar.setOnClickListener(v -> carregar());
        estado.addView(tentar, WarpUi.margens(this, -2, -2, 0, 18, 0, 0));
        estado.setVisibility(View.GONE);
        FrameLayout.LayoutParams pe = new FrameLayout.LayoutParams(-1, -2);
        pe.gravity = Gravity.CENTER;
        pe.topMargin = WarpUi.dp(this, 60);
        raiz.addView(estado, pe);

        setContentView(raiz);
    }

    private View montarBarraDeCima() {
        LinearLayout barra = WarpUi.linha(this);
        barra.setPadding(margem, WarpUi.dp(this, 14), margem - WarpUi.dp(this, 6), WarpUi.dp(this, 8));

        ImageView simbolo = new ImageView(this);
        simbolo.setImageResource(R.drawable.warp_simbolo);
        barra.addView(simbolo, new LinearLayout.LayoutParams(WarpUi.dp(this, 30), WarpUi.dp(this, 28)));

        LinearLayout nome = WarpUi.coluna(this);
        nome.setPadding(WarpUi.dp(this, 12), 0, 0, 0);
        TextView marca = WarpUi.titulo(this, "WARP", 18);
        marca.setLetterSpacing(0.08f);
        nome.addView(marca);
        TextView sub = WarpUi.texto(this, "Cloud Game", 11.5f, WarpUi.TEXTO_2, false);
        sub.setLetterSpacing(0.06f);
        nome.addView(sub);
        barra.addView(nome, new LinearLayout.LayoutParams(0, -2, 1));

        ImageButton ajustes = WarpUi.botaoIcone(this, R.drawable.warp_ic_ajustes, "Configurações");
        ajustes.setOnClickListener(v -> startActivity(new Intent(this, WarpConfigActivity.class)));
        barra.addView(ajustes);
        barra.addView(WarpUi.espaco(this, 10, 1));
        ImageButton conta = WarpUi.botaoIcone(this, R.drawable.warp_ic_conta, "Conta");
        conta.setOnClickListener(v -> abrirConta());
        barra.addView(conta);
        return barra;
    }

    private View montarDestaque() {
        LinearLayout caixa = WarpUi.coluna(this);
        caixa.setGravity(Gravity.BOTTOM);
        caixa.setPadding(margem, 0, margem, WarpUi.dp(this, 26));
        caixa.setMinimumHeight(alturaDestaque - WarpUi.dp(this, 70));

        destaqueRotulo = WarpUi.rotulo(this, "Em destaque");
        caixa.addView(destaqueRotulo);
        destaqueTitulo = WarpUi.titulo(this, "", WarpUi.tablet(this) ? 46 : 34);
        destaqueTitulo.setMaxLines(2);
        destaqueTitulo.setEllipsize(TextUtils.TruncateAt.END);
        caixa.addView(destaqueTitulo, WarpUi.margens(this, -2, -2, 0, 8, 0, 0));
        destaqueMeta = WarpUi.umaLinha(WarpUi.texto(this, "", 14.5f, WarpUi.TEXTO_2, true));
        caixa.addView(destaqueMeta, WarpUi.margens(this, -2, -2, 0, 8, 0, 0));
        destaqueDescricao = WarpUi.texto(this, "", 15, WarpUi.TEXTO_2, false);
        destaqueDescricao.setMaxLines(3);
        destaqueDescricao.setEllipsize(TextUtils.TruncateAt.END);
        destaqueDescricao.setMaxWidth(WarpUi.dp(this, 560));
        caixa.addView(destaqueDescricao, WarpUi.margens(this, -2, -2, 0, 10, 0, 0));

        LinearLayout botoes = WarpUi.linha(this);
        destaqueJogar = WarpUi.botao(this, "Jogar", R.drawable.warp_ic_jogar, true);
        destaqueJogar.setOnClickListener(v -> {
            if (destaque != null) WarpJogoActivity.jogarAgora(this, destaque);
        });
        botoes.addView(destaqueJogar);
        destaqueDetalhes = WarpUi.botao(this, "Detalhes", 0, false);
        destaqueDetalhes.setOnClickListener(v -> {
            if (destaque != null) abrirJogo(destaque);
        });
        botoes.addView(destaqueDetalhes, WarpUi.margens(this, -2, -2, 12, 0, 0, 0));
        caixa.addView(botoes, WarpUi.margens(this, -2, -2, 0, 20, 0, 0));
        caixa.setVisibility(View.INVISIBLE);
        return caixa;
    }

    /* ---------------------------------------------------------- dados */

    private void carregar() {
        if (!carregou) carregando.setVisibility(View.VISIBLE);
        estado.setVisibility(View.GONE);
        new Thread(() -> {
            List<JSONObject> lista = new ArrayList<>();
            String falha = null;
            try {
                JSONArray a = WarpApi.get(this).jogos();
                for (int i = 0; i < a.length(); i++) lista.add(a.getJSONObject(i));
                if (nomeDaConta == null) {
                    JSONObject eu = WarpApi.get(this).quemSou();
                    if (eu != null) {
                        nomeDaConta = eu.optString("name", "");
                        emailDaConta = eu.optString("email", "");
                    }
                }
            } catch (Exception e) {
                falha = e instanceof WarpApi.ErroApi ? e.getMessage() : "Sem conexão com a WARP. Confira a internet e tente de novo.";
            }
            final String mensagem = falha;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                carregando.setVisibility(View.GONE);
                if (mensagem != null && jogos.isEmpty()) {
                    mostrarEstado(mensagem);
                    return;
                }
                if (mensagem == null) {
                    jogos.clear();
                    jogos.addAll(lista);
                }
                carregou = true;
                if (jogos.isEmpty()) {
                    mostrarEstado("Nenhum jogo liberado ainda. Volte em breve.");
                    return;
                }
                montarFileiras();
            });
        }).start();
    }

    private void mostrarEstado(String texto) {
        estadoTexto.setText(texto);
        estado.setVisibility(View.VISIBLE);
        ((View) destaqueTitulo.getParent()).setVisibility(View.INVISIBLE);
        fileiras.removeAllViews();
    }

    private void montarFileiras() {
        View focoAntes = getCurrentFocus();
        String slugComFoco = focoAntes != null && focoAntes.getTag() instanceof JSONObject
                ? ((JSONObject) focoAntes.getTag()).optString("slug") : null;

        List<JSONObject> continuar = new ArrayList<>();
        List<JSONObject> agora = new ArrayList<>();
        List<JSONObject> breve = new ArrayList<>();
        for (JSONObject j : jogos) {
            if (!j.optBoolean("jogavel")) {
                breve.add(j);
                continue;
            }
            agora.add(j);
            if (WarpPreferencias.jogadoEm(this, j.optString("slug")) > 0) continuar.add(j);
        }
        Collections.sort(continuar, (a, b) -> Long.compare(
                WarpPreferencias.jogadoEm(this, b.optString("slug")),
                WarpPreferencias.jogadoEm(this, a.optString("slug"))));

        fileiras.removeAllViews();
        View primeiroCartao = null;
        if (!continuar.isEmpty()) primeiroCartao = adicionarFileira("Continue jogando", continuar, slugComFoco);
        View c2 = adicionarFileira("Jogue agora", agora, slugComFoco);
        if (primeiroCartao == null) primeiroCartao = c2;
        adicionarFileira("Em breve", breve, slugComFoco);

        // O destaque continua no mesmo jogo depois de recarregar a lista.
        JSONObject escolhido = null;
        if (destaque != null) {
            for (JSONObject j : jogos) {
                if (j.optString("slug").equals(destaque.optString("slug"))) escolhido = j;
            }
        }
        if (escolhido == null) {
            escolhido = !continuar.isEmpty() ? continuar.get(0) : !agora.isEmpty() ? agora.get(0) : jogos.get(0);
        }
        mostrarDestaque(escolhido);
        ((View) destaqueTitulo.getParent()).setVisibility(View.VISIBLE);

        if (slugComFoco == null && focoAntes == null) {
            if (destaqueJogar.isEnabled()) destaqueJogar.requestFocus();
            else if (primeiroCartao != null) primeiroCartao.requestFocus();
        }
    }

    /** Devolve o primeiro cartão (para o foco inicial), ou null se a lista está vazia. */
    private View adicionarFileira(String titulo, List<JSONObject> lista, String slugComFoco) {
        if (lista.isEmpty()) return null;
        boolean tv = WarpUi.ehTv(this);
        int largura = WarpUi.dp(this, tv ? 168 : WarpUi.tablet(this) ? 152 : 124);
        int altura = largura * 4 / 3;

        TextView t = WarpUi.texto(this, titulo, WarpUi.tablet(this) ? 19 : 17, WarpUi.TEXTO, true);
        t.setPadding(margem, WarpUi.dp(this, 18), margem, WarpUi.dp(this, 10));
        fileiras.addView(t);

        HorizontalScrollView rolar = new HorizontalScrollView(this);
        rolar.setHorizontalScrollBarEnabled(false);
        rolar.setClipToPadding(false);
        rolar.setClipChildren(false);
        // Espaço em volta para a moldura e o aumento do foco não serem cortados.
        int folga = WarpUi.dp(this, 10);
        rolar.setPadding(margem - folga, folga, margem - folga, folga);
        LinearLayout linha = WarpUi.linha(this);
        linha.setClipChildren(false);
        View primeiro = null;
        for (JSONObject jogo : lista) {
            View c = montarCartao(jogo, largura, altura);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(largura, altura);
            lp.setMargins(folga, 0, folga, 0);
            linha.addView(c, lp);
            if (primeiro == null) primeiro = c;
            if (jogo.optString("slug").equals(slugComFoco)) c.post(c::requestFocus);
        }
        rolar.addView(linha);
        fileiras.addView(rolar);
        return primeiro;
    }

    private View montarCartao(final JSONObject jogo, int largura, int altura) {
        FrameLayout cartao = new FrameLayout(this);
        cartao.setTag(jogo);
        cartao.setBackground(WarpUi.fundo(corDoJogo(jogo), 14, this));
        cartao.setForeground(getDrawable(R.drawable.warp_cartao_foco));
        cartao.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), WarpUi.dp(WarpJogosActivity.this, 14));
            }
        });
        cartao.setClipToOutline(true);
        cartao.setElevation(WarpUi.dp(this, 4));
        cartao.setFocusable(true);
        cartao.setClickable(true);
        cartao.setContentDescription(jogo.optString("title"));

        ImageView capa = new ImageView(this);
        capa.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cartao.addView(capa, new FrameLayout.LayoutParams(-1, -1));
        WarpImagens.carregar(capa, jogo.optString("coverUrl", null), largura, -1);

        View sombra = new View(this);
        sombra.setBackgroundResource(R.drawable.warp_degrade_baixo);
        FrameLayout.LayoutParams ps = new FrameLayout.LayoutParams(-1, altura / 2);
        ps.gravity = Gravity.BOTTOM;
        cartao.addView(sombra, ps);

        TextView nome = WarpUi.texto(this, jogo.optString("title"), 14, WarpUi.TEXTO, true);
        nome.setMaxLines(2);
        nome.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams pn = new FrameLayout.LayoutParams(-1, -2);
        pn.gravity = Gravity.BOTTOM;
        int p = WarpUi.dp(this, 11);
        nome.setPadding(p, 0, p, p);
        cartao.addView(nome, pn);

        if (!jogo.optBoolean("jogavel")) {
            TextView selo = WarpUi.texto(this, "EM BREVE", 10, WarpUi.TEXTO, true);
            selo.setLetterSpacing(0.1f);
            selo.setBackground(WarpUi.fundo(0xCC07131F, 6, this));
            selo.setPadding(WarpUi.dp(this, 7), WarpUi.dp(this, 3), WarpUi.dp(this, 7), WarpUi.dp(this, 3));
            FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(-2, -2);
            pl.gravity = Gravity.TOP | Gravity.END;
            pl.setMargins(0, p, p, 0);
            cartao.addView(selo, pl);
        }

        cartao.setOnClickListener(v -> abrirJogo(jogo));
        WarpUi.focoComZoom(cartao, 1.07f);
        final View.OnFocusChangeListener zoom = cartao.getOnFocusChangeListener();
        cartao.setOnFocusChangeListener((v, temFoco) -> {
            zoom.onFocusChange(v, temFoco);
            if (temFoco) mostrarDestaque(jogo);
        });
        return cartao;
    }

    /* ------------------------------------------------------- destaque */

    private void mostrarDestaque(JSONObject jogo) {
        destaque = jogo;
        String slug = jogo.optString("slug");
        boolean jogavel = jogo.optBoolean("jogavel");
        boolean jaJogou = WarpPreferencias.jogadoEm(this, slug) > 0;

        destaqueRotulo.setText(!jogavel ? "EM BREVE NA WARP" : jaJogou ? "CONTINUE DE ONDE PAROU" : "EM DESTAQUE");
        destaqueTitulo.setText(jogo.optString("title"));
        destaqueMeta.setText(meta(jogo));
        String descricao = jogo.optString("description", "");
        if (descricao.isEmpty() || "null".equals(descricao)) {
            descricao = "Jogue direto da nuvem da WARP, sem instalar nada. Seu progresso fica salvo na sua conta da loja.";
        }
        destaqueDescricao.setText(descricao);

        destaqueJogar.setEnabled(jogavel);
        destaqueJogar.setText(!jogavel ? "Em breve" : jaJogou ? "Continuar jogando" : "Jogar");
        destaqueJogar.setFocusable(jogavel);

        String capa = WarpApi.urlAbsoluta(jogo.optString("coverUrl", null));
        if (capa == null || !capa.equals(arteAtual)) {
            arteAtual = capa;
            arte.setBackgroundColor(corDoJogo(jogo));
            WarpImagens.carregar(arte, capa, getResources().getDisplayMetrics().widthPixels / 2, 0.9f);
        }
    }

    /** "Mundo aberto · Steam · Controle". */
    static String meta(JSONObject jogo) {
        List<String> partes = new ArrayList<>();
        String genero = jogo.optString("genre", "");
        if (!genero.isEmpty() && !"null".equals(genero)) partes.add(genero);
        List<String> lojas = WarpJogoActivity.lojas(jogo);
        if (!lojas.isEmpty()) partes.add(TextUtils.join(" / ", lojas));
        List<String> modos = WarpJogoActivity.modosDeControle(jogo);
        if (!modos.isEmpty()) partes.add(WarpJogoActivity.nomeDoModo(modos.get(0)));
        return TextUtils.join("  ·  ", partes);
    }

    static int corDoJogo(JSONObject jogo) {
        try {
            return Color.parseColor(jogo.optString("accentColor", "#15304a"));
        } catch (IllegalArgumentException e) {
            return Color.parseColor("#15304a");
        }
    }

    /* ---------------------------------------------------------- ações */

    private void abrirJogo(JSONObject jogo) {
        Intent i = new Intent(this, WarpJogoActivity.class);
        i.putExtra(WarpJogoActivity.EXTRA_JOGO, jogo.toString());
        startActivity(i);
    }

    private void abrirConta() {
        String quem = nomeDaConta != null && !nomeDaConta.isEmpty() ? nomeDaConta : "Sua conta";
        String detalhe = emailDaConta != null && !emailDaConta.isEmpty()
                ? "Conectado como " + emailDaConta + "." : "Conectado à WARP.";
        WarpUi.perguntar(this, quem, detalhe, "Sair da conta", true, () -> new Thread(() -> {
            WarpApi.get(this).sairDaConta();
            runOnUiThread(() -> {
                startActivity(new Intent(this, WarpEntrarActivity.class));
                finish();
            });
        }).start(), "Fechar");
    }

    @Override
    public void onBackPressed() {
        // Na fileira, voltar leva ao topo antes de sair do app (como nos consoles).
        if (rolagem.getScrollY() > 0) {
            rolagem.smoothScrollTo(0, 0);
            if (destaqueJogar.isEnabled()) destaqueJogar.requestFocus();
            return;
        }
        super.onBackPressed();
    }
}
