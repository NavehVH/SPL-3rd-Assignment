package bgu.spl.net.impl.tftp;

// import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import bgu.spl.net.api.MessageEncoderDecoder;


public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {

    private boolean endWithZero = false;
    private boolean itsDATA = false;
    private boolean itsACK = false;
    private boolean itsDIRQ = false;
    private boolean itsDisconect = false;
    private short size;
    private byte[] bytes = new byte[1 << 10]; // start with 1k
    private int len = 0;
    private byte [] dataSize = new byte [2];


    @Override
    public byte[] decodeNextByte(byte nextByte) {
        if (len == 1 && (nextByte == 1 || nextByte == 2 || nextByte == 5 || nextByte == 7 || nextByte == 8)) {
            endWithZero = true;

        } else if (len == 1 && nextByte == 3) {
            itsDATA = true;

        } else if (len == 1 && nextByte == 4) {
            itsACK = true;

        } else if (len == 1 && nextByte == 6) {
            itsDIRQ = true;

        } else if (len == 1 && nextByte == 10) {
            itsDisconect = true;

        }

        if (itsDATA) {
            if (len == 2) {
                dataSize [0] = nextByte;

            } else if (len == 3) {
                dataSize [1] = nextByte;
                size = (short) (((short)(dataSize[0] & 0xFF)) << 8 | (short)(dataSize[1] & 0xFF));
                size += 6; 
            }
        }
            
        
        if (endWithZero == true && nextByte == 0) {
            endWithZero = false;
            return popByte();

        } else if (itsDATA && len == size - 1) {
            pushByte(nextByte);
            itsDATA = false;
            return popByte();

        } else if (itsACK && len == 3) {
            pushByte(nextByte);
            itsACK = false;
            return popByte();

        } else if (itsDIRQ && len == 1) {
            pushByte(nextByte);
            itsDIRQ = false;
            return popByte();
            
        } else if (itsDisconect && len == 1) {
            pushByte(nextByte);
            itsDisconect = false;
            return popByte();
        }

        pushByte(nextByte);
        return null;
    }

    @Override
    public byte[] encode(byte[] message) {
        return message;
    }

    private void pushByte(byte nextByte) {
        if (len >= bytes.length) {
            bytes = Arrays.copyOf(bytes, len * 2);
        }

        bytes[len++] = nextByte;
    }

    private byte[] popByte() {
        byte[] result = Arrays.copyOfRange(bytes, 0, len);
        len = 0;
        return result;
    }
}