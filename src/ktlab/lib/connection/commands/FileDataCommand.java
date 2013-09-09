package ktlab.lib.connection.commands;

import android.os.Message;

import java.io.InputStream;
import java.nio.ByteOrder;

import ktlab.lib.connection.CommandReceiveThread;
import ktlab.lib.connection.ConnectionCommand;

/**
 * Created by louis on 04/09/13.
 */
public class FileDataCommand extends CommandReceiveThread {


    public FileDataCommand(InputStream in, Message msg, ByteOrder order) {
        super(in, msg, order);
    }

    @Override
    public void run() {

        //read get_user_par ACK
        if (!readAck()) {
            this.forceStop = true;
            return;
        }

        mMessage.obj = null;
        mMessage.sendToTarget();
    }
}
