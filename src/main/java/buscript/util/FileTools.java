package buscript.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.logging.Logger;

public class FileTools {

    public static String readFileAsString(File file, Logger log) {
        if (file == null) {
            throw new IllegalArgumentException("File may not be null!");
        }
        if (file.isDirectory()) {
            throw new IllegalArgumentException("File may not be directory!");
        }
        StringBuilder fileData = new StringBuilder(1000);
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            char[] buf = new char[1024];
            int numRead = 0;
            while((numRead = reader.read(buf)) != -1){
                String readData = String.valueOf(buf, 0, numRead);
                fileData.append(readData);
                buf = new char[1024];
            }
        } catch (IOException e) {
            log.warning("Error reading file '" + file + "': " + e.getMessage());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignore) { }
            }
        }
        return fileData.toString();
    }
}
