package com.limelight.warp;

/**
 * Números da transmissão nos últimos dois segundos, vindos do decodificador.
 * Alimentam a barra de estatísticas e a recomendação de qualidade.
 */
public final class WarpEstatisticas {
    public int largura;
    public int altura;
    public float fpsRecebido;
    public float fpsExibido;
    /** Quadros perdidos no caminho, em %. */
    public float perdaPct;
    /** Ida e volta até a máquina, em ms. */
    public int pingMs;
    public int variacaoMs;
    /** Taxa de vídeo realmente recebida. */
    public float mbps;
    public float decodificacaoMs;
    /** Tempo que a máquina leva para capturar e codificar cada quadro. */
    public float maquinaMs;
    public String codec;

    /** 0 = ótima, 1 = boa, 2 = instável. */
    public int nivel() {
        if (perdaPct >= 5 || pingMs >= 90) return 2;
        if (perdaPct >= 1 || pingMs >= 50 || variacaoMs >= 15) return 1;
        return 0;
    }

    public String nomeDoNivel() {
        switch (nivel()) {
            case 0: return "Ótima";
            case 1: return "Boa";
            default: return "Instável";
        }
    }
}
