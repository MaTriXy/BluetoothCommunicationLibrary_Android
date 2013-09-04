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

        // start_send_file_command
        final ConnectionCommand startSendFileCommand = readCommand();
        if(startSendFileCommand == null) {
            this.forceStop = true;
            return;
        }

        // send start_send_file ack
        mMessageSend.obj = startSendFileCommand;
        mMessageSend.sendToTarget();

        // file_data
        final ConnectionCommand fileDataCommand = readCommand();
        if(fileDataCommand == null) {
            this.forceStop = true;
            return;
        }

        // send file_data ack
//        mMessageSend.obj = fileDataCommand;
//        mMessageSend.sendToTarget();

        Message msg = Message.obtain();
        msg.setTarget(handler);
        msg.obj = fileDataCommand;
        msg.what = Connection.EVENT_SEND_ACK;
        msg.sendToTarget();

        mMessage.obj = fileDataCommand;
        mMessage.sendToTarget();

    }
}
