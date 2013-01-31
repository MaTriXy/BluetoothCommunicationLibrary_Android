package ktlab.lib.connection;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteOrder;

import android.os.Message;

public class CommandSendThread extends Thread {

    private OutputStream mOut;
    private Message mMessage;
    private ConnectionCommand mCommand;
    private ByteOrder mOrder;

    public CommandSendThread(OutputStream out, ConnectionCommand command,
            Message msg, ByteOrder order) {
        mOut = out;
        mCommand = command;
        mMessage = msg;
        mOrder = order;
    }

    @Override
    public void run() {
        try {
            mOut.write(ConnectionCommand.toByteArray(mCommand, mOrder));
        } catch (IOException e) {
            mMessage.what = Connection.EVENT_CONNECTION_FAIL;
        }

        mMessage.sendToTarget();
    }
}
