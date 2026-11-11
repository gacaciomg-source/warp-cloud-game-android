package com.limelight.warp;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.R;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Configurações da WARP, em português simples: qualidade da imagem, teste de
 * conexão, fluidez, estatísticas, controle na tela, toque e conta.
 *
 * Quase tudo também muda durante o jogo, pelo menu da partida, sem sair da
 * sessão nem voltar para a fila.
 */
public class WarpConfigActivity extends Activity {
    private final List<View> linhasDeQualidade = new ArrayList<>();
    private WarpQualidadeView caixaDeConexao;
    private LinearLayout pagina;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        montarTela();
    }

    private void montarTela() {
        int margem = WarpUi.dp(this, WarpUi.tablet(this) ? 48 : 22);

        FrameLayout raiz = new FrameLayout(this);
        raiz.setBackgroundColor(WarpUi.FUNDO);
        ScrollView rolagem = new ScrollView(this);
        rolagem.setVerticalScrollBarEnabled(false);
        LinearLayout centro = WarpUi.coluna(this);
        centro.setGravity(Gravity.CENTER_HORIZONTAL);
        pagina = WarpUi.coluna(this);
        pagina.setPadding(margem, WarpUi.dp(this, 14), margem, WarpUi.dp(this, 48));
        centro.addView(pagina, new LinearLayout.LayoutParams(Math.min(WarpUi.dp(this, 760),
                getResources().getDisplayMetrics().widthPixels), -2));
        rolagem.addView(centro);
        raiz.addView(rolagem, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout topo = WarpUi.linha(this);
        ImageButton voltar = WarpUi.botaoIcone(this, R.drawable.warp_ic_voltar, "Voltar");
        voltar.setOnClickListener(v -> finish());
        topo.addView(voltar);
        TextView titulo = WarpUi.titulo(this, "Configurações", 26);
        titulo.setPadding(WarpUi.dp(this, 14), 0, 0, 0);
        topo.addView(titulo);
        pagina.addView(topo);

        TextView dica = WarpUi.texto(this, "Dá para mudar quase tudo isto durante o jogo, pelo menu da partida, "
                + "sem perder a sua vez.", 14, WarpUi.APAGADO, false);
        pagina.addView(dica, WarpUi.margens(this, -2, -2, 0, 12, 0, 0));

        /* Qualidade */
        secao("Qualidade da imagem");
        String atual = WarpPreferencias.qualidade(this);
        for (final String q : WarpPreferencias.QUALIDADES) {
            View linha = linhaDeQualidade(q, q.equals(atual));
            linhasDeQualidade.add(linha);
            pagina.addView(linha, WarpUi.margens(this, -1, -2, 0, 0, 0, 10));
        }
        caixaDeConexao = new WarpQualidadeView(this, false);
        pagina.addView(caixaDeConexao, WarpUi.margens(this, -1, -2, 0, 6, 0, 0));

        /* Fluidez */
        secao("Fluidez");
        pagina.addView(WarpUi.opcoes(this, new String[]{"Menor atraso", "Mais fluido"},
                WarpPreferencias.fluido(this) ? 1 : 0, i -> WarpPreferencias.definirFluido(this, i == 1)));
        explicar("Menor atraso responde mais rápido. Mais fluido guarda um quadro de folga e evita engasgos "
                + "em Wi-Fi que oscila.");

        /* Durante o jogo */
        secao("Durante o jogo");
        pagina.addView(WarpUi.chave(this, "Estatísticas na tela",
                "Ping, taxa de vídeo, quadros por segundo e a qualidade da conexão.",
                WarpPreferencias.estatisticas(this), ligado -> WarpPreferencias.definirEstatisticas(this, ligado)));

        /* Controle na tela */
        secao("Controle na tela");
        String[] modos = {WarpPreferencias.OSC_AUTO, WarpPreferencias.OSC_SEMPRE, WarpPreferencias.OSC_NUNCA};
        int modoAtual = indice(modos, WarpPreferencias.modoOsc(this));
        pagina.addView(WarpUi.opcoes(this, new String[]{"Automático", "Sempre", "Nunca"}, modoAtual,
                i -> WarpPreferencias.definirModoOsc(this, modos[i])));
        explicar("No automático, os botões aparecem só quando não há um controle conectado.");

        subtitulo("Tamanho dos botões");
        final int[] tamanhos = {WarpPreferencias.OSC_PEQUENO, WarpPreferencias.OSC_MEDIO, WarpPreferencias.OSC_GRANDE};
        pagina.addView(WarpUi.opcoes(this, new String[]{"Pequeno", "Médio", "Grande"},
                indice(tamanhos, WarpPreferencias.tamanhoOsc(this)), i -> {
                    WarpPreferencias.definirTamanhoOsc(this, tamanhos[i]);
                    WarpPreferencias.restaurarPosicoesOsc(this);
                }));

        subtitulo("Transparência");
        pagina.addView(WarpUi.barra(this, 20, 100, WarpPreferencias.opacidadeOsc(this),
                v -> WarpPreferencias.definirOpacidadeOsc(this, v)), WarpUi.margens(this, -1, -2, 0, 4, 0, 0));

        pagina.addView(WarpUi.chave(this, "Vibrar ao tocar nos botões", null, WarpPreferencias.vibrarOsc(this),
                ligado -> WarpPreferencias.definirVibrarOsc(this, ligado)), WarpUi.margens(this, -1, -2, 0, 8, 0, 0));
        Button restaurar = WarpUi.botao(this, "Voltar os botões à posição padrão", 0, false);
        restaurar.setOnClickListener(v -> {
            WarpPreferencias.restaurarPosicoesOsc(this);
            Toast.makeText(this, "Botões de volta à posição padrão", Toast.LENGTH_SHORT).show();
        });
        pagina.addView(restaurar, WarpUi.margens(this, -2, -2, 0, 8, 0, 0));

        /* Toque */
        secao("Toque na tela");
        pagina.addView(WarpUi.opcoes(this, new String[]{"Direto", "Como trackpad"},
                WarpPreferencias.toqueTrackpad(this) ? 1 : 0, i -> WarpPreferencias.definirToqueTrackpad(this, i == 1)));
        explicar("Direto: tocar é clicar onde o dedo está. Como trackpad: o dedo arrasta o cursor, como no notebook.");

        /* Conta */
        secao("Conta");
        final TextView conta = WarpUi.texto(this, "…", 15, WarpUi.TEXTO_2, false);
        pagina.addView(conta);
        new Thread(() -> {
            JSONObject eu = WarpApi.get(this).quemSou();
            runOnUiThread(() -> conta.setText(eu == null ? "Não foi possível carregar a conta."
                    : "Conectado como " + eu.optString("email")));
        }).start();
        Button sair = WarpUi.botaoPerigo(this, "Sair da conta", R.drawable.warp_ic_sair);
        sair.setOnClickListener(v -> WarpUi.perguntar(this, "Sair da conta?",
                "Para jogar de novo, você entra com o e-mail e a senha da WARP.", "Sair", true,
                () -> new Thread(() -> {
                    WarpApi.get(this).sairDaConta();
                    runOnUiThread(() -> {
                        Intent i = new Intent(this, WarpEntrarActivity.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                        finish();
                    });
                }).start(), "Cancelar"));
        pagina.addView(sair, WarpUi.margens(this, -2, -2, 0, 12, 0, 0));

        /* Sobre */
        secao("Sobre");
        pagina.addView(WarpUi.texto(this, "WARP Cloud Game " + versao(), 15, WarpUi.TEXTO_2, false));
        Button licencas = WarpUi.botao(this, "Licenças de código aberto", 0, false);
        licencas.setOnClickListener(v -> mostrarLicencas());
        pagina.addView(licencas, WarpUi.margens(this, -2, -2, 0, 12, 0, 0));

        setContentView(raiz);
        voltar.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (caixaDeConexao != null) caixaDeConexao.atualizar();
    }

    private View linhaDeQualidade(final String id, boolean escolhida) {
        LinearLayout l = WarpUi.linha(this);
        l.setBackgroundResource(R.drawable.warp_opcao);
        int p = WarpUi.dp(this, 16);
        l.setPadding(p, WarpUi.dp(this, 14), p, WarpUi.dp(this, 14));
        l.setFocusable(true);
        l.setClickable(true);
        l.setSelected(escolhida);

        LinearLayout textos = WarpUi.coluna(this);
        LinearLayout nome = WarpUi.linha(this);
        nome.addView(WarpUi.texto(this, WarpPreferencias.nomeDaQualidade(id), 16.5f, WarpUi.TEXTO, true));
        if (WarpPreferencias.AUTO.equals(id)) {
            TextView rec = WarpUi.texto(this, "RECOMENDADA", 10, WarpUi.VERDE, true);
            rec.setLetterSpacing(0.1f);
            rec.setBackground(WarpUi.fundo(0x2600C48E, 6, this));
            rec.setPadding(WarpUi.dp(this, 7), WarpUi.dp(this, 2), WarpUi.dp(this, 7), WarpUi.dp(this, 2));
            nome.addView(rec, WarpUi.margens(this, -2, -2, 10, 0, 0, 0));
        }
        textos.addView(nome);
        WarpPreferencias.Perfil perfil = WarpPreferencias.perfil(this, id);
        TextView detalhe = WarpUi.texto(this, perfil.resumo() + "  ·  " + perfil.consumoPorHora(), 13.5f, WarpUi.TEXTO_2, true);
        detalhe.setPadding(0, WarpUi.dp(this, 3), 0, 0);
        textos.addView(detalhe);
        TextView explicacao = WarpUi.texto(this, WarpPreferencias.explicacaoDaQualidade(id), 13.5f, WarpUi.APAGADO, false);
        explicacao.setPadding(0, WarpUi.dp(this, 2), 0, 0);
        textos.addView(explicacao);
        l.addView(textos, new LinearLayout.LayoutParams(0, -2, 1));

        ImageView marca = WarpUi.imagemDeIcone(this, R.drawable.warp_ic_check, WarpUi.VERDE, 24);
        marca.setVisibility(escolhida ? View.VISIBLE : View.INVISIBLE);
        marca.setTag("marca");
        l.addView(marca, WarpUi.margens(this, WarpUi.dp(this, 24), WarpUi.dp(this, 24), 12, 0, 0, 0));

        l.setOnClickListener(v -> {
            WarpPreferencias.definirQualidade(this, id);
            for (View outra : linhasDeQualidade) {
                outra.setSelected(outra == v);
                outra.findViewWithTag("marca").setVisibility(outra == v ? View.VISIBLE : View.INVISIBLE);
            }
            caixaDeConexao.atualizar();
        });
        return l;
    }

    private void secao(String nome) {
        TextView t = WarpUi.rotulo(this, nome);
        t.setPadding(0, WarpUi.dp(this, 30), 0, WarpUi.dp(this, 12));
        pagina.addView(t);
    }

    private void subtitulo(String nome) {
        TextView t = WarpUi.texto(this, nome, 15, WarpUi.TEXTO, true);
        t.setPadding(0, WarpUi.dp(this, 16), 0, WarpUi.dp(this, 4));
        pagina.addView(t);
    }

    private void explicar(String texto) {
        TextView t = WarpUi.texto(this, texto, 13.5f, WarpUi.APAGADO, false);
        t.setPadding(WarpUi.dp(this, 4), WarpUi.dp(this, 4), 0, 0);
        pagina.addView(t);
    }

    private static int indice(String[] lista, String valor) {
        for (int i = 0; i < lista.length; i++) if (lista[i].equals(valor)) return i;
        return 0;
    }

    private static int indice(int[] lista, int valor) {
        for (int i = 0; i < lista.length; i++) if (lista[i] == valor) return i;
        return 1;
    }

    private String versao() {
        try {
            PackageInfo i = getPackageManager().getPackageInfo(getPackageName(), 0);
            return i.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    /**
     * O motor de transmissão é software livre (GPL-3.0): a licença pede que o
     * app mostre o aviso e diga onde está o código-fonte.
     */
    private void mostrarLicencas() {
        WarpUi.perguntar(this, "Licenças de código aberto",
                "O motor de transmissão deste app vem do Moonlight (moonlight-stream.org), software livre "
                        + "sob a licença GPL-3.0. O código-fonte completo do WARP Cloud Game, com as "
                        + "mudanças da WARP, é público: github.com/gacaciomg-source/warp-cloud-game-android",
                "Ver código-fonte", false, () -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/gacaciomg-source/warp-cloud-game-android")));
                    } catch (Exception ignored) {
                        // sem navegador (TV box): o endereço está na mensagem
                    }
                }, "Fechar");
    }
}
