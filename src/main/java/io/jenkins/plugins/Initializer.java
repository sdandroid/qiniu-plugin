package io.jenkins.plugins;

import com.qiniu.http.Client;

import java.util.logging.Level;
import java.util.logging.Logger;

public class Initializer {
    private static boolean haveSet = false;
    private static final Logger LOG = Logger.getLogger(Initializer.class.getName());

    static void setAppName() {
        if (!haveSet) {
            Client.setAppName("Qiniu-Jenkins-Plugin");
            haveSet = true;
        }
    }
}
