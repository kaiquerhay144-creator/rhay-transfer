package com.rhaytransfer;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

public class MainActivity extends Activity {

    LinearLayout main;
    TextView selectedFile;
    EditText ipAddress;

    int background = Color.rgb(13, 16, 25);
    int panel = Color.rgb(25, 30, 43);
    int green = Color.rgb(0, 220, 145);
    int white = Color.WHITE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(24, 35, 24, 30);
        main.setBackgroundColor(background);

        scroll.addView(main);
        setContentView(scroll);

        addText("RHAY TRANSFER", 28, green, true);
        addText("TRANSFERÊNCIA INTELIGENTE", 11, white, true);
        addSpace(25);

        addCardTitle("CONEXÃO COM PS4");

        ipAddress = new EditText(this);
        ipAddress.setSingleLine(true);
        ipAddress.setText("192.168.1.224");
        ipAddress.setTextColor(white);
        ipAddress.setHintTextColor(Color.GRAY);
        ipAddress.setHint("Endereço IP do PS4");
        ipAddress.setPadding(18, 12, 18, 12);
        ipAddress.setBackgroundColor(panel);
        main.addView(ipAddress);

        addSpace(12);

        addButton("CONECTAR AO PS4", green, background, v -> {
            Toast.makeText(this,
                "IP configurado. Aguardando serviço no PS4.",
                Toast.LENGTH_LONG).show();
        });

        addSpace(25);
        addCardTitle("TRANSFERÊNCIA DE ARQUIVOS");

        selectedFile = addText(
            "Nenhum arquivo selecionado",
            14, Color.LTGRAY, false
        );

        addSpace(12);

        addButton("＋ ESCOLHER ARQUIVO", panel, white, v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, 100);
        });

        addSpace(15);

        addButton("ENVIAR PARA PS4", green, background, v -> {
            Toast.makeText(this,
                "Selecione um arquivo e configure a conexão do console.",
                Toast.LENGTH_LONG).show();
        });

        addSpace(25);
        addCardTitle("VELOCIDADE DE TRANSFERÊNCIA");

        addText("0 MB/s", 34, green, true);
        addText("Aguardando transferência", 13, Color.LTGRAY, false);

        addSpace(25);
        addCardTitle("HISTÓRICO");

        addText("Nenhuma transferência realizada.",
            13, Color.LTGRAY, false);

        addSpace(30);
        TextView footer = addText(
            "RHAY TRANSFER • VERSÃO 1.0",
            11, Color.GRAY, true
        );
        footer.setGravity(Gravity.CENTER);
    }

    TextView addText(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        view.setPadding(0, 5, 0, 5);
        main.addView(view);
        return view;
    }

    void addCardTitle(String text) {
        TextView view = addText(text, 15, green, true);
        view.setPadding(0, 10, 0, 12);
    }

    void addSpace(int height) {
        View space = new View(this);
        main.addView(space,
            new LinearLayout.LayoutParams(1, height));
    }

    void addButton(String text, int color, int textColor,
                   View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(textColor);
        button.setTextSize(13);
        button.setTypeface(null, Typeface.BOLD);
        button.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(color));
        button.setOnClickListener(listener);

        LinearLayout.LayoutParams params =
            new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 58);
        main.addView(button, params);
    }

    @Override
    protected void onActivityResult(int requestCode,
            int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 100 && resultCode == RESULT_OK
                && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                selectedFile.setText(
                    "Arquivo selecionado:\n" +
                    uri.getLastPathSegment());
            }
        }
    }
}
