package org.greencheek.jvmtools;

import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.tools.attach.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

public class HeapDumper {
    private final static Logger logger = LoggerFactory.getLogger(HeapDumper.class);
    private final static String DEFAULT_HEAP_DUMP_FILE = "/tmp/jvm" + UUID.randomUUID().toString() + ".hprof";
    private final static String HEAP_DUMP_FILE = System.getProperty("dumpfile",DEFAULT_HEAP_DUMP_FILE);
    private final static String DEFAULT_DUMP_LIVE_ONLY = "false";
    private final static boolean DUMP_LIVE_ONLY = Boolean.parseBoolean(System.getProperty("dumpliveonly",DEFAULT_DUMP_LIVE_ONLY));

    public static void main(String[] args) {
        HeapDumper a = new HeapDumper();
        a.showData(args[0]);


    }

    private void showData(String pid) {
        List<VirtualMachineDescriptor> vms = VirtualMachine.list();
        for (VirtualMachineDescriptor virtualMachineDescriptor : vms) {
            String id = virtualMachineDescriptor.id();
            if(pid.equals(id)) {
                logger.info("============ JVM found, pid: {}, displayName: {}", virtualMachineDescriptor.id(), virtualMachineDescriptor.displayName());
                VirtualMachine virtualMachine = null;
                try {
                    virtualMachine = attach(virtualMachineDescriptor);
                    if (virtualMachine != null) {
                        String jmxUrl = getJmxUrl(virtualMachine);
                        readDataWithJmx(jmxUrl);
                    }
                } finally {
                    detachSilently(virtualMachine);
                }
            }

        }
    }

    private VirtualMachine attach(VirtualMachineDescriptor virtualMachineDescriptor) {
        VirtualMachine virtualMachine = null;
        try {
            virtualMachine = VirtualMachine.attach(virtualMachineDescriptor);
        } catch (AttachNotSupportedException anse) {
            logger.error("Couldn't attach", anse);
        } catch (IOException ioe) {
            logger.error("Exception attaching or reading a jvm.", ioe);
        } finally {
//            detachSilently(virtualMachine);
        }
        return virtualMachine;
    }

    private String readSystemProperty(VirtualMachine virtualMachine, String propertyName) {
        String propertyValue = null;
        try {
            Properties systemProperties = virtualMachine.getSystemProperties();
            propertyValue = systemProperties.getProperty(propertyName);
        } catch (IOException e) {
            logger.error("Reading system property failed", e);
        }
        return propertyValue;
    }

    private void detachSilently(VirtualMachine virtualMachine) {
        if (virtualMachine != null) {
            try {
                virtualMachine.detach();
            } catch (IOException e) {
                e.printStackTrace();
            }
        };
    }

    private String getJmxUrl(VirtualMachine virtualMachine) {
        String jmxUrl = readAgentProperty(virtualMachine, "com.sun.management.jmxremote.localConnectorAddress");
        if (jmxUrl == null) {
            loadMangementAgent(virtualMachine);
            jmxUrl = readAgentProperty(virtualMachine, "com.sun.management.jmxremote.localConnectorAddress");
        }
        logger.info("JMX URL found = {}", jmxUrl);
        return jmxUrl;
    }

    private void loadMangementAgent(VirtualMachine virtualMachine) {
        final String id = virtualMachine.id();
        String agent = null;
        Boolean loaded = false;
        try {
            String javaHome = readSystemProperty(virtualMachine, "java.home");
            agent = javaHome + "/lib/management-agent.jar";
            virtualMachine.loadAgent(agent);
            loaded = true;
        } catch (IOException e) {
            logger.error("Reading system properties or loading the agent resulted in a exception for pid " + id, e);
        } catch (AgentLoadException e) {
            logger.error("Loading agent failed for pid " + id, e);
        } catch (AgentInitializationException e) {
            logger.error("Agent initialization failed for pid " + id, e);
        }
        logger.info("Loading management agent \" succeeded = {}",loaded);
    }

    private String readAgentProperty(VirtualMachine virtualMachine, String propertyName) {
        String propertyValue = null;
        try {
            Properties agentProperties = virtualMachine.getAgentProperties();
            propertyValue = agentProperties.getProperty(propertyName);
        } catch (IOException e) {
            logger.error("Reading agent property failed", e);
        }
        return propertyValue;
    }

    private void readDataWithJmx(String jmxUrl) {
        JvmMxBean mxBean = new JvmMxBean(jmxUrl);
        try {
            if (mxBean.connect()) {
                RuntimeMXBean runtimeMXBean = mxBean.getRuntimeMXBean();
                logger.info("Jvm uptime = {}", runtimeMXBean.getUptime());
                OperatingSystemMXBean operatingSystemMXBean = mxBean.getOperatingSystemMXBean();
                logger.info("available processors = {}", operatingSystemMXBean.getAvailableProcessors());

                HotSpotDiagnosticMXBean hotspotBean = mxBean.getHotspotMBean();
                dumpheap(hotspotBean);
            }
        } finally {
            mxBean.disconnect();
        }
    }

    private void dumpheap(HotSpotDiagnosticMXBean hotspotMBean) {
        try {
            String hprof = HEAP_DUMP_FILE;

            if(new File(hprof).exists()) {
                logger.info("heap dump {} already exists.  Will dump to {} instead",hprof,DEFAULT_HEAP_DUMP_FILE);
                hprof = DEFAULT_HEAP_DUMP_FILE;
            }

            logger.info("Dumping heap... {}",hprof);
            hotspotMBean.dumpHeap(hprof, DUMP_LIVE_ONLY);
            logger.info("finished heap dump {}", hprof);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception exp) {
            throw new RuntimeException(exp);
        }
    }


}