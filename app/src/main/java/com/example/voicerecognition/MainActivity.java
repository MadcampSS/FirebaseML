package com.example.voicerecognition;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.FaceDetector;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class MainActivity extends Activity implements RejectedExecutionHandler, RecognitionListener {
    final int PERMISSION_READ_EXTERNAL_STORAGE = 111;

    ArrayList<String> imgPaths;
    FaceDetectionActivity faceDetectionActivity;

    GridView gridView;

    private SpeechRecognizer speech;

    private Intent recognizerIntent;
    private final int RESULT_SPEECH = 1000;

    // Command variables
    private int CMDNOUN = 0x0000;
    private int CMDVERB = 0x0000;
    private boolean CMDVALID = false;

    // Controller variables
    private byte[] CMDbuffer = new byte[10];
    int RGBon_off_flag=0;

    public static byte MOTOR_SPEED = 0;
    public static byte SEND_TXDATA = 1;
    public static byte SERVO_ANGLE = 3;
    public static byte ANALOG_WRITE = 4;
    public static byte DIGITAL_WRITE = 5;
    public static byte RGB_WRITE = 6;
    public static byte LCD_WRITE = 7;

    private int ServoHead = 90;
    private int ServoArm1 = 90;
    private int ServoArm2 = 90;

    Button BT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Robot settings
        mExec = Executors.newCachedThreadPool();
        ((ThreadPoolExecutor) mExec).setRejectedExecutionHandler(this);
        mBluetooth = BluetoothAdapter.getDefaultAdapter();
        BT = (Button)findViewById(R.id.Bluetooth);
        BT.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent selectDevice = new Intent(MainActivity.this, DeviceListActivity.class);
                startActivityForResult(selectDevice, REQUEST_SELECT_DEVICE);
            }

        });
        // Robot settings end

        gridView = (GridView) findViewById(R.id.gridView);
        faceDetectionActivity = new FaceDetectionActivity();

        checkPermission(this);

        speech = SpeechRecognizer.createSpeechRecognizer(this);
        speech.setRecognitionListener(this);

        findViewById(R.id.floatingButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR"); //언어지정입니다.
                recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
                recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
                recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);   //검색을 말한 결과를 보여주는 갯수
                startActivityForResult(recognizerIntent, RESULT_SPEECH);
            }
        });

        findViewById(R.id.previewButton).setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(MainActivity.this, PreviewActivity.class);
                startActivity(i);
            }
        });

    }

    private void initGridView() {
        imgPaths = getImagesPath(this);

        gridView.setAdapter(new ImageAdapter(this, imgPaths));

        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v,
                                    int position, long id) {
                String selectedImagePath = imgPaths.get(position);

                Intent intent = new Intent(MainActivity.this, FaceDetectionActivity.class);
                intent.putExtra("imgPath", selectedImagePath);
                startActivity(intent);
            }
        });
    }

    private void checkPermission(Activity activity) {

        if (ContextCompat.checkSelfPermission(activity,
                Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity,
                    Manifest.permission.READ_EXTERNAL_STORAGE)) {

                // Show an expanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        PERMISSION_READ_EXTERNAL_STORAGE);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        } else {
            initGridView();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_READ_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    initGridView();


                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    protected ArrayList<String> getImagesPath(Activity activity) {
        Uri uri;
        ArrayList<String> imageList = new ArrayList<String>();
        Cursor cursor;
        int column_index_data;

        String PathOfImage = null;
        uri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

        String[] projection = { MediaStore.MediaColumns.DATA };

        cursor = activity.getContentResolver().query(uri, projection, null,
                null, null);

        column_index_data = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);

        while (cursor.moveToNext()) {
            PathOfImage = cursor.getString(column_index_data);
            imageList.add(PathOfImage);
        }
        return imageList;
    }

    @Override
    public void onEndOfSpeech() {

    }

    @Override
    public void onReadyForSpeech(Bundle bundle) {

    }

    @Override
    public void onResults(Bundle results) {
        ArrayList<String> matches = results
                .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

        for(int i = 0; i < matches.size() ; i++){
            Log.e("GoogleActivity", "onResults text : " + matches.get(i));
        }

    }

    @Override
    public void onError(int errorCode) {

        String message;

        switch (errorCode) {

            case SpeechRecognizer.ERROR_AUDIO:
                message = "오디오 에러";
                break;

            case SpeechRecognizer.ERROR_CLIENT:
                message = "클라이언트 에러";
                break;

            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                message = "퍼미션없음";
                break;

            case SpeechRecognizer.ERROR_NETWORK:
                message = "네트워크 에러";
                break;

            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                message = "네트웍 타임아웃";
                break;

            case SpeechRecognizer.ERROR_NO_MATCH:
                message = "찾을수 없음";;
                break;

            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                message = "바쁘대";
                break;

            case SpeechRecognizer.ERROR_SERVER:
                message = "서버이상";;
                break;

            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                message = "말하는 시간초과";
                break;

            default:
                message = "알수없음";
                break;
        }

        Log.e("GoogleActivity", "SPEECH ERROR : " + message);
    }

    @Override
    public void onRmsChanged(float v) {

    }

    @Override
    public void onBeginningOfSpeech() {

    }

    @Override
    public void onEvent(int i, Bundle bundle) {

    }

    @Override
    public void onPartialResults(Bundle bundle) {

    }

    @Override
    public void onBufferReceived(byte[] bytes) {

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case RESULT_SPEECH : {
                if (resultCode == RESULT_OK && null != data) {
                    ArrayList<String> text = data
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    String command;
                    CMDNOUN = 0;
                    CMDVERB = 0;
                    CMDVALID = false;
                    for(int i = 0; i < text.size() ; i++){
                        command = text.get(i);
                        Log.e("GoogleActivity", "onActivityResult text : " + command);
                        // VALIDATION
                        if(command.contains("로봇"))
                            CMDVALID = true;

                        // NOUN
                        if(command.contains("팔"))
                            CMDNOUN |= 0x01;
                        if(command.contains("머리") || command.contains("고개"))
                            CMDNOUN |= 0x02;
                        if(command.contains("불"))
                            CMDNOUN |= 0x04;

                        // VERB
                        if(command.contains("멈춰") || command.contains("멈춤") || command.contains("정지"))
                            CMDVERB |= 0x0001;
                        if(command.contains("왼") || command.contains("좌측"))
                            CMDVERB |= 0x0002;
                        if(command.contains("오른") || command.contains("우측"))
                            CMDVERB |= 0x0004;
                        if(command.contains("아래") || command.contains("밑"))
                            CMDVERB |= 0x0008;
                        if(command.contains("뒤") || command.contains("후방"))
                            CMDVERB |= 0x0010;
                        if(command.contains("앞") || command.contains("전방"))
                            CMDVERB |= 0x0020;
                    }
                }

                // Translating

                if ((CMDVERB & 0x0001) != 0) {
                    Log.d("CMDVERB", "Stop");
                    setMotorSpeed(0,0);
                    setServoAngle(ServoHead, ServoArm1, ServoArm2);
                    break;
                }

                if (CMDVALID) {
                    if (CMDNOUN == 0x01) {
                        if ((CMDVERB & 0x0002) != 0) {
                            if ((CMDVERB & 0x0008) != 0) {
                                Log.d("CMDVERB", "Left Arm Bottom");
                                ServoArm2 = 90;
                            }
                            if ((CMDVERB & 0x0010) != 0) {
                                Log.d("CMDVERB", "Left Arm Back");
                                ServoArm2 = 170;
                            }
                            if ((CMDVERB & 0x0020) != 0) {
                                Log.d("CMDVERB", "Left Arm Front");
                                ServoArm2 = 10;
                            }
                        }
                        else if ((CMDVERB & 0x0004) != 0) {
                            if ((CMDVERB & 0x0008) != 0) {
                                Log.d("CMDVERB", "Right Arm Bottom");
                                ServoArm1 = 90;
                            }
                            if ((CMDVERB & 0x0010) != 0) {
                                Log.d("CMDVERB", "Right Arm Back");
                                ServoArm1 = 10;
                            }
                            if ((CMDVERB & 0x0020) != 0) {
                                Log.d("CMDVERB", "Right Arm Front");
                                ServoArm1 = 170;
                            }
                        }
                        else {
                            if ((CMDVERB & 0x0008) != 0) {
                                Log.d("CMDVERB", "Both Arm Bottom");
                                ServoArm1 = 90;
                                ServoArm2 = 90;
                            }
                            if ((CMDVERB & 0x0010) != 0) {
                                Log.d("CMDVERB", "Both Arm Back");
                                ServoArm1 = 10;
                                ServoArm2 = 170;
                            }
                            if ((CMDVERB & 0x0020) != 0) {
                                Log.d("CMDVERB", "Both Arm Front");
                                ServoArm1 = 170;
                                ServoArm2 = 10;
                            }
                        }
                        setServoAngle(ServoHead, ServoArm1, ServoArm2);
                    }
                    else if (CMDNOUN == 0x02) {
                        if ((CMDVERB & 0x0002) != 0) {
                            Log.d("CMDVERB", "Head Left");
                            ServoHead = 170;
                        }
                        if ((CMDVERB & 0x0004) != 0) {
                            Log.d("CMDVERB", "Head Right");
                            ServoHead = 10;
                        }
                        if ((CMDVERB & 0x0020) != 0) {
                            Log.d("CMDVERB", "Head Front");
                            ServoHead = 90;
                        }
                        setServoAngle(ServoHead, ServoArm1, ServoArm2);
                    }
                    else if (CMDNOUN == 0x04) {

                    }
                    else {
                        if (CMDVERB == 0x0002) {
                            Log.d("CMDVERB", "Left");
                            setMotorSpeed(170,-170);
                        }
                        if (CMDVERB == 0x0004) {
                            Log.d("CMDVERB", "Right");
                            setMotorSpeed(-170,170);
                        }
                        if (CMDVERB == 0x0010) {
                            Log.d("CMDVERB", "Backward");
                            setMotorSpeed(-170, -170);
                        }
                        if (CMDVERB == 0x0020) {
                            Log.d("CMDVERB", "Forward");
                            setMotorSpeed(170, 170);
                        }
                        if (CMDVERB == 0x0012) {
                            Log.d("CMDVERB", "Backward Left");
                            setMotorSpeed(-250, -170);
                        }
                        if (CMDVERB == 0x0014) {
                            Log.d("CMDVERB", "Backward Right");
                            setMotorSpeed(-170, -250);
                        }
                        if (CMDVERB == 0x0022) {
                            Log.d("CMDVERB", "Forward Left");
                            setMotorSpeed(250,170);
                        }
                        if (CMDVERB == 0x0024) {
                            Log.d("CMDVERB", "Forward Right");
                            setMotorSpeed(170,250);
                        }
                    }
                }
                break;
            }
            case REQUEST_SELECT_DEVICE: {
                consumeRequestDeviceSelect(resultCode, data);
                break;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mExec.shutdownNow();
    }

    private final class ConnectedTask implements Cancelable {
        private final AtomicBoolean mmClosed = new AtomicBoolean();
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private final BluetoothSocket mmSocket;
        public ConnectedTask(BluetoothSocket socket) {
            InputStream in = null;
            OutputStream out = null;
            try {
                in = socket.getInputStream();
                out = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(Constants.TAG, "sockets not created", e);
            }
            mmSocket = socket;
            mmInStream = in;
            mmOutStream = out;
        }
        public void cancel() {
            if (mmClosed.getAndSet(true)) {
                return;
            }
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(Constants.TAG, "close failed", e);
            }
        }
        public void SendData(byte[] data, int offset, int count){
            if (mmSocket != null && mmOutStream != null) {
                try {

                    //���� ����
                    byte[] bytes_len = ShortToBytes((short)count);
                    mmOutStream.write(bytes_len);
                    mmOutStream.flush();

                    mmOutStream.write(data, offset, count);
                    mmOutStream.flush();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }

        public void SendData(byte[] data){
            if (mmSocket != null && mmOutStream != null) {
                try {

                    //���� ����
                    byte[] bytes_len = ShortToBytes((short)data.length);
                    mmOutStream.write(bytes_len);
                    mmOutStream.flush();

                    mmOutStream.write(data);
                    mmOutStream.flush();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        short[] BytesToShorts(byte[] bytes) {
            int b_len = bytes.length;
            int s_len = b_len / 2;
            short[] shorts = new short[s_len];
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    .get(shorts);
            return shorts;
        }
        byte[] ShortsToBytes(short[] shorts) {
            int s_len = shorts.length;
            int b_len = s_len * 2;
            byte[] bytes = new byte[b_len];
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    .put(shorts);
            return bytes;
        }

        byte[] ShortToBytes(short val) {
            return ShortsToBytes(new short[] { val });
        }
        public void run() {
            InputStream in = mmInStream;
            byte[] buffer = new byte[Constants.BUFFER_SIZE];
            int count;
            while (!mmClosed.get()) {
                try {
                    count = in.read(buffer);
                    received(buffer, 0, count);
                } catch (IOException e) {
                    connectionLost(e);
                    cancel();
                    break;
                }
            }
        }
        /*
         * public void write(byte[] buffer) { try { mmOutStream.write(buffer); }
         * catch (IOException e) { Log.e(Constants.TAG, "write failed", e); } }
         */
        void connectionLost(IOException e) {
        }
        void received(byte[] buffer, int offset, int count) {
            String str = new String(buffer, offset, count);
            // serialPort_DataReceived(buffer, count);
        }
    }
    private final class ConnectTask implements Cancelable {
        private final AtomicBoolean mmClosed = new AtomicBoolean();
        private final BluetoothSocket mmSocket;
        public ConnectTask(BluetoothDevice device, UUID uuid) {
            BluetoothSocket socket = null;
            try {
                socket = device.createRfcommSocketToServiceRecord(uuid);
            } catch (IOException e) {
                Log.e(Constants.TAG, "create failed", e);
            }
            mmSocket = socket;
        }
        public void cancel() {
            if (mmClosed.getAndSet(true)) {
                return;
            }
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(Constants.TAG, "close failed", e);
            }
        }
        public void run() {
            if (mBluetooth.isDiscovering()) {
                mBluetooth.cancelDiscovery();
            }
            try {
                mmSocket.connect();
                connected(mmSocket);
            } catch (IOException e) {
                connectionFailed(e);
                cancel();
            }
        }
        void connected(BluetoothSocket socket) {
            mLock.lock();
            try {
                // dumpMessage("connected");
                final ConnectedTask task = new ConnectedTask(socket);
                Cancelable canceller = new CancellingTask(mExec, task);
                mExec.execute(canceller);
                mConnectedTask = task;
            } finally {
                mLock.unlock();
            }
        }
        void connectionFailed(IOException e) {
            // dumpMessage(e.getLocalizedMessage());
        }
    }
    private static final String RECENT_DEVICE = "recent_device";
    private static final int REQUEST_SELECT_DEVICE = 0;
    private BluetoothAdapter mBluetooth;
    private ConnectedTask mConnectedTask;
    private ExecutorService mExec;
    private ReentrantLock mLock = new ReentrantLock();
    /*
     * public void dumpReceivedMessage(String str) { dumpMessage(str); }
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.client, menu);
        return true;
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.recent_device:
                BluetoothDevice recent = loadDefault();
                onDeviceSelected(recent);
                break;
            case R.id.search:
                Intent selectDevice = new Intent(this, DeviceListActivity.class);
                startActivityForResult(selectDevice, REQUEST_SELECT_DEVICE);
                break;
            default:
                return super.onMenuItemSelected(featureId, item);
        }
        return true;
    }
    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {
        MenuItem item = menu.findItem(R.id.recent_device);
        BluetoothDevice recent = loadDefault();
        if (recent == null) {
            item.setVisible(false);
        } else {
            item.setTitle(recent.getName());
        }
        return super.onMenuOpened(featureId, menu);
    }
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        // noop.
    }
    private void consumeRequestDeviceSelect(int resultCode, Intent data) {
        if (resultCode != RESULT_OK) {
            return;
        }
        BluetoothDevice device = data
                .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        saveAsDefault(device);
        onDeviceSelected(device);
    }
    /*
     * private void dumpMessage(final String msg) { Runnable dumpTask = new
     * Runnable() { public void run() { edit1.setText(msg);
     * //System.out.print(msg.toString()); } }; runOnUiThread(dumpTask); }
     */
    private BluetoothDevice loadDefault() {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        String addr = prefs.getString(RECENT_DEVICE, null);
        if (addr == null) {
            return null;
        }
        BluetoothDevice device = mBluetooth.getRemoteDevice(addr);
        return device;
    }
    private void onDeviceSelected(BluetoothDevice device) {
        mLock.lock();
        try {
            if (mConnectedTask != null) {
                mConnectedTask.cancel();
                mConnectedTask = null;
            }
        } finally {
            mLock.unlock();
        }
        // dumpMessage("connecting");
        ConnectTask task = new ConnectTask(device,
                Constants.SERIAL_PORT_PROFILE);
        Cancelable canceller = new CancellingTask(mExec, task, 10,
                TimeUnit.SECONDS);
        mExec.execute(canceller);
    }
    private void saveAsDefault(BluetoothDevice device) {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = prefs.edit();
        String addr = device.getAddress();
        editor.putString(RECENT_DEVICE, addr);
        editor.commit();
    }

    Button.OnClickListener mClicListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            // TODO Auto-generated method stub
            Intent intent = new Intent(MainActivity.this, DeviceListActivity.class);
            startActivity(intent);
        }
    };

    void SendXBOTcmd(byte cmd, byte d0, byte d1, byte d2, byte d3, byte d4)
    {
        CMDbuffer[0] = (byte)'X';
        CMDbuffer[1] = (byte)'R';
        CMDbuffer[2] = cmd;
        CMDbuffer[3] = d0;
        CMDbuffer[4] = d1;
        CMDbuffer[5] = d2;
        CMDbuffer[6] = d3;
        CMDbuffer[7] = d4;
        CMDbuffer[8] = (byte)'S';
    }

    void setMotorSpeed(int speed1, int speed2)
    {

        if(mConnectedTask != null){
            SendXBOTcmd(MOTOR_SPEED, (byte)((speed1 & 0xFF00) >> 8), (byte)(speed1 & 0xFF),(byte)((speed2 & 0xFF00) >> 8), (byte)(speed2 & 0xFF), (byte)0);
            mConnectedTask.SendData(CMDbuffer, 0, 9);
        }
    };

    void setRGBLed(int rColor, int gColor, int bColor)
    {

        if(mConnectedTask != null)
        {
            //	sleep(300);
            SendXBOTcmd(RGB_WRITE, (byte)(255),(byte)(rColor),(byte)(gColor), (byte)(bColor), (byte)0);
            mConnectedTask.SendData(CMDbuffer, 0, 9);
        }
    };
    void setServoAngle(int angle1, int angle2, int angle3)
    {
        if(mConnectedTask != null){

            SendXBOTcmd(SERVO_ANGLE, (byte)(angle1), (byte)(angle2),(byte)(angle3), (byte)0, (byte)0);
            mConnectedTask.SendData(CMDbuffer, 0, 9);
        }
    }


}
