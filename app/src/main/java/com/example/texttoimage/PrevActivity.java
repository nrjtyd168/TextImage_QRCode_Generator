package com.example.texttoimage;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.FileContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class PrevActivity extends AppCompatActivity {

    public static final String MSG1 = "com.example.prevActivity";
    ImageView prevImage;
    TextView textView, textView2;
    Button edit, generate;
    public static String[] data;
    private final Executor mExecutor = Executors.newSingleThreadExecutor();

    @SuppressLint("WrongThread")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_prev);

        prevImage = findViewById(R.id.imageView2);
        textView = findViewById(R.id.textView);
        textView2 = findViewById(R.id.textView2);
        edit = findViewById(R.id.edit);
        generate = findViewById(R.id.generate);

        Intent intent = getIntent();
        data = intent.getStringExtra(MainActivity.MSG).split("~");
        textView.setText(data[0]);
        textView2.setText(data[1]);

        prevImage.post(new Runnable() {
            @Override
            public void run() {
                prevImage.setImageBitmap(loadBitmapFromView(prevImage, textView, textView2));
            }
        });

        edit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                intent.setClass(PrevActivity.this, MainActivity.class);
                startActivity(intent);

            }
        });

        generate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                GoogleSignInOptions signInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .requestScopes(new Scope(DriveScopes.DRIVE_FILE))
                        .build();

                GoogleSignInClient client = GoogleSignIn.getClient(PrevActivity.this, signInOptions);
                startActivityForResult(client.getSignInIntent(), 400);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case 400:
                if (resultCode == RESULT_OK) {
                    handleSignInIntent(data);
                }
                break;
        }
    }

    private void handleSignInIntent(Intent data) {
        GoogleSignIn.getSignedInAccountFromIntent(data)
                .addOnSuccessListener(new OnSuccessListener<GoogleSignInAccount>() {
                    @Override
                    public void onSuccess(GoogleSignInAccount googleSignInAccount) {
                        GoogleAccountCredential credential = GoogleAccountCredential
                                .usingOAuth2(PrevActivity.this, Collections.singleton(DriveScopes.DRIVE_FILE));
                        credential.setSelectedAccount(googleSignInAccount.getAccount());

                        Drive googleDriveServices = new Drive.Builder(
                                AndroidHttp.newCompatibleTransport(),
                                new GsonFactory(),
                                credential)
                                .setApplicationName("QRGenerator")
                                .build();

                        uploadBitmap(googleDriveServices);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(PrevActivity.this, "Failed to SignIn Account!!!", Toast.LENGTH_LONG).show();
                    }
                });
    }

    public void uploadBitmap(Drive mDriveService) {
        ProgressDialog progressDialog = new ProgressDialog(PrevActivity.this);
        progressDialog.setTitle("Uploading to Google Drive");
        progressDialog.show();

        Bitmap bitmap = loadBitmapFromView(prevImage, textView, textView2);
        File file = new File(getCacheDir(), "data.jpeg");
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
        } catch (IOException e) {
            Toast.makeText(this, "Error creating bitmap file", Toast.LENGTH_LONG).show();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    Toast.makeText(this, "Error closing file output stream", Toast.LENGTH_LONG).show();
                }
            }
        }
        createFileImage(mDriveService, getCacheDir().getAbsolutePath() + "/data.jpeg")
                .addOnSuccessListener(new OnSuccessListener<String>() {
                    @Override
                    public void onSuccess(String link) {
                        progressDialog.dismiss();
                        Toast.makeText(PrevActivity.this, "Upload Successful!!", Toast.LENGTH_SHORT).show();

                        Intent intent = new Intent(PrevActivity.this, QRCode.class);
                        intent.putExtra(MSG1, data[0] + "~" + link);
                        startActivity(intent);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        progressDialog.dismiss();
                        Toast.makeText(PrevActivity.this, "Check Your Google Drive Api Key", Toast.LENGTH_LONG).show();
                    }
                });
    }

    public Task<String> createFileImage(Drive mDriveService, String filePath) {
        return Tasks.call(mExecutor, () -> {
            com.google.api.services.drive.model.File fileMetaData = new com.google.api.services.drive.model.File();
            fileMetaData.setName(data[0]);
//            fileMetaData.setParents(Collections.singletonList("1clIwn7kKCWI4GlTrmDh8R0_P64X5H--8"));

            java.io.File file = new java.io.File(filePath);

            FileContent mediaContent = new FileContent("image/jpeg", file);

            com.google.api.services.drive.model.File myFile = null;
            try {
                myFile = mDriveService.files().create(fileMetaData, mediaContent)
                        .setFields("id, webViewLink")
                        .execute();
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (myFile == null) {
                throw new IOException("Null result when requesting file creation");
            }

            return myFile.getWebViewLink();
        });
    }

    private static Bitmap loadBitmapFromView(View i, View t, View t2) {
        Bitmap bitmap1 = Bitmap.createBitmap(i.getWidth(), i.getHeight(), Bitmap.Config.ARGB_8888);
        Bitmap bitmap2 = Bitmap.createBitmap(t.getWidth(), t.getHeight(), Bitmap.Config.ARGB_8888);
        Bitmap bitmap3 = Bitmap.createBitmap(t2.getWidth(), t2.getHeight(), Bitmap.Config.ARGB_8888);

        Canvas canvas1 = new Canvas(bitmap1);
        Canvas canvas2 = new Canvas(bitmap2);
        Canvas canvas3 = new Canvas(bitmap3);
        i.draw(canvas1);
        t.draw(canvas2);
        t2.draw(canvas3);

        Bitmap botmap = Bitmap.createBitmap(i.getWidth(), i.getHeight(), Bitmap.Config.ARGB_8888);
        int align_space = (bitmap1.getHeight() - (bitmap2.getHeight() + 10 + bitmap3.getHeight()))/2;

        Canvas convas = new Canvas(botmap);
        convas.drawBitmap(bitmap1, 0f, 0f, null);
        convas.drawBitmap(bitmap2, (bitmap1.getWidth() - bitmap2.getWidth())/2, align_space, null);
        convas.drawBitmap(bitmap3, (bitmap1.getWidth() - bitmap3.getWidth())/2, align_space + bitmap2.getHeight() + 10, null);

        return botmap;
    }
}