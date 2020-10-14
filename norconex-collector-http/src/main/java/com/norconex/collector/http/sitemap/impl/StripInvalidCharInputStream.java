package com.norconex.collector.http.sitemap.impl;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.io.*;

class StripInvalidCharInputStream extends FilterInputStream {
    private static Logger logger = LogManager.getLogger(StripInvalidCharInputStream.class);

    private boolean started;

    protected StripInvalidCharInputStream(InputStream in) {
        super(in);
        started = false;
    }

    @Override
    public int read(byte[] cbuf, int off, int len) throws IOException {
        int read = super.read(cbuf, off, len);
        if (read == -1) {
            return -1;
        }
        int pos = off - 1;
        for (int readPos = off; readPos < off + read; readPos++) {
            // ignore invalid XML 1.0 chars
            if (isInvalid(cbuf[readPos]) || isInvalid(cbuf, readPos) || isBlankStart(cbuf, readPos)) {
                logger.info("found control character: " + cbuf[readPos]);
                continue;
            } else {
                pos++;
            }
            if (pos < readPos) {
                cbuf[pos] = cbuf[readPos];
            }
        }
        return pos - off + 1;
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        started = false;
    }

    public boolean isBlankStart(byte[] cbuf, int readPos) {
        if(!started && cbuf[readPos] != '<') {
            return true;
        } else {
            started = true;
            return false;
        }
    }

    public static boolean isInvalid(byte[] cbuf, int pos) {
        if("&".getBytes()[0] == cbuf[pos] && pos + 1 < cbuf.length  && cbuf[pos+1] != "#".getBytes()[0] ) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean isInvalid(byte c) {
        return ((c >= 0x00 && c <= 0x08) ||
                (c >= 0x0b && c <= 0x0c) ||
                (c >= 0x0e && c <= 0x1F));
    }


}