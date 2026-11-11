package com.limelight.warp;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.limelight.R;

import java.io.IOException;

/**
 * Primeira tela do app: entrar com a conta da WARP.
 *
 * Quem já entrou antes vai direto para os jogos — a sessão fica guardada no
 * aparelho e se renova sozinha.
 */
public class WarpEntrarActivity extends Activity {
    private EditText email;
    private EditText senha;
    private Button botao;
    private ProgressBar carregando;
    private TextView erro;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.warp_entrar);

        email = findViewById(R.id.warp_email);
        senha = findViewById(R.id.warp_senha);
        botao = findViewById(R.id.warp_entrar);
        carregando = findViewById(R.id.warp_carregando);
        erro = findViewById(R.id.warp_erro);

        botao.setOnClickListener(v -> entrar());
        senha.setOnEditorActionListener((v, acao, evento) -> {
            if (acao == EditorInfo.IME_ACTION_DONE) {
                entrar();
                return true;
            }
            return false;
        });
        findViewById(R.id.warp_criar_conta).setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(WarpApi.BASE + "/cadastro"))));

        // Já logado? Confere em segundo plano e segue para os jogos.
        ocupado(true);
        new Thread(() -> {
            boolean logado = WarpApi.get(this).quemSou() != null;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (logado) {
                    abrirJogos();
                } else {
                    ocupado(false);
                }
            });
        }).start();
    }

    private void ocupado(boolean sim) {
        carregando.setVisibility(sim ? View.VISIBLE : View.GONE);
        botao.setEnabled(!sim);
        email.setEnabled(!sim);
        senha.setEnabled(!sim);
    }

    private void entrar() {
        final String e = email.getText().toString().trim();
        final String s = senha.getText().toString();
        if (e.isEmpty() || s.isEmpty()) {
            erro.setText("Preencha o e-mail e a senha.");
            erro.setVisibility(View.VISIBLE);
            return;
        }
        erro.setVisibility(View.GONE);
        ocupado(true);
        new Thread(() -> {
            String falha = null;
            try {
                WarpApi.get(this).entrar(e, s);
            } catch (IOException ex) {
                falha = ex instanceof WarpApi.ErroApi
                        ? ex.getMessage()
                        : "Sem conexão com a WARP. Confira a internet e tente de novo.";
            }
            final String mensagem = falha;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (mensagem == null) {
                    abrirJogos();
                } else {
                    ocupado(false);
                    erro.setText(mensagem);
                    erro.setVisibility(View.VISIBLE);
                }
            });
        }).start();
    }

    private void abrirJogos() {
        startActivity(new Intent(this, WarpJogosActivity.class));
        finish();
    }
}
