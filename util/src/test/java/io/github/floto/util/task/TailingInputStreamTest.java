package io.github.floto.util.task;

import com.google.common.base.Charsets;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.Timeout;

import java.io.*;

import static org.junit.Assert.*;

public class TailingInputStreamTest {

    @Rule
    public Timeout timeout = new Timeout(10000);

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void testTailing() throws Exception {
        byte[] buffer = new byte[1];
        File file = temporaryFolder.newFile();
        TailingInputStream tailingInputStream = new TailingInputStream(new FileInputStream(file));
        assertEquals("", read(tailingInputStream));
        assertEquals(0, tailingInputStream.read(buffer));
        FileOutputStream fos = new FileOutputStream(file);
        fos.write("Foobar".getBytes());
        assertEquals("Foobar", read(tailingInputStream));
        assertEquals(0, tailingInputStream.read(buffer));

        tailingInputStream.setFileClosed();
        assertEquals(-1, tailingInputStream.read(buffer));
    }

    private String read(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[1024];
        int readPos = 0;
        int readBytes = 0;
        for(int i = 0; i < 10; i++) {
            readBytes += inputStream.read(buffer, readPos, buffer.length-readPos);
        }
        if(readBytes >= buffer.length) {
            throw new RuntimeException("too many bytes to read");
        }
        return new String(buffer, 0, readBytes, Charsets.UTF_8);
    }

}