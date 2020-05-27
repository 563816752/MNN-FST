package com.taobao.android.mnndemo;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.taobao.android.mnn.MNNForwardType;
import com.taobao.android.mnn.MNNImageProcess;
import com.taobao.android.mnn.MNNNetInstance;
import com.taobao.android.utils.Common;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ImageActivity extends AppCompatActivity implements View.OnClickListener {

    private final String TargetPic = "chicago.jpg";




    private ImageView mImageView;
    private TextView mTextView0;
    private TextView mTextView1;
    private TextView mTextView2;
    private TextView mTextView3;
    private TextView mResultText;
    private TextView mTimeText;
    private RadioGroup mRadioGroup;
    private Bitmap mBitmap;
    private Bitmap oBitmap;
    private Boolean saveimgflag;
    private static final int IMAGE_REQUEST_CODE = 2;
    private MNNNetInstance mNetInstance;
    private MNNNetInstance.Session mSession;
    private MNNNetInstance.Session.Tensor mInputTensor;

    private final int InputWidth = 400;
    private final int InputHeight = 400;

    private int InitInputWidth;
    private int InitInputHeight;

    private class NetPrepareTask extends AsyncTask<String, Void, String> {
        protected String doInBackground(String... tasks) {
            prepareMobileNet("la.mnn");
            return "success";
        }

        protected void onPostExecute(String result) {
            oBitmap=null;
            saveimgflag = false;
            mTextView0.setText("开始图像风格迁移");
            mTextView0.setClickable(true);
        }
    }

    private class ImageProcessResult {
        public String result;
        public float inferenceTimeCost;
        public Bitmap bitmap;
    }

    private class ImageProcessTask extends AsyncTask<String, Void, ImageProcessResult> {

        protected ImageProcessResult doInBackground(String... tasks) {
            //图像转Tensor
            for (int i = 0; i < mRadioGroup.getChildCount(); i++) {
                mRadioGroup.getChildAt(i).setEnabled(false);
            }
            mTextView0.setClickable(false);
            mTextView1.setClickable(false);
            mTextView2.setClickable(false);
            mTextView3.setClickable(false);
            final MNNImageProcess.Config config = new MNNImageProcess.Config();
            config.mean = new float[]{103.94f, 116.78f, 123.68f};
            config.normal = new float[]{0.017f, 0.017f, 0.017f};
            config.dest = MNNImageProcess.Format.RGB;
            Matrix matrix = new Matrix();
            InitInputWidth = mBitmap.getWidth();
            InitInputHeight = mBitmap.getHeight();
            matrix.postScale(InputWidth / (float) mBitmap.getWidth(), InputHeight / (float) mBitmap.getHeight());
            matrix.invert(matrix);

            MNNImageProcess.convertBitmap(mBitmap, mInputTensor, config, matrix);

            final long startTimestamp = System.nanoTime();
            //推断
            mSession.run();
            MNNNetInstance.Session.Tensor output = mSession.getOutput(null);
            final long endTimestamp = System.nanoTime();
            float[] result = output.getFloatData();// get float results

            int[] bytes = new int[InputWidth*InputHeight];
            for (int i = 0; i < InputWidth*InputHeight; i++) {
                int R =  float2int(result[i*3]);
                int G =  float2int(result[i*3+1]);
                int B =  float2int(result[i*3+2]);
                bytes[i] = (255 << 24) | (R << 16) | (G << 8 )| B;

            }
            //Log.i(Common.TAG, "byte="+ bytes.length);
            Bitmap bitmap = Bitmap.createBitmap(bytes, InputWidth, InputHeight, Bitmap.Config.ARGB_8888);

            oBitmap =getBitmap(bitmap,InitInputWidth,InitInputHeight);

            saveimgflag = false;

            final float inferenceTimeCost = (endTimestamp - startTimestamp) / 1000000.0f;
            final ImageProcessResult processResult = new ImageProcessResult();

            processResult.inferenceTimeCost = inferenceTimeCost;
            processResult.bitmap = oBitmap;
            return processResult;
        }

        protected void onPostExecute(ImageProcessResult result) {
            for (int i = 0; i < mRadioGroup.getChildCount(); i++) {
                mRadioGroup.getChildAt(i).setEnabled(true);
            }
            mTextView0.setClickable(true);
            mTextView1.setClickable(true);
            mTextView2.setClickable(true);
            mTextView3.setClickable(true);
            mImageView.setImageBitmap(result.bitmap);
            mResultText.setText(result.result);
            mTimeText.setText("cost time：" + result.inferenceTimeCost/1000 + "s");
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image);
        //申请权限
        if (Build.VERSION.SDK_INT >= 23) {
            int REQUEST_CODE_CONTACT = 101;
            String[] permissions = {Manifest.permission.WRITE_EXTERNAL_STORAGE};
            for (String str : permissions) {
                if (this.checkSelfPermission(str) != PackageManager.PERMISSION_GRANTED) {
                    this.requestPermissions(permissions, REQUEST_CODE_CONTACT);
                }
            }
        }

        mImageView = findViewById(R.id.imageView);//图像展示
        mTextView0 = findViewById(R.id.textView0);//启动推理
        mTextView1 = findViewById(R.id.textView1);//选择图像
        mTextView2 = findViewById(R.id.textView2);//保存图像
        mTextView3 = findViewById(R.id.textView3);//重回图像
        mResultText = findViewById(R.id.editText);
        mTimeText = findViewById(R.id.timeText);
        mRadioGroup = findViewById(R.id.radioGroup1);

        mTextView0.setOnClickListener(new View.OnClickListener(){
        @Override
        public void onClick(View view) {
            if (mBitmap == null) {
                return;
            }



            mResultText.setText("inference result ...");
            ImageProcessTask imageProcessTask = new ImageProcessTask();
            imageProcessTask.execute("");

        }
        });



        mTextView1.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {

                Intent intent = new Intent(
                        Intent.ACTION_PICK,
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(intent, IMAGE_REQUEST_CODE);

            }
        });

        mTextView2.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                DateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");
                if (oBitmap ==null){
                    Toast toast=Toast.makeText(getApplicationContext(),"当前没有风格迁移后图像", Toast.LENGTH_LONG);
                    toast.show();
                }
                else {
                    if (!saveimgflag){
                        String outputname = saveBmp2Gallery(getBaseContext(), oBitmap, format.format(new Date()));
                        Toast toast = Toast.makeText(getApplicationContext(), "已保存至"+outputname, Toast.LENGTH_SHORT);
                        toast.show();
                        saveimgflag = true;
                    }
                    else{
                        Toast toast = Toast.makeText(getApplicationContext(), "本图像已被保存过，请查看相册或重新生成", Toast.LENGTH_SHORT);
                        toast.show();
                    }

                }
            }
        });

        mTextView3.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                if (mBitmap == null) {
                    return;
                }
                mImageView.setImageBitmap(mBitmap);
                oBitmap =null;
            }
        });

        //切换模型
        mRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                switch (checkedId){
                    case R.id.radio_la:
                        mTextView0.setClickable(false);
                        prepareMobileNet("la.mnn");
                        mTextView0.setClickable(true);
                        break;
                    case R.id.radio_rain:
                        mTextView0.setClickable(false);
                        prepareMobileNet("rain.mnn");
                        mTextView0.setClickable(true);
                        break;
                    case R.id.radio_scream:
                        mTextView0.setClickable(false);
                        prepareMobileNet("scream.mnn");
                        mTextView0.setClickable(true);
                        break;
                    case R.id.radio_udnie:
                        mTextView0.setClickable(false);
                        prepareMobileNet("udnie.mnn");
                        mTextView0.setClickable(true);
                        break;
                    case R.id.radio_wave:
                        mTextView0.setClickable(false);
                        prepareMobileNet("wave.mnn");
                        mTextView0.setClickable(true);
                        break;
                    default:
                        break;
                }
            }
        });



        mTextView0.setOnClickListener(this);
        // show image
        AssetManager am = getAssets();
        try {
            final InputStream picStream = am.open(TargetPic);
            mBitmap = BitmapFactory.decodeStream(picStream);
            picStream.close();
            mImageView.setImageBitmap(mBitmap);
        } catch (Throwable t) {
            t.printStackTrace();
        }



        mTextView0.setClickable(false);
        final NetPrepareTask prepareTask = new NetPrepareTask();
        prepareTask.execute("");
    }





    //载入模型
    private void prepareMobileNet(String modelname) {

        String modelPath = getCacheDir() + "XXX.mnn";

        try {
            Common.copyAssetResource2File(getBaseContext(), modelname, modelPath);
        } catch (Throwable t) {
            t.printStackTrace();
        }

        mNetInstance = MNNNetInstance.createFromFile(modelPath);
        MNNNetInstance.Config config = new MNNNetInstance.Config();
        config.numThread = 4;// set threads
        config.forwardType = MNNForwardType.FORWARD_OPENCL.type;// set CPU/GPU
        mSession = mNetInstance.createSession(config);
        mInputTensor = mSession.getInput(null);
    }



    @Override
    public void onClick(View view) {
        if (mBitmap == null) {
            return;
        }

        mResultText.setText("inference result ...");
        ImageProcessTask imageProcessTask = new ImageProcessTask();
        imageProcessTask.execute("");

    }


    @Override
    protected void onDestroy() {
        if (mNetInstance != null) {
            mNetInstance.release();
            mNetInstance = null;
        }

        super.onDestroy();
    }

    //存图
    public static String saveBmp2Gallery(Context context, Bitmap bmp, String picName) {


        //系统相册目录
        String galleryPath= Environment.getExternalStorageDirectory()
                + File.separator + Environment.DIRECTORY_DCIM
                +File.separator+"Camera"+File.separator;
        File file = null;
        file = new File(galleryPath, picName+ ".png");
        try {
            FileOutputStream fos = new FileOutputStream(file);
            bmp.compress(Bitmap.CompressFormat.PNG, 50, fos);
            fos.flush();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(new  File(file.getPath()))));
        return galleryPath+picName+ ".png";
    }

    //输出限制
    private int float2int(float input){
        int a = (int)input;
        if (a>255){
            a =255;
        }
        if (a<0){
            a = 0;
        }
        return a;
    }

    //从相册选择resize
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);
        if (Build.VERSION.SDK_INT >= 23) {
            int REQUEST_CODE_CONTACT = 101;
            String[] permissions = {Manifest.permission.WRITE_EXTERNAL_STORAGE};
            //验证是否许可权限
            for (String str : permissions) {
                if (this.checkSelfPermission(str) != PackageManager.PERMISSION_GRANTED) {
                    //申请权限
                    this.requestPermissions(permissions, REQUEST_CODE_CONTACT);
                }
            }
        }
        //在相册里面选择好相片之后调回到现在的这个activity中
        switch (requestCode) {
            case IMAGE_REQUEST_CODE://这里的requestCode是我自己设置的，就是确定返回到那个Activity的标志
                if (resultCode == RESULT_OK) {//resultcode是setResult里面设置的code值
                    try {
                        Uri selectedImage = data.getData(); //获取系统返回的照片的Uri
                        String[] filePathColumn = {MediaStore.Images.Media.DATA};
                        Cursor cursor = getContentResolver().query(selectedImage,
                                filePathColumn, null, null, null);//从系统表中查询指定Uri对应的照片
                        cursor.moveToFirst();
                        int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                        String path = cursor.getString(columnIndex);  //获取照片路径
                        Log.i(Common.TAG, "Path="+ path);
                        cursor.close();
                        mBitmap = BitmapFactory.decodeFile(path);
                        Log.i(Common.TAG,"Size = "+Integer.toString(mBitmap.getHeight())+Integer.toString(mBitmap.getWidth()));
                        mImageView.setImageBitmap(mBitmap);
                    } catch (Exception e) {
                        // TODO Auto-generatedcatch block
                        e.printStackTrace();
                    }
                }
                break;
        }
    }

    //图像resize
    public static Bitmap getBitmap(Bitmap bitmap, int screenWidth,
                                   int screenHight)
    {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        Matrix matrix = new Matrix();
        float scale = (float) screenWidth / w;
        float scale2 = (float) screenHight / h;
        // scale = scale < scale2 ? scale : scale2;
        matrix.postScale(scale, scale2);
        Bitmap bmp = Bitmap.createBitmap(bitmap, 0, 0, w, h, matrix, true);
        if (bitmap != null && !bitmap.equals(bmp) && !bitmap.isRecycled())
        {
            bitmap.recycle();
            bitmap = null;
        }
        return bmp;// Bitmap.createBitmap(bitmap, 0, 0, w, h, matrix, true);
    }

}
