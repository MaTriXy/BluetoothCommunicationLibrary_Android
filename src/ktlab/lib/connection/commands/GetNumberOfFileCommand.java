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
public class GetNumberOfFileCommand extends CommandReceiveThread {

    private Handler handler;
    public GetNumberOfFileCommand(InputStream in, Message msg, Message msgSend, ByteOrder order) {
        super(in, msg, msgSend, order);
        handler = mMessage.getTarget();
    }

    @Override
    public void run() {

        //read get_user_par ACK
        if(!readAck()) {
            this.forceStop = true;
            return;
        }

        // number_of_log
        final ConnectionCommand nblog = readCommand();
        if(nblog == null) {
            this.forceStop = true;
            return;
        }

        // send start_send_file ack
        mMessageSend.obj = nblog;
        mMessageSend.sendToTarget();

        mMessage.obj = nblog;
        mMessage.sendToTarget();
    }
}
