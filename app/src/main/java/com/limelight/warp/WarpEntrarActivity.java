package com.limelight.warp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.limelight.R;

import java.io.IOException;

/**
 * Primeira tela do app: entrar com a conta da WARP, ou criar uma ali mesmo (a
 * mesma conta do site, com o aceite dos termos).
 *
 * Quem já entrou antes vai direto para os jogos — a sessão fica guardada no
 * aparelho e se renova sozinha.
 */
public class WarpEntrarActivity extends Activity {
    private boolean criando;
    private EditText nome;
    private CheckBox termos;
    private TextView subtitulo;
    private TextView trocar;
    private EditText email;
    private EditText senha;
    private Button botao;
    private ProgressBar carregando;
    private TextView erro;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.warp_entrar);

        nome = findViewById(R.id.warp_nome);
        termos = findViewById(R.id.warp_termos);
        subtitulo = findViewById(R.id.warp_subtitulo);
        trocar = findViewById(R.id.warp_criar_conta);
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
        trocar.setOnClickListener(v -> mudarModo(!criando));

        TextView textoTermos = findViewById(R.id.warp_termos_texto);
        textoTermos.setText(Html.fromHtml("Li e aceito os <a href=\"" + WarpApi.BASE + "/termos\">termos de uso</a>"
                + " e a <a href=\"" + WarpApi.BASE + "/privacidade\">política de privacidade</a>."));
        textoTermos.setMovementMethod(LinkMovementMethod.getInstance());

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
        trocar.setEnabled(!sim);
        nome.setEnabled(!sim);
        email.setEnabled(!sim);
        senha.setEnabled(!sim);
    }

    private void mudarModo(boolean criar) {
        criando = criar;
        nome.setVisibility(criar ? View.VISIBLE : View.GONE);
        findViewById(R.id.warp_termos_linha).setVisibility(criar ? View.VISIBLE : View.GONE);
        subtitulo.setText(criar ? "Crie a sua conta da WARP para jogar." : "Entre com a sua conta da WARP para jogar.");
        senha.setHint(criar ? "Senha (pelo menos 10 caracteres)" : "Senha");
        botao.setText(criar ? "Criar conta" : "Entrar");
        trocar.setText(criar ? "Já tenho conta: entrar" : "Ainda não tem conta? Criar conta");
        erro.setVisibility(View.GONE);
        (criar ? nome : email).requestFocus();
    }

    private void falhar(String mensagem) {
        erro.setText(mensagem);
        erro.setVisibility(View.VISIBLE);
    }

    private void entrar() {
        final String n = nome.getText().toString().trim();
        final String e = email.getText().toString().trim();
        final String s = senha.getText().toString();
        if (criando && n.length() < 2) {
            falhar("Escreva o seu nome.");
            return;
        }
        if (e.isEmpty() || s.isEmpty()) {
            falhar("Preencha o e-mail e a senha.");
            return;
        }
        if (criando && s.length() < 10) {
            falhar("A senha precisa ter pelo menos 10 caracteres.");
            return;
        }
        if (criando && !termos.isChecked()) {
            falhar("Para criar a conta, aceite os termos de uso e a política de privacidade.");
            return;
        }
        final boolean cadastro = criando;
        erro.setVisibility(View.GONE);
        ocupado(true);
        new Thread(() -> {
            String falha = null;
            try {
                if (cadastro) {
                    WarpApi.get(this).criarConta(n, e, s);
                } else {
                    WarpApi.get(this).entrar(e, s);
                }
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
