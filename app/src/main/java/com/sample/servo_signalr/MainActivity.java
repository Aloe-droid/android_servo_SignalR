package com.sample.servo_signalr;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.Toast;

import com.microsoft.signalr.HubConnection;
import com.microsoft.signalr.HubConnectionBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;


public class MainActivity extends AppCompatActivity {
    HubConnection hubConnection;

    BluetoothAdapter bluetoothAdapter;
    Set<BluetoothDevice> PairedBluetoothDevices;
    List <String> ListBluetoothDevices;

    ConnectedBluetoothThread threadConnectedBluetooth;
    BluetoothDevice bluetoothDevice;
    BluetoothSocket bluetoothSocket;

    final static int BT_REQUEST_ENABLE = 1;
    final static UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //범용 고유 식별자




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //signalR 허브 연결하기
        try {
            String input = "http://hklab.hknu.ac.kr/chathub";
            hubConnection = HubConnectionBuilder.create(input).build();

            hubConnection.start().blockingAwait();
        }catch (Exception e){
            Toast.makeText(this,"연결 중 오류 발생",Toast.LENGTH_SHORT).show();
        }

        //블루투스 어댑터 만들기 (블루투스 키기)
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        //블루투스 켰는지 확인
        bluetoothOn();
        //페어링 된 기기 보여주기
        listPairedDevices();



        //signalR을 통해 입력 받기
        hubConnection.on("ReceiveMessage",(user,message) -> {
        //블루투스 제어
            if(threadConnectedBluetooth != null){
                threadConnectedBluetooth.write(message);
            }
        },String.class,String.class);

    }

    //블루투스 켰는지 확인
    void bluetoothOn() throws SecurityException{
        if(bluetoothAdapter == null){
            Toast.makeText(getApplicationContext(),"블루투스를 지원하지 않는 기기",Toast.LENGTH_SHORT).show();
        }else{
            Toast.makeText(getApplicationContext(),"블루투스가 활성화 되지 않음",Toast.LENGTH_SHORT).show();
            Intent intentBluetoothEnable =new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intentBluetoothEnable,BT_REQUEST_ENABLE);
        }
    }


    //블루투스 권환 확인하기
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if(requestCode == BT_REQUEST_ENABLE) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(getApplicationContext(),"블루투스 활성화",Toast.LENGTH_SHORT).show();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    //페어링된 기기 보여주기
    void listPairedDevices() throws SecurityException{
        if(bluetoothAdapter.isEnabled()){
            PairedBluetoothDevices = bluetoothAdapter.getBondedDevices();
            if(PairedBluetoothDevices.size() >0){
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("장치 선택");

                ListBluetoothDevices = new ArrayList<>();
                for(BluetoothDevice device : PairedBluetoothDevices){
                    ListBluetoothDevices.add(device.getName());
                }

                final CharSequence[] items = ListBluetoothDevices.toArray(new CharSequence[ListBluetoothDevices.size()]);
                ListBluetoothDevices.toArray(new CharSequence[ListBluetoothDevices.size()]);

                builder.setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int item) {
                        connectSelectDevice(items[item].toString());
                    }
                });
                AlertDialog dialog = builder.create();
                dialog.show();
            }else{
                Toast.makeText(getApplicationContext(),"페어링 된 기기가 없습니다",Toast.LENGTH_SHORT).show();
            }
        }else{
            Toast.makeText(getApplicationContext(),"블루투스가 비활성화 상태 입니다",Toast.LENGTH_SHORT).show();
        }
    }

    //알람창으로 뜬 디바이스 연결 및 새 쓰레드를 통해 특정 값 전송
    void connectSelectDevice(String selectedDeviceName) throws SecurityException{
        for(BluetoothDevice device : PairedBluetoothDevices){
            if(selectedDeviceName.equals(device.getName())){
                bluetoothDevice = device;
                break;
            }
        }
        try{
            bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(BT_UUID);
            bluetoothSocket.connect();
            threadConnectedBluetooth = new ConnectedBluetoothThread(bluetoothSocket);
            threadConnectedBluetooth.start();
            Toast.makeText(getApplicationContext(),"연결 성공!",Toast.LENGTH_SHORT).show();
        }catch (IOException e){
            Toast.makeText(getApplicationContext(),"블루투스 연결 중 오류 발생",Toast.LENGTH_SHORT).show();
        }
    }

    //블루투스 쓰레드 클래스
    public class ConnectedBluetoothThread extends Thread{
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        private ConnectedBluetoothThread(BluetoothSocket socket){
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try{
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            }catch (IOException e){
                Toast.makeText(getApplicationContext(),"소켓 연결 중 오류 발생",Toast.LENGTH_SHORT).show();
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run(){
            byte[] buffer = new byte[1024];
            int bytes;

            while (true){
                try{
                    bytes = mmInStream.available();
                    if(bytes != 0){
                        buffer = new byte[1024];
                        SystemClock.sleep(100);
                        bytes = mmInStream.available();
                        bytes = mmInStream.read(buffer,0,bytes);
                    }

                }catch (IOException e){
                    break;
                }
            }
        }

        public void write(String str){
            byte[] bytes = str.getBytes();
            try{
                mmOutStream.write(bytes);
            }catch (IOException e){
                Toast.makeText(getApplicationContext(),"데이터 전송 중 오류 발생",Toast.LENGTH_SHORT).show();
            }
        }

        public void cancel(){
            try{
                mmSocket.close();
            }catch (IOException e){
                Toast.makeText(getApplicationContext(),"소켓 닫는 중 오류 발생",Toast.LENGTH_SHORT).show();
            }
        }


    }

    //허브커넥션 자동 종료
    @Override
    protected void onDestroy()throws SecurityException {
        hubConnection.stop();
        threadConnectedBluetooth.cancel();
        super.onDestroy();
    }
}