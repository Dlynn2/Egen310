package com.dylan.egen310car;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.drm.DrmStore;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.SocketImplFactory;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    // GUI Components
    private boolean flag = false;
    private TextView mBluetoothStatus;
    private TextView mReadBuffer;
    private Button mScanBtn;
    private Button mOffBtn;
    private Button mListPairedDevicesBtn;
    private Button mDiscoverBtn;
    private Button left;
    private Button right;
    private Button forward;
    private Button back;
    private Button stop;
    private BluetoothAdapter mBTAdapter;
    private Set<BluetoothDevice> mPairedDevices;
    private ArrayAdapter<String> mBTArrayAdapter;
    private ListView mDevicesListView;
    private SeekBar speedControl;
    private int progressChanged = 0;

    private Handler mHandler; // Our main handler that will receive callback notifications
    public ConnectedThread mConnectedThread; // bluetooth background worker thread to send and receive data
    private BluetoothSocket mBTSocket = null; // bi-directional client-to-client data path

    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // "random" unique identifier


    // #defines for identifying shared types between calling functions
    private final static int REQUEST_ENABLE_BT = 1; // used to identify adding bluetooth names
    private final static int MESSAGE_READ = 2; // used in bluetooth handler to identify message update
    private final static int CONNECTING_STATUS = 3; // used in bluetooth handler to identify message status
    private final static int REQUEST_PERMISSION_BLUETOOTH=4;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mBluetoothStatus = (TextView) findViewById(R.id.bluetoothStatus);
        mReadBuffer = (TextView) findViewById(R.id.readBuffer);
        mScanBtn = (Button) findViewById(R.id.scan);
        mOffBtn = (Button) findViewById(R.id.off);
        mDiscoverBtn = (Button) findViewById(R.id.discover);
        mListPairedDevicesBtn = (Button) findViewById(R.id.PairedBtn);
        left = findViewById(R.id.left);
        right = findViewById(R.id.right);
        forward = findViewById(R.id.forward);
        back = findViewById(R.id.backward);
//        stop = findViewById(R.id.stop);
        speedControl = findViewById(R.id.seekBar);
        speedControl.setMax(255);
        mBTArrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        mBTAdapter = BluetoothAdapter.getDefaultAdapter(); // get a handle on the bluetooth radio

        mDevicesListView = (ListView) findViewById(R.id.devicesListView);
        mDevicesListView.setAdapter(mBTArrayAdapter); // assign model to view
        mDevicesListView.setOnItemClickListener(mDeviceClickListener);


        mHandler = new Handler() {
            public void handleMessage(android.os.Message msg) {
                if (msg.what == MESSAGE_READ) {
                    String readMessage = null;
                    try {
                        readMessage = new String((byte[]) msg.obj, "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    mReadBuffer.setText(readMessage);
                }

                if (msg.what == CONNECTING_STATUS) {
                    if (msg.arg1 == 1)
                        mBluetoothStatus.setText("Connected to Device: " + (String) (msg.obj));
                    else
                        mBluetoothStatus.setText("Connection Failed");
                }
            }
        };

        if (mBTArrayAdapter == null) {
            // Device does not support Bluetooth
            mBluetoothStatus.setText("Status: Bluetooth not found");
            Toast.makeText(getApplicationContext(), "Bluetooth device not found!", Toast.LENGTH_SHORT).show();
        }



//            mLED1.setOnClickListener(new View.OnClickListener(){
//                @Override
//                public void onClick(View v){
//                    if(mConnectedThread != null) //First check to make sure thread created
//                        mConnectedThread.write("1");
//            });
//        right.setOnTouchListener(new View.OnTouchListener() {
//            @Override
//            public boolean onTouch(View v, MotionEvent event) {
//                if (event.getAction() == MotionEvent.ACTION_BUTTON_RELEASE) {
////                    mConnectedThread.write("Stop");
//                    Toast.makeText(getApplicationContext(),"lifted",Toast.LENGTH_SHORT).show();
//                }
//                return false;
//            }
//        });
        //these methods are called as soon as your finger touches one of the respective buttons
        right.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                //what is sent to the car.
                mConnectedThread.write("R\n" + progressChanged);
                //this stops the button from sending a message to the car again for 75 milliseconds
                try {
                    Thread.currentThread().setPriority(1);
                    Thread.sleep(75);
                } catch (InterruptedException e) {
                    Toast.makeText(getApplicationContext(), "failed", Toast.LENGTH_SHORT).show();

                }
                //this is a backup event if the button release fails. if the finger is lifted it sends
                // S and speed of 0 S being the signal for Stop.
                if (event.getAction() == MotionEvent.ACTION_UP){
                    mConnectedThread.write("S\n" + 0);
                }
                //this tells the methods that the finger is no longer touching the button
                return false;
            }

        });
        //these methods are called as soon as your finger touches one of the respective buttons
        left.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                //what is sent to the car.
                mConnectedThread.write("L\n" + progressChanged);
                //this stops the button from sending a message to the car again for 75 milliseconds
                try {
                    Thread.currentThread().setPriority(1);
                    Thread.sleep(75);
                } catch (InterruptedException e) {
                    Toast.makeText(getApplicationContext(), "failed", Toast.LENGTH_SHORT).show();
                }
                //this is a backup event if the button release fails. if the finger is lifted it sends
                // S and speed of 0 S being the signal for Stop.
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        mConnectedThread.write("S\n" + 0);
                    }
                //this tells the methods that the finger is no longer touching the button
                return false;
            }
        });
        back.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mConnectedThread.write("B\n" + progressChanged);
                  try {
                      Thread.currentThread().setPriority(1);
                      Thread.sleep(75);
                  } catch (InterruptedException e) {
                      Toast.makeText(getApplicationContext(), "Fuck", Toast.LENGTH_SHORT).show();
                  }
                  if (event.getAction() == MotionEvent.ACTION_UP){
                      mConnectedThread.write("S\n"+0);
                }

                return false;
            }
        });
        forward.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mConnectedThread.write("F\n" + progressChanged);
                try {
                    Thread.currentThread().setPriority(1);
                    Thread.sleep(75);
                } catch (InterruptedException e) {
                    Toast.makeText(getApplicationContext(), "Fuck", Toast.LENGTH_SHORT).show();
                }
                if (event.getAction() == MotionEvent.ACTION_UP){
                    mConnectedThread.write("S\n"+0);
                }
                return false;
            }
        });
        //this is the slider at the bottom that is used to control the speed value sent to the car
        speedControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                progressChanged=progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }
            //this puts out a value to see what speed you have selected
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Toast.makeText(getApplicationContext(),"changed value:" + progressChanged,Toast.LENGTH_SHORT).show();
            }
        });
        //these methods are called as soon as your finger is lifted from the respective buttons.
        right.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //this tells the car to send a speed value of zero so the car stops nearly instantly.
                mConnectedThread.write("R\n" + 0);
            }
        });
        //these methods are called as soon as your finger is lifted from the respective buttons.
        left.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //this tells the car to send a speed value of zero so the car stops nearly instantly.
                mConnectedThread.write("L\n" + 0);
            }
        });
        //these methods are called as soon as your finger is lifted from the respective buttons.
        forward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //this tells the car to send a speed value of zero so the car stops nearly instantly.
                mConnectedThread.write("F\n" + 0);
            }
        });
        //these methods are called as soon as your finger is lifted from the respective buttons.
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //this tells the car to send a speed value of zero so the car stops nearly instantly.
                mConnectedThread.write("B\n" + 0);
            }
        });
        mScanBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bluetoothOn(v);
            }
        });

        mOffBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                bluetoothOff(v);
            }
        });

        mListPairedDevicesBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v){
                listPairedDevices(v);
            }
        });

        mDiscoverBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                discover(v);
            }
        });
    }

    private void bluetoothOn(View view){
        if (!mBTAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            mBluetoothStatus.setText("Bluetooth enabled");
            Toast.makeText(getApplicationContext(),"Bluetooth turned on",Toast.LENGTH_SHORT).show();

        }
        else{
            Toast.makeText(getApplicationContext(),"Bluetooth is already on", Toast.LENGTH_SHORT).show();
        }
    }

    // Enter here after user selects "yes" or "no" to enabling radio
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent Data){
        // Check which request we're responding to
        if (requestCode == REQUEST_ENABLE_BT) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                // The user picked a contact.
                // The Intent's data Uri identifies which contact was selected.
                mBluetoothStatus.setText("Enabled");
            }
            else
                mBluetoothStatus.setText("Disabled");
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_PERMISSION_BLUETOOTH: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    Toast.makeText(this, "Permission granted.", Toast.LENGTH_SHORT).show();

                    // Permission granted.
                } else {
                    Toast.makeText(this, "Permission must be granted to use the application.", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void bluetoothOff(View view){
        mBTAdapter.disable(); // turn off
        mBluetoothStatus.setText("Bluetooth disabled");
        Toast.makeText(getApplicationContext(),"Bluetooth turned Off", Toast.LENGTH_SHORT).show();
    }

    private void discover(View view) {
        // Check if the device is already discovering
        if (mBTAdapter.isDiscovering()) {
            mBTAdapter.cancelDiscovery();
            Toast.makeText(getApplicationContext(), "Discovery stopped", Toast.LENGTH_SHORT).show();
        } else {
            if (mBTAdapter.isEnabled()) {
                mBTArrayAdapter.clear(); // clear items
//                 Handling permissions.
                if (ContextCompat.checkSelfPermission(this,android.Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
                }
                else{
                    Toast.makeText(getApplicationContext(), "Bluetooth not on", Toast.LENGTH_SHORT).show();
                }
//                 Permission is not granted
                if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION)) {

                    // Not to annoy user.
                    Toast.makeText(this, "Permission must be granted to use the app.", Toast.LENGTH_SHORT).show();
                } else {

                    // Request permission.
                    ActivityCompat.requestPermissions( this, new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_PERMISSION_BLUETOOTH);
                }
            } else {
                // Permission has already been granted.
                Toast.makeText(this, "Permission already granted.", Toast.LENGTH_SHORT).show();
            }
            mBTAdapter.startDiscovery();
            Toast.makeText(getApplicationContext(), "Discovery started", Toast.LENGTH_SHORT).show();
            registerReceiver(blReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        }
    }
//    }


    final BroadcastReceiver blReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(BluetoothDevice.ACTION_FOUND.equals(action)){
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // add the name to the list
                mBTArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                mBTArrayAdapter.notifyDataSetChanged();
            }
        }
    };

    private void listPairedDevices(View view){
        mPairedDevices = mBTAdapter.getBondedDevices();
        mBTArrayAdapter.clear(); // clear items
        if(mBTAdapter.isEnabled()) {
            // put it's one to the adapter
            for (BluetoothDevice device : mPairedDevices)
                mBTArrayAdapter.add(device.getName() + "\n" + device.getAddress());

            Toast.makeText(getApplicationContext(), "Show Paired Devices", Toast.LENGTH_SHORT).show();
        }
        else
            Toast.makeText(getApplicationContext(), "Bluetooth not on", Toast.LENGTH_SHORT).show();
    }

    private AdapterView.OnItemClickListener mDeviceClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {

            if(!mBTAdapter.isEnabled()) {
                Toast.makeText(getBaseContext(), "Bluetooth not on", Toast.LENGTH_SHORT).show();
                return;
            }

            mBluetoothStatus.setText("Connecting...");
            // Get the device MAC address, which is the last 17 chars in the View
            String info = ((TextView) v).getText().toString();
            final String address = info.substring(info.length() - 17);
            final String name = info.substring(0,info.length() - 17);

            // Spawn a new thread to avoid blocking the GUI one
            new Thread()
            {
                public void run() {
                    boolean fail = false;

                    BluetoothDevice device = mBTAdapter.getRemoteDevice(address);

                    try {
                        mBTSocket = createBluetoothSocket(device);
                    } catch (IOException e) {
                        fail = true;
                        Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_SHORT).show();
                    }
                    // Establish the Bluetooth socket connection.
                    try {
                        mBTSocket.connect();
                    } catch (IOException e) {
                        try {
                            fail = true;
                            mBTSocket.close();
                            mHandler.obtainMessage(CONNECTING_STATUS, -1, -1)
                                    .sendToTarget();
                        } catch (IOException e2) {
                            //insert code to deal with this
                            Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_SHORT).show();
                        }
                    }
                    if(fail == false) {
                        mConnectedThread = new ConnectedThread(mBTSocket);
                        mConnectedThread.start();

                        mHandler.obtainMessage(CONNECTING_STATUS, 1, -1, name)
                                .sendToTarget();
                    }
                }
            }.start();
        }
    };

    public BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        return  device.createRfcommSocketToServiceRecord(BTMODULEUUID);
        //creates secure outgoing connection with BT device using UUID
    }
    public UUID getConnection(){
        return BTMODULEUUID;
    }
    public class ConnectedThread extends Thread {
        private  final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes; // bytes returned from read()
            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.available();
                    if(bytes != 0) {
//                        SystemClock.sleep(100); //pause and wait for rest of data. Adjust this depending on your sending speed.
                        bytes = mmInStream.available(); // how many bytes are ready to be read?
                        bytes = mmInStream.read(buffer, 0, bytes); // record how many bytes we actually read
                        mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer)
                                .sendToTarget(); // Send the obtained bytes to the UI activity
                    }
                } catch (IOException e) {
                    e.printStackTrace();

                    break;
                }
            }
        }
        /* Call this from the main activity to send data to the remote device */
        public void write(String input) {
            byte[] bytes = input.getBytes();           //converts entered String into bytes
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) { }
        }
//        public void write(String input,int speed) {
//
//            byte[] bytes = input.getBytes();           //converts entered String into bytes
//            try {
//                mmOutStream.write(bytes);
//                mmOutStream.write(speed);
//            } catch (IOException e) { }
//        }
        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }
}
class Background_get extends AsyncTask<String, Void, String> {
    @Override
    protected String doInBackground(String... params) {
        try {
            Thread.dumpStack();
            Socket.setSocketImplFactory((SocketImplFactory) this);
        }
        catch(IOException e){
            System.out.println("duh");
            }
            return "this";
    }
}
