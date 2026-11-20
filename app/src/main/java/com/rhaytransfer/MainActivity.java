package com.rhaytransfer;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.os.Handler;
import android.os.Looper;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int BG = Color.rgb(5, 5, 7);
    private static final int PANEL = Color.rgb(18, 18, 21);
    private static final int PANEL_LIGHT = Color.rgb(31, 31, 35);
    private static final int NEON = Color.rgb(245, 245, 245);
    private static final int CYAN = Color.rgb(185, 185, 192);
    private static final int WHITE = Color.rgb(250, 250, 250);
    private static final int MUTED = Color.rgb(155, 155, 162);

    private LinearLayout content;
    private TextView selectedFile;
    private EditText ipAddress;
    private TextView connectionStatus;
    private Uri selectedUri;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(18), dp(20), dp(28));
        scroll.addView(content);
        setContentView(scroll);

        buildDashboard();
    }

    private void buildDashboard() {
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        TextView name = text("RHAY TRANSFER", 22, NEON, true);
        TextView subtitle = text("LOCAL FILE COMMAND CENTER", 9, MUTED, true);
        subtitle.setLetterSpacing(0.13f);
        brand.addView(name);
        brand.addView(subtitle);
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));

        TextView version = text("v1.4", 11, CYAN, true);
        version.setGravity(Gravity.CENTER);
        version.setPadding(dp(12), dp(7), dp(12), dp(7));
        version.setBackground(round(PANEL_LIGHT, 30, CYAN, 1));
        top.addView(version);
        content.addView(top);

        space(24);
        LinearLayout hero = card(PANEL);
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView live = text("●  RHAY TRANSFER", 10, NEON, true);
        live.setLetterSpacing(0.08f);
        statusRow.addView(live, new LinearLayout.LayoutParams(0, -2, 1));
        TextView local = text("REDE LOCAL", 9, CYAN, true);
        statusRow.addView(local);
        hero.addView(statusRow);

        spaceIn(hero, 15);
        hero.addView(text("Envie seus arquivos.", 25, WHITE, true));
        hero.addView(text("Seu PS4. Seu celular. Uma conexão.", 13, MUTED, false));
        spaceIn(hero, 18);

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.addView(statBlock("0 MB/s", "VELOCIDADE", NEON),
                new LinearLayout.LayoutParams(0, -2, 1));
        stats.addView(statBlock("0", "ENVIOS", CYAN),
                new LinearLayout.LayoutParams(0, -2, 1));
        hero.addView(stats);
        content.addView(hero);

        section("CONEXÃO DO CONSOLE", "01");
        LinearLayout connection = card(PANEL);
        connection.setPadding(dp(16), dp(15), dp(16), dp(16));

        LinearLayout connectionHead = new LinearLayout(this);
        connectionHead.setGravity(Gravity.CENTER_VERTICAL);
        connectionHead.addView(text("PlayStation 4", 16, WHITE, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        connectionStatus = text("●  NÃO TESTADO", 9, MUTED, true);
        TextView waiting = connectionStatus;
        waiting.setPadding(dp(9), dp(6), dp(9), dp(6));
        waiting.setBackground(round(PANEL_LIGHT, 20, MUTED, 1));
        connectionHead.addView(waiting);
        connection.addView(connectionHead);

        spaceIn(connection, 14);
        connection.addView(text("ENDEREÇO IP", 9, MUTED, true));
        spaceIn(connection, 6);
        ipAddress = new EditText(this);
        ipAddress.setSingleLine(true);
        ipAddress.setText("192.168.1.224");
        ipAddress.setTextSize(15);
        ipAddress.setTextColor(WHITE);
        ipAddress.setHintTextColor(MUTED);
        ipAddress.setPadding(dp(13), 0, dp(13), 0);
        ipAddress.setSelectAllOnFocus(true);
        ipAddress.setBackground(round(PANEL_LIGHT, 12, Color.rgb(75, 75, 82), 1));
        connection.addView(ipAddress, new LinearLayout.LayoutParams(-1, dp(49)));
        spaceIn(connection, 12);

        Button connect = button("CONECTAR AO PS4", NEON, BG);
        connection.addView(connect, new LinearLayout.LayoutParams(-1, dp(49)));
        connect.setOnClickListener(v -> testFtp());
        content.addView(connection);

        section("CENTRAL DE TRANSFERÊNCIA", "02");
        LinearLayout transfer = card(PANEL);
        transfer.setPadding(dp(16), dp(16), dp(16), dp(16));
        transfer.addView(text("Escolha o que deseja enviar", 16, WHITE, true));
        spaceIn(transfer, 5);
        transfer.addView(text("PKG, vídeos, músicas, fotos e documentos", 12, MUTED, false));
        spaceIn(transfer, 15);

        selectedFile = text("Nenhum arquivo selecionado", 12, MUTED, false);
        selectedFile.setPadding(dp(12), dp(13), dp(12), dp(13));
        selectedFile.setBackground(round(PANEL_LIGHT, 12, Color.TRANSPARENT, 0));
        transfer.addView(selectedFile);
        spaceIn(transfer, 12);

        Button choose = button("＋   ESCOLHER ARQUIVO", PANEL_LIGHT, WHITE);
        transfer.addView(choose, new LinearLayout.LayoutParams(-1, dp(48)));
        choose.setOnClickListener(v -> chooseFile());

        spaceIn(transfer, 10);
        Button send = button("INICIAR TRANSFERÊNCIA  →", NEON, BG);
        transfer.addView(send, new LinearLayout.LayoutParams(-1, dp(51)));
        send.setOnClickListener(v -> sendSelectedFile());
        content.addView(transfer);

        section("ATIVIDADE", "03");
        LinearLayout activity = card(PANEL);
        activity.setPadding(dp(16), dp(15), dp(16), dp(15));
        LinearLayout activityRow = new LinearLayout(this);
        activityRow.setGravity(Gravity.CENTER_VERTICAL);
        activityRow.addView(text("Velocidade atual", 13, WHITE, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        activityRow.addView(text("0.00 MB/s", 17, NEON, true));
        activity.addView(activityRow);
        spaceIn(activity, 12);
        View track = new View(this);
        track.setBackground(round(PANEL_LIGHT, 20, Color.TRANSPARENT, 0));
        activity.addView(track, new LinearLayout.LayoutParams(-1, dp(7)));
        spaceIn(activity, 12);
        activity.addView(text("Nenhuma transferência em andamento", 11, MUTED, false));
        content.addView(activity);

        section("ATALHOS", "04");
        LinearLayout shortcuts = new LinearLayout(this);
        shortcuts.setOrientation(LinearLayout.HORIZONTAL);
        shortcuts.addView(shortcut("▣", "Arquivos"),
                new LinearLayout.LayoutParams(0, dp(82), 1));
        shortcuts.addView(shortcut("↗", "Enviar"),
                new LinearLayout.LayoutParams(0, dp(82), 1));
        shortcuts.addView(shortcut("◷", "Histórico"),
                new LinearLayout.LayoutParams(0, dp(82), 1));
        content.addView(shortcuts);

        space(22);
        TextView footer = text("RHAY TRANSFER  •  FEITO PARA SUA REDE", 9, MUTED, true);
        footer.setGravity(Gravity.CENTER);
        footer.setLetterSpacing(0.08f);
        content.addView(footer);
    }

    private void chooseFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, 100);
    }


    private String getFileName(Uri uri) {
        String name = uri.getLastPathSegment();
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) name = c.getString(i);
            }
        } catch (Exception ignored) {}
        return name == null ? "arquivo" : name.replaceAll("[\\\\/\\\\r\\\\n]", "_");
    }

    private String getIp() {
        String ip = ipAddress.getText().toString().trim();
        if (ip.isEmpty()) throw new IllegalArgumentException("Informe o IP do PS4.");
        return ip;
    }

    private void testFtp() {
        final String ip;
        try { ip = getIp(); } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show(); return;
        }
        connectionStatus.setText("●  TESTANDO...");
        connectionStatus.setTextColor(CYAN);
        io.execute(() -> {
            FtpClient ftp = new FtpClient(ip, 2121);
            try {
                String result = ftp.connectAndLogin();
                main.post(() -> {
                    connectionStatus.setText("●  CONECTADO");
                    connectionStatus.setTextColor(NEON);
                    Toast.makeText(this, result, Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                main.post(() -> {
                    connectionStatus.setText("●  FALHA NA CONEXÃO");
                    connectionStatus.setTextColor(MUTED);
                    Toast.makeText(this, "Não conectou: " + e.getMessage()
                            + "\\nConfira FTP ativo, IP e Wi-Fi.", Toast.LENGTH_LONG).show();
                });
            } finally { ftp.close(); }
        });
    }

    private void sendSelectedFile() {
        if (selectedUri == null) {
            Toast.makeText(this, "Escolha um arquivo primeiro.", Toast.LENGTH_LONG).show(); return;
        }
        final String ip;
        try { ip = getIp(); } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show(); return;
        }
        String name = getFileName(selectedUri);
        if (!name.toLowerCase().endsWith(".pkg")) {
            Toast.makeText(this, "Nesta primeira etapa, o envio automático está limitado a arquivos PKG.", Toast.LENGTH_LONG).show();
            return;
        }
        final Uri uri = selectedUri;
        final String remote = "/data/pkg/" + name;
        Toast.makeText(this, "Enviando PKG para /data/pkg no PS4...", Toast.LENGTH_LONG).show();
        io.execute(() -> {
            FtpClient ftp = new FtpClient(ip, 2121);
            try {
                ftp.connectAndLogin();
                ftp.upload(new FtpClient.UriSource() {
                    public InputStream open() throws java.io.IOException {
                        return getContentResolver().openInputStream(uri);
                    }
                    public long length() {
                        try (android.content.res.AssetFileDescriptor afd = getContentResolver().openAssetFileDescriptor(uri, "r")) {
                            return afd == null ? -1 : afd.getLength();
                        } catch (Exception e) { return -1; }
                    }
                }, remote, (sent, total) -> main.post(() ->
                        selectedFile.setText("ENVIANDO: " + name + "\\n" + sent / 1024 / 1024 + " MB enviados")));
                main.post(() -> {
                    selectedFile.setText("ENVIO CONCLUÍDO\\n" + name);
                    Toast.makeText(this, "PKG enviado para /data/pkg. Agora instale pelo Package Installer do PS4.", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                main.post(() -> Toast.makeText(this, "Falha no envio: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } finally { ftp.close(); }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    private LinearLayout statBlock(String value, String label, int color) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(text(value, 21, color, true));
        box.addView(text(label, 9, MUTED, true));
        return box;
    }

    private LinearLayout shortcut(String symbol, String label) {
        LinearLayout box = card(PANEL_LIGHT);
        box.setGravity(Gravity.CENTER);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView icon = text(symbol, 23, NEON, true);
        icon.setGravity(Gravity.CENTER);
        box.addView(icon);
        TextView caption = text(label, 10, WHITE, true);
        caption.setGravity(Gravity.CENTER);
        box.addView(caption);
        LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) box.getLayoutParams();
        return box;
    }

    private void section(String title, String number) {
        space(21);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = text(title, 11, WHITE, true);
        heading.setLetterSpacing(0.1f);
        row.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(text(number, 10, CYAN, true));
        content.addView(row);
        space(9);
    }

    private LinearLayout card(int color) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(round(color, 18, Color.rgb(58, 58, 64), 1));
        return box;
    }

    private Button button(String label, int color, int textColor) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTextColor(textColor);
        b.setTypeface(null, Typeface.BOLD);
        b.setLetterSpacing(0.03f);
        b.setBackground(round(color, 13, color, 0));
        return b;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private GradientDrawable round(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (strokeWidth > 0) d.setStroke(dp(strokeWidth), strokeColor);
        return d;
    }

    private void space(int height) {
        View v = new View(this);
        content.addView(v, new LinearLayout.LayoutParams(1, dp(height)));
    }

    private void spaceIn(LinearLayout parent, int height) {
        View v = new View(this);
        parent.addView(v, new LinearLayout.LayoutParams(1, dp(height)));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                selectedUri = uri;
                String name = getFileName(uri);
                selectedFile.setText("ARQUIVO SELECIONADO\\n" + name);
                selectedFile.setTextColor(NEON);
            }
        }
    }
}
