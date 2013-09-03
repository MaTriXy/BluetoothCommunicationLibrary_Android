package ktlab.lib.connection;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteOrder;
import java.util.LinkedList;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

public abstract class Connection extends Handler {

    private static final String TAG = "Connection";

    public enum  CONNECTION_STATE {
        IDLE,
        WAITING_ANSWER,
    }

    // Event
    public static final int EVENT_CONNECT_COMPLETE      = 1;
    public static final int EVENT_DATA_RECEIVED         = 2;
    public static final int EVENT_DATA_SEND_COMPLETE    = 3;
    public static final int EVENT_UNKNOWN_COMMAND       = 4;
    public static final int EVENT_CONNECTION_FAIL       = 101;

    protected ConnectionCallback mCallback;

    // communication thread
    protected ConnectionThread mConnectionThread;
    protected CommandReceiveThread mReceiveThread;
    protected CommandSendThread mSendThread;

    // stream
    protected InputStream mInput;
    protected OutputStream mOutput;

    // send/close flag
    protected boolean isSending = false;
    protected boolean forceStop = false;

    // send data queue
    protected ConnectionCommand mCurrentCommand;
    protected CONNECTION_STATE mState = CONNECTION_STATE.IDLE;
    protected final boolean canQueueing;
    protected LinkedList<PendingData> mQueue = null;
    private final ByteOrder mOrder;

    @Override
    public void handleMessage(Message msg) {

        if (forceStop) {
            mConnectionThread.close();
            return;
        }

        switch (msg.what) {
        case EVENT_CONNECT_COMPLETE:
            Log.i(TAG, "connect complete");
            mInput = mConnectionThread.getInputStream();
            mOutput = mConnectionThread.getOutputStream();
            mCallback.onConnectComplete();

            // receive thread starting
            mReceiveThread = new CommandReceiveThread(mInput, obtainMessage(EVENT_DATA_RECEIVED),
                    mOrder);
            mReceiveThread.start();
            break;

        case EVENT_DATA_RECEIVED:
            ConnectionCommand cmd = (ConnectionCommand) msg.obj;
//            Log.i(TAG, "data received, id : " +  cmd.type);
            mCallback.onCommandReceived(cmd);

            // receive thread starting
            mReceiveThread = null;
            mReceiveThread = new CommandReceiveThread(mInput, obtainMessage(EVENT_DATA_RECEIVED),
                    mOrder);
            mReceiveThread.start();
            break;
        case EVENT_UNKNOWN_COMMAND:
            //TODO sen error message when receive nack
            break;
        case EVENT_DATA_SEND_COMPLETE:
            int id = msg.arg1;
//            Log.i(TAG, "data send complete, id : " + id);
            mSendThread = null;
            isSending = false;
            mCallback.onDataSendComplete(id);

            ConnectionCommand command = (ConnectionCommand) msg.obj;

            if(isLastAllowed(command.type)) {
                mState = CONNECTION_STATE.IDLE;
            }

            // if queueing data exists, send first data
            if (mState == CONNECTION_STATE.IDLE) {
                sendPendingData();
            }
            break;

        case EVENT_CONNECTION_FAIL:
            Log.e(TAG, "connection failed");
            mSendThread = null;
            isSending = false;
            mCallback.onConnectionFailed();
            break;

        default:
            Log.e(TAG, "Unknown Event");
        }
    }

    /**
     * Constructor
     *
     * @param cb
     *            callback for communication result
     * @param canQueueing
     *            true if can queue sending data
     */
    protected Connection(ConnectionCallback cb, boolean canQueueing) {
        this(cb, canQueueing, ByteOrder.nativeOrder());
    }

    /**
     * Constructor
     *
     * @param cb
     *            callback for communication result
     * @param canQueueing
     *            true if can queue sending data
     * @param order
     *            byte order of the destination
     */
    protected Connection(ConnectionCallback cb, boolean canQueueing, ByteOrder order) {
        mCallback = cb;

        this.canQueueing = canQueueing;
        if (canQueueing) {
            mQueue = new LinkedList<PendingData>();
        }

        mOrder = order;
    }

    /**
     * stop connection. this method must be called when application will stop
     * connection
     */
    public void stopConnection() {

        forceStop = true;

        // stop connection thread
        mConnectionThread.close();

        // stop receive thread
        if (mReceiveThread != null) {
            mReceiveThread.forceStop();
            mReceiveThread = null;
        }

        // stop send thread
        mSendThread = null;
        clearQueuedData();

        mInput = null;
        mOutput = null;
    }

    private boolean isAllowed(final byte type) {
        byte[] allowed = null;
        if(mCurrentCommand != null)
            allowed = mCurrentCommand.allowed;
        if(allowed == null || allowed.length == 0)
            return false;
        for(int i = 0; i < allowed.length; i++) {
            if(allowed[i] == type) {
                return true;
            }
        }
        return false;
    }

    private boolean isLastAllowed(final byte type) {
        byte[] allowed = null;
        if(mCurrentCommand != null)
            allowed = mCurrentCommand.allowed;
        if(allowed == null || allowed.length == 0)
            return true;
        if(allowed[allowed.length - 1] == type) {
            return true;
        }
        return false;
    }

    /**
     *
     * @param type
     *            command type
     * @param data
     *            option data
     * @param id
     *            send id
     * @return return true if success sending or queueing data. if "canQueueing"
     *         is false and sending any data, return false.
     */
    public boolean sendData(byte type, byte[] data, int id, byte[] allowed) {

        // if sending data, queueing...
        if (mState == CONNECTION_STATE.WAITING_ANSWER && !isAllowed(type)) {
            if (canQueueing) {
                synchronized (mQueue) {
                    PendingData p = new PendingData(id, new ConnectionCommand(type, data, allowed));
                    mQueue.offer(p);
                }
                Log.i(TAG, "sendData(), pending...");
                return true;
            } else {
                return false;
            }
        }

        Message msg = obtainMessage(EVENT_DATA_SEND_COMPLETE);
        msg.arg1 = id;
        ConnectionCommand command = new ConnectionCommand(type, data, allowed);
        msg.obj = command;

        if(mState == CONNECTION_STATE.IDLE) {
            mCurrentCommand = command;
            mState = CONNECTION_STATE.WAITING_ANSWER;
        }

        mSendThread = new CommandSendThread(mOutput, command, msg, mOrder);
        mSendThread.start();
        isSending = true;
        return true;
    }

    /**
     *
     * @param type
     *            command type
     * @param id
     *            send id
     * @return return true if success sending or queueing data. if "canQueueing"
     *         is false and sending any data, return false.
     */
    public boolean sendData(byte type, int id) {

        // if sending data, queueing...
        if (isSending) {
            if (canQueueing) {
                synchronized (mQueue) {
                    PendingData p = new PendingData(id, new ConnectionCommand(type));
                    mQueue.offer(p);
                }
                Log.i(TAG, "sendData(), pending...");
                return true;
            } else {
                return false;
            }
        }

        Message msg = obtainMessage(EVENT_DATA_SEND_COMPLETE);
        msg.arg1 = id;
        ConnectionCommand command = new ConnectionCommand(type);
        mSendThread = new CommandSendThread(mOutput, command, msg, mOrder);
        mSendThread.start();

        isSending = true;
        return true;
    }

    /**
     * send data internal.
     *
     * @param pendingData
     *            pending data
     * @return always true
     * @hide
     */
    private boolean sendData(PendingData pendingData) {

        Log.i(TAG, "send PendingData");
        Message msg = obtainMessage(EVENT_DATA_SEND_COMPLETE);
        msg.arg1 = pendingData.id;
        msg.obj = pendingData.command;


        mCurrentCommand = pendingData.command;
        mState = CONNECTION_STATE.WAITING_ANSWER;

        mSendThread = new CommandSendThread(mOutput, pendingData.command, msg, mOrder);
        mSendThread.start();

        isSending = true;
        return true;
    }

    /**
     * send pending data if exists.
     *
     * @hide
     */
    private void sendPendingData() {
        PendingData pendingData = null;
        synchronized (mQueue) {
            if (mQueue.size() > 0) {
                pendingData = mQueue.poll();
            }
        }
        if (pendingData != null) {
            sendData(pendingData);
        }
    }

    /**
     * clear queue data
     *
     * @hide
     */
    private void clearQueuedData() {
        if (canQueueing) {
            synchronized (mQueue) {
                mQueue.clear();
            }
        }
    }

    /**
     * pending data
     *
     * @hide
     */
    private class PendingData {
        int id;
        ConnectionCommand command;

        PendingData(int id, ConnectionCommand command) {
            this.id = id;
            this.command = command;
        }
    }

    abstract public void startConnection();
}
