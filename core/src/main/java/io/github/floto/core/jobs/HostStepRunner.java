package io.github.floto.core.jobs;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Throwables;
import io.github.floto.core.FlotoService;
import io.github.floto.core.util.TemplateUtil;
import io.github.floto.core.virtualization.HypervisorService;
import io.github.floto.dsl.model.Host;
import io.github.floto.dsl.model.Manifest;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class HostStepRunner {
    private Logger log = LoggerFactory.getLogger(HostStepRunner.class);

    private Host host;
    private final FlotoService flotoService;
    private final Manifest manifest;
    private final HypervisorService hypervisorService;
    private final String vmName;

    public HostStepRunner(Host host, FlotoService flotoService, Manifest manifest, HypervisorService hypervisorService, String vmName) {
        this.host = host;
        this.flotoService = flotoService;
        this.manifest = manifest;
        this.hypervisorService = hypervisorService;
        this.vmName = vmName;
    }


    public void run(List<JsonNode> steps) {
        Map<String, Object> globalConfig = flotoService.createGlobalConfig(manifest);
        HostManipulator hostManipulator = new HypervisorHostManipulator(hypervisorService, vmName);
        for (JsonNode step : steps) {
            String stepType = step.get("type").asText();
            switch (stepType) {
                case "ADD_TEMPLATE":
                    String destination = step.path("destination").asText();
                    String templated = new TemplateUtil().getTemplate(step, globalConfig);
                    hostManipulator.writeToVm(templated, destination);
                    break;
                case "RUN":
                    String line = step.path("line").asText();
                    if (flotoService.isUseProxy()) {
                        line = "http_proxy='" + flotoService.getHttpProxyUrl() + "' " + line;
                    }
                    hostManipulator.run(line);
                    break;
                case "DETERMINE_IP":
                    String command = step.path("command").asText();
                    File ipFile = null;
                    try {
                        ipFile = File.createTempFile("floto-", "-guestFile");
                        hypervisorService.runInVm(vmName, command + " > /ip.txt");
                        hypervisorService.runInVm(vmName, "chmod 755 /ip.txt");
                        hypervisorService.copyFileFromGuest(vmName, "/ip.txt", ipFile);
                        String ipAddress = FileUtils.readFileToString(ipFile).replaceAll("\\s", "");
                        if (ipAddress.isEmpty()) {
                            ipAddress = host.ip;
                        }
                        log.info("Using IP {} for host {}", ipAddress, vmName);
                        flotoService.setExternalHostIp(host.name, ipAddress);
                        hostManipulator = new SshHostManipulator(ipAddress);
                        hypervisorService.runInVm(vmName, "rm /ip.txt");
                    } catch (IOException e) {
                        throw Throwables.propagate(e);
                    } finally {
                        FileUtils.deleteQuietly(ipFile);
                    }
                    break;
                default:
                    throw new IllegalArgumentException("No handler for step type " + step + "\n" + step);
            }
        }

    }
}
