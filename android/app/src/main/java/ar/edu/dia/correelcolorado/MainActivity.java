package ar.edu.dia.correelcolorado;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

/**
 * ¡Corré El Colorado! como app de Android.
 * El juego (index.html) viaja adentro de la app y se abre sin internet.
 * Se sirve desde una dirección https interna para que el navegador
 * permita la cámara, y la app le pasa el permiso de cámara de Android.
 */
public class MainActivity extends Activity {

    private static final int PEDIDO_CAMARA = 1;
    private static final int PEDIDO_FOTOS = 2;
    private static final String INICIO =
            "https://appassets.androidplatform.net/assets/index.html";

    private WebView web;
    private PermissionRequest pedidoPendiente;
    private ValueCallback<Uri[]> fotosPendientes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        final WebViewAssetLoader cargador = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web = new WebView(this);
        WebSettings ajustes = web.getSettings();
        ajustes.setJavaScriptEnabled(true);
        ajustes.setDomStorageEnabled(true);
        ajustes.setMediaPlaybackRequiresUserGesture(false);
        ajustes.setAllowFileAccess(false);
        ajustes.setAllowContentAccess(false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return cargador.shouldInterceptRequest(r.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if ("appassets.androidplatform.net".equals(u.getHost())) return false;
                // Los enlaces externos se abren en el navegador del celular.
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignorado) { }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest pedido) {
                runOnUiThread(() -> {
                    boolean pideCamara = false;
                    for (String rec : pedido.getResources()) {
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(rec)) pideCamara = true;
                    }
                    if (!pideCamara) { pedido.deny(); return; }
                    if (tieneCamara()) {
                        pedido.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                    } else {
                        pedidoPendiente = pedido;
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, PEDIDO_CAMARA);
                    }
                });
            }

            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                // Para «Poner fotos propias de los lugares».
                if (fotosPendientes != null) fotosPendientes.onReceiveValue(null);
                fotosPendientes = callback;
                try {
                    startActivityForResult(params.createIntent(), PEDIDO_FOTOS);
                } catch (Exception e) {
                    fotosPendientes = null;
                    return false;
                }
                return true;
            }
        });

        setContentView(web);
        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(INICIO);
    }

    private boolean tieneCamara() {
        return Build.VERSION.SDK_INT < 23
                || checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int codigo, String[] permisos, int[] resultados) {
        if (codigo == PEDIDO_CAMARA && pedidoPendiente != null) {
            if (resultados.length > 0 && resultados[0] == PackageManager.PERMISSION_GRANTED) {
                pedidoPendiente.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
            } else {
                pedidoPendiente.deny();
            }
            pedidoPendiente = null;
        }
    }

    @Override
    protected void onActivityResult(int codigo, int resultado, Intent datos) {
        if (codigo == PEDIDO_FOTOS && fotosPendientes != null) {
            fotosPendientes.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultado, datos));
            fotosPendientes = null;
            return;
        }
        super.onActivityResult(codigo, resultado, datos);
    }

    @Override
    protected void onSaveInstanceState(Bundle estado) {
        super.onSaveInstanceState(estado);
        web.saveState(estado);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }
}
