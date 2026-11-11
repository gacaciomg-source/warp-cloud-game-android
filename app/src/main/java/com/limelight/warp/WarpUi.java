package com.limelight.warp;

import android.app.Activity;
import android.app.Dialog;
import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;

/**
 * Peças de interface da WARP montadas no código: textos, botões, opções e a
 * caixa de diálogo. Tudo no mesmo visual (fundo marinho, verde da marca) e
 * pronto para controle e TV: o que é clicável também recebe foco, e o foco
 * aparece (borda verde, leve aumento).
 */
public final class WarpUi {
    public static final int VERDE = 0xFF00C48E;
    public static final int VERDE_ESCURO = 0xFF04211A;
    public static final int FUNDO = 0xFF07131F;
    public static final int SUPERFICIE = 0xFF0F2336;
    public static final int TEXTO = 0xFFEAF3F8;
    public static final int TEXTO_2 = 0xFFADC4D6;
    public static final int APAGADO = 0xFF6F8BA3;
    public static final int AMARELO = 0xFFF5B83D;
    public static final int ERRO = 0xFFF2545B;

    private WarpUi() {
    }

    public static int dp(Context c, float valor) {
        return Math.round(valor * c.getResources().getDisplayMetrics().density);
    }

    /** TV ou TV box: a tela é de longe e tudo se navega pelo controle. */
    public static boolean ehTv(Context c) {
        UiModeManager m = (UiModeManager) c.getSystemService(Context.UI_MODE_SERVICE);
        return m != null && m.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    public static boolean telaLarga(Context c) {
        return c.getResources().getConfiguration().screenWidthDp >= 720;
    }

    public static boolean tablet(Context c) {
        return c.getResources().getConfiguration().smallestScreenWidthDp >= 600 || ehTv(c);
    }

    /* ----------------------------------------------------------- textos */

    public static TextView texto(Context c, CharSequence s, float sp, int cor, boolean negrito) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(cor);
        t.setLineSpacing(0, 1.15f);
        if (negrito) t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        return t;
    }

    /** Título grande das telas (nome do jogo, "Configurações"). */
    public static TextView titulo(Context c, CharSequence s, float sp) {
        TextView t = texto(c, s, sp, TEXTO, true);
        t.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        t.setLetterSpacing(-0.01f);
        t.setLineSpacing(0, 1.0f);
        return t;
    }

    /** Rótulo verde em maiúsculas: "EM DESTAQUE", "QUALIDADE". */
    public static TextView rotulo(Context c, String s) {
        TextView t = texto(c, s, 11.5f, VERDE, true);
        t.setAllCaps(true);
        t.setLetterSpacing(0.14f);
        return t;
    }

    public static TextView umaLinha(TextView t) {
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    /* ----------------------------------------------------------- blocos */

    public static LinearLayout coluna(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout linha(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static View espaco(Context c, int dpLargura, int dpAltura) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, dpLargura), dp(c, dpAltura)));
        return v;
    }

    /** Margens de um filho de LinearLayout, em dp. */
    public static LinearLayout.LayoutParams margens(Context c, int largura, int altura,
                                                    int esq, int cima, int dir, int baixo) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(largura, altura);
        p.setMargins(dp(c, esq), dp(c, cima), dp(c, dir), dp(c, baixo));
        return p;
    }

    public static GradientDrawable fundo(int cor, float raioDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(cor);
        g.setCornerRadius(dp(c, raioDp));
        return g;
    }

    /* ------------------------------------------------------------ ícones */

    public static Drawable icone(Context c, int res, int cor, int tamanhoDp) {
        Drawable d = c.getResources().getDrawable(res, c.getTheme()).mutate();
        d.setColorFilter(cor, PorterDuff.Mode.SRC_IN);
        int t = dp(c, tamanhoDp);
        d.setBounds(0, 0, t, t);
        return d;
    }

    public static ImageView imagemDeIcone(Context c, int res, int cor, int tamanhoDp) {
        ImageView i = new ImageView(c);
        i.setImageResource(res);
        i.setColorFilter(cor, PorterDuff.Mode.SRC_IN);
        i.setLayoutParams(new LinearLayout.LayoutParams(dp(c, tamanhoDp), dp(c, tamanhoDp)));
        return i;
    }

    /* ----------------------------------------------------------- botões */

    /** Botão grande: principal (verde) ou secundário (vidro escuro). */
    public static Button botao(Context c, String texto, int icone, boolean principal) {
        Button b = new Button(c);
        b.setText(texto);
        b.setAllCaps(false);
        b.setTextSize(ehTv(c) ? 17 : 15.5f);
        b.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        b.setTextColor(principal ? VERDE_ESCURO : TEXTO);
        b.setBackgroundResource(principal ? R.drawable.warp_botao : R.drawable.warp_botao_secundario);
        b.setStateListAnimator(null);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        int h = dp(c, 22), v = dp(c, ehTv(c) ? 15 : 13);
        b.setPadding(h, v, h + (icone != 0 ? dp(c, 4) : 0), v);
        if (icone != 0) {
            b.setCompoundDrawables(icone(c, icone, principal ? VERDE_ESCURO : TEXTO, 20), null, null, null);
            b.setCompoundDrawablePadding(dp(c, 10));
        }
        b.setFocusable(true);
        focoComZoom(b, 1.04f);
        return b;
    }

    public static Button botaoPerigo(Context c, String texto, int icone) {
        Button b = botao(c, texto, 0, false);
        b.setBackgroundResource(R.drawable.warp_botao_perigo);
        b.setTextColor(0xFFFFB3B6);
        if (icone != 0) {
            b.setCompoundDrawables(icone(c, icone, 0xFFFFB3B6, 20), null, null, null);
            b.setCompoundDrawablePadding(dp(c, 10));
        }
        return b;
    }

    /** Botão redondo só com ícone. */
    public static ImageButton botaoIcone(Context c, int icone, String descricao) {
        ImageButton b = new ImageButton(c);
        b.setImageResource(icone);
        b.setColorFilter(TEXTO, PorterDuff.Mode.SRC_IN);
        b.setBackgroundResource(R.drawable.warp_botao_icone);
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int p = dp(c, 11);
        b.setPadding(p, p, p, p);
        b.setContentDescription(descricao);
        b.setFocusable(true);
        int t = dp(c, ehTv(c) ? 52 : 46);
        b.setLayoutParams(new LinearLayout.LayoutParams(t, t));
        focoComZoom(b, 1.08f);
        return b;
    }

    /** Opção para escolher (qualidade, loja, tamanho). Escolhida = selected. */
    public static TextView chip(Context c, String texto) {
        TextView t = texto(c, texto, ehTv(c) ? 15.5f : 14f, TEXTO, true);
        t.setBackgroundResource(R.drawable.warp_chip);
        int h = dp(c, 16), v = dp(c, 9);
        t.setPadding(h, v, h, v);
        t.setGravity(Gravity.CENTER);
        t.setFocusable(true);
        t.setClickable(true);
        focoComZoom(t, 1.05f);
        return t;
    }

    public interface AoEscolher {
        void escolheu(int indice);
    }

    public interface AoMudar {
        void mudou(boolean ligado);
    }

    /**
     * Grupo de opções lado a lado (uma escolhida). Rola para o lado se não
     * couber na tela.
     */
    public static View opcoes(Context c, String[] nomes, int atual, final AoEscolher aoEscolher) {
        android.widget.HorizontalScrollView rolar = new android.widget.HorizontalScrollView(c);
        rolar.setHorizontalScrollBarEnabled(false);
        rolar.setClipToPadding(false);
        int folga = dp(c, 6);
        rolar.setPadding(folga, folga, folga, folga);
        final LinearLayout l = linha(c);
        final TextView[] chips = new TextView[nomes.length];
        for (int i = 0; i < nomes.length; i++) {
            final int indice = i;
            chips[i] = chip(c, nomes[i]);
            chips[i].setSelected(i == atual);
            chips[i].setOnClickListener(v -> {
                for (TextView t : chips) t.setSelected(t == v);
                aoEscolher.escolheu(indice);
            });
            l.addView(chips[i], margens(c, -2, -2, 0, 0, i < nomes.length - 1 ? 8 : 0, 0));
        }
        rolar.addView(l);
        return rolar;
    }

    /** Linha com título, explicação e chave liga/desliga. A linha toda é clicável. */
    public static View chave(Context c, String titulo, String explicacao, boolean ligado, final AoMudar aoMudar) {
        final LinearLayout l = linha(c);
        l.setBackgroundResource(R.drawable.warp_linha_opcao);
        int p = dp(c, 12);
        l.setPadding(p, p, p, p);
        l.setFocusable(true);
        l.setClickable(true);
        LinearLayout textos = coluna(c);
        textos.addView(texto(c, titulo, 15.5f, TEXTO, true));
        if (explicacao != null) {
            TextView e = texto(c, explicacao, 13.5f, APAGADO, false);
            e.setPadding(0, dp(c, 3), 0, 0);
            textos.addView(e);
        }
        l.addView(textos, new LinearLayout.LayoutParams(0, -2, 1));
        final android.widget.Switch s = new android.widget.Switch(c);
        s.setChecked(ligado);
        s.setFocusable(false);
        s.setClickable(false);
        s.setThumbTintList(new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}}, new int[]{VERDE, 0xFFB8C7D3}));
        s.setTrackTintList(new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}}, new int[]{0x8000C48E, 0x55FFFFFF}));
        l.addView(s, margens(c, -2, -2, 12, 0, 0, 0));
        l.setOnClickListener(v -> {
            s.setChecked(!s.isChecked());
            aoMudar.mudou(s.isChecked());
        });
        return l;
    }

    /** Barra de arrastar (transparência), já com as cores da WARP. */
    public static android.widget.SeekBar barra(Context c, int min, int max, int valor, final AoEscolher aoMudar) {
        final int base = min;
        android.widget.SeekBar b = new android.widget.SeekBar(c);
        b.setMax(max - min);
        b.setProgress(Math.max(0, Math.min(max - min, valor - min)));
        b.setProgressTintList(android.content.res.ColorStateList.valueOf(VERDE));
        b.setThumbTintList(android.content.res.ColorStateList.valueOf(VERDE));
        b.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x55FFFFFF));
        b.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar s, int p, boolean doUsuario) {
                if (doUsuario) aoMudar.escolheu(base + p);
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar s) {
            }
        });
        return b;
    }

    /** Aumenta um pouco o que está com foco (controle e TV), sem apagar outro listener. */
    public static void focoComZoom(final View v, final float escala) {
        final View.OnFocusChangeListener anterior = v.getOnFocusChangeListener();
        v.setOnFocusChangeListener((vista, temFoco) -> {
            vista.animate().scaleX(temFoco ? escala : 1f).scaleY(temFoco ? escala : 1f)
                    .setDuration(140).start();
            if (anterior != null) anterior.onFocusChange(vista, temFoco);
        });
    }

    /* ----------------------------------------------------------- avisos */

    /**
     * Caixa de diálogo da WARP. `principal` pode ser null (só o botão de
     * fechar). Devolve o diálogo, já aberto.
     */
    public static Dialog perguntar(Activity a, String titulo, CharSequence mensagem,
                                   String principal, boolean perigo, final Runnable aoConfirmar,
                                   String secundario) {
        final Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout caixa = coluna(a);
        caixa.setBackground(fundo(SUPERFICIE, 22, a));
        int p = dp(a, 26);
        caixa.setPadding(p, p, p, dp(a, 20));

        if (titulo != null) {
            TextView t = titulo(a, titulo, 21);
            caixa.addView(t);
        }
        if (mensagem != null) {
            TextView m = texto(a, mensagem, 15, TEXTO_2, false);
            m.setPadding(0, dp(a, titulo != null ? 10 : 0), 0, 0);
            caixa.addView(m);
        }

        LinearLayout botoes = linha(a);
        botoes.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        botoes.setPadding(0, dp(a, 22), 0, 0);
        Button sec = botao(a, secundario != null ? secundario : "Fechar", 0, false);
        sec.setOnClickListener(v -> d.dismiss());
        botoes.addView(sec);
        Button pri = null;
        if (principal != null) {
            pri = perigo ? botaoPerigo(a, principal, 0) : botao(a, principal, 0, true);
            pri.setOnClickListener(v -> {
                d.dismiss();
                if (aoConfirmar != null) aoConfirmar.run();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(dp(a, 10));
            botoes.addView(pri, lp);
        }
        caixa.addView(botoes);

        d.setContentView(caixa);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(Math.min(dp(a, 480), a.getResources().getDisplayMetrics().widthPixels - dp(a, 32)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        d.show();
        (pri != null && !perigo ? pri : sec).requestFocus();
        return d;
    }
}
