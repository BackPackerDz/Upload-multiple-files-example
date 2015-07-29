package com.androidpourtous.uploadfile;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.nononsenseapps.filepicker.FilePickerActivity;
import com.squareup.okhttp.MultipartBuilder;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import is.arontibo.library.ElasticDownloadView;


public class MainActivity extends AppCompatActivity {

    /**
     *  Le lien du script, qu'on appellera pour uploader notre fichier
     */
    private static final String URL_UPLOAD = "http://192.168.1.2/upload.php";

    private final OkHttpClient client = new OkHttpClient();

    private static final String CONTENT_TYPE = "application/octet-stream";

    private static final int FILE_CODE = 9999;


    private static final String FORM_NAME = "file";

    private static final String TAG_SUCCESS = "success";
    private static final String TAG_MESSAGE = "message";

    private ElasticDownloadView mElasticDownloadView;
    private Button button;


    /**
     *  Liste des fichier à upload
     */
    private ArrayList<File> files = new ArrayList<>();

    /**
     *  la taile de notre fichier
     */
    private long totalSize = 0;

    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        handler = new Handler(getMainLooper());

        mElasticDownloadView = (ElasticDownloadView) findViewById(R.id.elastic_download_view);
        button = (Button) findViewById(R.id.btn_choose_file);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Intent i = new Intent(MainActivity.this, FilePickerActivity.class);

                // Cette fois on permet à l'utilisateur de choisir plusieurs fichiers
                i.putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, true);
                i.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, false);
                i.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_FILE);

                // Configure initial directory by specifying a String.
                // You could specify a String like "/storage/emulated/0/", but that can
                // dangerous. Always use Android's API calls to get paths to the SD-card or
                // internal memory.
                i.putExtra(FilePickerActivity.EXTRA_START_PATH, Environment.getExternalStorageDirectory().getPath());

                startActivityForResult(i, FILE_CODE);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == FILE_CODE && resultCode == RESULT_OK) {
            if (data.getBooleanExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false)) {
                // For JellyBean and above
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    ClipData clip = data.getClipData();

                    if (clip != null) {


                        for (int i = 0; i < clip.getItemCount(); i++)
                        {

                            Uri uri = clip.getItemAt(i).getUri();

                            files.add(new File(uri.getPath()));

                        }

                        // On lance l'upload
                        new UploadTask(files.get(0)).execute();


                    }
                    // For Ice Cream Sandwich
                } else {
                    ArrayList<String> paths = data.getStringArrayListExtra
                            (FilePickerActivity.EXTRA_PATHS);

                    if (paths != null) {

                        for (String path: paths)
                        {
                            Uri uri = Uri.parse(path);

                            files.add(new File(uri.getPath()));
                        }

                        new UploadTask(files.get(0)).execute();


                    }
                }

            } else {
                Uri uri = data.getData();

                files.add(new File(uri.getPath()));

                new UploadTask(files.get(0)).execute();
            }
        }

    }


    private class UploadTask extends AsyncTask<Void, Integer, String> {

        private File file;

        public UploadTask(File file) {
            this.file = file;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            mElasticDownloadView.startIntro();
            mElasticDownloadView.setProgress(0);
        }

        @Override
        protected String doInBackground(Void... voids) {
            totalSize = file.length();

            RequestBody requestBody = new MultipartBuilder()
                    .type(MultipartBuilder.FORM)
                    .addFormDataPart(FORM_NAME, file.getName(),
                            new CountingFileRequestBody(file, CONTENT_TYPE, new CountingFileRequestBody.ProgressListener() {
                                @Override
                                public void transferred(long num) {

                                    final float progress = (num / (float) totalSize) * 100;

                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            onProgressUpdate((int) progress);
                                        }
                                    });
                                }
                            }))
                    .build();

            Request request = new Request.Builder()
                    .url(URL_UPLOAD)
                    .post(requestBody)
                    .build();

            Response response = null;

            try {
                // On exécute la requête
                response = client.newCall(request).execute();

                String responseStr = response.body().string();

                return responseStr;


            } catch (IOException e) {
                e.printStackTrace();
            }

            return  null;

        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            // On affiche le pourcentage d'ulpoad
            mElasticDownloadView.setProgress(values[0]);
        }


        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);


            try {
                JSONObject jsonObject = new JSONObject(s);

                int success = Integer.valueOf(jsonObject.getString(TAG_SUCCESS));
                String message = jsonObject.getString(TAG_MESSAGE);

                // Si c'est 1 donc l'upload s'est bien faite
                if (success == 1)
                    mElasticDownloadView.success();
                else
                    mElasticDownloadView.fail();

                // On affiche le message à l'utilisateur
                Toast.makeText(MainActivity.this, "Upload du fichier " + file.getName() + " terminé !", Toast.LENGTH_LONG).show();

                // Normalement l'upload a terminé à cette étape la,
                // donc on enlève notre objet de notre list
                files.remove(file);

                // Et on relance si notre list n'est pas vide
                if (!files.isEmpty())
                    new UploadTask(files.get(0)).execute();
                    // Il n'y a plus rien à uploader
                else
                    Toast.makeText(MainActivity.this, "Upload des fichiers terminés :D", Toast.LENGTH_LONG).show();

            } catch (JSONException e) {
                e.printStackTrace();
            }

        }
    }



}
