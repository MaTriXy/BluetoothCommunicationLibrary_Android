package ktlab.lib.connection;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteOrder;

import android.os.Message;
import android.util.Log;

public class CommandSendThread extends Thread {

    private OutputStream mOut;
    private Message mMessage;
    private ConnectionCommand mCommand;
    private ByteOrder mOrder;

    public CommandSendThread(OutputStream out, ConnectionCommand command,
                             Message msg, ByteOrder order) {
        Log.v("CommandSendThread", "creating command send thread with command " + command.type);
        mOut = out;
        mCommand = command;
        mMessage = msg;
        mOrder = order;
    }

    public ConnectionCommand getCommand() {
        return mCommand;
    }

    @Override
    public void run() {
        if(mOut != null) {
            try {
                mOut.write(ConnectionCommand.toByteArray(mCommand, mOrder));
            } catch (IOException e) {
                e.printStackTrace();
                mMessage.what = Connection.EVENT_CONNECTION_FAIL;
            }
        } else {
            mMessage.what = Connection.EVENT_CONNECTION_FAIL;
        }

        mMessage.sendToTarget();
    }
}
