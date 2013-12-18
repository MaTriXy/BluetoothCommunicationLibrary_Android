package ktlab.lib.connection.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Message;

import java.io.IOException;
import java.util.UUID;

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

        if (!createSocket(0)) return;

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
                if(!createSocket(count + 1))
                    return;
            }
            // retry
        } while (count++ < 5);
    }

    private boolean createSocket(int retry) {
        try {
            UUID uuid = BluetoothConnection.SERVICE_UUID;

//            Log.e("getSocket", uuid.toString());
//            Log.e("getSocket mDevice getUuids", mDevice.getUuids() == null ? "length 0" : "length " + mDevice.getUuids().length);
           
//            if(mDevice.getUuids() != null && mDevice.getUuids().length > 0)
//            {
//                if(retry < mDevice.getUuids().length)
//                uuid = mDevice.getUuids()[retry].getUuid();
//                Log.e("getSocket mDevice 0", uuid.toString());
//            }

            if(mSocket != null)
                mSocket.close();

            mSocket = mDevice.createInsecureRfcommSocketToServiceRecord(uuid);
        } catch (Exception e) {
            mSocket = null;
            return false;
        }
        return true;
    }
}
