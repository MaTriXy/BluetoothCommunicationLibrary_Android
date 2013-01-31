BluetoothCommunicationLibrary_Android
=====================================

Simple Bluetooth Communication Library for Android.

Use this library, you can make Bluetooth P2P Communication on android easily.
This library includes both server and client side code.

* If use this library,needs bluetooth permission in application.

/**
 *
 * Sample Code(ServerSide)
 *
 */

import ktlab.lib.connection.ConnectionCommand;
import ktlab.lib.connection.bluetooth.BluetoothConnection;
import ktlab.lib.connection.bluetooth.ServerBluetoothConnection;
import ktlab.lib.connection.ConnectionCallback;

import android.app.Activity;

public class TestActivity extends Activity {

    // you can define original command(byte) 
    public static final byte COMMAND_TEST = 1;
    
    public static final int COMMAND_ID_1 = 1;
    public static final int COMMAND_ID_2 = 1;

    private BluetoothConnection mConnection;

    /**
     * on Create
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // you can start Bluetooth Connection
        // by call Server(Client)BluetoothConnection.startConnection()
        mConnection = new ServerBluetoothConnection(new CameraConnectionCallback(), true);
        mConnection.startConnection();
    }

    /**
     * Implements ConnectionCallback
     * (first argument of Server(Client)BluetoothConnection Contructor)
     */
    class CameraConnectionCallback implements ConnectionCallback{

        /*
         *  this method called when connection established
         */
        public void onConnectComplete(){
        
            // If you want to send a simple command, use sendData(int command, int id).
            mConnection.sendData(COMMAND_TEST, COMMAND_ID_1);
        }

        /*
         *  this method called when connection error occurred
         */
        public void onConnectionFailed(){
            finish();
        }

        /*
         *  this method called when command received
         */
        public void onCommandReceived(ConnectionCommand command) {

            switch (command.type){
            case COMMAND_TEST:
                // do something
                break;
            
            default:
                break;
            }
        }

        /*
         *  this method called when data send completed
         */
        public void onDataSendComplete(int id) {

            // "id" indicates which data was sent.
            // you can set by sendData() arguments.
            switch (id){
            case COMMAND_ID_1:
            
                // If you want to send a data, use sendData(int command, byte[] data, int id).
                byte[] data = new byte[]{'h', 'o', 'g', 'e'};
                mConnection.sendData(COMMAND_TEST, data, COMMAND_ID_2);
                break;

            case COMMAND_ID_2:
                break;
            default :
                break;
            }
        }
    }

    /**
     * on stop
     */
    @Override
    public void onStop(){
        mConnection.stopConnection();
    }
}
