package ktlab.lib.connection.commands;

import android.os.Handler;
import android.os.Message;

import java.io.InputStream;
import java.nio.ByteOrder;

import ktlab.lib.connection.CommandReceiveThread;

/**
 * Created by louis on 04/09/13.
 */
public class PowerDownCommand extends CommandReceiveThread {

    private Handler handler;
    public PowerDownCommand(InputStream in, Message msg, Message msgSend, ByteOrder order) {
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

        mMessage.obj = null;
        mMessage.sendToTarget();
    }
}
