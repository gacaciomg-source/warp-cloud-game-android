package com.limelight.warp;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limelight.R;

import java.util.ArrayDeque;
import java.util.Locale;

/**
 * Tudo da WARP por cima do jogo: a tela de conexão, o botão e o menu da
 * partida, a barra de estatísticas e os avisos (conexão ruim, ajuste dos
 * botões).
 *
 * O menu muda as coisas sem sair da sessão. Estatísticas, controle na tela,
 * tamanho, transparência e toque valem na hora. A qualidade (resolução e taxa
 * de vídeo) só é combinada quando a transmissão começa, então "Aplicar"
 * reconecta à mesma máquina em poucos segundos: o jogo continua aberto lá, a
 * sessão continua a mesma, ninguém volta para a fila.
 */
public final class WarpCamadaDoJogo {
    /** Resultados do jogo para a tela da sessão. */
    public static final int RESULTADO_SAIR = Activity.RESULT_FIRST_USER + 10;
    public static final int RESULTADO_RECONECTAR = Activity.RESULT_FIRST_USER + 11;
    public static final int RESULTADO_ERRO = Activity.RESULT_FIRST_USER + 12;
    public static final int RESULTADO_JOGO_FECHOU = Activity.RESULT_FIRST_USER + 13;
    public static final String EXTRA_MENSAGEM = "warp_mensagem";

    /** Dados do jogo que a sessão passa para a tela do jogo. */
    public static final String EXTRA_TITULO = "warp_jogo_titulo";
    public static final String EXTRA_CAPA = "warp_jogo_capa";
    public static final String EXTRA_COR = "warp_jogo_cor";
    public static final String EXTRA_RECONEXAO = "warp_reconexao";

    /** O que a camada pede à tela do jogo (Game). */
    public interface Jogo {
        void warpMostrarControleNaTela(boolean mostrar);

        void warpAtualizarControleNaTela();

        /** Próximo passo do ajuste dos botões; null se o controle na tela está desligado. */
        String warpAjustarBotoes();

        void warpDefinirToque(boolean trackpad);

        void warpTeclado();

        void warpSair(int resultado, String mensagem);

        /** Menu abriu ou fechou (soltar e prender o mouse, por exemplo). */
        void warpMenuAberto(boolean aberto);
    }

    private static boolean dicaDoMenuMostrada;

    /**
     * A tela do jogo está aberta. A sessão usa isto para não segurar a máquina
     * para quem apertou "Início" e esqueceu o app aberto.
     */
    public static volatile boolean jogoNaTela;

    private final Activity a;
    private final Jogo jogo;
    private final String titulo;
    private final FrameLayout camada;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final ArrayDeque<WarpEstatisticas> janela = new ArrayDeque<>();

    private FrameLayout telaConectando;
    private TextView conectandoEtapa;
    private ImageButton botaoMenu;
    private LinearLayout barraEstatisticas;
    private View bolinha;
    private TextView textoEstatisticas;
    private LinearLayout aviso;
    private TextView avisoTexto;
    private Button avisoAcao;
    private View veu;
    private FrameLayout painel;
    private LinearLayout confirmacao;
    private LinearLayout acoes;
    private TextView painelTempo, painelConexao, painelDetalhe;
    private View painelBolinha;
    private Button voltarAoJogo, aplicar;

    private boolean menuAberto;
    private boolean conectado;
    private boolean ajustandoBotoes;
    private long inicioMs;
    private long ultimoAvisoDeConexao;
    private long ultimoToqueNoMenu;
    private String qualidadeEscolhida;
    private WarpEstatisticas ultimas;

    public WarpCamadaDoJogo(Activity a, FrameLayout raiz, Jogo jogo) {
        this.a = a;
        this.jogo = jogo;
        String t = a.getIntent().getStringExtra(EXTRA_TITULO);
        this.titulo = t != null ? t : "WARP Cloud Game";

        camada = new FrameLayout(a);
        raiz.addView(camada, new FrameLayout.LayoutParams(-1, -1));

        montarEstatisticas();
        montarAviso();
        montarBotaoMenu();
        montarMenu();
        montarConectando();
        barraEstatisticas.setVisibility(View.GONE);
    }

    private int dp(float v) {
        return WarpUi.dp(a, v);
    }

    /** O controle na tela é recriado por cima de tudo; isto devolve a WARP para o topo. */
    public void trazerParaFrente() {
        camada.bringToFront();
    }

    /* ------------------------------------------------------ conectando */

    private void montarConectando() {
        telaConectando = new FrameLayout(a);
        telaConectando.setBackgroundColor(WarpUi.FUNDO);
        telaConectando.setClickable(true);

        ImageView arte = new ImageView(a);
        arte.setScaleType(ImageView.ScaleType.CENTER_CROP);
        arte.setBackgroundColor(a.getIntent().getIntExtra(EXTRA_COR, WarpUi.SUPERFICIE));
        telaConectando.addView(arte, new FrameLayout.LayoutParams(-1, -1));
        WarpImagens.carregar(arte, a.getIntent().getStringExtra(EXTRA_CAPA),
                a.getResources().getDisplayMetrics().widthPixels / 3, 0.35f);
        View baixo = new View(a);
        baixo.setBackgroundResource(R.drawable.warp_degrade_baixo);
        telaConectando.addView(baixo, new FrameLayout.LayoutParams(-1, -1));
        View esquerda = new View(a);
        esquerda.setBackgroundResource(R.drawable.warp_degrade_esquerda);
        telaConectando.addView(esquerda, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout centro = WarpUi.coluna(a);
        centro.setGravity(Gravity.CENTER);
        ImageView simbolo = new ImageView(a);
        simbolo.setImageResource(R.drawable.warp_simbolo);
        centro.addView(simbolo, new LinearLayout.LayoutParams(dp(44), dp(41)));
        TextView nome = WarpUi.titulo(a, titulo, 28);
        nome.setGravity(Gravity.CENTER);
        centro.addView(nome, WarpUi.margens(a, -2, -2, 0, 18, 0, 0));
        ProgressBar giro = new ProgressBar(a);
        giro.setIndeterminateTintList(ColorStateList.valueOf(WarpUi.VERDE));
        centro.addView(giro, WarpUi.margens(a, dp(34), dp(34), 0, 22, 0, 0));
        boolean reconexao = a.getIntent().getBooleanExtra(EXTRA_RECONEXAO, false);
        conectandoEtapa = WarpUi.texto(a, reconexao ? "Aplicando a nova qualidade…" : "Conectando à máquina…",
                15, WarpUi.TEXTO_2, false);
        conectandoEtapa.setGravity(Gravity.CENTER);
        centro.addView(conectandoEtapa, WarpUi.margens(a, -2, -2, 0, 14, 0, 0));
        FrameLayout.LayoutParams pc = new FrameLayout.LayoutParams(-1, -2);
        pc.gravity = Gravity.CENTER;
        telaConectando.addView(centro, pc);

        camada.addView(telaConectando, new FrameLayout.LayoutParams(-1, -1));
    }

    /** Etapa da conexão vinda do motor, em português. */
    public void etapa(final String nome) {
        h.post(() -> {
            String n = nome == null ? "" : nome.toLowerCase(Locale.ROOT);
            String texto;
            if (n.contains("rtsp")) texto = "Combinando a transmissão…";
            else if (n.contains("control")) texto = "Abrindo o canal de controle…";
            else if (n.contains("video")) texto = "Ligando a imagem…";
            else if (n.contains("audio")) texto = "Ligando o som…";
            else if (n.contains("input")) texto = "Conectando os controles…";
            else texto = "Conectando…";
            conectandoEtapa.setText(texto);
        });
    }

    public void conectou() {
        h.post(() -> {
            conectado = true;
            inicioMs = SystemClock.elapsedRealtime();
            telaConectando.animate().alpha(0f).setDuration(350).withEndAction(() ->
                    telaConectando.setVisibility(View.GONE)).start();
            botaoMenu.setVisibility(WarpPreferencias.temTelaDeToque(a) ? View.VISIBLE : View.GONE);
            barraEstatisticas.setVisibility(WarpPreferencias.estatisticas(a) ? View.VISIBLE : View.GONE);
            if (!dicaDoMenuMostrada) {
                dicaDoMenuMostrada = true;
                mostrarAviso(WarpPreferencias.temTelaDeToque(a)
                        ? "Menu do jogo: toque no botão da WARP no canto ou use o voltar do aparelho."
                        : "Menu do jogo: aperte Voltar no controle remoto (ou Start + Select + LB + RB).",
                        null, null, 6000);
            }
        });
    }

    /** O jogo não abriu ou a conexão caiu: a tela da sessão mostra o erro e oferece reconectar. */
    public void falhou(String mensagem) {
        h.post(() -> jogo.warpSair(RESULTADO_ERRO, mensagem));
    }

    /* ---------------------------------------------------- estatísticas */

    private void montarEstatisticas() {
        barraEstatisticas = WarpUi.linha(a);
        barraEstatisticas.setBackgroundResource(R.drawable.warp_pilula);
        barraEstatisticas.setPadding(dp(12), dp(6), dp(14), dp(6));
        bolinha = new View(a);
        bolinha.setBackgroundResource(R.drawable.warp_bolinha);
        barraEstatisticas.addView(bolinha, new LinearLayout.LayoutParams(dp(8), dp(8)));
        textoEstatisticas = WarpUi.texto(a, "Medindo…", 12.5f, WarpUi.TEXTO, true);
        textoEstatisticas.setPadding(dp(8), 0, 0, 0);
        barraEstatisticas.addView(textoEstatisticas);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-2, -2);
        p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        p.topMargin = dp(10);
        camada.addView(barraEstatisticas, p);
    }

    private static int corDoNivel(int nivel) {
        return nivel == 0 ? WarpUi.VERDE : nivel == 1 ? WarpUi.AMARELO : WarpUi.ERRO;
    }

    private static String resumo(WarpEstatisticas e) {
        return String.format(Locale.forLanguageTag("pt-BR"), "%d ms  ·  %s Mbps  ·  %.0f fps  ·  %dp %s%s",
                e.pingMs, WarpPreferencias.mbpsTexto(e.mbps), e.fpsExibido, e.altura, e.codec,
                e.perdaPct >= 0.5f ? String.format(Locale.forLanguageTag("pt-BR"), "  ·  perda %.1f%%", e.perdaPct) : "");
    }

    /** Chega do decodificador a cada segundo. */
    public void estatisticas(final WarpEstatisticas e) {
        h.post(() -> {
            ultimas = e;
            int cor = corDoNivel(e.nivel());
            bolinha.getBackground().mutate().setColorFilter(cor, PorterDuff.Mode.SRC_IN);
            textoEstatisticas.setText(resumo(e));
            if (menuAberto) atualizarConexaoNoMenu();
            avaliarConexao(e);
        });
    }

    /** Aviso do motor de que a conexão piorou (muita perda ou atraso). */
    public void conexaoRuim(final boolean ruim) {
        h.post(() -> {
            if (ruim) sugerirQualidadeMenor(ultimas != null ? ultimas.perdaPct : 0);
        });
    }

    /**
     * Perda alta por alguns segundos seguidos: a internet não está dando conta
     * da taxa de vídeo. Sugere baixar (60% da atual) — no máximo a cada 2 min.
     */
    private void avaliarConexao(WarpEstatisticas e) {
        janela.addLast(e);
        while (janela.size() > 6) janela.removeFirst();
        if (janela.size() < 6) return;
        float perda = 0;
        for (WarpEstatisticas x : janela) perda += x.perdaPct;
        perda /= janela.size();
        if (perda >= 4f) sugerirQualidadeMenor(perda);
    }

    private void sugerirQualidadeMenor(float perda) {
        long agora = SystemClock.elapsedRealtime();
        if (!conectado || menuAberto || ajustandoBotoes || agora - ultimoAvisoDeConexao < 120_000) return;
        final int atual = WarpPreferencias.perfilAtual(a).kbps;
        final int sugerido = Math.max(4000, Math.round(atual * 0.6f / 500) * 500);
        if (sugerido >= atual) return;
        ultimoAvisoDeConexao = agora;
        String quanto = perda >= 0.5f
                ? String.format(Locale.forLanguageTag("pt-BR"), " (perda de %.0f%%)", perda) : "";
        mostrarAviso("Sua internet está perdendo imagem" + quanto + ". Em "
                        + WarpPreferencias.mbpsTexto(sugerido) + " Mbps o jogo fica mais estável.",
                "Ajustar", () -> {
                    WarpPreferencias.definirQualidade(a, WarpPreferencias.AUTO);
                    WarpPreferencias.aprenderTeto(a, sugerido);
                    reconectar();
                }, 15000);
    }

    /* ----------------------------------------------------------- aviso */

    private void montarAviso() {
        aviso = WarpUi.linha(a);
        aviso.setBackgroundResource(R.drawable.warp_pilula);
        aviso.setPadding(dp(16), dp(8), dp(8), dp(8));
        aviso.setVisibility(View.GONE);
        avisoTexto = WarpUi.texto(a, "", 14, WarpUi.TEXTO, false);
        avisoTexto.setMaxWidth(dp(460));
        aviso.addView(avisoTexto);
        avisoAcao = WarpUi.botao(a, "", 0, true);
        avisoAcao.setTextSize(13.5f);
        avisoAcao.setPadding(dp(14), dp(7), dp(14), dp(7));
        aviso.addView(avisoAcao, WarpUi.margens(a, -2, -2, 12, 0, 0, 0));
        ImageButton fechar = new ImageButton(a);
        fechar.setImageResource(R.drawable.warp_ic_fechar);
        fechar.setColorFilter(WarpUi.TEXTO_2, PorterDuff.Mode.SRC_IN);
        fechar.setBackground(null);
        fechar.setPadding(dp(8), dp(8), dp(8), dp(8));
        fechar.setOnClickListener(v -> esconderAviso());
        aviso.addView(fechar, new LinearLayout.LayoutParams(dp(36), dp(36)));
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-2, -2);
        p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        p.topMargin = dp(48);
        p.leftMargin = p.rightMargin = dp(70);
        camada.addView(aviso, p);
    }

    private final Runnable esconderAvisoDepois = this::esconderAviso;

    private void mostrarAviso(String texto, String acao, final Runnable aoTocar, long duracaoMs) {
        avisoTexto.setText(texto);
        if (acao != null) {
            avisoAcao.setText(acao);
            avisoAcao.setVisibility(View.VISIBLE);
            avisoAcao.setOnClickListener(v -> {
                esconderAviso();
                if (aoTocar != null) aoTocar.run();
            });
        } else {
            avisoAcao.setVisibility(View.GONE);
        }
        aviso.setAlpha(0f);
        aviso.setVisibility(View.VISIBLE);
        aviso.animate().alpha(1f).setDuration(200).start();
        h.removeCallbacks(esconderAvisoDepois);
        if (duracaoMs > 0) h.postDelayed(esconderAvisoDepois, duracaoMs);
    }

    private void esconderAviso() {
        h.removeCallbacks(esconderAvisoDepois);
        aviso.animate().alpha(0f).setDuration(200).withEndAction(() -> aviso.setVisibility(View.GONE)).start();
    }

    /* ---------------------------------------------------- botão do menu */

    private void montarBotaoMenu() {
        botaoMenu = new ImageButton(a);
        botaoMenu.setImageResource(R.drawable.warp_simbolo);
        botaoMenu.setBackgroundResource(R.drawable.warp_botao_menu);
        botaoMenu.setScaleType(ImageView.ScaleType.FIT_CENTER);
        botaoMenu.setPadding(dp(10), dp(10), dp(10), dp(10));
        botaoMenu.setContentDescription("Menu do jogo");
        botaoMenu.setFocusable(false);
        botaoMenu.setAlpha(0.6f);
        botaoMenu.setVisibility(View.GONE);
        botaoMenu.setOnClickListener(v -> abrirMenu());
        botaoMenu.setOnTouchListener((v, ev) -> {
            if (ev.getAction() == MotionEvent.ACTION_DOWN) v.setAlpha(1f);
            else if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().alpha(0.6f).setDuration(600).start();
            }
            return false;
        });
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(42), dp(42));
        p.gravity = Gravity.TOP | Gravity.START;
        p.topMargin = dp(12);
        p.leftMargin = dp(14);
        camada.addView(botaoMenu, p);
    }

    /* ------------------------------------------------------------ menu */

    private void montarMenu() {
        veu = new View(a);
        veu.setBackgroundColor(0x80000000);
        veu.setClickable(true);
        veu.setVisibility(View.GONE);
        veu.setOnClickListener(v -> fecharMenu());
        camada.addView(veu, new FrameLayout.LayoutParams(-1, -1));

        painel = new FrameLayout(a);
        painel.setBackgroundResource(R.drawable.warp_painel);
        painel.setClickable(true);
        painel.setElevation(dp(16));
        painel.setVisibility(View.GONE);
        int largura = Math.min(dp(420), (int) (a.getResources().getDisplayMetrics().widthPixels * 0.92f));
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(largura, -1);
        pp.gravity = Gravity.END;
        camada.addView(painel, pp);

        ScrollView rolagem = new ScrollView(a);
        rolagem.setVerticalScrollBarEnabled(false);
        LinearLayout c = WarpUi.coluna(a);
        c.setPadding(dp(22), dp(18), dp(22), dp(28));
        rolagem.addView(c);
        painel.addView(rolagem, new FrameLayout.LayoutParams(-1, -1));

        // Cabeçalho.
        LinearLayout topo = WarpUi.linha(a);
        ImageView simbolo = new ImageView(a);
        simbolo.setImageResource(R.drawable.warp_simbolo);
        topo.addView(simbolo, new LinearLayout.LayoutParams(dp(26), dp(24)));
        LinearLayout nomes = WarpUi.coluna(a);
        nomes.setPadding(dp(12), 0, dp(8), 0);
        nomes.addView(WarpUi.umaLinha(WarpUi.titulo(a, titulo, 19)));
        painelTempo = WarpUi.texto(a, "", 13, WarpUi.APAGADO, false);
        nomes.addView(painelTempo);
        topo.addView(nomes, new LinearLayout.LayoutParams(0, -2, 1));
        ImageButton fechar = WarpUi.botaoIcone(a, R.drawable.warp_ic_fechar, "Fechar o menu");
        fechar.setOnClickListener(v -> fecharMenu());
        topo.addView(fechar);
        c.addView(topo);

        // Voltar e encerrar (o que mais se usa fica em cima).
        acoes = WarpUi.linha(a);
        voltarAoJogo = WarpUi.botao(a, "Voltar ao jogo", R.drawable.warp_ic_jogar, true);
        voltarAoJogo.setOnClickListener(v -> fecharMenu());
        acoes.addView(voltarAoJogo, new LinearLayout.LayoutParams(0, -2, 1));
        Button encerrar = WarpUi.botaoPerigo(a, "Encerrar", R.drawable.warp_ic_sair);
        encerrar.setOnClickListener(v -> mostrarConfirmacao());
        acoes.addView(encerrar, WarpUi.margens(a, -2, -2, 10, 0, 0, 0));
        c.addView(acoes, WarpUi.margens(a, -1, -2, 0, 18, 0, 0));

        confirmacao = WarpUi.coluna(a);
        confirmacao.setBackground(WarpUi.fundo(0x1FF2545B, 16, a));
        confirmacao.setPadding(dp(16), dp(14), dp(16), dp(14));
        confirmacao.setVisibility(View.GONE);
        confirmacao.addView(WarpUi.texto(a, "Encerrar a sessão?", 16, WarpUi.TEXTO, true));
        TextView explicacao = WarpUi.texto(a, "A WARP fecha o jogo do jeito certo e espera o save terminar de "
                + "subir para a nuvem da loja antes de liberar a máquina.", 13.5f, WarpUi.TEXTO_2, false);
        confirmacao.addView(explicacao, WarpUi.margens(a, -2, -2, 0, 6, 0, 0));
        LinearLayout simNao = WarpUi.linha(a);
        Button sim = WarpUi.botaoPerigo(a, "Encerrar sessão", 0);
        sim.setOnClickListener(v -> jogo.warpSair(RESULTADO_SAIR, null));
        simNao.addView(sim);
        Button nao = WarpUi.botao(a, "Continuar jogando", 0, false);
        nao.setOnClickListener(v -> esconderConfirmacao());
        simNao.addView(nao, WarpUi.margens(a, -2, -2, 10, 0, 0, 0));
        confirmacao.addView(simNao, WarpUi.margens(a, -2, -2, 0, 12, 0, 0));
        c.addView(confirmacao, WarpUi.margens(a, -1, -2, 0, 18, 0, 0));

        // Conexão agora.
        LinearLayout caixa = WarpUi.coluna(a);
        caixa.setBackgroundResource(R.drawable.warp_caixa);
        caixa.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout l = WarpUi.linha(a);
        painelBolinha = new View(a);
        painelBolinha.setBackgroundResource(R.drawable.warp_bolinha);
        l.addView(painelBolinha, new LinearLayout.LayoutParams(dp(10), dp(10)));
        painelConexao = WarpUi.texto(a, "Conexão", 15.5f, WarpUi.TEXTO, true);
        painelConexao.setPadding(dp(10), 0, 0, 0);
        l.addView(painelConexao);
        caixa.addView(l);
        painelDetalhe = WarpUi.texto(a, "", 13.5f, WarpUi.TEXTO_2, false);
        caixa.addView(painelDetalhe, WarpUi.margens(a, -2, -2, 0, 6, 0, 0));
        c.addView(caixa, WarpUi.margens(a, -1, -2, 0, 18, 0, 0));

        // Qualidade.
        c.addView(secao("Qualidade da imagem"));
        String[] nomesQ = new String[WarpPreferencias.QUALIDADES.length];
        int atual = 0;
        for (int i = 0; i < nomesQ.length; i++) {
            nomesQ[i] = WarpPreferencias.nomeDaQualidade(WarpPreferencias.QUALIDADES[i]);
            if (WarpPreferencias.QUALIDADES[i].equals(WarpPreferencias.qualidade(a))) atual = i;
        }
        c.addView(WarpUi.opcoes(a, nomesQ, atual, i -> escolherQualidade(WarpPreferencias.QUALIDADES[i])));
        aplicar = WarpUi.botao(a, "Aplicar", R.drawable.warp_ic_check, true);
        aplicar.setVisibility(View.GONE);
        aplicar.setOnClickListener(v -> {
            if (qualidadeEscolhida != null) WarpPreferencias.definirQualidade(a, qualidadeEscolhida);
            reconectar();
        });
        c.addView(aplicar, WarpUi.margens(a, -1, -2, 0, 10, 0, 0));
        TextView notaQ = WarpUi.texto(a, "Ao aplicar, a imagem some por uns segundos e volta. O jogo continua "
                + "aberto e você não perde a vez.", 12.5f, WarpUi.APAGADO, false);
        c.addView(notaQ, WarpUi.margens(a, -2, -2, 4, 6, 0, 0));

        // Na tela.
        c.addView(secao("Na tela"));
        c.addView(WarpUi.chave(a, "Estatísticas", "Ping, taxa de vídeo e quadros por segundo.",
                WarpPreferencias.estatisticas(a), ligado -> {
                    WarpPreferencias.definirEstatisticas(a, ligado);
                    barraEstatisticas.setVisibility(ligado ? View.VISIBLE : View.GONE);
                }));
        c.addView(WarpUi.chave(a, "Controle na tela", "Botões de controle por cima do jogo.",
                WarpPreferencias.mostrarOsc(a), ligado -> {
                    boolean temControle = WarpPreferencias.temControleConectado(a);
                    WarpPreferencias.definirModoOsc(a, ligado
                            ? (temControle ? WarpPreferencias.OSC_SEMPRE : WarpPreferencias.OSC_AUTO)
                            : (temControle ? WarpPreferencias.OSC_AUTO : WarpPreferencias.OSC_NUNCA));
                    jogo.warpMostrarControleNaTela(ligado);
                }), WarpUi.margens(a, -1, -2, 0, 6, 0, 0));

        c.addView(subtitulo("Tamanho dos botões"));
        final int[] tamanhos = {WarpPreferencias.OSC_PEQUENO, WarpPreferencias.OSC_MEDIO, WarpPreferencias.OSC_GRANDE};
        int t = WarpPreferencias.tamanhoOsc(a);
        c.addView(WarpUi.opcoes(a, new String[]{"Pequeno", "Médio", "Grande"},
                t == tamanhos[0] ? 0 : t == tamanhos[2] ? 2 : 1, i -> {
                    WarpPreferencias.definirTamanhoOsc(a, tamanhos[i]);
                    WarpPreferencias.restaurarPosicoesOsc(a);
                    jogo.warpAtualizarControleNaTela();
                }));
        c.addView(subtitulo("Transparência dos botões"));
        c.addView(WarpUi.barra(a, 20, 100, WarpPreferencias.opacidadeOsc(a), v -> {
            WarpPreferencias.definirOpacidadeOsc(a, v);
            jogo.warpAtualizarControleNaTela();
        }), WarpUi.margens(a, -1, -2, 0, 4, 0, 0));
        Button mover = WarpUi.botao(a, "Mudar a posição dos botões", 0, false);
        mover.setOnClickListener(v -> ajustarBotoes());
        c.addView(mover, WarpUi.margens(a, -1, -2, 0, 12, 0, 0));

        c.addView(subtitulo("Toque na tela"));
        c.addView(WarpUi.opcoes(a, new String[]{"Direto", "Como trackpad"},
                WarpPreferencias.toqueTrackpad(a) ? 1 : 0, i -> {
                    WarpPreferencias.definirToqueTrackpad(a, i == 1);
                    jogo.warpDefinirToque(i == 1);
                }));

        Button teclado = WarpUi.botao(a, "Abrir o teclado", R.drawable.warp_ic_teclado, false);
        teclado.setOnClickListener(v -> {
            fecharMenu();
            h.postDelayed(jogo::warpTeclado, 250);
        });
        c.addView(teclado, WarpUi.margens(a, -1, -2, 0, 22, 0, 0));
    }

    private TextView secao(String nome) {
        TextView t = WarpUi.rotulo(a, nome);
        t.setPadding(0, dp(24), 0, dp(8));
        return t;
    }

    private TextView subtitulo(String nome) {
        TextView t = WarpUi.texto(a, nome, 14.5f, WarpUi.TEXTO, true);
        t.setPadding(0, dp(14), 0, dp(4));
        return t;
    }

    private void escolherQualidade(String id) {
        qualidadeEscolhida = id;
        boolean mudou = !id.equals(WarpPreferencias.qualidade(a));
        aplicar.setVisibility(mudou ? View.VISIBLE : View.GONE);
        if (mudou) aplicar.setText("Aplicar  ·  " + WarpPreferencias.perfil(a, id).resumo());
    }

    private void atualizarConexaoNoMenu() {
        long minutos = conectado ? (SystemClock.elapsedRealtime() - inicioMs) / 60000 : 0;
        painelTempo.setText(!conectado ? "Conectando…" : minutos < 1 ? "Jogando agora"
                : "Jogando há " + (minutos >= 60 ? (minutos / 60) + " h " + (minutos % 60) + " min" : minutos + " min"));
        WarpPreferencias.Perfil p = WarpPreferencias.perfilAtual(a);
        String ideal = WarpPreferencias.temTeste(a)
                ? "\nRecomendado pelo teste de conexão: " + WarpPreferencias.recomendado(a).resumo() + "." : "";
        if (ultimas == null) {
            painelConexao.setText("Conexão");
            painelDetalhe.setText("Medindo… Usando " + WarpPreferencias.nomeDaQualidade(WarpPreferencias.qualidade(a))
                    + " (" + p.resumo() + ")." + ideal);
            return;
        }
        painelBolinha.getBackground().mutate().setColorFilter(corDoNivel(ultimas.nivel()), PorterDuff.Mode.SRC_IN);
        painelConexao.setText("Conexão " + ultimas.nomeDoNivel().toLowerCase(Locale.forLanguageTag("pt-BR")));
        painelDetalhe.setText(resumo(ultimas) + "\nUsando " + WarpPreferencias.nomeDaQualidade(WarpPreferencias.qualidade(a))
                + " (" + p.resumo() + ")." + ideal);
    }

    public boolean menuAberto() {
        return menuAberto;
    }

    public void alternarMenu() {
        if (menuAberto) fecharMenu();
        else abrirMenu();
    }

    public void abrirMenu() {
        // O voltar do sistema e o OnBackInvokedCallback podem chegar juntos.
        long agora = SystemClock.elapsedRealtime();
        if (menuAberto || !conectado || agora - ultimoToqueNoMenu < 300) return;
        ultimoToqueNoMenu = agora;
        if (ajustandoBotoes) terminarAjusteDosBotoes();
        menuAberto = true;
        qualidadeEscolhida = null;
        aplicar.setVisibility(View.GONE);
        esconderConfirmacao();
        atualizarConexaoNoMenu();
        jogo.warpMenuAberto(true);

        veu.setAlpha(0f);
        veu.setVisibility(View.VISIBLE);
        veu.animate().alpha(1f).setDuration(180).start();
        painel.setVisibility(View.VISIBLE);
        painel.setTranslationX(painel.getLayoutParams().width);
        painel.animate().translationX(0).setDuration(220).start();
        botaoMenu.setVisibility(View.GONE);
        voltarAoJogo.requestFocus();
    }

    public void fecharMenu() {
        long agora = SystemClock.elapsedRealtime();
        if (!menuAberto || agora - ultimoToqueNoMenu < 300) return;
        ultimoToqueNoMenu = agora;
        menuAberto = false;
        jogo.warpMenuAberto(false);
        veu.animate().alpha(0f).setDuration(180).withEndAction(() -> veu.setVisibility(View.GONE)).start();
        painel.animate().translationX(painel.getWidth()).setDuration(200)
                .withEndAction(() -> painel.setVisibility(View.GONE)).start();
        if (conectado && WarpPreferencias.temTelaDeToque(a)) botaoMenu.setVisibility(View.VISIBLE);
    }

    private void mostrarConfirmacao() {
        acoes.setVisibility(View.GONE);
        confirmacao.setVisibility(View.VISIBLE);
        confirmacao.getChildAt(2).post(() -> ((LinearLayout) confirmacao.getChildAt(2)).getChildAt(1).requestFocus());
    }

    private void esconderConfirmacao() {
        boolean estava = confirmacao.getVisibility() == View.VISIBLE;
        confirmacao.setVisibility(View.GONE);
        acoes.setVisibility(View.VISIBLE);
        if (estava) voltarAoJogo.requestFocus();
    }

    private void reconectar() {
        jogo.warpSair(RESULTADO_RECONECTAR, null);
    }

    /* ------------------------------------------- ajuste dos botões */

    private void ajustarBotoes() {
        if (!WarpPreferencias.mostrarOsc(a)) {
            mostrarAviso("Ligue o controle na tela para mudar a posição dos botões.", null, null, 4000);
            return;
        }
        fecharMenu();
        ajustandoBotoes = true;
        String passo = jogo.warpAjustarBotoes();
        if (passo == null) {
            ajustandoBotoes = false;
            return;
        }
        mostrarAviso(passo + ".", "Mudar tamanho", this::proximoPassoDoAjuste, 0);
    }

    private void proximoPassoDoAjuste() {
        String passo = jogo.warpAjustarBotoes();
        mostrarAviso(passo + ".", "Concluir", this::terminarAjusteDosBotoes, 0);
    }

    private void terminarAjusteDosBotoes() {
        String passo = jogo.warpAjustarBotoes();
        ajustandoBotoes = false;
        if (passo != null) mostrarAviso(passo + ".", null, null, 2500);
    }

    /* ---------------------------------------------------------- teclas */

    /**
     * Com o menu aberto, o controle navega no menu em vez de ir para o jogo:
     * B, Voltar e Start fecham; A confirma; as setas andam pelas opções.
     */
    public boolean tratarTecla(KeyEvent e) {
        boolean solta = e.getAction() == KeyEvent.ACTION_UP;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_MENU:
                if (solta) {
                    if (confirmacao.getVisibility() == View.VISIBLE) esconderConfirmacao();
                    else fecharMenu();
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_A:
                if (solta) {
                    View foco = a.getCurrentFocus();
                    if (foco != null) foco.performClick();
                }
                return true;
            default:
                return false;
        }
    }
}
