package com.limelight.warp;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.binding.PlatformBinding;
import com.limelight.computers.ComputerManagerService;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.utils.ServerHelper;

import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

/**
 * Do toque no jogo até o jogo aberto, sem IP nem código.
 *
 *  1. pede a sessão à WARP; se as máquinas estão ocupadas, mostra a fila;
 *  2. com a máquina reservada, cadastra o endereço dela no Moonlight;
 *  3. se o aparelho ainda não está pareado, inventa o código e a WARP o
 *     entrega ao Sunshine da máquina (é aqui que some a tela de código);
 *  4. abre o jogo pelo nome cadastrado no painel.
 *
 * Enquanto a pessoa joga, esta tela fica por baixo mandando sinal de vida.
 * Quando o jogo fecha, a sessão acaba e a máquina fica livre para a fila.
 */
public class WarpSessaoActivity extends Activity {
    public static final String EXTRA_SLUG = "warp_slug";
    public static final String EXTRA_TITULO = "warp_titulo";
    public static final String EXTRA_CAPA = "warp_capa";

    private static final int PEDIDO_JOGO = 4101;
    private static final long INTERVALO_FILA_MS = 3000;
    private static final long INTERVALO_SINAL_MS = 20000;

    private volatile ComputerManagerService.ComputerManagerBinder gerente;
    private volatile boolean ativa = true;
    private volatile boolean jogando = false;
    private volatile String sessaoId;
    private volatile boolean encerrada = false;

    private String slug;
    private String tituloJogo;
    private Thread fluxo;
    private Thread sinal;

    private TextView etapa;
    private TextView detalhe;
    private ProgressBar giro;
    private Button botaoSair;

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
        setContentView(R.layout.warp_sessao);

        slug = getIntent().getStringExtra(EXTRA_SLUG);
        tituloJogo = getIntent().getStringExtra(EXTRA_TITULO);

        ((TextView) findViewById(R.id.warp_titulo)).setText(tituloJogo);
        etapa = findViewById(R.id.warp_etapa);
        detalhe = findViewById(R.id.warp_detalhe);
        giro = findViewById(R.id.warp_giro);
        botaoSair = findViewById(R.id.warp_sair);
        botaoSair.setOnClickListener(v -> confirmarSaida());

        bindService(new Intent(this, ComputerManagerService.class), conexao, Service.BIND_AUTO_CREATE);

        fluxo = new Thread(this::rodar);
        fluxo.start();
    }

    @Override
    public void onBackPressed() {
        confirmarSaida();
    }

    @Override
    protected void onActivityResult(int pedido, int resultado, Intent dados) {
        super.onActivityResult(pedido, resultado, dados);
        if (pedido == PEDIDO_JOGO) {
            // O jogo fechou: acabou a sessão, a máquina volta para a fila.
            jogando = false;
            encerrar();
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ativa = false;
        if (fluxo != null) fluxo.interrupt();
        if (sinal != null) sinal.interrupt();
        encerrar();
        try {
            unbindService(conexao);
        } catch (IllegalArgumentException ignored) {
            // já desligado
        }
    }

    /* ---------------------------------------------------------- tela */

    private void mostrar(final String textoEtapa, final String textoDetalhe) {
        runOnUiThread(() -> {
            etapa.setText(textoEtapa);
            if (textoDetalhe == null || textoDetalhe.isEmpty()) {
                detalhe.setVisibility(View.GONE);
            } else {
                detalhe.setText(textoDetalhe);
                detalhe.setVisibility(View.VISIBLE);
            }
        });
    }

    private void falhar(final String mensagem) {
        encerrar();
        runOnUiThread(() -> {
            if (isFinishing()) return;
            giro.setVisibility(View.GONE);
            etapa.setText("Não deu certo desta vez");
            detalhe.setText(mensagem);
            detalhe.setVisibility(View.VISIBLE);
            botaoSair.setText("Voltar aos jogos");
        });
    }

    private void confirmarSaida() {
        if (encerrada || sessaoId == null) {
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage("Sair agora? Você perde o seu lugar na fila.")
                .setPositiveButton("Sair", (d, w) -> {
                    encerrar();
                    finish();
                })
                .setNegativeButton("Continuar esperando", null)
                .show();
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

    /** Sinal de vida para a WARP enquanto a pessoa espera ou joga. */
    private void iniciarSinal() {
        if (sinal != null) return;
        final WarpApi api = WarpApi.get(this);
        sinal = new Thread(() -> {
            while (ativa && !encerrada) {
                try {
                    Thread.sleep(INTERVALO_SINAL_MS);
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

    private ComputerManagerService.ComputerManagerBinder esperarGerente() throws InterruptedException {
        for (int i = 0; i < 100 && gerente == null; i++) Thread.sleep(100);
        if (gerente == null) throw new InterruptedException("serviço do Moonlight não subiu");
        return gerente;
    }

    private void rodar() {
        WarpApi api = WarpApi.get(this);
        try {
            mostrar("Pedindo sua máquina…", null);
            JSONObject s = api.pedirSessao(slug);
            sessaoId = s.getString("id");

            while (ativa && "queued".equals(s.optString("status"))) {
                int pos = s.optInt("posicao", 0);
                mostrar(pos <= 1 ? "Você é o próximo da fila" : "Você é o " + pos + "º da fila",
                        "As máquinas estão ocupadas. Deixe o app aberto: quando uma liberar, o jogo abre sozinho.");
                Thread.sleep(INTERVALO_FILA_MS);
                s = api.consultarSessao(sessaoId, false);
            }
            if (!ativa) return;
            if ("ended".equals(s.optString("status"))) {
                throw new Falha(s.optString("motivoDoFim", "A sessão foi encerrada."));
            }

            JSONObject maquina = s.getJSONObject("maquina");
            String appNome = s.getJSONObject("jogo").optString("appNoSunshine", "");
            iniciarSinal();

            mostrar("Conectando à máquina…", maquina.optString("nome"));
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
                mostrar("Liberando seu acesso…", "A WARP faz isso por você, sem código.");
                parear(api, conn, pc, g);
                conn = abrirConexao(pc, g);
            }
            if (!ativa) return;

            mostrar("Abrindo " + tituloJogo + "…", null);
            NvApp app = appNome.isEmpty() ? null : conn.getAppByName(appNome);
            if (app == null) {
                List<NvApp> apps = conn.getAppList();
                if (apps.isEmpty()) throw new Falha("A máquina não tem nenhum jogo configurado no Sunshine.");
                app = apps.get(0);
            }
            if (!ativa) return;

            final Intent jogo = ServerHelper.createStartIntent(this, app, pc, g);
            jogando = true;
            runOnUiThread(() -> {
                if (!isFinishing()) startActivityForResult(jogo, PEDIDO_JOGO);
            });
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
     * Pareamento sem código na tela.
     *
     * O Moonlight começa a parear e fica esperando o código ser digitado no
     * Sunshine. Em paralelo, mandamos o código para a WARP, que o entrega ao
     * Sunshine. O Sunshine só aceita depois que o pedido do app chegou, então
     * a entrega tenta de novo por alguns segundos.
     */
    private void parear(final WarpApi api, NvHTTP conn, ComputerDetails pc,
                        ComputerManagerService.ComputerManagerBinder g) throws Exception {
        final String pin = PairingManager.generatePinString();
        final String[] erroEntrega = {null};

        Thread entrega = new Thread(() -> {
            for (int i = 0; i < 20; i++) {
                try {
                    Thread.sleep(1200);
                    api.entregarCodigo(sessaoId, pin);
                    return;
                } catch (InterruptedException e) {
                    return;
                } catch (WarpApi.ErroApi e) {
                    if (!"PAREAMENTO_NAO_INICIADO".equals(e.codigo)) {
                        erroEntrega[0] = e.getMessage();
                        return;
                    }
                } catch (IOException e) {
                    // rede oscilou: tenta de novo
                }
            }
            erroEntrega[0] = "A máquina não confirmou o acesso a tempo.";
        });
        entrega.setDaemon(true);
        entrega.start();

        PairingManager pm = conn.getPairingManager();
        PairingManager.PairState estado = pm.pair(conn.getServerInfo(true), pin);
        entrega.interrupt();

        if (estado != PairingManager.PairState.PAIRED) {
            if (erroEntrega[0] != null) throw new Falha(erroEntrega[0]);
            if (estado == PairingManager.PairState.ALREADY_IN_PROGRESS) {
                throw new Falha("A máquina está liberando outro acesso agora. Tente de novo em instantes.");
            }
            throw new Falha("A máquina recusou o acesso. Tente de novo.");
        }

        // Guarda o certificado da máquina para as próximas conexões seguras.
        pc.serverCert = pm.getPairedCert();
        ComputerDetails noGerente = g.getComputer(pc.uuid);
        if (noGerente != null) noGerente.serverCert = pc.serverCert;
        g.invalidateStateForComputer(pc.uuid);
    }
}
