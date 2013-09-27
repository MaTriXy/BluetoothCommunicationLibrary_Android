package ktlab.lib.connection;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class CommandReceiveThread extends Thread {

    protected final static String LOG_TAG = "CommandReceiveThread";
    protected boolean forceStop = false;

    protected final InputStream mInput;
    protected Message mMessage;
    protected Message mMessageSend;
    protected ByteOrder mOrder;
    protected Handler mHandler;

    public CommandReceiveThread(InputStream in, Message msg, ByteOrder order) {
        mInput = in;
        mMessage = msg;
        mOrder = order;
        mHandler = mMessage.getTarget();
    }

    protected boolean readAck() {
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
                }
            }
        }

        byte[] orderedHeader = new byte[rawHeader.length];
        ByteBuffer.wrap(rawHeader).order(mOrder).get(orderedHeader);

        final int optionLen = orderedHeader[ConnectionCommand.HEADER_DATA_SIZE_INDEX];
        final int commandId = orderedHeader[ConnectionCommand.HEADER_COMMAND_INDEX];

        byte[] rawOption = new byte[optionLen];
        receivedSize = 0;

        // receive option
        while (!forceStop && (receivedSize < optionLen)) {
            int length;
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

        Log.v(LOG_TAG, "ACK RECEIVED " + ConnectionCommand.getPrintableBytesArray(rawHeader) + ConnectionCommand.getPrintableBytesArray(rawOption));
        final byte ack = orderedOption[ConnectionCommand.HEADER_ACK_INDEX];
        if (!(ack == 1)) {
            mMessage.what = Connection.EVENT_RECEIVE_NACK;
            mMessage.sendToTarget();
            return false;
        }
        return true;
    }

    public ConnectionCommand readCommand() {

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
                return null;
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
        Log.v("brako", "rawheader" + ConnectionCommand.getPrintableBytesArray(rawHeader));
        ByteBuffer.wrap(rawHeader).order(mOrder).get(orderedHeader);

        int optionLen = orderedHeader[ConnectionCommand.HEADER_DATA_SIZE_INDEX];

        if (optionLen < 0)
            optionLen += 256;

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
                return null;
            }
            if (length != -1) {
                receivedSize += length;
            }
        }

        byte[] orderedOption = new byte[rawOption.length];
        ByteBuffer.wrap(rawOption).order(mOrder).get(orderedOption);

        ConnectionCommand command = ConnectionCommand.fromHeaderAndOption(
                rawHeader, rawOption, mOrder);
        return command;
    }


    public void run() {
    }

    protected ConnectionCommand receiveFile() {
        // start_send_file_command
        final ConnectionCommand startSendFileCommand = readCommand();
        if (startSendFileCommand == null) {
            Log.v(LOG_TAG, "startSendFileCommand EMPTY COMMAND ");
            this.forceStop = true;
            return null;
        }

        sendAck(startSendFileCommand);


        int fileSize = getFileSize(startSendFileCommand.option);
        Log.v(LOG_TAG, "start send file, option " + ConnectionCommand.getPrintableBytesArray(startSendFileCommand.option));
        Log.v(LOG_TAG, "start send file, size " + fileSize);
        int receiveSize = 0;

        ConnectionCommand resultCommand = null;

        while (receiveSize < fileSize) {
            Log.v("brako", "readCommand");
            // file_data
            final ConnectionCommand fileDataCommand = readCommand();
            if (fileDataCommand == null) {
                Log.v("brako", "empty command");
                this.forceStop = true;
                return null;
            }

            sendAck(fileDataCommand);
            if (resultCommand == null) {
                resultCommand = new ConnectionCommand(fileDataCommand.type, fileDataCommand.option);
            } else {
                resultCommand.option = concatByteArray(resultCommand.option, fileDataCommand.option);
            }

            receiveSize += fileDataCommand.optionLen;
        }

        return resultCommand;
    }

    private int getFileSize(final byte[] data) {
        if (data == null || data.length < 16)
            return 0;
        ByteBuffer bf = ByteBuffer.wrap(data).order(mOrder);
        return bf.getInt(12);
    }


    protected void sendAck(final ConnectionCommand command) {
        if (command == null)
            return;
        final Message msg = Message.obtain();
        msg.setTarget(mHandler);
        msg.obj = command;
        msg.what = Connection.EVENT_SEND_ACK;
        msg.sendToTarget();
    }

    protected byte[] concatByteArray(final byte[] a, final byte[] b) {
        if (a == null || b == null)
            return a == null ? b : a;

        byte[] c = new byte[a.length + b.length];
        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }

    protected boolean checkHeader(final byte[] data, final int len) {
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
