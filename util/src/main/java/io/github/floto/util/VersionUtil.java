package io.github.floto.util;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VersionUtil {
    static Logger log = LoggerFactory.getLogger(VersionUtil.class);
    public static String version;
    public static String revision;
    static {
        try {
            version = IOUtils.toString(VersionUtil.class.getResource("version.txt")).replaceAll("\\s", "");
        } catch (Throwable e) {
            log.error("Unable to acquire floto version", e);
            version = "UNKNOWN";
        }
        log.info("Floto version: {}", version);
        try {
            revision = IOUtils.toString(VersionUtil.class.getResource("revision.txt")).replaceAll("\\s", "");
        } catch (Throwable e) {
            log.error("Unable to acquire floto revision", e);
            revision = "UNKNOWN";
        }
        log.info("Floto revision: {}", revision);
    }
}
