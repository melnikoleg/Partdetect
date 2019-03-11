package com.objectdetect.tflite;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;

import static android.content.ContentValues.TAG;
import android.app.Activity;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import android.util.JsonReader;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Toast;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.StrictMode;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;


import androidx.appcompat.app.AppCompatActivity;

import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;


import com.wonderkiln.camerakit.CameraKitError;
import com.wonderkiln.camerakit.CameraKitEvent;
import com.wonderkiln.camerakit.CameraKitEventListener;
import com.wonderkiln.camerakit.CameraKitImage;
import com.wonderkiln.camerakit.CameraKitVideo;
import com.wonderkiln.camerakit.CameraView;

import android.widget.AdapterView;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;


import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String MODEL_PATH = "quantized_model.tflite";

    private static final int INPUT_SIZE = 224;
    public static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");
    private OkHttpClient client;
    private Classifier classifier;
    private Executor executor = Executors.newSingleThreadExecutor();
    //private TextView textViewResult;
    private ImageButton btnDetectObject;
    private ImageView imageViewResult;
    private CameraView cameraView;
    private ListView listView;
    private CustomAdapter pAdapter;

    //private EditText connectIp;
    private ArrayList<Parts> partsList;

    private File screenshotPath;

    private File outputDir;
    private Spinner spinner;
    private String respond;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        outputDir = getCacheDir(); // context being the Activity pointer

        setContentView(R.layout.activity_main);
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        cameraView = findViewById(R.id.cameraView);
        //  imageViewResult = findViewById(R.id.imageViewResult);
        //textViewResult = findViewById(R.id.textViewResult);
        //textViewResult.setMovementMethod(new ScrollingMovementMethod());
        btnDetectObject = (ImageButton) findViewById(R.id.btnDetectObject);

        cameraView.setCropOutput(true);
        listView = (ListView) findViewById(R.id.part_list);
//        connectIp = (EditText) findViewById(R.id.connectIp);


        cameraView.addCameraKitListener(new CameraKitEventListener() {
            @Override
            public void onEvent(CameraKitEvent cameraKitEvent) {
            }

            @Override
            public void onError(CameraKitError cameraKitError) {
            }

            @Override
            public void onImage(CameraKitImage cameraKitImage) {

                // создаём битмап
                Bitmap bitmap = cameraKitImage.getBitmap();

                //tryTosave(bitmap,"/sdcard/out1.jpg");
                // создаём и обрабатываем изображение
                bitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, false);

                final float[][] result = classifier.recognizeImage(bitmap);

                partsList = new ArrayList<>();
                JSONArray jsonArray = new JSONArray(Arrays.asList(result));

                client = new OkHttpClient();

                String url = "http://192.168.1.4:5000/recognise_image";


                listView = (ListView) findViewById(R.id.part_list);
                // создаем адаптер
                pAdapter = new CustomAdapter(MainActivity.this, partsList);
                listView.setAdapter(pAdapter);
                pAdapter.notifyDataSetChanged();
                String respond;
                try {
                    respond = post(url, jsonArray.toString());
                    String jsonData = respond;
                    JSONObject Jobject = new JSONObject(jsonData);
                    JSONArray Jarray = Jobject.getJSONArray("predict_result");

                    Log.d(TAG, "lenght " + String.valueOf(Jarray.length()));
                    for (int i=0; i < Jarray.length(); i++) {

                        JSONObject data = new JSONObject(Jarray.getJSONObject(i).toString());
                        JSONObject JoImg = new JSONObject(data.get("draw_img_preview").toString());
                        byte[] decodedBytes = Base64.decode(JoImg.get("$binary").toString(), Base64.DEFAULT);
                        Bitmap drawImgPreview = BitmapFactory.decodeByteArray(decodedBytes , 0, decodedBytes.length);
                        JSONObject drawId = new JSONObject();
                        drawId.put("id",data.get("draw_img_id"));
                        partsList.add(new Parts(
                                data.get("Name").toString(),
                                data.get("Designation").toString(),
                                drawImgPreview,
                                drawId)
                                );

                    }



                } catch (IOException | JSONException e) {
                    e.printStackTrace();
                    Toast.makeText(MainActivity.this,
                            e.toString(), Toast.LENGTH_LONG).show();
                }

                listView = (ListView) findViewById(R.id.part_list);
                // создаем адаптер
                pAdapter = new CustomAdapter(MainActivity.this, partsList);
                listView.setAdapter(pAdapter);
                pAdapter.notifyDataSetChanged();
                Toast.makeText(MainActivity.this,
                        "Image recognized", Toast.LENGTH_LONG).show();

            }

            @Override
            public void onVideo(CameraKitVideo cameraKitVideo) {

            }
        });

        btnDetectObject.setOnClickListener(this);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View itemClicked, int position,
                                    long id) {

                Intent intent = new Intent(MainActivity.this, PartActivity.class);
                String url = "http://192.168.1.4:5000/get_image";
                Parts item = partsList.get(position);


                String respondDraw;
                try {
                    respondDraw = post(url, item.getImageDrawId().toString());
                    JSONObject JobjectDraw = new JSONObject(respondDraw);

                    byte[] decodedBytes = Base64.decode(JobjectDraw.get("$binary").toString(), Base64.DEFAULT);
                    //Bitmap drawImg = BitmapFactory.decodeByteArray(decodedBytes , 0, decodedBytes.length);

                    intent.putExtra("image",decodedBytes);
                } catch (IOException | JSONException e) {
                    e.printStackTrace();
                    Toast.makeText(MainActivity.this,
                            e.toString(), Toast.LENGTH_LONG).show();
                }


                startActivity(intent);

                //Uri imageUri = Uri.fromFile(getFromDB(img_full));




            }
        });


        initTensorFlowAndLoadModel();
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnDetectObject:
                cameraView.captureImage();
                break;


        }


    }

    @Override
    protected void onResume() {
        super.onResume();
        cameraView.start();
    }

    @Override
    protected void onPause() {
        cameraView.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                classifier.close();
            }
        });
    }

    private void initTensorFlowAndLoadModel() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    classifier = TensorFlowImageClassifier.create(
                            getAssets(),
                            MODEL_PATH,
                            INPUT_SIZE);

                } catch (final Exception e) {
                    throw new RuntimeException("Error initializing TensorFlow!", e);
                }
            }
        });
    }

    String post(String url, String json) throws IOException {


        RequestBody body = RequestBody.create(JSON, json);

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();
        Response response = client.newCall(request).execute();


        return response.body().string();
    }

    private void tryTosave(Bitmap bitmap, String path) {
        File fn2;
        try { // Try to Save #2
            fn2 = new File(path);
            FileOutputStream out = new FileOutputStream(fn2);
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
            out.flush();
            out.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void makeButtonVisible() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                btnDetectObject.setVisibility(View.VISIBLE);
            }
        });
    }


    private String captalize(String text) {
        StringBuilder sb = new StringBuilder(text);
        sb.setCharAt(0, Character.toUpperCase(sb.charAt(0)));
        return sb.toString();
    }


}
