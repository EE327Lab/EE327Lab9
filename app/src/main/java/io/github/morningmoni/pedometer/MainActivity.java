package io.github.morningmoni.pedometer;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.DecimalFormat;
import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
public class MainActivity extends Activity implements SensorEventListener,
        OnClickListener {
    /** Called when the activity is first created. */
//Create a LOG label
    private Button mWriteButton, mStopButton;
    private boolean doWrite = false;
    private SensorManager sm;
    private float lowX = 0, lowY = 0, lowZ = 0;
    private final float FILTERING_VALAUE = 0.1f;
    private TextView AT,ACT, STEP;
    private double[] var = new double[3];
    int ct = 0;
    int steps = 0;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        AT = (TextView)findViewById(R.id.AT);
        ACT = (TextView)findViewById(R.id.onAccuracyChanged);
        STEP = (TextView)findViewById(R.id.STEP);
//Create a SensorManager to get the system’s sensor service
        sm =
                (SensorManager)getSystemService(Context.SENSOR_SERVICE);
/*
*Using the most common method to register an event
* Parameter1 ：SensorEventListener detectophone
* Parameter2 ：Sensor one service could have several Sensor
realizations.Here,We use getDefaultSensor to get the defaulted Sensor
* Parameter3 ：Mode We can choose the refresh frequency of the
data change
* */
// Register the acceleration sensor
        sm.registerListener(this,
                sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_FASTEST);//High sampling rate；.SENSOR_DELAY_NORMAL
                // means a lower sampling rate
        try {
            FileOutputStream fout = openFileOutput("acc.txt",
                    Context.MODE_PRIVATE);
            fout.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mWriteButton = (Button) findViewById(R.id.Button_Write);
        mWriteButton.setOnClickListener(this);
        mStopButton = (Button) findViewById(R.id.Button_Stop);
        mStopButton.setOnClickListener(this);
    }
    public void onPause(){
        super.onPause();
    }
    public void onClick(View v) {
        if (v.getId() == R.id.Button_Write) {
            doWrite = true;
        }
        if (v.getId() == R.id.Button_Stop) {
            doWrite = false;
        }
    }
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        ACT.setText("onAccuracyChanged is detonated");
    }

    private final static String TAG = "StepDetector";
    private float   mLimit = 10;
    private float   mLastValues[] = new float[3*2];
    private float   mScale[] = new float[2];
    private float   mYOffset;

    private float   mLastDirections[] = new float[3*2];
    private float   mLastExtremes[][] = { new float[3*2], new float[3*2] };
    private float   mLastDiff[] = new float[3*2];
    private int     mLastMatch = -1;
    public MainActivity(){
        int h = 480; // TODO: remove this constant
        mYOffset = h * 0.5f;
        mScale[0] = - (h * 0.5f * (1.0f / (SensorManager.STANDARD_GRAVITY * 2)));
        mScale[1] = - (h * 0.5f * (1.0f / (SensorManager.MAGNETIC_FIELD_EARTH_MAX)));
    }

    public void onSensorChanged(SensorEvent event) {
        String message = new String();
        if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float X = event.values[0];
            float Y = event.values[1];
            float Z = event.values[2];
//Low-Pass Filter
            lowX = X * FILTERING_VALAUE + lowX * (1.0f -
                    FILTERING_VALAUE);
            lowY = Y * FILTERING_VALAUE + lowY * (1.0f -
                    FILTERING_VALAUE);
            lowZ = Z * FILTERING_VALAUE + lowZ * (1.0f -
                    FILTERING_VALAUE);
//High-pass filter
            float highX = X - lowX;
            float highY = Y - lowY;
            float highZ = Z - lowZ;
            double highA = Math.sqrt(highX * highX + highY * highY + highZ
                    * highZ);
            DecimalFormat df = new DecimalFormat("#,##0.000");
            message = df.format(highX) + " ";
            message += df.format(highY) + " ";
            message += df.format(highZ) + " ";
            message += df.format(highA) + "\n";
            AT.setText(message + "\n");

//            var[0] = var[1];
//            var[1] = var[2];
//            var[2] = highA;
//            if(var[1] > 8.5 && var[1] >= var[0] && var[1] >= var[2]) {
//                steps += 1;
//                message += df.format(var[0]) + "\n";
//                message += df.format(var[1]) + "\n";
//                message += df.format(var[2]) + "\n";
//            }
            float vSum = 0;
            for (int i=0 ; i<3 ; i++) {
                final float v = mYOffset + event.values[i] * mScale[1];
                vSum += v;
            }
            int k = 0;
            float v = vSum / 3;

            float direction = (v > mLastValues[k] ? 1 : (v < mLastValues[k] ? -1 : 0));
            if (direction == - mLastDirections[k]) {
                // Direction changed
                int extType = (direction > 0 ? 0 : 1); // minumum or maximum?
                mLastExtremes[extType][k] = mLastValues[k];
                float diff = Math.abs(mLastExtremes[extType][k] - mLastExtremes[1 - extType][k]);

                if (diff > mLimit) {

                    boolean isAlmostAsLargeAsPrevious = diff > (mLastDiff[k]*2/3);
                    boolean isPreviousLargeEnough = mLastDiff[k] > (diff/3);
                    boolean isNotContra = (mLastMatch != 1 - extType);

                    if (isAlmostAsLargeAsPrevious && isPreviousLargeEnough && isNotContra) {
                        steps += 1;
                        mLastMatch = extType;
                    }
                    else {
                        mLastMatch = -1;
                    }
                }
                mLastDiff[k] = diff;
            }
            mLastDirections[k] = direction;
            mLastValues[k] = v;
            Log.e("msg", message);
            STEP.setText(steps + "");
            if (doWrite) {
                write2file(message);
            }
        }
    }
    private void write2file(String a){
        try {
            File file = new File("/sdcard/acc.txt");//write the result into/sdcard/acc.txt
            if (!file.exists()){
                file.createNewFile();}
// Open a random access file stream for reading and writing
            RandomAccessFile randomFile = new
                    RandomAccessFile("/sdcard/acc.txt", "rw");
// The length of the file (the number of bytes)
            long fileLength = randomFile.length();
// Move the file pointer to the end of the file
            randomFile.seek(fileLength);
            randomFile.writeBytes(a);
            randomFile.close();
        } catch (IOException e) {
// TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
