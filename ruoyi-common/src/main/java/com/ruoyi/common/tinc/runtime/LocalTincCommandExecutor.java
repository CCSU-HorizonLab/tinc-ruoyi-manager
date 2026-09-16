package com.ruoyi.common.tinc.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** ProcessBuilder based executor with a fixed executable allow-list and bounded output. */
public class LocalTincCommandExecutor implements TincCommandExecutor {
    private static final Logger log = LoggerFactory.getLogger(LocalTincCommandExecutor.class);
    private static final int MAX_OUTPUT_BYTES = 32 * 1024;
    private static final String HELPER = "/usr/local/sbin/ruoyi-tinc-runtime";
    private static final Set<String> HELPER_OPERATIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "systemctl", "tincd", "ip", "ss", "firewall", "sh-check", "nft-check", "iptables-check"
    )));
    private static final Set<String> ALLOWED = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "/usr/bin/systemctl", "/usr/sbin/tincd", "/usr/sbin/ip", "/usr/sbin/ss",
            "/usr/bin/firewall-cmd", "/usr/bin/sh", "/usr/sbin/nft", "/usr/sbin/iptables"
            , "/usr/local/sbin/ruoyi-tinc-runtime", "/usr/bin/sudo"
    )));
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_]{1,64}");
    private static final Pattern UNIT = Pattern.compile("tinc@[A-Za-z0-9_]{1,64}\\.service");
    private static final Pattern INTERFACE = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,14}");
    private static final Pattern PORT_EXPRESSION = Pattern.compile(":[1-9][0-9]{0,4}");
    private static final Pattern FIREWALL_RULE = Pattern.compile("--(?:query|add)-port=([1-9][0-9]{0,4})/(?:tcp|udp)");
    private static final Pattern TINC_SCRIPT = Pattern.compile("/etc/tinc/[A-Za-z0-9_]{1,64}/tinc-(?:up|down)");

    @Override
    public TincCommandResult execute(List<String> command, Duration timeout) {
        if (command == null || command.isEmpty() || !ALLOWED.contains(command.get(0))) {
            throw new IllegalArgumentException("Tinc runtime executable is not allow-listed");
        }
        validateCommand(command);
        if (timeout == null || timeout.isZero() || timeout.isNegative()
                || timeout.compareTo(Duration.ofSeconds(60)) > 0) {
            throw new IllegalArgumentException("Tinc runtime timeout must be between 1ms and 60s");
        }
        log.debug("执行 Tinc 运行命令: executable={}, argCount={}", command.get(0), command.size() - 1);
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            final Process running = process;
            final ByteArrayOutputStream output = new ByteArrayOutputStream();
            Thread reader = new Thread(() -> copyBounded(running.getInputStream(), output), "tinc-command-output");
            reader.setDaemon(true);
            reader.start();
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroy();
                if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                }
                reader.join(500);
                log.warn("Tinc 运行命令超时: executable={}", command.get(0));
                return new TincCommandResult(-1, decode(output), true);
            }
            reader.join(1000);
            return new TincCommandResult(process.exitValue(), decode(output), false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Tinc runtime command interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("Tinc runtime command failed to execute", e);
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    /** Package-private for command-policy regression tests. */
    static void validateCommand(List<String> command) {
        if (command == null || command.isEmpty() || !ALLOWED.contains(command.get(0))) {
            throw new IllegalArgumentException("Tinc runtime executable is not allow-listed");
        }
        if ("/usr/bin/sudo".equals(command.get(0))) {
            if (command.size() < 4 || !"-n".equals(command.get(1)) || !HELPER.equals(command.get(2))
                    || !HELPER_OPERATIONS.contains(command.get(3))) {
                throw new IllegalArgumentException("sudo is restricted to the fixed Tinc runtime helper");
            }
            validateHelperCommand(command.subList(2, command.size()));
        } else if (HELPER.equals(command.get(0))) {
            validateHelperCommand(command);
        } else {
            validateDirectCommand(command);
        }
    }

    private static void validateHelperCommand(List<String> command) {
        if (command.size() < 2 || !HELPER.equals(command.get(0)) || !HELPER_OPERATIONS.contains(command.get(1))) {
            throw new IllegalArgumentException("Tinc runtime helper operation is not allow-listed");
        }
        String executable;
        switch (command.get(1)) {
            case "systemctl": executable = "/usr/bin/systemctl"; break;
            case "tincd": executable = "/usr/sbin/tincd"; break;
            case "ip": executable = "/usr/sbin/ip"; break;
            case "ss": executable = "/usr/sbin/ss"; break;
            case "firewall": executable = "/usr/bin/firewall-cmd"; break;
            case "sh-check": executable = "/usr/bin/sh"; break;
            case "nft-check": executable = "/usr/sbin/nft"; break;
            case "iptables-check": executable = "/usr/sbin/iptables"; break;
            default: throw new IllegalArgumentException("Tinc runtime helper operation is not allow-listed");
        }
        java.util.ArrayList<String> direct = new java.util.ArrayList<>();
        direct.add(executable);
        direct.addAll(command.subList(2, command.size()));
        validateDirectCommand(direct);
    }

    private static void validateDirectCommand(List<String> command) {
        String executable = command.get(0);
        boolean valid;
        switch (executable) {
            case "/usr/bin/systemctl":
                valid = validSystemctl(command);
                break;
            case "/usr/sbin/tincd":
                valid = command.size() == 4 && "-n".equals(command.get(1))
                        && IDENTIFIER.matcher(command.get(2)).matches() && "-kHUP".equals(command.get(3));
                break;
            case "/usr/sbin/ip":
                valid = (command.size() == 5 && "link".equals(command.get(1))
                        && "show".equals(command.get(2)) && "dev".equals(command.get(3))
                        && INTERFACE.matcher(command.get(4)).matches())
                        || (command.size() == 7 && "-4".equals(command.get(1)) && "-o".equals(command.get(2))
                        && "addr".equals(command.get(3)) && "show".equals(command.get(4))
                        && "dev".equals(command.get(5)) && INTERFACE.matcher(command.get(6)).matches());
                break;
            case "/usr/sbin/ss":
                valid = command.size() == 6 && "-H".equals(command.get(1))
                        && ("-ltnp".equals(command.get(2)) || "-lunp".equals(command.get(2)))
                        && "sport".equals(command.get(3)) && "=".equals(command.get(4))
                        && validPortExpression(command.get(5));
                break;
            case "/usr/bin/firewall-cmd":
                valid = validFirewall(command);
                break;
            case "/usr/bin/sh":
                valid = command.size() == 3 && "-n".equals(command.get(1))
                        && TINC_SCRIPT.matcher(command.get(2)).matches();
                break;
            case "/usr/sbin/nft":
                valid = command.size() == 3 && "list".equals(command.get(1))
                        && "ruleset".equals(command.get(2));
                break;
            case "/usr/sbin/iptables":
                valid = command.size() == 2 && "-S".equals(command.get(1));
                break;
            default:
                valid = false;
        }
        if (!valid) {
            throw new IllegalArgumentException("Tinc runtime command arguments are not allow-listed");
        }
    }

    private static boolean validSystemctl(List<String> command) {
        if (command.size() == 3
                && ("is-active".equals(command.get(1)) || "is-enabled".equals(command.get(1)))) {
            return UNIT.matcher(command.get(2)).matches();
        }
        if (command.size() == 4 && "enable".equals(command.get(1)) && "--now".equals(command.get(2))) {
            return UNIT.matcher(command.get(3)).matches();
        }
        return command.size() == 6 && "show".equals(command.get(1))
                && UNIT.matcher(command.get(2)).matches() && "--property".equals(command.get(3))
                && "MainPID".equals(command.get(4)) && "--value".equals(command.get(5));
    }

    private static boolean validFirewall(List<String> command) {
        if (command.size() == 2 && "--state".equals(command.get(1))) {
            return true;
        }
        int ruleIndex;
        if (command.size() == 2) {
            ruleIndex = 1;
        } else if (command.size() == 3 && "--permanent".equals(command.get(1))) {
            ruleIndex = 2;
        } else {
            return false;
        }
        java.util.regex.Matcher matcher = FIREWALL_RULE.matcher(command.get(ruleIndex));
        if (!matcher.matches()) {
            return false;
        }
        try {
            int port = Integer.parseInt(matcher.group(1));
            return port >= 1 && port <= 65535;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean validPortExpression(String value) {
        if (!PORT_EXPRESSION.matcher(value).matches()) {
            return false;
        }
        try {
            int port = Integer.parseInt(value.substring(1));
            return port >= 1 && port <= 65535;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static void copyBounded(InputStream input, ByteArrayOutputStream output) {
        byte[] buffer = new byte[1024];
        int total = 0;
        try {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (total < MAX_OUTPUT_BYTES) {
                    int accepted = Math.min(read, MAX_OUTPUT_BYTES - total);
                    output.write(buffer, 0, accepted);
                    total += accepted;
                }
            }
        } catch (Exception ignored) {
            // The process may close the stream while being terminated after a timeout.
        }
    }

    private static String decode(ByteArrayOutputStream output) {
        return new String(output.toByteArray(), StandardCharsets.UTF_8).trim();
    }
}
