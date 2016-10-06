package io.github.floto.core.jobs;

import java.io.File;

import io.github.floto.core.virtualization.HypervisorService;

public class HypervisorHostManipulator implements HostManipulator {

    private HypervisorService hypervisorService;
    private String vmName;

    public HypervisorHostManipulator(HypervisorService hypervisorService, String vmName) {
        this.hypervisorService = hypervisorService;
        this.vmName = vmName;
    }

    @Override
    public void run(String command) {
        hypervisorService.runInVm(vmName, command);
    }

    @Override
    public void writeToVm(String content, String destination) {
        String command = "cat << 'EOFEOFEOF' > "+destination+"\n" + content + "\nEOFEOFEOF\n";
        hypervisorService.runInVm(vmName, command);

    }

	@Override
	public void copyToVm(File file, String destination) {
		throw new UnsupportedOperationException("copy To VM not supported before determineIp was called");
	}

}
