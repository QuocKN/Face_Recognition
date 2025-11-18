package com.atharvakale.facerecognition;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.ListenableFuture;


import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.os.ParcelFileDescriptor;
import android.text.InputType;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.view.View;

import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.tensorflow.lite.Interpreter;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.FileChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import mqtt.FaceProcessor;
import mqtt.MqttManager;

public class MainActivity extends AppCompatActivity {
    FaceDetector detector;

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    PreviewView previewView;
    ImageView face_preview;
    Interpreter tfLite;
    TextView reco_name,preview_info,textAbove_preview;
    Button recognize,camera_switch, actions;
    ImageButton add_face;
    CameraSelector cameraSelector;
    boolean developerMode=false;
    float distance= 0.65f;
    boolean start=true,flipX=false;
    Context context=MainActivity.this;
    int cam_face=CameraSelector.LENS_FACING_FRONT; //Default Back Camera

    int[] intValues;
    int inputSize= 112;  //Input size for model
    boolean isModelQuantized=false;
//    float[][] embeedings;
    float IMAGE_MEAN = 128.0f;
    float IMAGE_STD = 128.0f;
    int OUTPUT_SIZE=192; //Output size of model
    private static int SELECT_PICTURE = 1;
    ProcessCameraProvider cameraProvider;
    private static final int MY_CAMERA_REQUEST_CODE = 100;
    //
    SimilarityClassifier.Recognition recTmp = new SimilarityClassifier.Recognition(
            "0",
            "tmp",
            -1f
    );
    long lastRecognizedTime = 0;
    String modelFile="mobile_face_net.tflite"; //model name

    private HashMap<String, List<SimilarityClassifier.Recognition>> registered = new HashMap<>(); //saved Faces
    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registered=readFromSP(); //Load saved faces from memory when app starts
        runOnUiThread(()->
                Toast.makeText(context, "Recognitions Loaded", Toast.LENGTH_SHORT).show()
        );
//        registered.putAll(readFromSP());
        setContentView(R.layout.activity_main);
        face_preview =findViewById(R.id.imageView);
        reco_name =findViewById(R.id.textView);
        preview_info =findViewById(R.id.textView2);
        textAbove_preview =findViewById(R.id.textAbovePreview);
        add_face=findViewById(R.id.imageButton);
        add_face.setVisibility(View.INVISIBLE);

        SharedPreferences sharedPref = getSharedPreferences("Distance",Context.MODE_PRIVATE);
        distance = sharedPref.getFloat("distance",1.00f);

        face_preview.setVisibility(View.INVISIBLE);
        recognize=findViewById(R.id.button3);
        camera_switch=findViewById(R.id.button5);
        actions=findViewById(R.id.button2);
        textAbove_preview.setText("Recognized Face:");
        // preview_info.setText("        Recognized Face:");
//        registered.putAll(readFromSP());

        // MQTT
        FaceProcessor faceProcessor = new FaceProcessor(this);

        MqttManager mqttManager = new MqttManager(
                this,
                "ssl://71e2c6502603479280eb36c1b5b12bfc.s1.eu.hivemq.cloud:8883",
                "server/device01/face_data",
                "mqttnkq",
                "Soict2025",
                (fullName, embedding) -> {
                    // 2️⃣ Gói embedding vào Recognition object
                    SimilarityClassifier.Recognition rec = new SimilarityClassifier.Recognition(
                            "0",
                            fullName,
                            -1f
                    );

                    // ✅ Gói lại embedding đúng định dạng float[1][OUTPUT_SIZE]
                    float[][] embeddingWrapped = new float[1][embedding.length];
                    embeddingWrapped[0] = embedding;

                    // ✅ setExtra phải là float[1][N], KHÔNG phải float[]
                    rec.setExtra(embeddingWrapped);

                    // 3️⃣ Tạo HashMap chứa khuôn mặt mới
                    HashMap<String, List<SimilarityClassifier.Recognition>> newFace = new HashMap<>();
//                    newFace.put(fullName, rec);


                    // 5️⃣ Hiển thị thông báo
//                    runOnUiThread(() ->
//                            Toast.makeText(MainActivity.this,
//                                    "✅ Đã thêm khuôn mặt từ MQTT: " + fullName,
//                                    Toast.LENGTH_SHORT).show()
//                    );
                    Snackbar.make(findViewById(android.R.id.content),
                                    "✅ Đã thêm khuôn mặt từ MQTT: " + fullName,
                                    Snackbar.LENGTH_SHORT)
                            .show();

                    // 4️⃣ Lưu vào SharedPreferences (mode=2: update)
                    insertToSP(newFace, fullName, rec, 0);
//                    insertToSP(registered,0);
                    registered.putAll(readFromSP());
//                    runOnUiThread(() ->
//                            Toast.makeText(context, "Recognitions Saved", Toast.LENGTH_SHORT).show()
//                    );
                    Log.d("MQTT", "✅ Đã thêm khuôn mặt: " + fullName);
                }
        );
        mqttManager.connect();

        //Camera Permission
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, MY_CAMERA_REQUEST_CODE);
        }
        //On-screen Action Button
        actions.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle("Select Action:");

                // add a checkbox list
                String[] names= {"View Recognition List","Update Recognition List","Save Recognitions","Load Recognitions","Clear All Recognitions","Developer Mode"};

                builder.setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        switch (which)
                        {
                            case 0:
                                displaynameListview();
                                break;
                            case 1:
                                updatenameListview();
                                break;
                            case 2:
                                insertToSP(registered, "xoa", recTmp, 0); //mode: 0:save all, 1:clear all, 2:update all
                                break;
                            case 3:
                                registered.putAll(readFromSP());
                                break;
                            case 4:
                                clearnameList();
                                break;
                            case 5:
                                developerMode();
                                break;
                        }

                    }
                });


                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
                builder.setNegativeButton("Cancel", null);

                // create and show the alert dialog
                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });

        //On-screen switch to toggle between Cameras.
        camera_switch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cam_face==CameraSelector.LENS_FACING_BACK) {
                    cam_face = CameraSelector.LENS_FACING_FRONT;
                    flipX=true;
                }
                else {
                    cam_face = CameraSelector.LENS_FACING_BACK;
                    flipX=false;
                }
                cameraProvider.unbindAll();
                cameraBind();
            }
        });

        add_face.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                addFace();
            }
        }));


        recognize.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(recognize.getText().toString().equals("Recognize"))
                {
                 start=true;
                 textAbove_preview.setText("Recognized Face:");
                recognize.setText("Add Face");
                add_face.setVisibility(View.INVISIBLE);
                reco_name.setVisibility(View.VISIBLE);
                face_preview.setVisibility(View.INVISIBLE);
                preview_info.setText("");
                //preview_info.setVisibility(View.INVISIBLE);
                }
                else
                {
                    textAbove_preview.setText("Face Preview: ");
                    recognize.setText("Recognize");
                    add_face.setVisibility(View.VISIBLE);
                    reco_name.setVisibility(View.INVISIBLE);
                    face_preview.setVisibility(View.VISIBLE);
                    preview_info.setText("1.Bring Face in view of Camera.\n\n2.Your Face preview will appear here.\n\n3.Click Add button to save face.");


                }

            }
        });

        //Load model
        try {
            tfLite=new Interpreter(loadModelFile(MainActivity.this,modelFile));
        } catch (IOException e) {
            e.printStackTrace();
        }
        //Initialize Face Detector
        FaceDetectorOptions highAccuracyOpts =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .build();
        detector = FaceDetection.getClient(highAccuracyOpts);

        cameraBind();
    }

    private void developerMode()
    {
        if (developerMode) {
            developerMode = false;
            Toast.makeText(context, "Developer Mode OFF", Toast.LENGTH_SHORT).show();
        }
        else {
            developerMode = true;
            Toast.makeText(context, "Developer Mode ON", Toast.LENGTH_SHORT).show();
        }
    }

    float[] embedding_global;
    private void addFace() {
        start = false;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Enter Name");

        // Set up the input
        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton("ADD", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) {
                    Toast.makeText(context, "Tên không được để trống!", Toast.LENGTH_SHORT).show();
                    start = true;
                    return;
                }

                // === BẢO VỆ 1: Kiểm tra face_preview có ảnh không ===
                if (face_preview.getWidth() == 0 || face_preview.getHeight() == 0) {
                    Toast.makeText(context, "Chưa có khuôn mặt để lưu!", Toast.LENGTH_SHORT).show();
                    start = true;
                    return;
                }

                if (embedding_global == null) {
                    Toast.makeText(context, "Lỗi xử lý khuôn mặt!", Toast.LENGTH_SHORT).show();
                    start = true;
                    return;
                }

                // Đóng gói và lưu
                float[][] wrapped = new float[1][OUTPUT_SIZE];
                System.arraycopy(embedding_global, 0, wrapped[0], 0, OUTPUT_SIZE);

                SimilarityClassifier.Recognition result = new SimilarityClassifier.Recognition("0", name, -1f);
                result.setExtra(wrapped);

//                registered.put(name, result);
//                insertToSP(registered, 2);

                Toast.makeText(context, "Đã lưu khuôn mặt: " + name, Toast.LENGTH_SHORT).show();
                start = true;
            }
        });

        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                start = true;
                dialog.cancel();
            }
        });

        builder.show();
    }
    private  void clearnameList()
    {
        AlertDialog.Builder builder =new AlertDialog.Builder(context);
        builder.setTitle("Do you want to delete all Recognitions?");
        builder.setPositiveButton("Delete All", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                registered.clear();
                Toast.makeText(context, "Recognitions Cleared", Toast.LENGTH_SHORT).show();
            }
        });
//        insertToSP(registered,1);
        insertToSP(registered, "xoa", recTmp, 1); //mode: 0:save all, 1:clear all, 2:update all

        builder.setNegativeButton("Cancel",null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    private void updatenameListview()
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        if(registered.isEmpty()) {
            builder.setTitle("No Faces Added!!");
            builder.setPositiveButton("OK",null);
        }
        else{
            builder.setTitle("Select Recognition to delete:");

        // add a checkbox list
        String[] names= new String[registered.size()];
        boolean[] checkedItems = new boolean[registered.size()];
         int i=0;
                for (Map.Entry<String, List<SimilarityClassifier.Recognition>> entry : registered.entrySet())
                {
                    //System.out.println("NAME"+entry.getKey());
                    names[i]=entry.getKey();
                    checkedItems[i]=false;
                    i=i+1;

                }

                builder.setMultiChoiceItems(names, checkedItems, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        // user checked or unchecked a box
                        //Toast.makeText(MainActivity.this, names[which], Toast.LENGTH_SHORT).show();
                       checkedItems[which]=isChecked;

                    }
                });


        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                       // System.out.println("status:"+ Arrays.toString(checkedItems));
                        for(int i=0;i<checkedItems.length;i++)
                        {
                            //System.out.println("status:"+checkedItems[i]);
                            if(checkedItems[i])
                            {
//                                Toast.makeText(MainActivity.this, names[i], Toast.LENGTH_SHORT).show();
                                registered.remove(names[i]);
                            }

                        }
                insertToSP(registered, "", recTmp, 2);
//                insertToSP(registered,2); //mode: 0:save all, 1:clear all, 2:update all
                Toast.makeText(context, "Recognitions Updated", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);

        // create and show the alert dialog
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    }
    private void displaynameListview()
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
       // System.out.println("Registered"+registered);
        if(registered.isEmpty())
            builder.setTitle("No Faces Added!!");
        else
            builder.setTitle("Recognitions:");

        // add a checkbox list
        String[] names= new String[registered.size()];
        boolean[] checkedItems = new boolean[registered.size()];
        int i=0;
        for (Map.Entry<String, List<SimilarityClassifier.Recognition>> entry : registered.entrySet())
        {
            //System.out.println("NAME"+entry.getKey());
            names[i]=entry.getKey();
            checkedItems[i]=false;
            i=i+1;

        }
        builder.setItems(names,null);



        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });

            // create and show the alert dialog
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_CAMERA_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "camera permission granted", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "camera permission denied", Toast.LENGTH_LONG).show();
            }
        }
    }

    private MappedByteBuffer loadModelFile(Activity activity, String MODEL_FILE) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(MODEL_FILE);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    //Bind camera and preview view
    private void cameraBind()
    {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        previewView=findViewById(R.id.previewView);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();

                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                // No errors need to be handled for this in Future.
                // This should never be reached.
            }
        }, ContextCompat.getMainExecutor(this));
    }
    void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder()
                .build();

        cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(cam_face)
                .build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        ImageAnalysis imageAnalysis =
                new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) //Latest frame is shown
                        .build();

        Executor executor = Executors.newSingleThreadExecutor();
        imageAnalysis.setAnalyzer(executor, new ImageAnalysis.Analyzer() {
            @androidx.camera.core.ExperimentalGetImage
            @Override
            public void analyze(@NonNull ImageProxy imageProxy) {
                try {
                    Thread.sleep(0);  //Camera preview refreshed every 10 millisec(adjust as required)
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                InputImage image = null;


                @SuppressLint("UnsafeExperimentalUsageError")
                // Camera Feed-->Analyzer-->ImageProxy-->mediaImage-->InputImage(needed for ML kit face detection)

                Image mediaImage = imageProxy.getImage();

                if (mediaImage != null) {
                    image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
//                    System.out.println("Rotation "+imageProxy.getImageInfo().getRotationDegrees());
                }

//                System.out.println("ANALYSIS");

                //Process acquired image to detect faces
                Task<List<Face>> result =
                        detector.process(image)
                                .addOnSuccessListener(
                                        new OnSuccessListener<List<Face>>() {
                                            @Override
                                            public void onSuccess(List<Face> faces) {

                                                if(faces.size()!=0) {

                                                    Face face = faces.get(0); //Get first face from detected faces
//                                                    System.out.println(face);

                                                    //mediaImage to Bitmap
                                                    Bitmap frame_bmp = toBitmap(mediaImage);

                                                    int rot = imageProxy.getImageInfo().getRotationDegrees();

                                                    //Adjust orientation of Face
                                                    Bitmap frame_bmp1 = rotateBitmap(frame_bmp, rot, false, false);



                                                    //Get bounding box of face
                                                    RectF boundingBox = new RectF(face.getBoundingBox());

                                                    //Crop out bounding box from whole Bitmap(image)
                                                    Bitmap cropped_face = getCropBitmapByCPU(frame_bmp1, boundingBox);

                                                    if(flipX)
                                                        cropped_face = rotateBitmap(cropped_face, 0, flipX, false);
                                                    //Scale the acquired Face to 112*112 which is required input for model
                                                    Bitmap scaled = getResizedBitmap(cropped_face, 112, 112);

                                                    if(start)
                                                        recognizeImage(scaled); //Send scaled bitmap to create face embeddings.
//                                                    System.out.println(boundingBox);

                                                }
                                                else
                                                {
                                                    if(registered.isEmpty())
                                                        reco_name.setText("Add Face");
                                                    else
                                                        reco_name.setText("No Face Detected!");
                                                }

                                            }
                                        })
                                .addOnFailureListener(
                                        new OnFailureListener() {
                                            @Override
                                            public void onFailure(@NonNull Exception e) {
                                                // Task failed with an exception
                                                // ...
                                            }
                                        })
                                .addOnCompleteListener(new OnCompleteListener<List<Face>>() {
                            @Override
                            public void onComplete(@NonNull Task<List<Face>> task) {

                                imageProxy.close(); //v.important to acquire next frame for analysis
                            }
                        });


            }
        });

        cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, imageAnalysis, preview);
    }

    public void recognizeImage(final Bitmap bitmap) {

        // set Face to Preview
        face_preview.setImageBitmap(bitmap);
        embedding_global = extractEmbedding(bitmap);

        float distance_local = Float.MAX_VALUE;
        String id = "0";
        String label = "?";

        //Compare new face with saved Faces.
        if (registered.size() > 0) {
            if (System.currentTimeMillis() - lastRecognizedTime > 800) {  // mỗi 0.8 giây mới nhận diện lại
                lastRecognizedTime = System.currentTimeMillis();
                final List<Pair<String, Float>> nearest = findNearest(embedding_global);//Find 2 closest matching face

                if (nearest.get(0) != null) {

                    final String name = nearest.get(0).first; //get name and distance of closest matching face
                    // label = name;
                    distance_local = nearest.get(0).second;

                    // === Thêm đoạn này ===
                    Deque<Float> recentDistances = new ArrayDeque<>();
                    recentDistances.add(distance_local);
                    if (recentDistances.size() > 5) recentDistances.poll();

                    float avgDistance = 0f;
                    for (float d : recentDistances) avgDistance += d;
                    avgDistance /= recentDistances.size();

                    // Dùng giá trị trung bình thay vì distance_local
                    float smoothedDistance = avgDistance;
                    // ======================
                    if (developerMode) {
                        if (smoothedDistance < distance)
                            reco_name.setText("Nearest: " + name + "\nDist: " + String.format("%.3f", smoothedDistance) + "\n2nd Nearest: " + nearest.get(1).first + "\nDist: " + String.format("%.3f", nearest.get(1).second));
                        else
                            reco_name.setText("Unknown " + "\nDist: " + String.format("%.3f", smoothedDistance) + "\nNearest: " + name + "\nDist: " + String.format("%.3f", smoothedDistance) + "\n2nd Nearest: " + nearest.get(1).first + "\nDist: " + String.format("%.3f", nearest.get(1).second));

    //                    System.out.println("nearest: " + name + " - distance: " + distance_local);
                    } else {
                        if (smoothedDistance < distance)
                            reco_name.setText(name);
                        else
                            reco_name.setText("Unknown");
    //                    System.out.println("nearest: " + name + " - distance: " + distance_local);
                    }
                }
            }
        }
    }


    //Compare Faces by distance between face embeddings
//    private List<Pair<String, Float>> findNearest(float[] emb) {
//        List<Pair<String, Float>> neighbour_list = new ArrayList<Pair<String, Float>>();
//        Pair<String, Float> ret = null; //to get closest match
//        Pair<String, Float> prev_ret = null; //to get second closest match
//        for (Map.Entry<String, List<SimilarityClassifier.Recognition>> entry : registered.entrySet())
//        {
//            final String name = entry.getKey();
//            final float[] knownEmb = ((float[][]) entry.getValue().getExtra())[0];
//
//            float distance = 0;
//            for (int i = 0; i < emb.length; i++) {
//                float diff = emb[i] - knownEmb[i];
//                distance += diff*diff;
//            }
//            distance = (float) Math.sqrt(distance);
//            if (ret == null || distance < ret.second) {
//                prev_ret=ret;
//                ret = new Pair<>(name, distance);
//            }
//        }
//        if(prev_ret==null) prev_ret=ret;
//        neighbour_list.add(ret);
//        neighbour_list.add(prev_ret);
//
//        return neighbour_list;
//
//    }
    private List<Pair<String, Float>> findNearest(float[] emb) {
        List<Pair<String, Float>> neighbour_list = new ArrayList<>();
        Pair<String, Float> ret = null;      // closest match
        Pair<String, Float> prev_ret = null; // second closest match

        for (Map.Entry<String, List<SimilarityClassifier.Recognition>> entry : registered.entrySet()) {
            final String name = entry.getKey();
            List<SimilarityClassifier.Recognition> recList = entry.getValue();

            for (SimilarityClassifier.Recognition rec : recList) {
                float[][] knownEmbArray = (float[][]) rec.getExtra();
                if (knownEmbArray == null || knownEmbArray.length == 0) continue; // bỏ qua nếu null
                float[] knownEmb = knownEmbArray[0];
                float distance = 0f;
                for (int i = 0; i < emb.length; i++) {
                    float diff = emb[i] - knownEmb[i];
                    distance += diff * diff;
                }
                distance = (float) Math.sqrt(distance);

                if (ret == null || distance < ret.second) {
                    prev_ret = ret;
                    ret = new Pair<>(name, distance);
                }
            }
        }

        if (prev_ret == null) prev_ret = ret;
        neighbour_list.add(ret);
        neighbour_list.add(prev_ret);

        return neighbour_list;
    }

    public Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        // CREATE A MATRIX FOR THE MANIPULATION
        Matrix matrix = new Matrix();
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight);

        // "RECREATE" THE NEW BITMAP
        Bitmap resizedBitmap = Bitmap.createBitmap(
                bm, 0, 0, width, height, matrix, false);
        bm.recycle();
        return resizedBitmap;
    }
    private static Bitmap getCropBitmapByCPU(Bitmap source, RectF cropRectF) {
        Bitmap resultBitmap = Bitmap.createBitmap((int) cropRectF.width(),
                (int) cropRectF.height(), Bitmap.Config.ARGB_8888);
        Canvas cavas = new Canvas(resultBitmap);

        // draw background
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        paint.setColor(Color.WHITE);
        cavas.drawRect(
                new RectF(0, 0, cropRectF.width(), cropRectF.height()),
                paint);

        Matrix matrix = new Matrix();
        matrix.postTranslate(-cropRectF.left, -cropRectF.top);

        cavas.drawBitmap(source, matrix, paint);

        if (source != null && !source.isRecycled()) {
            source.recycle();
        }

        return resultBitmap;
    }

    private static Bitmap rotateBitmap(
            Bitmap bitmap, int rotationDegrees, boolean flipX, boolean flipY) {
        Matrix matrix = new Matrix();

        // Rotate the image back to straight.
        matrix.postRotate(rotationDegrees);

        // Mirror the image along the X or Y axis.
        matrix.postScale(flipX ? -1.0f : 1.0f, flipY ? -1.0f : 1.0f);
        Bitmap rotatedBitmap =
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        // Recycle the old bitmap if it has changed.
        if (rotatedBitmap != bitmap) {
            bitmap.recycle();
        }
        return rotatedBitmap;
    }

    //IMPORTANT. If conversion not done ,the toBitmap conversion does not work on some devices.
    private static byte[] YUV_420_888toNV21(Image image) {

        int width = image.getWidth();
        int height = image.getHeight();
        int ySize = width*height;
        int uvSize = width*height/4;

        byte[] nv21 = new byte[ySize + uvSize*2];

        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer(); // Y
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer(); // U
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer(); // V

        int rowStride = image.getPlanes()[0].getRowStride();
        assert(image.getPlanes()[0].getPixelStride() == 1);

        int pos = 0;

        if (rowStride == width) { // likely
            yBuffer.get(nv21, 0, ySize);
            pos += ySize;
        }
        else {
            long yBufferPos = -rowStride; // not an actual position
            for (; pos<ySize; pos+=width) {
                yBufferPos += rowStride;
                yBuffer.position((int) yBufferPos);
                yBuffer.get(nv21, pos, width);
            }
        }

        rowStride = image.getPlanes()[2].getRowStride();
        int pixelStride = image.getPlanes()[2].getPixelStride();

        assert(rowStride == image.getPlanes()[1].getRowStride());
        assert(pixelStride == image.getPlanes()[1].getPixelStride());

        if (pixelStride == 2 && rowStride == width && uBuffer.get(0) == vBuffer.get(1)) {
            // maybe V an U planes overlap as per NV21, which means vBuffer[1] is alias of uBuffer[0]
            byte savePixel = vBuffer.get(1);
            try {
                vBuffer.put(1, (byte)~savePixel);
                if (uBuffer.get(0) == (byte)~savePixel) {
                    vBuffer.put(1, savePixel);
                    vBuffer.position(0);
                    uBuffer.position(0);
                    vBuffer.get(nv21, ySize, 1);
                    uBuffer.get(nv21, ySize + 1, uBuffer.remaining());

                    return nv21; // shortcut
                }
            }
            catch (ReadOnlyBufferException ex) {
                // unfortunately, we cannot check if vBuffer and uBuffer overlap
            }

            // unfortunately, the check failed. We must save U and V pixel by pixel
            vBuffer.put(1, savePixel);
        }

        // other optimizations could check if (pixelStride == 1) or (pixelStride == 2),
        // but performance gain would be less significant

        for (int row=0; row<height/2; row++) {
            for (int col=0; col<width/2; col++) {
                int vuPos = col*pixelStride + row*rowStride;
                nv21[pos++] = vBuffer.get(vuPos);
                nv21[pos++] = uBuffer.get(vuPos);
            }
        }

        return nv21;
    }

    private Bitmap toBitmap(Image image) {

        byte[] nv21=YUV_420_888toNV21(image);


        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 75, out);

        byte[] imageBytes = out.toByteArray();
        //System.out.println("bytes"+ Arrays.toString(imageBytes));

        //System.out.println("FORMAT"+image.getFormat());

        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

//    //Save Faces to Shared Preferences.Conversion of Recognition objects to json string
//    private void insertToSP(HashMap<String, SimilarityClassifier.Recognition> jsonMap,int mode) {
//        if(mode==1)  //mode: 0:save all, 1:clear all, 2:update all
//            jsonMap.clear();
//        else if (mode==0)
//            jsonMap.putAll(readFromSP());
//        String jsonString = new Gson().toJson(jsonMap);
////        for (Map.Entry<String, SimilarityClassifier.Recognition> entry : jsonMap.entrySet())
////        {
////            System.out.println("Entry Input "+entry.getKey()+" "+  entry.getValue().getExtra());
////        }
//        SharedPreferences sharedPreferences = getSharedPreferences("HashMap", MODE_PRIVATE);
//        SharedPreferences.Editor editor = sharedPreferences.edit();
//        editor.putString("map", jsonString);
//        //System.out.println("Input josn"+jsonString.toString());
//        editor.apply();
////        runOnUiThread(() ->
////                Toast.makeText(context, "Recognitions Saved", Toast.LENGTH_SHORT).show()
////        );
//    }
    private void insertToSP(
            HashMap<String, List<SimilarityClassifier.Recognition>> jsonMap, // toàn bộ map hiện tại
            String name, // tên hoặc id của người
            SimilarityClassifier.Recognition rec, // embedding mới muốn lưu
            int mode // 0: save all, 1: clear all, 2: update all
    ) {
        if (mode == 1) {
            jsonMap.clear();
        } else if (mode == 0) {
            jsonMap.putAll(readFromSP()); // đọc map cũ từ SharedPreferences
        }

        // Lấy danh sách embeddings hiện tại của người này, nếu chưa có thì tạo mới
        List<SimilarityClassifier.Recognition> list = jsonMap.getOrDefault(name, new ArrayList<>());
        list.add(rec); // thêm embedding mới
        jsonMap.put(name, list); // cập nhật lại map

        // Chuyển map thành JSON và lưu vào SharedPreferences
        String jsonString = new Gson().toJson(jsonMap);
        SharedPreferences sharedPreferences = getSharedPreferences("HashMap", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("map", jsonString);
        editor.apply();
    }




    //Load Faces from Shared Preferences.Json String to Recognition object
//    private HashMap<String, SimilarityClassifier.Recognition> readFromSP(){
//        SharedPreferences sharedPreferences = getSharedPreferences("HashMap", MODE_PRIVATE);
//        String defValue = new Gson().toJson(new HashMap<String, SimilarityClassifier.Recognition>());
//        String json=sharedPreferences.getString("map",defValue);
//
//        TypeToken<HashMap<String,SimilarityClassifier.Recognition>> token = new TypeToken<HashMap<String,SimilarityClassifier.Recognition>>() {};
//        HashMap<String,SimilarityClassifier.Recognition> retrievedMap=new Gson().fromJson(json,token.getType());
//
//        //So embeddings need to be extracted from it in required format(eg.double to float).
//        for (Map.Entry<String, SimilarityClassifier.Recognition> entry : retrievedMap.entrySet())
//        {
//            float[][] output=new float[1][OUTPUT_SIZE];
//            ArrayList arrayList= (ArrayList) entry.getValue().getExtra();
//            arrayList = (ArrayList) arrayList.get(0);
//            for (int counter = 0; counter < arrayList.size(); counter++) {
//                output[0][counter]= ((Double) arrayList.get(counter)).floatValue();
//            }
//            entry.getValue().setExtra(output);
//        }
//        return retrievedMap;
//    }
    private HashMap<String, List<SimilarityClassifier.Recognition>> readFromSP() {
        SharedPreferences sharedPreferences = getSharedPreferences("HashMap", MODE_PRIVATE);
        // Xóa map cũ
//        SharedPreferences.Editor editor = sharedPreferences.edit();
//        editor.remove("map");
//        editor.apply();
        // Mặc định là map rỗng với List<Recognition>
        String defValue = new Gson().toJson(new HashMap<String, List<SimilarityClassifier.Recognition>>());
        String json = sharedPreferences.getString("map", defValue);

        TypeToken<HashMap<String, List<SimilarityClassifier.Recognition>>> token =
                new TypeToken<HashMap<String, List<SimilarityClassifier.Recognition>>>() {};
        HashMap<String, List<SimilarityClassifier.Recognition>> retrievedMap =
                new Gson().fromJson(json, token.getType());

        // Chuyển đổi extra từ ArrayList sang float[][] cho từng Recognition
        for (Map.Entry<String, List<SimilarityClassifier.Recognition>> entry : retrievedMap.entrySet()) {
            List<SimilarityClassifier.Recognition> list = entry.getValue();
            for (SimilarityClassifier.Recognition rec : list) {
                Object extra = rec.getExtra();
                if (extra == null) {
                    // nếu null, khởi tạo mảng rỗng để tránh crash
                    rec.setExtra(new float[1][OUTPUT_SIZE]);
                    continue;
                }

                // nếu extra vẫn là ArrayList (từ JSON mới lưu)
                if (extra instanceof ArrayList) {
                    ArrayList arrayList = (ArrayList) extra;
                    if (arrayList.size() == 0 || arrayList.get(0) == null) {
                        rec.setExtra(new float[1][OUTPUT_SIZE]);
                        continue;
                    }

                    ArrayList firstList = (ArrayList) arrayList.get(0);
                    float[][] output = new float[1][firstList.size()];
                    for (int i = 0; i < firstList.size(); i++) {
                        output[0][i] = ((Double) firstList.get(i)).floatValue();
                    }
                    rec.setExtra(output);
                }
            }
        }

        return retrievedMap;
    }

    //Similar Analyzing Procedure
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == SELECT_PICTURE) {
                Uri selectedImageUri = data.getData();
                try {
                    InputImage impphoto=InputImage.fromBitmap(getBitmapFromUri(selectedImageUri),0);
                    detector.process(impphoto).addOnSuccessListener(new OnSuccessListener<List<Face>>() {
                        @Override
                        public void onSuccess(List<Face> faces) {

                            if(faces.size()!=0) {
                                recognize.setText("Recognize");
                                add_face.setVisibility(View.VISIBLE);
                                reco_name.setVisibility(View.INVISIBLE);
                                face_preview.setVisibility(View.VISIBLE);
                                preview_info.setText("1.Bring Face in view of Camera.\n\n2.Your Face preview will appear here.\n\n3.Click Add button to save face.");
                                Face face = faces.get(0);
//                                System.out.println(face);

                                //write code to recreate bitmap from source
                                //Write code to show bitmap to canvas

                                Bitmap frame_bmp= null;
                                try {
                                    frame_bmp = getBitmapFromUri(selectedImageUri);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                Bitmap frame_bmp1 = rotateBitmap(frame_bmp, 0, flipX, false);

                                //face_preview.setImageBitmap(frame_bmp1);

                                RectF boundingBox = new RectF(face.getBoundingBox());

                                Bitmap cropped_face = getCropBitmapByCPU(frame_bmp1, boundingBox);

                                Bitmap scaled = getResizedBitmap(cropped_face, 112, 112);
                                // face_preview.setImageBitmap(scaled);

                                    recognizeImage(scaled);
                                    addFace();
//                                System.out.println(boundingBox);
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            start=true;
                            Toast.makeText(context, "Failed to add", Toast.LENGTH_SHORT).show();
                        }
                    });
                    face_preview.setImageBitmap(getBitmapFromUri(selectedImageUri));
                } catch (IOException e) {
                    e.printStackTrace();
                }


            }
        }
    }

    private Bitmap getBitmapFromUri(Uri uri) throws IOException {
        ParcelFileDescriptor parcelFileDescriptor =
                getContentResolver().openFileDescriptor(uri, "r");
        FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
        Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);
        parcelFileDescriptor.close();
        return image;
    }

    private float[] extractEmbedding(Bitmap bitmap) {
        // Chuẩn bị bộ nhớ cho input
        ByteBuffer imgData = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4);
        imgData.order(ByteOrder.nativeOrder());

        int[] intValues = new int[inputSize * inputSize];
        try {
            bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        } catch (Exception e) {
            Log.e("extractEmbedding", "getPixels() lỗi: " + e.getMessage());
            return null;
        }
//        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0,
//                bitmap.getWidth(), bitmap.getHeight());
        imgData.rewind();

        // Chuẩn hóa ảnh (normalize)
        for (int i = 0; i < inputSize; ++i) {
            for (int j = 0; j < inputSize; ++j) {
                int pixelValue = intValues[i * inputSize + j];
                if (isModelQuantized) {
                    // Model dùng int8
                    imgData.put((byte) ((pixelValue >> 16) & 0xFF));
                    imgData.put((byte) ((pixelValue >> 8) & 0xFF));
                    imgData.put((byte) (pixelValue & 0xFF));
                } else {
                    // Model float32
                    imgData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                }
            }
        }

        // Input và output cho model
        Object[] inputArray = {imgData};
        Map<Integer, Object> outputMap = new HashMap<>();

        float[][] embeddings = new float[1][OUTPUT_SIZE];
        outputMap.put(0, embeddings);

        // Chạy model TensorFlow Lite
        tfLite.runForMultipleInputsOutputs(inputArray, outputMap);

        // embeddings[0] là vector đặc trưng của khuôn mặt
        return embeddings[0];
    }


}

