package com.ruoyi.common.tinc.runtime;

import java.time.Duration;
import java.util.List;

/** Executes an already tokenized, allow-listed command. */
public interface TincCommandExecutor {
    TincCommandResult execute(List<String> command, Duration timeout);
}
