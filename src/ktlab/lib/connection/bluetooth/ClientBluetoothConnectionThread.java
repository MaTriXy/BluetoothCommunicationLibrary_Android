package ktlab.lib.connection.bluetooth;

import java.io.IOException;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Message;

public class ClientBluetoothConnectionThread extends BluetoothConnectionThread {

    protected final BluetoothDevice mDevice;
    protected final BluetoothAdapter mBAdapter;

    public ClientBluetoothConnectionThread(BluetoothDevice device, Message msg) {
        super(msg);
        mDevice = device;
        mBAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    @Override
    protected void getSocket() {

        try {
            mSocket = mDevice.createInsecureRfcommSocketToServiceRecord(BluetoothConnection.SERVICE_UUID);
        } catch (IOException e) {
            mSocket = null;
            return;
        }

        int count = 0;
        do {
            try {
                if (mSocket != null) {
                    mBAdapter.cancelDiscovery();
                    mSocket.connect();
                }
                break;
            } catch (IOException e) {
                e.printStackTrace();
                // DO NOTHING
            }
            // retry
        } while (count++ < 1);
    }
}
