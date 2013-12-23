package ktlab.lib.connection.commands;

import android.os.Message;
import android.util.Log;

import java.io.InputStream;
import java.nio.ByteOrder;

import ktlab.lib.connection.CommandReceiveThread;
import ktlab.lib.connection.ConnectionCommand;

/**
 * Created by louis on 04/09/13.
 */
public class GetFileCommand extends CommandReceiveThread {


    public GetFileCommand(InputStream in, Message msg, ByteOrder order) {
        super(in, msg, order);
    }

    @Override
    public void run() {



        //read get_user_par ACK
        if (!readAck()) {
            this.forceStop = true;
            return;
        }

        final ConnectionCommand fileDataCommand = receiveFile();
        if (fileDataCommand == null) {
            this.forceStop = true;
            return;
        }
        Log.v(LOG_TAG, "GET FILE FINISHED");
        mMessage.obj = fileDataCommand;
        mMessage.sendToTarget();
    }
}
