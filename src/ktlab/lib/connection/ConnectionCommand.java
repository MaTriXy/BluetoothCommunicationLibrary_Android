package ktlab.lib.connection;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ConnectionCommand {

    private final static String LOG_KEY = "ConnectionCommand";
    // Header (type + optionLen) length
    public static final int HEADER_LENGTH = 6;
    public static final char[] BABOLAT_HEADER = {0x55, 0xAA, 0x55, 0xAA};
    public static final int ACK_REPONSE_SIZE = HEADER_LENGTH + 1;
    public static final int HEADER_DATA_SIZE_INDEX = 5;
    public static final int HEADER_COMMAND_INDEX = 4;
    public static final int HEADER_ACK_INDEX = 0;

    // Command fields
    public byte type;
    public int optionLen;
    public byte[] option;
    public int id;

    /**
     * Constructor Create BTCommand without option.
     *
     * @param type Commands type
     */
    public ConnectionCommand(byte type) {
        this(type, null);
    }

    /**
     * Constructor Create BTCommand with option.
     *
     * @param type   Commands type
     * @param option Commands option
     */
    public ConnectionCommand(byte type, byte[] option) {
        this(type, option, -1);
    }

    public ConnectionCommand(byte type, byte[] option, int commandId) {
        this.type = type;
        this.id = commandId;
        if (option != null) {
            optionLen = option.length;
            this.option = new byte[option.length];
            System.arraycopy(option, 0, this.option, 0, option.length);
        } else {
            optionLen = 0;
            this.option = new byte[0];
        }
    }

    /**
     * Convert BTCommand to byte array
     *
     * @param command target command
     * @param order   byte order
     * @return byte array
     * @hide
     */
    protected static byte[] toByteArray(ConnectionCommand command, ByteOrder order) {
        final byte[] header = stringToBytesASCII(BABOLAT_HEADER);
        byte[] ret = new byte[command.optionLen + HEADER_LENGTH];

        ByteBuffer bf = ByteBuffer.wrap(ret).order(order);
        bf.put(header);
        bf.put(command.type);
        bf.put((byte) command.optionLen);
        if (command.optionLen > 0)
            bf.put(command.option);

        Log.v(LOG_KEY, "SEND DATA " + getPrintableBytesArray(ret));

        return ret;
    }

    public static byte[] getHeader() {
        return stringToBytesASCII(BABOLAT_HEADER);
    }

    public static String getPrintableBytesArray(final byte[] data, int length) {
        if (data == null || data.length == 0)
            return "[empty]";
        length = length < 0 ? 0 :length;

        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length && i < data.length; i++) {
            if (i == 0) {
                builder.append('[');
                builder.append(data[i]);
            } else {
                builder.append(',');
                builder.append(data[i]);
            }
            if (i == (data.length - 1)) {
                builder.append(']');
            }
        }
        return builder.toString();
    }

    public static String getPrintableBytesArray(final byte[] data) {
        if (data == null || data.length == 0)
            return "[empty]";

        return getPrintableBytesArray(data, data.length);
    }


    public static byte[] stringToBytesASCII(char[] str) {
        byte[] b = new byte[str.length];
        for (int i = 0; i < str.length; i++) {
            b[i] = (byte) str[i];
        }
        return b;
    }

    /**
     * Convert byte array to BTCommand
     *
     * @param data  byte array
     * @param order Byte order
     * @return BTCommand
     * @hide
     */
    protected static ConnectionCommand fromByteArray(byte[] data, ByteOrder order) {
        ByteBuffer bf = ByteBuffer.wrap(data).order(order);
        byte type = bf.get(ConnectionCommand.HEADER_COMMAND_INDEX);
        int len = bf.get(ConnectionCommand.HEADER_DATA_SIZE_INDEX);
        byte[] option = new byte[len];
        bf.get(option);

        return new ConnectionCommand(type, option);
    }

    /**
     * create BTCommand from Header and Option
     *
     * @param header header(byte array)
     * @param option option(byte array)
     * @return BTCommand
     * @hide
     */
    protected static ConnectionCommand fromHeaderAndOption(byte[] header, byte[] option,
                                                           ByteOrder order) {
//        byte[] data = new byte[header.length + option.length];
//
//        System.arraycopy(header, 0, data, 0, header.length);
//        System.arraycopy(option, 0, data, header.length, option.length);

        ByteBuffer bf = ByteBuffer.wrap(header).order(order);
        byte type = bf.get(ConnectionCommand.HEADER_COMMAND_INDEX);
        return new ConnectionCommand(type, option);
//        return fromByteArray(data, order);
    }
}
