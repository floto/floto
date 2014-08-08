package io.github.floto.core.virtualization.workstation;

import com.google.common.base.Throwables;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class VmxUtils {
    public static Map<String, String> readVmx(InputStream inputStream) {
        HashMap<String, String> result = new HashMap<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        reader.lines().forEach((line) -> {
            if (line.startsWith("#")) {
                return;
            }
            String[] parts = line.split("\\s*=\\s*");
            String value = parts[1];
            if (value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            result.put(parts[0].trim(), value);
        });

        return result;
    }

    public static void writeVmx(Map<String, String> vmx, OutputStream outputStream) {
        PrintStream out = new PrintStream(outputStream);
        vmx.forEach((key, value) -> {
            out.print(key);
            out.print("=\"");
            out.print(value);
            out.println("\"");
        });
        out.flush();
    }
}
