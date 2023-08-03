package com.example.texttoimage;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    public static final String MSG = "com.example.mainActivity";
    Button preview;
    EditText title, body;
    public static String text = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preview = findViewById(R.id.button);
        title = findViewById(R.id.titleHead);
        body = findViewById(R.id.typeHere);

        Intent intent = getIntent();
        if (text != null) {
            title.setText(text.split("~")[0]);
            body.setText(text.split("~")[1]);
        }
        preview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (body.getText().toString().equals(""))
                    text = title.getText().toString() + "~ ";
                else if (title.getText().toString().equals(""))
                    text = " ~" + body.getText().toString();
                else if (title.getText().toString().equals("") && body.getText().toString().equals(""))
                    text = " ~ ";
                else
                    text = title.getText().toString() + "~" + body.getText().toString();

                Intent intent = new Intent(MainActivity.this, PrevActivity.class);
                intent.putExtra(MSG, text);
                startActivity(intent);
            }
        });
    }

    @Override
    public void onBackPressed() {
        finishAffinity();
        finish();
    }
}