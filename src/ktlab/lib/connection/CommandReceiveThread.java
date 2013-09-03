package ktlab.lib.connection;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import android.os.Message;
import android.util.Log;

public class CommandReceiveThread extends Thread {

    private final static String LOG_TAG = "CommandReceiveThread";
    private boolean forceStop = false;

    private final InputStream mInput;
    private Message mMessage;
    private ByteOrder mOrder;

    public CommandReceiveThread(InputStream in, Message msg, ByteOrder order) {
        mInput = in;
        mMessage = msg;
        mOrder = order;
    }


    public boolean manageRead(boolean readAck) {

        byte[] rawHeader = new byte[ConnectionCommand.HEADER_LENGTH];
        int receivedSize = 0;

        // receive header
        while (!forceStop && (receivedSize < ConnectionCommand.HEADER_LENGTH)) {
            int length = 0;
            try {
                length = mInput.read(rawHeader, receivedSize,
                        ConnectionCommand.HEADER_LENGTH - receivedSize);
            } catch (IOException e) {
                mMessage.what = Connection.EVENT_CONNECTION_FAIL;
                mMessage.sendToTarget();
                return false;
            }
            if (length != -1) {
                receivedSize += length;
                if (!checkHeader(rawHeader, receivedSize)) {
                    Log.v(LOG_TAG, "BAD HEADER: " + ConnectionCommand.getPrintableBytesArray(rawHeader));
                    receivedSize = 0;
                    continue;
                }
            }

            if (length == 0) {
                try {
                    sleep(50);
                } catch (InterruptedException e) {
                    // DO NOTHING
                }
            }
        }

        byte[] orderedHeader = new byte[rawHeader.length];
        ByteBuffer.wrap(rawHeader).order(mOrder).get(orderedHeader);

        final int optionLen = orderedHeader[ConnectionCommand.HEADER_DATA_SIZE_INDEX];
        final int commandId = orderedHeader[ConnectionCommand.HEADER_COMMAND_INDEX];

        // si la command est 5 alors il n'y pas de ACK
        if (commandId == 5)
            readAck = false;

        byte[] rawOption = new byte[optionLen];
        receivedSize = 0;

        // receive option
        while (!forceStop && (receivedSize < optionLen)) {

            int length = 0;
            try {
                length = mInput.read(rawOption, receivedSize, optionLen
                        - receivedSize);
            } catch (IOException e) {
                mMessage.what = Connection.EVENT_CONNECTION_FAIL;
                mMessage.sendToTarget();
                return false;
            }
            if (length != -1) {
                receivedSize += length;
            }
        }

        byte[] orderedOption = new byte[rawOption.length];
        ByteBuffer.wrap(rawOption).order(mOrder).get(orderedOption);

        // si on il y a un ACK
        if (readAck) {
            Log.v(LOG_TAG, "ACK RECEIVED " + ConnectionCommand.getPrintableBytesArray(rawHeader) + ConnectionCommand.getPrintableBytesArray(rawOption));
            final byte ack = orderedOption[ConnectionCommand.HEADER_ACK_INDEX];
            if (!(ack == 1)) {
                //TODO RECEIVE NO ACK
                return false;
            }
            return true;
        } else {
            // data received
            Log.v(LOG_TAG, "DATA RECEIVED " + ConnectionCommand.getPrintableBytesArray(rawHeader));
            ConnectionCommand command = ConnectionCommand.fromHeaderAndOption(
                    rawHeader, rawOption, mOrder);
            mMessage.obj = command;
            mMessage.sendToTarget();
        }
        return false;
    }


    public void run() {

        if (manageRead(true))
            manageRead(false);

    }

    private boolean checkHeader(final byte[] data, final int len) {
        final byte[] header = ConnectionCommand.getHeader();
        final int size = len > header.length ? header.length : len;
        for (int i = 0; i < size; i++) {
            if (data[i] != header[i])
                return false;
        }
        return true;
    }

    protected void forceStop() {
        forceStop = true;
    }
}
