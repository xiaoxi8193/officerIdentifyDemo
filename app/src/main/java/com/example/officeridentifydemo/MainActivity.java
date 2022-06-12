package com.example.officeridentifydemo;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.huawei.hiai.pdk.resultcode.HwHiAIResultCode; // 加载引擎状态类
import com.huawei.hiai.vision.common.ConnectionCallback; // 加载连接服务的回调函数
import com.huawei.hiai.vision.common.VisionBase;  // 加载连接服务的静态类
import com.huawei.hiai.vision.common.VisionImage; // 加载VisionImage类
import com.huawei.hiai.vision.text.TemplateDetector; // 加载卡证检测类
import com.huawei.hiai.vision.visionkit.text.config.VisionCardConfiguration;// 加载配置类
import com.huawei.hiai.vision.visionkit.text.templateocr.TemplateData; // 加载返回结果类
import com.huawei.hiai.vision.visionkit.text.templateocr.TemplateText;// 加载返回结果类中的成员变量类

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String IMAGE_CARD = "ImageCard";

    private static final String TAG = "OCR_Detect";

    private ImageView mImageView;

    private TextView mTextViewResult;

    private Uri fileUri;

    private Bitmap mBitmap;

    private ProgressDialog dialog;

    private TemplateDetector mTemplateDetector;

    private static final int REQUEST_IMAGE_TAKE = 100;

    private static final int REQUEST_IMAGE_SELECT = 200;

    private static final int REQUEST_PERMISSION_CODE = 300;

    private static final String TEMPLATE_JSON_PATH = "officer.json";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        requestPermissions();
        visionInit();
    }
    private void initView() {
        mImageView = findViewById(R.id.imgViewPicture);
        mTextViewResult = findViewById(R.id.result_table);
        mTextViewResult.setMovementMethod(ScrollingMovementMethod.getInstance());
    }

    private void visionInit() {
        VisionBase.init(this, new ConnectionCallback() {
            @Override
            public void onServiceConnect() {
                Log.e(TAG, " onServiceConnect");
            }

            @Override
            public void onServiceDisconnect() {
                Log.e(TAG, " onServiceDisconnect");
            }
        });
    }

    /**
     * 点击事件
     * @param view 对应的view
     */
    public void onChildClick(View view) {
        switch (view.getId()) {
            case R.id.btnSelect:
                Intent intentSelect = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(intentSelect, REQUEST_IMAGE_SELECT);
                break;
            case R.id.btnTake:
                fileUri = getOutputMediaFileUri(IMAGE_CARD);
                Intent intentTake = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                intentTake.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
                startActivityForResult(intentTake, REQUEST_IMAGE_TAKE);
                break;
            default:
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if ((requestCode == REQUEST_IMAGE_TAKE || requestCode == REQUEST_IMAGE_SELECT) && resultCode == RESULT_OK) {
            String imgPath;
            if (requestCode == REQUEST_IMAGE_TAKE) {
                imgPath = Environment.getExternalStorageDirectory() + fileUri.getPath();
            } else {
                imgPath = getImagePathForSelectImage(data);
            }
            mBitmap = BitmapFactory.decodeFile(imgPath);
            if (mBitmap == null) {
                Toast.makeText(this, "获取图片错误,为null", Toast.LENGTH_SHORT).show();
                return;
            }
            mImageView.setImageBitmap(mBitmap);
            showDialog();
            WeakReference<MainActivity> reference = new WeakReference<>(MainActivity.this);
            new OfficerCardDetectTask(reference).execute(mBitmap);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * 军官证识别异步处理
     */
    private class OfficerCardDetectTask extends AsyncTask<Bitmap, String, TemplateText> {
        private WeakReference<MainActivity> reference;

        private OfficerCardDetectTask(WeakReference<MainActivity> activityWeakReference) {
            reference = activityWeakReference;
        }

        @Override
        protected TemplateText doInBackground(Bitmap... bitmap) {
            final MainActivity activity = reference.get();

            VisionCardConfiguration visionCardConfiguration  = new VisionCardConfiguration.Builder()
                    .setAppType(VisionCardConfiguration.APP_NORMAL)
                    .setProcessMode(VisionCardConfiguration.MODE_IN)
                    .setCardType(VisionCardConfiguration.TEMPLATE).build();
            activity.mTemplateDetector = new TemplateDetector(activity.getApplicationContext());
            activity.mTemplateDetector.setVisionConfiguration(visionCardConfiguration );
            activity.mTemplateDetector.prepare();

            // read template json
            JSONObject templateJson = null;
            try {
                templateJson = new JSONObject(readJsonFile(TEMPLATE_JSON_PATH));
            } catch (JSONException err) {
                Log.d("trans json error", err.toString());
            }
            if (templateJson == null) {
                Log.e(TAG, "templateJson is null");
            }

            VisionImage image = VisionImage.fromBitmap(bitmap[0]);
            TemplateText templateText = new TemplateText();
            int mResultCode = activity.mTemplateDetector.detectTemplate(image,templateJson, templateText, null);
            return templateText;
        }

        private String readJsonFile(String fileName) {
            try {
                InputStream is = getApplicationContext().getAssets().open(fileName);
                int size = is.available();
                byte[] buffer = new byte[size];
                is.read(buffer);
                is.close();
                return new String(buffer, StandardCharsets.UTF_8);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(TemplateText templateText) {
            MainActivity activity = reference.get();
            activity.dismissDialog();
            if (templateText == null) {
                activity.mTextViewResult.setText("Failed to detect text lines, result is null.");
                return;
            }
            List<TemplateData> templateDataList = templateText.getTemplateData();
            if (templateDataList != null) {
                mTextViewResult.append("The key value pairs of identification results are as follows:\n");
                int index = 0;
                for (TemplateData element : templateDataList) {
                    mTextViewResult.append("recognition area" + index + "\n");
                    mTextViewResult.append("Key:" + element.getWordKey() + "\n");
                    mTextViewResult.append("Value:" + element.getWordValue() + "\n");
                    mTextViewResult.append("\n");
                    index++;
                }
            }
        }
    }

    private void showDialog() {
        if (dialog == null) {
            dialog = new ProgressDialog(MainActivity.this);
            dialog.setTitle("Predicting...");
            dialog.setMessage("Wait for one sec...");
            dialog.setIndeterminate(true);
        }
        dialog.show();
    }

    private void dismissDialog() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    /**
     * 生成拍摄照片保存的uri
     * @param typeName 子路径名
     * @return uri
     */
    protected Uri getOutputMediaFileUri(String typeName) {
        return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", getOutputMediaFile(typeName));
    }

    /**
     * 生成拍摄保存的文件
     * @param typeName 子路径名
     * @return file
     */
    private static File getOutputMediaFile(String typeName) {
        File mediaStorageDir =
                new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), typeName);
        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d(TAG, "failed to create directory");
                return null;
            }
        }
        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile = new File(mediaStorageDir.getPath() + File.separator + "IMG_" + timeStamp + ".jpg");
        return mediaFile;
    }

    /**
     * 根据返回的Intent获取图片的路径
     * @param data Intent
     * @return 图片路径
     */
    protected String getImagePathForSelectImage(Intent data) {
        Uri selectedImage = data.getData();
        if (selectedImage == null) {
            Log.e(TAG, "getImagePathForSelectImage error selectedImage is null");
            return null;
        }
        String[] filePathColumn = {MediaStore.Images.Media.DATA};
        Cursor cursor = this.getContentResolver().query(selectedImage, filePathColumn, null, null, null);
        if (cursor == null) {
            Log.e(TAG, "getImagePathForSelectImage error cursor is null");
            return null;
        }
        cursor.moveToFirst();
        int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
        String imgPath = cursor.getString(columnIndex);
        cursor.close();
        return imgPath;
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int permission1 = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            int permission2 = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
            if (permission1 != PackageManager.PERMISSION_GRANTED || permission2 != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.CAMERA},
                        REQUEST_PERMISSION_CODE);
            }
        }
    }
}