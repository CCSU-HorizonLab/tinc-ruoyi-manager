package com.ruoyi.common.tinc.runtime;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Routes the same tokenized command model through a root-owned fixed helper. */
public final class HelperTincCommandExecutor implements TincCommandExecutor {
    private static final String HELPER = "/usr/local/sbin/ruoyi-tinc-runtime";
    private final LocalTincCommandExecutor local = new LocalTincCommandExecutor();
    private final boolean useSudo;

    public HelperTincCommandExecutor(boolean useSudo) {
        this.useSudo = useSudo;
    }

    @Override
    public TincCommandResult execute(List<String> command, Duration timeout) {
        if (command == null || command.isEmpty() || !command.get(0).startsWith("/")) {
            throw new IllegalArgumentException("Invalid runtime command");
        }
        List<String> helperCommand = new ArrayList<>();
        if (useSudo) {
            helperCommand.add("/usr/bin/sudo");
            helperCommand.add("-n");
        }
        helperCommand.add(HELPER);
        helperCommand.add(operation(command.get(0)));
        helperCommand.addAll(command.subList(1, command.size()));
        return local.execute(helperCommand, timeout);
    }

    private String operation(String executable) {
        switch (executable) {
            case "/usr/bin/systemctl": return "systemctl";
            case "/usr/sbin/tincd": return "tincd";
            case "/usr/sbin/ip": return "ip";
            case "/usr/sbin/ss": return "ss";
            case "/usr/bin/firewall-cmd": return "firewall";
            case "/usr/bin/sh": return "sh-check";
            case "/usr/sbin/nft": return "nft-check";
            case "/usr/sbin/iptables": return "iptables-check";
            default: throw new IllegalArgumentException("Runtime operation is not allow-listed");
        }
    }
}
