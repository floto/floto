package io.github.floto.util.task;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class TailingInputStream extends FilterInputStream {
    private volatile boolean fileClosed = false;

    public TailingInputStream(InputStream delegate) {
        super(delegate);
    }

    @Override
    public int read() throws IOException {
        int read;
        while(true) {
            read = super.read();
            if(fileClosed) {
                break;
            }
            if(read >= 0) {
                break;
            }
            sleep();
        }
        return read;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int read;
        while(true) {
            read = super.read(b, off, len);
            if(fileClosed) {
                break;
            }
            if(read >= 0) {
                break;
            }
            sleep();
        }
        return read;
    }

    private void sleep() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void setFileClosed() {
        fileClosed = true;
    }
}
