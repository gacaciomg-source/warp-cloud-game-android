package com.limelight.warp;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Conversa com a API da WARP: conta, catálogo e sessões do Cloud Game.
 *
 * A API autentica por cookie (o mesmo do site). Os cookies ficam guardados no
 * aparelho, então a pessoa entra uma vez e continua logada; quando o acesso de
 * 15 minutos expira, o app renova sozinho com o cookie de renovação.
 */
public final class WarpApi {
    public static final String BASE = "https://warpserver.com.br";
    private static final String API = BASE + "/api/v1";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static WarpApi instancia;

    private final OkHttpClient http;
    private final CookiesGuardados cookies;

    /** Erro devolvido pela API, com o código (ex.: PAREAMENTO_NAO_INICIADO). */
    public static final class ErroApi extends IOException {
        public final int status;
        public final String codigo;

        ErroApi(int status, String codigo, String mensagem) {
            super(mensagem);
            this.status = status;
            this.codigo = codigo;
        }
    }

    public static synchronized WarpApi get(Context ctx) {
        if (instancia == null) {
            instancia = new WarpApi(ctx.getApplicationContext());
        }
        return instancia;
    }

    private WarpApi(Context ctx) {
        cookies = new CookiesGuardados(ctx.getSharedPreferences("warp_sessao", Context.MODE_PRIVATE));
        http = new OkHttpClient.Builder()
                .cookieJar(cookies)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /** Endereço completo de uma mídia do painel (que vem como "/api/v1/..."). */
    public static String urlAbsoluta(String url) {
        if (url == null || url.isEmpty()) return null;
        return url.startsWith("/") ? BASE + url : url;
    }

    private JSONObject chamar(String metodo, String caminho, JSONObject corpo, boolean podeRenovar) throws IOException {
        RequestBody body = null;
        if (corpo != null) {
            body = RequestBody.create(corpo.toString(), JSON);
        } else if ("POST".equals(metodo)) {
            body = RequestBody.create(new byte[0], null);
        }

        Request req = new Request.Builder()
                .url(API + caminho)
                .method(metodo, body)
                .header("Accept", "application/json")
                .build();

        int status;
        String texto;
        try (Response r = http.newCall(req).execute()) {
            status = r.code();
            texto = r.body().string();
        }

        if (status == 401 && podeRenovar && renovar()) {
            return chamar(metodo, caminho, corpo, false);
        }

        JSONObject json;
        try {
            json = texto.isEmpty() ? new JSONObject() : new JSONObject(texto);
        } catch (JSONException e) {
            json = new JSONObject();
        }

        if (status < 200 || status >= 300) {
            String mensagem = json.optString("message", "");
            if (mensagem.isEmpty()) {
                mensagem = status == 401 ? "Sua sessão expirou. Entre de novo." : "Erro " + status + " na WARP.";
            }
            throw new ErroApi(status, json.optString("code", ""), mensagem);
        }
        return json;
    }

    /** Troca o cookie de renovação por um acesso novo. */
    private boolean renovar() {
        try {
            chamar("POST", "/auth/refresh", null, false);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /* ------------------------------------------------------------ conta */

    public JSONObject entrar(String email, String senha) throws IOException {
        try {
            JSONObject corpo = new JSONObject().put("email", email).put("password", senha);
            return chamar("POST", "/auth/login", corpo, false);
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    /** Devolve o usuário logado, ou null se não houver sessão válida. */
    public JSONObject quemSou() {
        if (!cookies.temSessao()) return null;
        try {
            return chamar("GET", "/auth/me", null, true).optJSONObject("user");
        } catch (IOException e) {
            return null;
        }
    }

    public void sairDaConta() {
        try {
            chamar("POST", "/auth/logout", null, false);
        } catch (IOException ignored) {
            // sai do mesmo jeito: os cookies somem daqui
        }
        cookies.limpar();
    }

    /* ------------------------------------------------------- Cloud Game */

    public JSONArray jogos() throws IOException {
        JSONArray lista = chamar("GET", "/cloud-game/games", null, true).optJSONArray("games");
        return lista != null ? lista : new JSONArray();
    }

    public JSONObject pedirSessao(String slug) throws IOException {
        try {
            return chamar("POST", "/cloud-game/sessions", new JSONObject().put("jogo", slug), true);
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    public JSONObject consultarSessao(String id, boolean jogando) throws IOException {
        return chamar("GET", "/cloud-game/sessions/" + id + (jogando ? "?jogando=1" : ""), null, true);
    }

    public void entregarCodigo(String id, String pin) throws IOException {
        try {
            chamar("POST", "/cloud-game/sessions/" + id + "/pin", new JSONObject().put("pin", pin), true);
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    public void sairDaSessao(String id) throws IOException {
        chamar("POST", "/cloud-game/sessions/" + id + "/end", null, true);
    }

    /* ---------------------------------------------------------- cookies */

    /** Cookies da WARP guardados no aparelho, para continuar logado. */
    private static final class CookiesGuardados implements CookieJar {
        private final SharedPreferences prefs;
        private final Map<String, Cookie> porNome = new HashMap<>();

        CookiesGuardados(SharedPreferences prefs) {
            this.prefs = prefs;
            HttpUrl base = HttpUrl.get(BASE);
            for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
                if (e.getKey().startsWith("cookie_") && e.getValue() instanceof String) {
                    Cookie c = Cookie.parse(base, (String) e.getValue());
                    if (c != null) porNome.put(c.name(), c);
                }
            }
        }

        @Override
        public synchronized void saveFromResponse(HttpUrl url, List<Cookie> lista) {
            SharedPreferences.Editor ed = prefs.edit();
            long agora = System.currentTimeMillis();
            for (Cookie c : lista) {
                if (c.expiresAt() <= agora) {
                    porNome.remove(c.name());
                    ed.remove("cookie_" + c.name());
                } else {
                    porNome.put(c.name(), c);
                    ed.putString("cookie_" + c.name(), c.toString());
                }
            }
            ed.apply();
        }

        @Override
        public synchronized List<Cookie> loadForRequest(HttpUrl url) {
            List<Cookie> r = new ArrayList<>();
            long agora = System.currentTimeMillis();
            for (Cookie c : porNome.values()) {
                if (c.expiresAt() > agora && c.matches(url)) r.add(c);
            }
            return r;
        }

        synchronized boolean temSessao() {
            return !porNome.isEmpty();
        }

        synchronized void limpar() {
            porNome.clear();
            prefs.edit().clear().apply();
        }
    }
}
