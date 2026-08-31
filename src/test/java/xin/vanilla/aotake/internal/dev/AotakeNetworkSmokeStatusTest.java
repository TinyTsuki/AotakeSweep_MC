package xin.vanilla.aotake.internal.dev;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AotakeNetworkSmokeStatusTest {
    @Test
    public void readsTheOptionalSparkReportPath() {
        String previous = System.getProperty(AotakeNetworkSmokeStatus.SPARK_REPORT_PROPERTY);
        try {
            System.setProperty(AotakeNetworkSmokeStatus.SPARK_REPORT_PROPERTY, "C:/tmp/aotake.sparkprofile");
            assertEquals("C:/tmp/aotake.sparkprofile", AotakeNetworkSmokeStatus.sparkReport());
        } finally {
            if (previous == null) System.clearProperty(AotakeNetworkSmokeStatus.SPARK_REPORT_PROPERTY);
            else System.setProperty(AotakeNetworkSmokeStatus.SPARK_REPORT_PROPERTY, previous);
        }
    }
}
