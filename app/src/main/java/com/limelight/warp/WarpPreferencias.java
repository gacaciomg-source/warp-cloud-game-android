package com.limelight.warp;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.preference.PreferenceManager;
import android.view.Display;
import android.view.WindowManager;

import com.limelight.binding.input.ControllerHandler;
import com.limelight.binding.input.virtual_controller.VirtualControllerConfigurationLoader;

import java.util.Locale;

/**
 * Preferências da WARP e a tradução delas para o motor de vídeo.
 *
 * O jogador escolhe em termos simples (qualidade "Equilibrada", controle na
 * tela "Médio"); daqui sai a resolução, os quadros e a taxa de vídeo que o
 * motor usa. As chaves do motor são as do Moonlight (lidas pelo
 * PreferenceConfiguration); as da WARP começam com "warp_".
 */
public final class WarpPreferencias {
    public static final String AUTO = "auto";
    public static final String ECONOMICA = "economica";
    public static final String EQUILIBRADA = "equilibrada";
    public static final String ALTA = "alta";
    public static final String MAXIMA = "maxima";

    public static final String[] QUALIDADES = {AUTO, ECONOMICA, EQUILIBRADA, ALTA, MAXIMA};

    public static final String OSC_AUTO = "auto";
    public static final String OSC_SEMPRE = "sempre";
    public static final String OSC_NUNCA = "nunca";

    /** Tamanhos do controle na tela, em % do tamanho de referência (celular). */
    public static final int OSC_PEQUENO = 80;
    public static final int OSC_MEDIO = 100;
    public static final int OSC_GRANDE = 125;

    private static final String QUALIDADE = "warp_qualidade";
    private static final String REDE_PING = "warp_rede_ping";
    private static final String REDE_MBPS = "warp_rede_mbps";
    private static final String REDE_QUANDO = "warp_rede_quando";
    private static final String ESTATISTICAS = "warp_estatisticas";
    private static final String OSC = "warp_osc";
    private static final String OSC_TAMANHO = "warp_osc_tamanho";
    private static final String TOQUE_TRACKPAD = "warp_toque_trackpad";
    private static final String FLUIDO = "warp_fluido";
    private static final String TETO_MAQUINA = "warp_teto_maquina";
    private static final String TETO_APRENDIDO = "warp_teto_aprendido";

    // Chaves do motor (as mesmas do PreferenceConfiguration).
    private static final String M_RESOLUCAO = "list_resolution";
    private static final String M_FPS = "list_fps";
    private static final String M_BITRATE = "seekbar_bitrate_kbps";
    private static final String M_OSC = "checkbox_show_onscreen_controls";
    private static final String M_OSC_OPACIDADE = "seekbar_osc_opacity";
    private static final String M_OSC_VIBRAR = "checkbox_vibrate_osc";
    private static final String M_ESTATISTICAS_MOONLIGHT = "checkbox_enable_perf_overlay";
    private static final String M_TRACKPAD = "checkbox_touchscreen_trackpad";
    private static final String M_RITMO = "frame_pacing";
    private static final String M_AVISO_LATENCIA = "checkbox_enable_post_stream_toast";

    private WarpPreferencias() {
    }

    private static SharedPreferences prefs(Context ctx) {
        return PreferenceManager.getDefaultSharedPreferences(ctx);
    }

    /* ------------------------------------------------------- qualidade */

    /** Resolução, quadros e taxa de vídeo de uma escolha de qualidade. */
    public static final class Perfil {
        public final String id;
        public final int altura;
        public final int fps;
        public final int kbps;

        Perfil(String id, int altura, int fps, int kbps) {
            this.id = id;
            this.altura = altura;
            this.fps = fps;
            this.kbps = kbps;
        }

        public int largura() {
            return altura * 16 / 9;
        }

        /** "1080p · 15 Mbps". */
        public String resumo() {
            return altura + "p · " + mbpsTexto(kbps) + " Mbps";
        }

        /** Consumo de internet por hora jogando. */
        public String consumoPorHora() {
            float gb = kbps / 1000f * 0.45f;
            return String.format(Locale.forLanguageTag("pt-BR"), "%.1f GB por hora", gb);
        }
    }

    public static String nomeDaQualidade(String id) {
        switch (id) {
            case ECONOMICA: return "Econômica";
            case EQUILIBRADA: return "Equilibrada";
            case ALTA: return "Alta";
            case MAXIMA: return "Máxima";
            default: return "Automática";
        }
    }

    public static String explicacaoDaQualidade(String id) {
        switch (id) {
            case ECONOMICA: return "Para internet móvel ou fraca. Gasta menos dados.";
            case EQUILIBRADA: return "Boa imagem para a maioria das conexões.";
            case ALTA: return "Imagem mais nítida. Pede internet estável.";
            case MAXIMA: return "O melhor que a máquina entrega. Pede internet rápida e cabo ou Wi-Fi 5 GHz.";
            default: return "A WARP testa a sua conexão e escolhe por você.";
        }
    }

    public static String qualidade(Context ctx) {
        return prefs(ctx).getString(QUALIDADE, AUTO);
    }

    public static void definirQualidade(Context ctx, String id) {
        prefs(ctx).edit().putString(QUALIDADE, id).apply();
    }

    /** Lado menor da tela, em pixels (a "altura" com o aparelho deitado). */
    public static int alturaDaTela(Context ctx) {
        WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        Display d = wm.getDefaultDisplay();
        Point p = new Point();
        d.getRealSize(p);
        return Math.min(p.x, p.y);
    }

    /** Resolução que vale a pena para esta tela: acima disso, o aparelho só reduz. */
    private static int alturaMaxima(Context ctx) {
        return alturaDaTela(ctx) >= 1300 ? 1440 : 1080;
    }

    /** Perfil de uma qualidade, já com o teto da máquina e o que a conexão mostrou. */
    public static Perfil perfil(Context ctx, String id) {
        Perfil p;
        switch (id) {
            case ECONOMICA:
                p = new Perfil(id, 720, 60, 6000);
                break;
            case EQUILIBRADA:
                p = new Perfil(id, 1080, 60, 15000);
                break;
            case ALTA:
                p = new Perfil(id, 1080, 60, 25000);
                break;
            case MAXIMA:
                p = alturaMaxima(ctx) >= 1440 ? new Perfil(id, 1440, 60, 50000) : new Perfil(id, 1080, 60, 40000);
                break;
            default:
                p = recomendado(ctx);
                break;
        }
        int teto = tetoMaquina(ctx);
        if (teto > 0 && p.kbps > teto) {
            p = new Perfil(p.id, teto < 10000 ? Math.min(p.altura, 720) : p.altura, p.fps, teto);
        }
        return p;
    }

    public static Perfil perfilAtual(Context ctx) {
        return perfil(ctx, qualidade(ctx));
    }

    /**
     * O que a conexão aguenta: 60% da velocidade medida (o resto é folga para
     * oscilação), entre 4 e 40 Mbps. Ping alto pede menos dados por quadro.
     * Sem teste ainda, fica na Equilibrada.
     */
    public static Perfil recomendado(Context ctx) {
        float mbps = mbpsMedido(ctx);
        int ping = pingMedido(ctx);
        int kbps = mbps > 0 ? Math.round(mbps * 600) : 15000;
        kbps = Math.max(4000, Math.min(40000, kbps));
        if (ping >= 80) kbps = Math.min(kbps, 12000);
        int aprendido = prefs(ctx).getInt(TETO_APRENDIDO, 0);
        if (aprendido > 0) kbps = Math.min(kbps, aprendido);
        kbps = (kbps / 500) * 500;
        int altura = kbps >= 12000 && alturaDaTela(ctx) >= 900 ? 1080 : 720;
        return new Perfil(AUTO, altura, 60, kbps);
    }

    /**
     * A transmissão mostrou que a conexão não aguentou: a qualidade automática
     * passa a respeitar este teto até o próximo teste de conexão.
     */
    public static void aprenderTeto(Context ctx, int kbps) {
        prefs(ctx).edit().putInt(TETO_APRENDIDO, kbps).apply();
    }

    public static void definirTetoMaquina(Context ctx, int kbps) {
        prefs(ctx).edit().putInt(TETO_MAQUINA, Math.max(0, kbps)).apply();
    }

    public static int tetoMaquina(Context ctx) {
        return prefs(ctx).getInt(TETO_MAQUINA, 0);
    }

    /* ---------------------------------------------------- teste de rede */

    public static void guardarTeste(Context ctx, int pingMs, float mbps) {
        prefs(ctx).edit()
                .putInt(REDE_PING, pingMs)
                .putFloat(REDE_MBPS, mbps)
                .putLong(REDE_QUANDO, System.currentTimeMillis())
                .remove(TETO_APRENDIDO)
                .apply();
    }

    public static int pingMedido(Context ctx) {
        return prefs(ctx).getInt(REDE_PING, 0);
    }

    public static float mbpsMedido(Context ctx) {
        return prefs(ctx).getFloat(REDE_MBPS, 0);
    }

    /** Teste de mais de 30 minutos atrás (ou nunca feito): vale testar de novo. */
    public static boolean testeVelho(Context ctx) {
        return System.currentTimeMillis() - prefs(ctx).getLong(REDE_QUANDO, 0) > 30 * 60 * 1000L;
    }

    public static boolean temTeste(Context ctx) {
        return prefs(ctx).getLong(REDE_QUANDO, 0) > 0;
    }

    /* --------------------------------------------------------- na tela */

    public static boolean estatisticas(Context ctx) {
        return prefs(ctx).getBoolean(ESTATISTICAS, false);
    }

    public static void definirEstatisticas(Context ctx, boolean ligar) {
        prefs(ctx).edit().putBoolean(ESTATISTICAS, ligar).apply();
    }

    public static String modoOsc(Context ctx) {
        return prefs(ctx).getString(OSC, OSC_AUTO);
    }

    public static void definirModoOsc(Context ctx, String modo) {
        prefs(ctx).edit().putString(OSC, modo).apply();
    }

    public static int tamanhoOsc(Context ctx) {
        return prefs(ctx).getInt(OSC_TAMANHO, OSC_MEDIO);
    }

    public static void definirTamanhoOsc(Context ctx, int pct) {
        prefs(ctx).edit().putInt(OSC_TAMANHO, pct).apply();
    }

    public static int opacidadeOsc(Context ctx) {
        return prefs(ctx).getInt(M_OSC_OPACIDADE, 70);
    }

    public static void definirOpacidadeOsc(Context ctx, int pct) {
        prefs(ctx).edit().putInt(M_OSC_OPACIDADE, pct).apply();
    }

    public static boolean vibrarOsc(Context ctx) {
        return prefs(ctx).getBoolean(M_OSC_VIBRAR, true);
    }

    public static void definirVibrarOsc(Context ctx, boolean ligar) {
        prefs(ctx).edit().putBoolean(M_OSC_VIBRAR, ligar).apply();
    }

    /** Posições dos botões voltam ao padrão da WARP. */
    public static void restaurarPosicoesOsc(Context ctx) {
        ctx.getSharedPreferences(VirtualControllerConfigurationLoader.OSC_PREFERENCE, Context.MODE_PRIVATE)
                .edit().clear().apply();
    }

    public static boolean toqueTrackpad(Context ctx) {
        return prefs(ctx).getBoolean(TOQUE_TRACKPAD, false);
    }

    public static void definirToqueTrackpad(Context ctx, boolean trackpad) {
        prefs(ctx).edit().putBoolean(TOQUE_TRACKPAD, trackpad).putBoolean(M_TRACKPAD, trackpad).apply();
    }

    /** Mais fluido (um quadro de folga) em vez de menor atraso. */
    public static boolean fluido(Context ctx) {
        return prefs(ctx).getBoolean(FLUIDO, false);
    }

    public static void definirFluido(Context ctx, boolean fluido) {
        prefs(ctx).edit().putBoolean(FLUIDO, fluido).apply();
    }

    public static boolean temTelaDeToque(Context ctx) {
        return ctx.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN);
    }

    public static boolean temControleConectado(Context ctx) {
        return ControllerHandler.getAttachedControllerMask(ctx) != 0;
    }

    /** Se o controle na tela aparece agora: no automático, só sem controle de verdade. */
    public static boolean mostrarOsc(Context ctx) {
        String modo = modoOsc(ctx);
        if (OSC_NUNCA.equals(modo)) return false;
        if (OSC_SEMPRE.equals(modo)) return true;
        return temTelaDeToque(ctx) && !temControleConectado(ctx);
    }

    /* ------------------------------------------------------------ motor */

    /**
     * Grava no motor o que vale para a próxima conexão. Chamado antes de abrir
     * o jogo e antes de reconectar com outra qualidade.
     */
    public static Perfil aplicarNoMotor(Context ctx) {
        Perfil p = perfilAtual(ctx);
        prefs(ctx).edit()
                .putString(M_RESOLUCAO, p.largura() + "x" + p.altura)
                .putString(M_FPS, String.valueOf(p.fps))
                .putInt(M_BITRATE, p.kbps)
                .putBoolean(M_OSC, mostrarOsc(ctx))
                .putInt(M_OSC_OPACIDADE, opacidadeOsc(ctx))
                .putBoolean(M_TRACKPAD, toqueTrackpad(ctx))
                .putString(M_RITMO, fluido(ctx) ? "balanced" : "latency")
                // As telas antigas de estatística e de latência dão lugar às da WARP.
                .putBoolean(M_ESTATISTICAS_MOONLIGHT, false)
                .putBoolean(M_AVISO_LATENCIA, false)
                .apply();
        return p;
    }

    /* ---------------------------------------------------------- jogos */

    public static void marcarJogado(Context ctx, String slug) {
        prefs(ctx).edit().putLong("warp_jogado_" + slug, System.currentTimeMillis()).apply();
    }

    public static long jogadoEm(Context ctx, String slug) {
        return prefs(ctx).getLong("warp_jogado_" + slug, 0);
    }

    public static String loja(Context ctx, String slug, String padrao) {
        return prefs(ctx).getString("warp_loja_" + slug, padrao);
    }

    public static void definirLoja(Context ctx, String slug, String loja) {
        prefs(ctx).edit().putString("warp_loja_" + slug, loja).apply();
    }

    /* -------------------------------------------------------- formatos */

    public static String mbpsTexto(int kbps) {
        float m = kbps / 1000f;
        return m == Math.floor(m) ? String.valueOf((int) m)
                : String.format(Locale.forLanguageTag("pt-BR"), "%.1f", m);
    }

    public static String mbpsTexto(float mbps) {
        return String.format(Locale.forLanguageTag("pt-BR"), mbps >= 100 ? "%.0f" : "%.1f", mbps);
    }
}
