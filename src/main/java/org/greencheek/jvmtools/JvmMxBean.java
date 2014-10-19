package org.greencheek.jvmtools;

import com.sun.management.HotSpotDiagnosticMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.ListenerNotFoundException;
import javax.management.MBeanServerConnection;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.remote.JMXConnectionNotification;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;

public class JvmMxBean implements NotificationListener {

    private Logger logger = LoggerFactory.getLogger(JvmMxBean.class);
    
    private final String jmxUrl;
    private static final String HOTSPOT_BEAN_NAME = "com.sun.management:type=HotSpotDiagnostic";
    private MBeanServerConnection mBeanServerConnection;
    private JMXConnector connector;

    private RuntimeMXBean runtimeMXBean;
    private OperatingSystemMXBean operatingSystemMXBean;
    private HotSpotDiagnosticMXBean hotSpotDiagnosticMXBean;

    public JvmMxBean(final String jmxUrl) {
        super();
        this.jmxUrl = jmxUrl;
    }
    
    public RuntimeMXBean getRuntimeMXBean() {
        if (mBeanServerConnection != null && runtimeMXBean == null) {
            runtimeMXBean = getMxBean(ManagementFactory.RUNTIME_MXBEAN_NAME, RuntimeMXBean.class);
        }
        return runtimeMXBean;
    }

    public synchronized OperatingSystemMXBean getOperatingSystemMXBean() {
        if (mBeanServerConnection != null && operatingSystemMXBean == null) {
            operatingSystemMXBean = getMxBean(ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME, OperatingSystemMXBean.class);
        }
        return operatingSystemMXBean;
    }

    // get the hotspot diagnostic MBean from the
    // platform MBean server
    public synchronized HotSpotDiagnosticMXBean getHotspotMBean() {
        if (mBeanServerConnection != null && hotSpotDiagnosticMXBean == null) {
            hotSpotDiagnosticMXBean = getMxBean(HOTSPOT_BEAN_NAME, HotSpotDiagnosticMXBean.class);
        }
        return hotSpotDiagnosticMXBean;
    }


    public boolean connect() {
        try {
            JMXServiceURL url = new JMXServiceURL(jmxUrl);
            connector = JMXConnectorFactory.newJMXConnector(url, null);
            connector.addConnectionNotificationListener(this, null, jmxUrl);
            connector.connect();
            mBeanServerConnection = connector.getMBeanServerConnection();
            return true;
        } catch (Exception e) {
            disconnect();
            throw new IllegalStateException("Couldn't connect to JVM with URL: " + jmxUrl, e);
        }
    }
    
    public boolean disconnect() {
        boolean result = false;
        try {
            if (connector != null) {
                connector.removeConnectionNotificationListener(this);
                connector.close();
            }
            result = true;
        } catch (ListenerNotFoundException e) {
            logger.debug("Removing the listener from the connector resulted in an exception, but is ignored.", e);
            result = true;
        } catch (Exception e) {
            logger.error("Closing the JMX connection failed.", e);
            throw new IllegalStateException("Closing the connection failed for JVM", e);
        } finally {
            mBeanServerConnection = null;
            connector = null;
        }
        return result;
    }

    @Override
    public void handleNotification(Notification notification, Object handback) {
        final JMXConnectionNotification noti = (JMXConnectionNotification) notification;
        if (!handback.equals(jmxUrl)) {
            return;
        }
        logger.info("Handling \"{}\" noitification from MBeanServer", noti.toString());
        if (noti.getType().equals(JMXConnectionNotification.CLOSED)) {
            disconnect();
        } else if (noti.getType().equals(JMXConnectionNotification.FAILED)) {
            disconnect();
        } else if (noti.getType().equals(JMXConnectionNotification.NOTIFS_LOST)) {
            disconnect();
        }
    }

    private <MX> MX getMxBean(String mxBeanName, Class<MX> mxBeanInterfaceClass) {
        MX result = null;
        if (mBeanServerConnection != null) {
            try {
                result = ManagementFactory.newPlatformMXBeanProxy(mBeanServerConnection, mxBeanName, mxBeanInterfaceClass);
            } catch (IOException ioe) {
                logger.error("A communication problem occured with jvm", ioe);
            } catch (IllegalArgumentException iae) {
                logger.error("A configuration problem resulted in an exception for jvm", iae);
            }
        }
        return result;
    }
}