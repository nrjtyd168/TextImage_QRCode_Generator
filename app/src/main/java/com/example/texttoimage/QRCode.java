package com.example.texttoimage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.EnumMap;
import java.util.Map;

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

public class QRCode extends AppCompatActivity {
    public static final String MSG2 = "com.example.QRCode";
    ImageView qrcode;
    TextView header, title;
    Button home, save;
    public Bitmap qrBitmap;
    public static String[] data;
    private final Executor mExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qrcode);

        qrcode = findViewById(R.id.imageView);
        header = findViewById(R.id.textView3);
        title = findViewById(R.id.textView4);
        home = findViewById(R.id.home);
        save = findViewById(R.id.save);

        Intent intent = getIntent();
        data = intent.getStringExtra(PrevActivity.MSG1).split("~");
        title.setText(data[0]);

        generateQrCode(data[1]);
        qrcode.setImageBitmap(qrBitmap);

        GoogleSignInOptions signInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail().requestScopes(new Scope(DriveScopes.DRIVE_FILE)).build();

        GoogleSignInClient client = GoogleSignIn.getClient(QRCode.this, signInOptions);
        startActivityForResult(client.getSignInIntent(), 400);

        home.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                intent.setClass(QRCode.this, MainActivity.class);
                startActivity(intent);
            }
        });

        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (dir != null) saveImageToStorage(dir);
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
        GoogleSignIn.getSignedInAccountFromIntent(data).addOnSuccessListener(new OnSuccessListener<GoogleSignInAccount>() {
            @Override
            public void onSuccess(GoogleSignInAccount googleSignInAccount) {
                GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(QRCode.this, Collections.singleton(DriveScopes.DRIVE_FILE));
                credential.setSelectedAccount(googleSignInAccount.getAccount());

                Drive googleDriveServices = new Drive.Builder(AndroidHttp.newCompatibleTransport(), new GsonFactory(), credential).setApplicationName("QRGenerator").build();

                uploadBitmap(googleDriveServices);
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(QRCode.this, "Failed to SignIn Account!!!", Toast.LENGTH_LONG).show();
            }
        });
    }

    public void uploadBitmap(Drive mDriveService) {

        File file = new File(getCacheDir(), "qrcode.jpeg");
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            saveBitmapFromView(qrcode, header, title).compress(Bitmap.CompressFormat.JPEG, 100, fos);
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

        createFileImage(mDriveService, getCacheDir().getAbsolutePath() + "/qrcode.jpeg");
    }

    public Task<String> createFileImage(Drive mDriveService, String filePath) {
        return Tasks.call(mExecutor, () -> {
            com.google.api.services.drive.model.File fileMetaData = new com.google.api.services.drive.model.File();
            fileMetaData.setName(data[0]);
//            fileMetaData.setParents(Collections.singletonList("1haFZahMff7Xf8GrkDzvkFI7X2xya0H7s"));

            java.io.File file = new java.io.File(filePath);

            FileContent mediaContent = new FileContent("image/jpeg", file);

            com.google.api.services.drive.model.File myFile = null;
            try {
                myFile = mDriveService.files().create(fileMetaData, mediaContent).setFields("id, webViewLink").execute();
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (myFile == null) {
                throw new IOException("Null result when requesting file creation");
            }

            return myFile.getWebViewLink();
        });
    }

    private void generateQrCode(String data) {
        int qrCodeSize = 500;
        try {
            // Generate QR code
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(data, BarcodeFormat.QR_CODE, qrCodeSize, qrCodeSize, hints);
            int width = bitMatrix.getWidth();
            qrBitmap = Bitmap.createBitmap(width, width, Bitmap.Config.RGB_565);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < width; y++) {
                    qrBitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }

        } catch (WriterException e) {
            e.printStackTrace();
        }
    }

    private void saveImageToStorage(File dirUri) {
        try {
            File file = new File(dirUri, data[0] + ".png");
            FileOutputStream fos = new FileOutputStream(file);
            if (fos != null) {
                saveBitmapFromView(qrcode, header, title).compress(Bitmap.CompressFormat.JPEG, 100, fos);
                fos.flush();
                fos.close();
                Toast.makeText(this, "Image Saved", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Bitmap saveBitmapFromView(ImageView qrcode, TextView header, TextView title) {
        Bitmap bitmap1 = Bitmap.createBitmap(qrcode.getWidth(), qrcode.getHeight(), Bitmap.Config.ARGB_8888);
        Bitmap bitmap2 = Bitmap.createBitmap(header.getWidth(), header.getHeight(), Bitmap.Config.ARGB_8888);
        Bitmap bitmap3 = Bitmap.createBitmap(title.getWidth(), title.getHeight(), Bitmap.Config.ARGB_8888);

        Canvas canvas1 = new Canvas(bitmap2);
        Canvas canvas2 = new Canvas(bitmap1);
        Canvas canvas3 = new Canvas(bitmap3);
        header.draw(canvas1);
        qrcode.draw(canvas2);
        title.draw(canvas3);

        Bitmap botmap = Bitmap.createBitmap(header.getWidth(), bitmap1.getHeight() + bitmap2.getHeight() + bitmap3.getHeight() + 60, Bitmap.Config.ARGB_8888);

        Canvas convas = new Canvas(botmap);
        convas.drawBitmap(bitmap2, 0f, 0f, null);
        convas.drawBitmap(bitmap1, (bitmap2.getWidth() - bitmap1.getWidth())/2, bitmap2.getHeight() + 30, null);
        convas.drawBitmap(bitmap3, 0f, bitmap1.getHeight() + bitmap2.getHeight() + 60, null);

        return botmap;
    }
}