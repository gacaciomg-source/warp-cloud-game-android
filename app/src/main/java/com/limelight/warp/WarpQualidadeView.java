package com.limelight.warp;

import android.app.Activity;
import android.content.Intent;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;

import java.util.Locale;

/**
 * Caixa "Qualidade da transmissão": o que vai ser usado (ex.: Automática,
 * 1080p · 20 Mbps), o resultado do último teste de conexão e os botões de
 * testar de novo e de mudar. Aparece na tela do jogo e nas configurações.
 */
public class WarpQualidadeView extends LinearLayout {
    private final Activity tela;
    private final TextView perfil;
    private final TextView conexao;
    private final Button testar;
    private volatile boolean testando;

    public WarpQualidadeView(Activity tela, boolean comBotaoMudar) {
        super(tela);
        this.tela = tela;
        setOrientation(VERTICAL);
        setBackgroundResource(R.drawable.warp_caixa);
        int p = WarpUi.dp(tela, 18);
        setPadding(p, p, p, p);

        addView(WarpUi.rotulo(tela, "Qualidade da transmissão"));
        perfil = WarpUi.texto(tela, "", 17, WarpUi.TEXTO, true);
        addView(perfil, WarpUi.margens(tela, -2, -2, 0, 8, 0, 0));
        conexao = WarpUi.texto(tela, "", 14, WarpUi.TEXTO_2, false);
        addView(conexao, WarpUi.margens(tela, -2, -2, 0, 4, 0, 0));

        LinearLayout botoes = WarpUi.linha(tela);
        testar = WarpUi.botao(tela, "Testar conexão", R.drawable.warp_ic_grafico, false);
        testar.setOnClickListener(v -> testar());
        botoes.addView(testar);
        if (comBotaoMudar) {
            Button mudar = WarpUi.botao(tela, "Mudar", R.drawable.warp_ic_ajustes, false);
            mudar.setOnClickListener(v -> tela.startActivity(new Intent(tela, WarpConfigActivity.class)));
            botoes.addView(mudar, WarpUi.margens(tela, -2, -2, 10, 0, 0, 0));
        }
        addView(botoes, WarpUi.margens(tela, -2, -2, 0, 14, 0, 0));
        atualizar();
    }

    public void atualizar() {
        String q = WarpPreferencias.qualidade(tela);
        WarpPreferencias.Perfil p = WarpPreferencias.perfil(tela, q);
        perfil.setText(WarpPreferencias.nomeDaQualidade(q) + "  ·  " + p.resumo());
        if (testando) return;
        if (WarpPreferencias.temTeste(tela)) {
            conexao.setText(String.format(Locale.forLanguageTag("pt-BR"),
                    "Sua conexão: ping %d ms · %s Mbps. Recomendado: %s.",
                    WarpPreferencias.pingMedido(tela),
                    WarpPreferencias.mbpsTexto(WarpPreferencias.mbpsMedido(tela)),
                    WarpPreferencias.recomendado(tela).resumo()));
        } else {
            conexao.setText("Teste a conexão para a WARP escolher a melhor imagem para a sua internet.");
        }
    }

    /** Ping e velocidade até a WARP, em segundo plano. */
    public void testar() {
        if (testando) return;
        testando = true;
        testar.setEnabled(false);
        conexao.setText("Testando sua conexão…");
        conexao.setGravity(Gravity.START);
        new Thread(() -> {
            String erro = null;
            try {
                WarpApi api = WarpApi.get(tela);
                int ping = api.medirPing();
                float mbps = api.medirMbps();
                WarpPreferencias.guardarTeste(tela, ping, mbps);
            } catch (Exception e) {
                erro = "Não foi possível testar agora. Confira a internet e tente de novo.";
            }
            final String mensagem = erro;
            tela.runOnUiThread(() -> {
                testando = false;
                testar.setEnabled(true);
                atualizar();
                if (mensagem != null) conexao.setText(mensagem);
            });
        }).start();
    }
}
