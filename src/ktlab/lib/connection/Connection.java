package ktlab.lib.connection;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteOrder;
import java.util.LinkedList;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import ktlab.lib.connection.commands.EraseFileCommand;
import ktlab.lib.connection.commands.FileDataCommand;
import ktlab.lib.connection.commands.GetFileCommand;
import ktlab.lib.connection.commands.GetNumberOfFileCommand;
import ktlab.lib.connection.commands.GetUserParCommand;
import ktlab.lib.connection.commands.PowerDownCommand;
import ktlab.lib.connection.commands.ResetCommand;
import ktlab.lib.connection.commands.SetTimeCommand;
import ktlab.lib.connection.commands.StartSendFileCommand;

public abstract class Connection extends Handler {

    private static final String TAG = "Connection";

    public enum CONNECTION_STATE {
        REQUEST_STARTED,
        REQUEST_FINISHED,
    }

    // Event
    public static final int EVENT_CONNECT_COMPLETE = 1;
    public static final int EVENT_REQUEST_FINISHED = 2;
    public static final int EVENT_REQUEST_STARTED = 3;
    public static final int EVENT_DATA_SEND_COMPLETE = 4;
    public static final int EVENT_UNKNOWN_COMMAND = 5;
    public static final int EVENT_SEND_ACK = 6;
    public static final int EVENT_RECEIVE_NACK = 7;
    public static final int EVENT_CONNECTION_FAIL = 101;

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
    protected CONNECTION_STATE mState = CONNECTION_STATE.REQUEST_FINISHED;
    protected final boolean canQueueing;
    protected LinkedList<PendingData> mQueue = null;
    protected LinkedList<PendingData> mInnerSendQueue = null;
    private final ByteOrder mOrder;

    @Override
    public void handleMessage(Message msg) {

        if (forceStop) {
            mConnectionThread.close();
            return;
        }

        switch (msg.what) {
            case EVENT_CONNECT_COMPLETE:
                Log.v(TAG, "=EVENT_CONNECT_COMPLETE=");
                mInput = mConnectionThread.getInputStream();
                mOutput = mConnectionThread.getOutputStream();
                mCallback.onConnectComplete();
                break;
            case EVENT_REQUEST_STARTED:
                Log.v(TAG, "=EVENT_REQUEST_STARTED=");
                final ConnectionCommand command = (ConnectionCommand) msg.obj;
                mReceiveThread = getReceiveThread(command.type);
                if (mReceiveThread != null) {
                    mReceiveThread.start();
                } else {
                    mState = CONNECTION_STATE.REQUEST_FINISHED;
                    sendPendingData();
                }
                break;
            case EVENT_RECEIVE_NACK:
                Log.v(TAG, "=EVENT_RECEIVE_NACK=");
                mState = CONNECTION_STATE.REQUEST_FINISHED;
                sendPendingData();
                break;
            case EVENT_SEND_ACK:
                Log.v(TAG, "=EVENT_SEND_ACK=");
                ConnectionCommand commandSendAck = (ConnectionCommand) msg.obj;
                this.sendDataInternal(commandSendAck.type, getAckData(true), mCurrentCommand.id);
                break;
            case EVENT_REQUEST_FINISHED:
                Log.v(TAG, "=EVENT_REQUEST_FINISHED=");
                ConnectionCommand cmd = (ConnectionCommand) msg.obj;

                if (cmd == null)
                    cmd = mCurrentCommand;

                Log.v(TAG, "Request finished: received command " + cmd.id);

                Log.v(TAG, "Request finished: " + mCurrentCommand.id);
                mCallback.onCommandReceived(mCurrentCommand.id, cmd);
                mReceiveThread = null;
                mState = CONNECTION_STATE.REQUEST_FINISHED;
//                isSending = false;
                sendPendingDataInternal();
                sendPendingData();
                break;
            case EVENT_DATA_SEND_COMPLETE:
                Log.v(TAG, "=EVENT_DATA_SEND_COMPLETE=");
                int id = msg.arg1;
                mSendThread = null;
                isSending = false;
                mCallback.onDataSendComplete(id);
                sendPendingDataInternal();
                sendPendingData();
                break;
            case EVENT_CONNECTION_FAIL:
                Log.v(TAG, "=EVENT_CONNECTION_FAIL=");
                mSendThread = null;
                isSending = false;
                Log.v("IS_SENDING", "EVENT_CONNECTION_FAIL false");
                mCallback.onConnectionFailed();
                break;

            default:
                Log.e(TAG, "Unknown Event");
        }
    }

    private CommandReceiveThread getReceiveThread(final byte type) {
        if (type == 0) {
            return new GetNumberOfFileCommand(mInput, obtainMessage(EVENT_REQUEST_FINISHED), mOrder);
        } else if (type == 8) {
            return new GetUserParCommand(mInput, obtainMessage(EVENT_REQUEST_FINISHED), mOrder);
        } else if (type == 6) {
            return new EraseFileCommand(mInput, obtainMessage(EVENT_REQUEST_FINISHED), mOrder);
        } else if (type == 7) {
            return new GetFileCommand(mInput, obtainMessage(EVENT_REQUEST_FINISHED), mOrder);
        } else if (type == 9) {
            return new SetTimeCommand(mInput, obtainMessage(EVENT_REQUEST_FINISHED), mOrder);
        } else if (type == 11) {
            return new PowerDownCommand(mInput, obtainMessage(EVENT_REQUEST_FINISHED), mOrder);
        } else if (type == 4) {
            return new StartSendFileCommand(mInput, obtainMessage(EVENT_REQUEST_FINISHED), mOrder);
        } else if (type == 5) {
            return new FileDataCommand(mInput, obtainMessage(EVENT_REQUEST_FINISHED), mOrder);
        } else if (type == 10) {
            return new ResetCommand(mInput, obtainMessage(EVENT_REQUEST_FINISHED), mOrder);
        }
        return null;
    }

    private byte[] getAckData(boolean acknowleged) {
        final byte[] acked = {0x01};
        if (!acknowleged)
            acked[0] = 0x00;
        return acked;
    }

    /**
     * Constructor
     *
     * @param cb          callback for communication result
     * @param canQueueing true if can queue sending data
     */
    protected Connection(ConnectionCallback cb, boolean canQueueing) {
        this(cb, canQueueing, ByteOrder.nativeOrder());
    }

    /**
     * Constructor
     *
     * @param cb          callback for communication result
     * @param canQueueing true if can queue sending data
     * @param order       byte order of the destination
     */
    protected Connection(ConnectionCallback cb, boolean canQueueing, ByteOrder order) {
        mCallback = cb;

        this.canQueueing = canQueueing;
        if (canQueueing) {
            mQueue = new LinkedList<PendingData>();
        }
        mInnerSendQueue = new LinkedList<PendingData>();
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

    /**
     * @param type command type
     * @param data option data
     * @param id   send id
     * @return return true if success sending or queueing data. if "canQueueing"
     * is false and sending any data, return false.
     */
    public boolean sendData(byte type, byte[] data, int id) {

        if (mState == CONNECTION_STATE.REQUEST_STARTED || isSending) {
            synchronized (mQueue) {
                PendingData p = new PendingData(id, new ConnectionCommand(type, data, id));
                mQueue.offer(p);
            }
            Log.i(TAG, "Request type " + type + " , pending...");
            return true;

        }

        Message msg = obtainMessage(EVENT_REQUEST_STARTED);
        msg.arg1 = id;
        ConnectionCommand command = new ConnectionCommand(type, data, id);
        msg.obj = command;

        mCurrentCommand = command;
        mState = CONNECTION_STATE.REQUEST_STARTED;
        mSendThread = new CommandSendThread(mOutput, command, msg, mOrder);
        mSendThread.start();
        return true;
    }

    private boolean sendDataInternal(byte type, byte[] data, int id) {
        if (isSending) {
            synchronized (mInnerSendQueue) {
                PendingData p = new PendingData(id, new ConnectionCommand(type));
                mInnerSendQueue.offer(p);
                Log.i(TAG, "QUEUE PendingData internal, id " + p.command.type);
            }
            return true;
        }

        Message msg = obtainMessage(EVENT_DATA_SEND_COMPLETE);
        msg.arg1 = id;
        ConnectionCommand command = new ConnectionCommand(type, data, id);
        msg.obj = command;

        mSendThread = new CommandSendThread(mOutput, command, msg, mOrder);
        mSendThread.start();
        isSending = true;
        Log.v("IS_SENDING", "sendDataInternal(byte type, byte[] data, int id) true");
        return true;
    }

    private boolean sendDataInternal(PendingData pendingData) {

        Message msg = obtainMessage(EVENT_DATA_SEND_COMPLETE);
        msg.arg1 = pendingData.id;
        msg.obj = pendingData.command;

        Log.i(TAG, "SEND PendingData internal, id " + pendingData.command.type);

        mSendThread = new CommandSendThread(mOutput, pendingData.command, msg, mOrder);
        mSendThread.start();
        isSending = true;
        return true;
    }

    /**
     * @param type command type
     * @param id   send id
     * @return return true if success sending or queueing data. if "canQueueing"
     * is false and sending any data, return false.
     */
    public boolean sendData(byte type, int id) {

        // if sending data, queueing...
        if (mState == CONNECTION_STATE.REQUEST_STARTED || isSending) {
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

        Message msg = obtainMessage(EVENT_REQUEST_STARTED);
        msg.arg1 = id;
        ConnectionCommand command = new ConnectionCommand(type);
        mSendThread = new CommandSendThread(mOutput, command, msg, mOrder);
        mSendThread.start();
        Log.v("IS_SENDING", "sendData(byte type, int id) true");
        isSending = true;
        return true;
    }

    /**
     * send data internal.
     *
     * @param pendingData pending data
     * @return always true
     * @hide
     */
    private boolean sendData(PendingData pendingData) {


        Message msg = obtainMessage(EVENT_REQUEST_STARTED);
        msg.arg1 = pendingData.id;
        msg.obj = pendingData.command;

        Log.i(TAG, "SEND PendingData, id " + pendingData.command.type);

        mCurrentCommand = pendingData.command;
        mState = CONNECTION_STATE.REQUEST_STARTED;

        mSendThread = new CommandSendThread(mOutput, pendingData.command, msg, mOrder);
        mSendThread.start();
        return true;
    }

    /**
     * send pending data if exists.
     *
     * @hide
     */
    private void sendPendingData() {
        PendingData pendingData = null;
        if (!isSending && mState == CONNECTION_STATE.REQUEST_FINISHED) {
            synchronized (mQueue) {
                if (mQueue.size() > 0) {
                    pendingData = mQueue.poll();
                }
            }
        }
        if (pendingData != null) {
            sendData(pendingData);
        }
    }

    private void sendPendingDataInternal() {
        PendingData pendingData = null;
        if (!isSending) {
            synchronized (mInnerSendQueue) {
                if (mInnerSendQueue.size() > 0) {
                    pendingData = mQueue.poll();
                }
            }
        }
        if (pendingData != null) {
            sendDataInternal(pendingData);
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

        synchronized (mInnerSendQueue) {
            mInnerSendQueue.clear();
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
