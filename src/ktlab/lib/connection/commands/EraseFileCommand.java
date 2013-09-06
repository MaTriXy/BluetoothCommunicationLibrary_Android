package ktlab.lib.connection.commands;

import android.os.Handler;
import android.os.Message;

import java.io.InputStream;
import java.nio.ByteOrder;

import ktlab.lib.connection.CommandReceiveThread;
import ktlab.lib.connection.Connection;
import ktlab.lib.connection.ConnectionCommand;

/**
 * Created by louis on 04/09/13.
 */
public class EraseFileCommand extends CommandReceiveThread {

    private Handler handler;
    public EraseFileCommand(InputStream in, Message msg, ByteOrder order) {
        super(in, msg, order);
        handler = mMessage.getTarget();
    }

    @Override
    public void run() {

        //read get_user_par ACK
        if(!readAck()) {
            this.forceStop = true;
            return;
        }

        mMessage.obj = null;
        mMessage.sendToTarget();

    }
}
