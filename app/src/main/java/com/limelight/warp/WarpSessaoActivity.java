package com.limelight.warp;

import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.binding.PlatformBinding;
import com.limelight.computers.ComputerManagerService;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.HostHttpResponseException;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.utils.ServerHelper;

import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Do "Jogar" até o jogo aberto, sem IP nem código, e o que vem depois.
 *
 *  1. pede a sessão à WARP; se as máquinas estão ocupadas, mostra a fila;
 *  2. com a máquina reservada, cadastra o endereço dela no motor;
 *  3. se o aparelho ainda não está pareado, inventa o código e a WARP o
 *     entrega ao Sunshine da máquina (é aqui que some a tela de código);
 *  4. abre o jogo com a qualidade escolhida (ou a recomendada pelo teste).
 *
 * Enquanto a pessoa joga, esta tela fica por baixo mandando sinal de vida. Se
 * a pessoa troca a qualidade no menu, o jogo reconecta por aqui mesmo, sem
 * fila. Se sai da tela do jogo ou a conexão cai, a máquina fica reservada por
 * alguns minutos, com "Voltar ao jogo". No "Encerrar", a WARP fecha o jogo e
 * espera o save subir para a nuvem antes de liberar a máquina.
 */
public class WarpSessaoActivity extends Activity {
    public static final String EXTRA_SLUG = "warp_slug";
    public static final String EXTRA_TITULO = "warp_titulo";
    public static final String EXTRA_CAPA = "warp_capa";
    public static final String EXTRA_COR = "warp_cor";
    public static final String EXTRA_LOJA = "warp_loja";

    private static final int PEDIDO_JOGO = 4101;
    private static final long INTERVALO_FILA_MS = 3000;
    private static final long INTERVALO_SINAL_MS = 20000;
    /** Tentativas de liberar o acesso, com a máquina destravada entre elas. */
    private static final int TENTATIVAS_DE_PAREAR = 3;
    /** Prazo de cada tentativa; normalmente fecha em 2 a 3 segundos. */
    private static final long PRAZO_PAREAR_MS = 20000;
    /** Fora da tela do jogo, a máquina fica reservada por este tempo. */
    private static final long RESERVA_MS = 3 * 60 * 1000;

    private static final int ESPERANDO = 0, PAUSADO = 1, SALVANDO = 2, ERRO = 3;

    private volatile ComputerManagerService.ComputerManagerBinder gerente;
    private volatile boolean ativa = true;
    private volatile boolean jogando = false;
    private volatile String sessaoId;
    private volatile boolean encerrada = false;
    /** Esta tela está à vista (a do jogo é WarpCamadaDoJogo.jogoNaTela). */
    private volatile boolean naTela = false;

    private final Handler h = new Handler(Looper.getMainLooper());
    private String slug, tituloJogo, loja;
    private Thread fluxo, sinal, testeDeRede;
    private Intent intentDoJogo;
    private int estado = ESPERANDO;
    private long reservaAte;

    private TextView rotulo, etapa, detalhe;
    private ProgressBar giro, barra;
    private LinearLayout passos, botoes;
    private final TextView[] nomesDosPassos = new TextView[4];
    private final View[] pontosDosPassos = new View[4];

    /** Erro com mensagem pronta para mostrar. */
    private static final class Falha extends Exception {
        Falha(String mensagem) {
            super(mensagem);
        }
    }

    private final ServiceConnection conexao = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName nome, IBinder binder) {
            final ComputerManagerService.ComputerManagerBinder local =
                    (ComputerManagerService.ComputerManagerBinder) binder;
            new Thread(() -> {
                local.waitForReady();
                gerente = local;
            }).start();
        }

        @Override
        public void onServiceDisconnected(ComponentName nome) {
            gerente = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        slug = getIntent().getStringExtra(EXTRA_SLUG);
        tituloJogo = getIntent().getStringExtra(EXTRA_TITULO);
        loja = getIntent().getStringExtra(EXTRA_LOJA);
        montarTela();

        bindService(new Intent(this, ComputerManagerService.class), conexao, Service.BIND_AUTO_CREATE);
        testarRedeSePreciso();
        fluxo = new Thread(this::rodar);
        fluxo.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        naTela = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        naTela = false;
    }

    @Override
    public void onBackPressed() {
        switch (estado) {
            case ESPERANDO:
                if (sessaoId == null || encerrada) {
                    finish();
                } else {
                    WarpUi.perguntar(this, "Sair da fila?", "Você perde o seu lugar e a máquina reservada.",
                            "Sair", true, () -> {
                                encerrar();
                                finish();
                            }, "Continuar esperando");
                }
                break;
            case PAUSADO:
                confirmarEncerrar();
                break;
            default:
                finish();
        }
    }

    @Override
    protected void onActivityResult(int pedido, int resultado, Intent dados) {
        super.onActivityResult(pedido, resultado, dados);
        if (pedido != PEDIDO_JOGO) return;
        String mensagem = dados != null ? dados.getStringExtra(WarpCamadaDoJogo.EXTRA_MENSAGEM) : null;
        if (resultado == WarpCamadaDoJogo.RESULTADO_RECONECTAR) {
            // Nova qualidade: mesma sessão, mesma máquina, o jogo continua aberto lá.
            WarpPreferencias.aplicarNoMotor(this);
            mostrarEsperando(3, "Aplicando a nova qualidade…", WarpPreferencias.perfilAtual(this).resumo());
            h.postDelayed(() -> abrirJogo(true), 400);
        } else if (resultado == WarpCamadaDoJogo.RESULTADO_SAIR) {
            sairSalvando();
        } else if (resultado == WarpCamadaDoJogo.RESULTADO_ERRO) {
            pausar("A conexão caiu", mensagem != null ? mensagem : "A transmissão foi interrompida.", "Reconectar");
        } else if (resultado == WarpCamadaDoJogo.RESULTADO_JOGO_FECHOU) {
            pausar("O jogo foi fechado", "O jogo foi fechado na máquina. Você pode abrir de novo ou encerrar a sessão.",
                    "Abrir de novo");
        } else {
            pausar("Partida pausada", "Você saiu da tela do jogo. Ele continua aberto na máquina, reservado para você.",
                    "Voltar ao jogo");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ativa = false;
        h.removeCallbacksAndMessages(null);
        if (fluxo != null) fluxo.interrupt();
        if (sinal != null) sinal.interrupt();
        encerrar();
        try {
            unbindService(conexao);
        } catch (IllegalArgumentException ignored) {
            // já desligado
        }
    }

    /* ------------------------------------------------------------ tela */

    private void montarTela() {
        FrameLayout raiz = new FrameLayout(this);
        raiz.setBackgroundColor(WarpUi.FUNDO);

        ImageView arte = new ImageView(this);
        arte.setScaleType(ImageView.ScaleType.CENTER_CROP);
        arte.setBackgroundColor(getIntent().getIntExtra(EXTRA_COR, WarpUi.SUPERFICIE));
        raiz.addView(arte, new FrameLayout.LayoutParams(-1, -1));
        WarpImagens.carregar(arte, getIntent().getStringExtra(EXTRA_CAPA),
                getResources().getDisplayMetrics().widthPixels / 3, 0.35f);
        View baixo = new View(this);
        baixo.setBackgroundResource(R.drawable.warp_degrade_baixo);
        raiz.addView(baixo, new FrameLayout.LayoutParams(-1, -1));
        View esquerda = new View(this);
        esquerda.setBackgroundResource(R.drawable.warp_degrade_esquerda);
        raiz.addView(esquerda, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout centro = WarpUi.coluna(this);
        centro.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = WarpUi.dp(this, 28);
        centro.setPadding(p, p, p, p);

        rotulo = WarpUi.rotulo(this, tituloJogo != null ? tituloJogo : "Cloud Game");
        rotulo.setGravity(Gravity.CENTER);
        centro.addView(rotulo);
        etapa = WarpUi.titulo(this, "Pedindo sua máquina…", WarpUi.tablet(this) ? 32 : 26);
        etapa.setGravity(Gravity.CENTER);
        centro.addView(etapa, WarpUi.margens(this, -2, -2, 0, 12, 0, 0));
        detalhe = WarpUi.texto(this, "", 15.5f, WarpUi.TEXTO_2, false);
        detalhe.setGravity(Gravity.CENTER);
        detalhe.setMaxWidth(WarpUi.dp(this, 520));
        centro.addView(detalhe, WarpUi.margens(this, -2, -2, 0, 10, 0, 0));

        passos = WarpUi.linha(this);
        passos.setGravity(Gravity.CENTER);
        String[] nomes = {"Fila", "Máquina", "Acesso", "Jogo"};
        for (int i = 0; i < nomes.length; i++) {
            LinearLayout passo = WarpUi.linha(this);
            pontosDosPassos[i] = new View(this);
            pontosDosPassos[i].setBackgroundResource(R.drawable.warp_bolinha);
            passo.addView(pontosDosPassos[i], new LinearLayout.LayoutParams(WarpUi.dp(this, 8), WarpUi.dp(this, 8)));
            nomesDosPassos[i] = WarpUi.texto(this, nomes[i], 13, WarpUi.APAGADO, true);
            nomesDosPassos[i].setPadding(WarpUi.dp(this, 7), 0, 0, 0);
            passo.addView(nomesDosPassos[i]);
            passos.addView(passo, WarpUi.margens(this, -2, -2, i == 0 ? 0 : 16, 0, 0, 0));
        }
        centro.addView(passos, WarpUi.margens(this, -2, -2, 0, 26, 0, 0));

        giro = new ProgressBar(this);
        giro.setIndeterminateTintList(ColorStateList.valueOf(WarpUi.VERDE));
        centro.addView(giro, WarpUi.margens(this, WarpUi.dp(this, 40), WarpUi.dp(this, 40), 0, 26, 0, 0));
        barra = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        barra.setProgressDrawable(getDrawable(R.drawable.warp_barra));
        barra.setMax(1000);
        barra.setVisibility(View.GONE);
        centro.addView(barra, WarpUi.margens(this, WarpUi.dp(this, 320), WarpUi.dp(this, 6), 0, 30, 0, 0));

        botoes = WarpUi.linha(this);
        botoes.setGravity(Gravity.CENTER);
        centro.addView(botoes, WarpUi.margens(this, -2, -2, 0, 30, 0, 0));

        FrameLayout.LayoutParams pc = new FrameLayout.LayoutParams(-1, -2);
        pc.gravity = Gravity.CENTER;
        raiz.addView(centro, pc);
        setContentView(raiz);

        mostrarEsperando(0, "Pedindo sua máquina…", null);
    }

    private void textos(String textoEtapa, String textoDetalhe) {
        etapa.setText(textoEtapa);
        if (textoDetalhe == null || textoDetalhe.isEmpty()) {
            detalhe.setVisibility(View.GONE);
        } else {
            detalhe.setText(textoDetalhe);
            detalhe.setVisibility(View.VISIBLE);
        }
    }

    private void marcarPasso(int atual) {
        passos.setVisibility(atual < 0 ? View.GONE : View.VISIBLE);
        for (int i = 0; i < 4; i++) {
            int cor = i < atual ? WarpUi.VERDE : i == atual ? WarpUi.TEXTO : 0x55FFFFFF;
            pontosDosPassos[i].getBackground().mutate().setColorFilter(
                    i <= atual ? WarpUi.VERDE : 0x55FFFFFF, android.graphics.PorterDuff.Mode.SRC_IN);
            nomesDosPassos[i].setTextColor(cor);
        }
    }

    private Button botaoSair(String texto) {
        Button b = WarpUi.botao(this, texto, 0, false);
        b.setOnClickListener(v -> onBackPressed());
        return b;
    }

    /** Fila, conexão, pareamento: etapa 0 a 3. */
    private void mostrarEsperando(final int passo, final String textoEtapa, final String textoDetalhe) {
        runOnUiThread(() -> {
            if (isFinishing()) return;
            boolean mudouDeEstado = estado != ESPERANDO || botoes.getChildCount() == 0;
            estado = ESPERANDO;
            h.removeCallbacks(contagemDaReserva);
            rotulo.setText(tituloJogo != null ? tituloJogo : "Cloud Game");
            textos(textoEtapa, textoDetalhe);
            marcarPasso(passo);
            giro.setVisibility(View.VISIBLE);
            barra.setVisibility(View.GONE);
            if (mudouDeEstado) {
                botoes.removeAllViews();
                Button cancelar = botaoSair("Cancelar");
                botoes.addView(cancelar);
                cancelar.requestFocus();
            }
        });
    }

    private void falhar(final String mensagem) {
        encerrar();
        runOnUiThread(() -> {
            if (isFinishing()) return;
            estado = ERRO;
            textos("Não deu certo desta vez", mensagem);
            marcarPasso(-1);
            giro.setVisibility(View.GONE);
            barra.setVisibility(View.GONE);
            botoes.removeAllViews();
            Button denovo = WarpUi.botao(this, "Tentar de novo", R.drawable.warp_ic_atualizar, true);
            denovo.setOnClickListener(v -> {
                Intent i = getIntent();
                finish();
                startActivity(i);
            });
            botoes.addView(denovo);
            botoes.addView(botaoSair("Voltar"), WarpUi.margens(this, -2, -2, 12, 0, 0, 0));
            denovo.requestFocus();
        });
    }

    /**
     * Fora da tela do jogo (saiu, caiu, o jogo fechou): a máquina fica
     * reservada por alguns minutos, com a opção de voltar sem fila.
     */
    private void pausar(String titulo, String mensagem, String rotuloVoltar) {
        if (encerrada) {
            finish();
            return;
        }
        estado = PAUSADO;
        reservaAte = SystemClock.elapsedRealtime() + RESERVA_MS;
        textos(titulo, mensagem);
        marcarPasso(-1);
        giro.setVisibility(View.GONE);
        barra.setVisibility(View.VISIBLE);
        botoes.removeAllViews();
        Button voltar = WarpUi.botao(this, rotuloVoltar, R.drawable.warp_ic_jogar, true);
        voltar.setOnClickListener(v -> {
            WarpPreferencias.aplicarNoMotor(this);
            mostrarEsperando(3, "Voltando ao jogo…", null);
            abrirJogo(true);
        });
        botoes.addView(voltar);
        Button fim = WarpUi.botaoPerigo(this, "Encerrar sessão", R.drawable.warp_ic_sair);
        fim.setOnClickListener(v -> confirmarEncerrar());
        botoes.addView(fim, WarpUi.margens(this, -2, -2, 12, 0, 0, 0));
        voltar.requestFocus();
        h.removeCallbacks(contagemDaReserva);
        h.post(contagemDaReserva);
    }

    private final Runnable contagemDaReserva = new Runnable() {
        @Override
        public void run() {
            if (estado != PAUSADO) return;
            long falta = reservaAte - SystemClock.elapsedRealtime();
            if (falta <= 0) {
                sairSalvando();
                return;
            }
            barra.setProgress((int) (falta * 1000 / RESERVA_MS));
            long s = (falta + 999) / 1000;
            rotulo.setText(String.format(Locale.ROOT, "Máquina reservada por mais %d:%02d", s / 60, s % 60));
            h.postDelayed(this, 1000);
        }
    };

    private void confirmarEncerrar() {
        WarpUi.perguntar(this, "Encerrar a sessão?",
                "A WARP fecha o jogo do jeito certo e espera o save terminar de subir para a nuvem da loja "
                        + "antes de liberar a máquina.", "Encerrar sessão", true, this::sairSalvando,
                "Continuar");
    }

    /**
     * Fim da sessão com o tempo de salvar: a WARP fecha o jogo na máquina e a
     * segura enquanto a loja envia o save. A pessoa pode sair desta tela a
     * qualquer momento — a espera acontece na WARP, não no aparelho.
     */
    private void sairSalvando() {
        estado = SALVANDO;
        h.removeCallbacks(contagemDaReserva);
        rotulo.setText(tituloJogo != null ? tituloJogo : "Cloud Game");
        String nuvem = loja == null ? "a nuvem da loja" : "Steam".equalsIgnoreCase(loja) ? "a Steam Cloud"
                : "a nuvem da " + loja;
        textos("Salvando seu progresso…", "A WARP está fechando o jogo e esperando " + nuvem
                + " terminar de enviar o save. Pode sair desta tela: a máquina só é liberada depois.");
        marcarPasso(-1);
        giro.setVisibility(View.GONE);
        barra.setVisibility(View.VISIBLE);
        barra.setProgress(0);
        botoes.removeAllViews();
        Button pronto = WarpUi.botao(this, "Voltar aos jogos", 0, false);
        pronto.setOnClickListener(v -> finish());
        botoes.addView(pronto);
        pronto.requestFocus();

        final String id = sessaoId;
        jogando = false;
        new Thread(() -> {
            int segundos = 0;
            if (id != null && !encerrada) {
                encerrada = true;
                try {
                    JSONObject r = WarpApi.get(this).sairDaSessao(id);
                    if (!r.isNull("salvandoAte")) segundos = r.optInt("salvarSegundos", 0);
                } catch (IOException e) {
                    // sem rede: a WARP encerra sozinha depois de 2 min sem sinal, com o mesmo tempo de salvar
                    segundos = 0;
                }
            }
            final int total = Math.max(segundos, 0);
            runOnUiThread(() -> contarSalvamento(total));
        }).start();
    }

    private void contarSalvamento(final int total) {
        final long fim = SystemClock.elapsedRealtime() + total * 1000L;
        h.post(new Runnable() {
            @Override
            public void run() {
                if (estado != SALVANDO || isFinishing()) return;
                long falta = fim - SystemClock.elapsedRealtime();
                if (total <= 0 || falta <= 0) {
                    barra.setProgress(1000);
                    textos("Pronto!", "O jogo foi fechado e a espera para o save subir terminou. Até a próxima!");
                    h.postDelayed(() -> {
                        if (!isFinishing()) finish();
                    }, 3000);
                    return;
                }
                barra.setProgress((int) (1000 - falta * 1000 / (total * 1000L)));
                h.postDelayed(this, 200);
            }
        });
    }

    /* --------------------------------------------------------- sessão */

    /** Libera a máquina (ou o lugar na fila). Uma vez só, em segundo plano. */
    private synchronized void encerrar() {
        if (encerrada || sessaoId == null) return;
        encerrada = true;
        final String id = sessaoId;
        final WarpApi api = WarpApi.get(this);
        new Thread(() -> {
            try {
                api.sairDaSessao(id);
            } catch (IOException ignored) {
                // sem rede: a WARP encerra sozinha depois de 2 min sem sinal
            }
        }).start();
    }

    /**
     * Sinal de vida para a WARP enquanto a pessoa espera, joga ou está com a
     * partida pausada. Com o app fora da tela (apertou "Início" e esqueceu),
     * a máquina fica reservada pelo mesmo tempo da partida pausada e depois a
     * sessão acaba, com o tempo de salvar de sempre.
     */
    private void iniciarSinal() {
        if (sinal != null) return;
        final WarpApi api = WarpApi.get(this);
        sinal = new Thread(() -> {
            long foraDaTelaDesde = 0;
            while (ativa && !encerrada) {
                try {
                    Thread.sleep(INTERVALO_SINAL_MS);
                    long agora = SystemClock.elapsedRealtime();
                    if (naTela || WarpCamadaDoJogo.jogoNaTela) {
                        foraDaTelaDesde = 0;
                    } else if (foraDaTelaDesde == 0) {
                        foraDaTelaDesde = agora;
                    } else if (agora - foraDaTelaDesde > RESERVA_MS) {
                        encerrar();
                        runOnUiThread(this::finish);
                        return;
                    }
                    if (sessaoId != null && !encerrada) api.consultarSessao(sessaoId, jogando);
                } catch (InterruptedException e) {
                    return;
                } catch (IOException ignored) {
                    // tenta de novo no próximo ciclo
                }
            }
        });
        sinal.setDaemon(true);
        sinal.start();
    }

    /**
     * Na qualidade automática, mede a conexão enquanto a fila anda (se o
     * último teste é velho). Fica pronto antes de o jogo abrir.
     */
    private void testarRedeSePreciso() {
        if (!WarpPreferencias.AUTO.equals(WarpPreferencias.qualidade(this)) || !WarpPreferencias.testeVelho(this)) {
            return;
        }
        testeDeRede = new Thread(() -> {
            try {
                WarpApi api = WarpApi.get(this);
                int ping = api.medirPing();
                float mbps = api.medirMbps();
                WarpPreferencias.guardarTeste(this, ping, mbps);
            } catch (Exception e) {
                LimeLog.info("Teste de conexão falhou: " + e.getMessage());
            }
        });
        testeDeRede.setDaemon(true);
        testeDeRede.start();
    }

    private void abrirJogo(boolean reconexao) {
        if (intentDoJogo == null || isFinishing()) return;
        intentDoJogo.putExtra(WarpCamadaDoJogo.EXTRA_RECONEXAO, reconexao);
        jogando = true;
        startActivityForResult(intentDoJogo, PEDIDO_JOGO);
    }

    private ComputerManagerService.ComputerManagerBinder esperarGerente() throws InterruptedException {
        for (int i = 0; i < 100 && gerente == null; i++) Thread.sleep(100);
        if (gerente == null) throw new InterruptedException("serviço de conexão não subiu");
        return gerente;
    }

    private void rodar() {
        WarpApi api = WarpApi.get(this);
        try {
            JSONObject s = api.pedirSessao(slug);
            sessaoId = s.getString("id");

            while (ativa && "queued".equals(s.optString("status"))) {
                int pos = s.optInt("posicao", 0);
                String detalheFila = "As máquinas estão ocupadas. Deixe o app aberto: quando uma liberar, o jogo abre sozinho.";
                if (!s.isNull("liberaEm")) {
                    detalheFila = "Uma máquina está terminando de salvar a partida de quem acabou de sair. Libera em instantes.";
                }
                mostrarEsperando(0, pos <= 1 ? "Você é o próximo da fila" : "Você é o " + pos + "º da fila", detalheFila);
                Thread.sleep(INTERVALO_FILA_MS);
                s = api.consultarSessao(sessaoId, false);
            }
            if (!ativa) return;
            if ("ended".equals(s.optString("status"))) {
                throw new Falha(s.optString("motivoDoFim", "A sessão foi encerrada."));
            }

            JSONObject maquina = s.getJSONObject("maquina");
            JSONObject jogoDaSessao = s.getJSONObject("jogo");
            String appNome = jogoDaSessao.optString("appNoSunshine", "");
            iniciarSinal();

            mostrarEsperando(1, "Conectando à máquina…", maquina.optString("nome"));
            ComputerManagerService.ComputerManagerBinder g = esperarGerente();
            ComputerDetails novo = new ComputerDetails();
            novo.manualAddress = new ComputerDetails.AddressTuple(maquina.getString("host"), maquina.getInt("porta"));
            if (!g.addComputerBlocking(novo)) {
                throw new Falha("A máquina não respondeu. Tente de novo em instantes.");
            }
            ComputerDetails pc = g.getComputer(novo.uuid);
            if (pc == null) pc = novo;

            NvHTTP conn = abrirConexao(pc, g);
            if (conn.getPairState() != PairingManager.PairState.PAIRED) {
                parearComTentativas(api, pc, g);
                conn = abrirConexao(pc, g);
            }
            if (!ativa) return;

            mostrarEsperando(3, "Abrindo " + tituloJogo + "…", null);
            NvApp app = appNome.isEmpty() ? null : conn.getAppByName(appNome);
            if (app == null) {
                List<NvApp> apps = conn.getAppList();
                if (apps.isEmpty()) throw new Falha("A máquina não tem nenhum jogo configurado.");
                app = apps.get(0);
            }
            if (!ativa) return;

            // Qualidade: o teste de conexão (se estava rodando) termina antes.
            if (testeDeRede != null) testeDeRede.join(6000);
            WarpPreferencias.definirTetoMaquina(this, maquina.optInt("bitrateMaximoKbps", 0));
            WarpPreferencias.aplicarNoMotor(this);
            WarpPreferencias.marcarJogado(this, slug);

            intentDoJogo = ServerHelper.createStartIntent(this, app, pc, g);
            intentDoJogo.putExtra(WarpCamadaDoJogo.EXTRA_TITULO, tituloJogo);
            intentDoJogo.putExtra(WarpCamadaDoJogo.EXTRA_CAPA, getIntent().getStringExtra(EXTRA_CAPA));
            intentDoJogo.putExtra(WarpCamadaDoJogo.EXTRA_COR, getIntent().getIntExtra(EXTRA_COR, WarpUi.SUPERFICIE));
            runOnUiThread(() -> abrirJogo(false));
        } catch (InterruptedException e) {
            // a pessoa saiu da tela
        } catch (Falha | WarpApi.ErroApi e) {
            if (ativa) falhar(e.getMessage());
        } catch (Exception e) {
            if (ativa) falhar("Não foi possível conectar à máquina (" + e.getMessage() + ").");
        }
    }

    private NvHTTP abrirConexao(ComputerDetails pc, ComputerManagerService.ComputerManagerBinder g) throws IOException {
        return new NvHTTP(ServerHelper.getCurrentAddressFromComputer(pc), pc.httpsPort,
                g.getUniqueId(), pc.serverCert, PlatformBinding.getCryptoProvider(this));
    }

    /**
     * Pareamento sem código, com prazo e novas tentativas.
     *
     * O Sunshine aceita um pareamento por vez. Se uma tentativa anterior ficou
     * pela metade (de qualquer aparelho, ou um toque antes da hora), ele recusa
     * com 409 ou simplesmente não responde. Nesses casos a WARP destrava a
     * máquina (cancela os pedidos pendurados) e o app tenta de novo.
     */
    private void parearComTentativas(WarpApi api, ComputerDetails pc,
                                     ComputerManagerService.ComputerManagerBinder g) throws Exception {
        for (int tentativa = 1; tentativa <= TENTATIVAS_DE_PAREAR; tentativa++) {
            if (!ativa) throw new InterruptedException();
            mostrarEsperando(2, tentativa == 1 ? "Liberando seu acesso…" : "Liberando seu acesso (tentativa " + tentativa + ")…",
                    "A WARP faz isso por você, sem código.");

            NvHTTP conn = abrirConexao(pc, g);
            if (conn.getPairState() == PairingManager.PairState.PAIRED) return;
            if (parearUmaVez(api, conn, pc, g)) return;

            if (tentativa < TENTATIVAS_DE_PAREAR) {
                mostrarEsperando(2, "Destravando a máquina…", "Uma tentativa anterior ficou pela metade. Leva alguns segundos.");
                api.destravar(sessaoId);
                g.invalidateStateForComputer(pc.uuid);
            }
        }
        throw new Falha("Não foi possível liberar o acesso à máquina. Tente de novo em instantes.");
    }

    /** Erros da WARP que não melhoram tentando de novo (configuração da máquina, por exemplo). */
    private static boolean naoAdiantaRepetir(WarpApi.ErroApi e) {
        return "SUNSHINE_SENHA".equals(e.codigo) || "SUNSHINE_SEM_SENHA".equals(e.codigo)
                || "SESSAO_SEM_MAQUINA".equals(e.codigo) || "MAQUINA_EM_USO".equals(e.codigo);
    }

    /**
     * Uma tentativa, com prazo.
     *
     * O motor começa a parear e fica esperando o código no Sunshine; em
     * paralelo, a WARP entrega o código (o Sunshine só aceita depois que o
     * pedido do app chegou, então a entrega insiste por alguns segundos).
     *
     * @return true se pareou; false se vale destravar a máquina e tentar de novo.
     */
    private boolean parearUmaVez(final WarpApi api, final NvHTTP conn, ComputerDetails pc,
                                 ComputerManagerService.ComputerManagerBinder g) throws Exception {
        final String pin = PairingManager.generatePinString();
        final WarpApi.ErroApi[] erroEntrega = {null};

        Thread entrega = new Thread(() -> {
            for (int i = 0; i < 15; i++) {
                try {
                    Thread.sleep(1200);
                    api.entregarCodigo(sessaoId, pin);
                    return;
                } catch (InterruptedException e) {
                    return;
                } catch (WarpApi.ErroApi e) {
                    if (!"PAREAMENTO_NAO_INICIADO".equals(e.codigo)) {
                        erroEntrega[0] = e;
                        return;
                    }
                } catch (IOException e) {
                    // rede oscilou: tenta de novo
                }
            }
        });
        entrega.setDaemon(true);
        entrega.start();

        // Nome que a WARP procura na lista de pareamentos pendentes do Sunshine.
        conn.setPairingDeviceName("warp-" + sessaoId.substring(0, 8));

        final PairingManager pm = conn.getPairingManager();
        ExecutorService exec = Executors.newSingleThreadExecutor();
        Future<PairingManager.PairState> futuro = exec.submit(() -> pm.pair(conn.getServerInfo(true), pin));
        PairingManager.PairState estadoPar = null;
        try {
            long limite = System.currentTimeMillis() + PRAZO_PAREAR_MS;
            while (!futuro.isDone()) {
                WarpApi.ErroApi e = erroEntrega[0];
                if (e != null && naoAdiantaRepetir(e)) throw new Falha(e.getMessage());
                if (System.currentTimeMillis() > limite) return false; // pendurou: destrava e tenta de novo
                Thread.sleep(300);
            }
            estadoPar = futuro.get();
        } catch (ExecutionException e) {
            // 409 "pareamento já existe" ou queda de rede no meio: destrava e tenta de novo.
            Throwable causa = e.getCause();
            if (causa instanceof HostHttpResponseException) {
                LimeLog.info("Pareamento recusado pela máquina: " + causa.getMessage());
            }
            return false;
        } finally {
            futuro.cancel(true);
            exec.shutdownNow();
            entrega.interrupt();
        }

        if (estadoPar != PairingManager.PairState.PAIRED) {
            WarpApi.ErroApi e = erroEntrega[0];
            if (e != null && naoAdiantaRepetir(e)) throw new Falha(e.getMessage());
            return false;
        }

        // Guarda o certificado da máquina para as próximas conexões seguras.
        pc.serverCert = pm.getPairedCert();
        ComputerDetails noGerente = g.getComputer(pc.uuid);
        if (noGerente != null) noGerente.serverCert = pc.serverCert;
        g.invalidateStateForComputer(pc.uuid);
        return true;
    }
}
