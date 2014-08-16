package io.github.floto.core.jobs;

import io.github.floto.core.virtualization.HypervisorService;
import io.github.floto.core.virtualization.esx.EsxHypervisorService;
import io.github.floto.core.virtualization.workstation.WorkstationHypervisorService;
import io.github.floto.dsl.model.*;

import java.io.File;

public abstract class HypervisorJob<T> extends HostJob<T> {

    protected final HypervisorService hypervisorService;

    public HypervisorJob(Manifest manifest, String hostName) {
        super(manifest, hostName);
        this.hypervisorService = createHypervisorService();
    }

    protected HypervisorService createHypervisorService() {
        HypervisorDescription hypervisorDescription = host.vmConfiguration.hypervisor;
        if(hypervisorDescription instanceof WorkstationHypervisorDescription) {
            return new WorkstationHypervisorService(new File(System.getProperty("user.home")+"/.floto/vm"));
        } else if(hypervisorDescription instanceof EsxHypervisorDescription) {
            EsxHypervisorDescription description = (EsxHypervisorDescription) hypervisorDescription;
            return new EsxHypervisorService(description, manifest.site.get("domainName").asText());
        } else {
            throw new IllegalArgumentException("Unknown hypervisor type: "+hypervisorDescription.getClass().getName());
        }
    }

    public HypervisorService getHypervisorService() {
        return hypervisorService;
    }
}

