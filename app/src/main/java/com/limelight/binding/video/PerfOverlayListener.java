package com.limelight.binding.video;

public interface PerfOverlayListener {
    void onPerfUpdate(final String text);

    /** WARP: números da transmissão a cada segundo (sempre, com ou sem o painel aberto). */
    void onWarpStats(com.limelight.warp.WarpEstatisticas estatisticas);
}
