package com.newtermux.features;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects package install/uninstall commands typed by the user (e.g. {@code pkg install foo}) so
 * they can be run in a throwaway session that closes itself when the install finishes, mirroring
 * the behaviour of the Paquete Manager.
 */
public final class PackageInstallInterceptor {

    private PackageInstallInterceptor() {}

    private static final Pattern PATTERN = Pattern.compile(
        "^(?:sudo\\s+)?(?:pkg|apt|apt-get)\\s+(?:install|uninstall|remove|reinstall)\\b.*$",
        Pattern.CASE_INSENSITIVE);

    /** Matches the package manager + verb prefix of an install/upgrade/uninstall command. */
    private static final Pattern INSTALL_PREFIX = Pattern.compile(
        "^(?:sudo\\s+)?(?:pkg|apt|apt-get)\\s+(?:install|reinstall|upgrade|full-upgrade|uninstall|remove)\\b",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern YES_FLAG = Pattern.compile("(?:^|\\s)-y(?:\\s|$)");

    /**
     * Returns the trimmed command if the typed line is a package install/uninstall command,
     * otherwise {@code null}. Only lines that start with the package manager are matched, so
     * unrelated commands (or strings that merely mention them) are left untouched.
     */
    public static String extract(String typedLine) {
        if (typedLine == null) return null;
        String line = typedLine.trim();
        if (line.isEmpty()) return null;
        return PATTERN.matcher(line).matches() ? line : null;
    }

    /** Whether {@code command} is an install/upgrade that may block on a confirmation prompt. */
    public static boolean isInstallCommand(String command) {
        if (command == null) return false;
        String line = command.trim();
        if (line.isEmpty()) return false;
        return INSTALL_PREFIX.matcher(line).find();
    }

    /**
     * Inject {@code -y} into install/upgrade commands (unless already present) so apt never blocks
     * on a "Do you want to continue? [Y/n]" confirmation. Such a prompt would keep the throwaway
     * session alive forever and defeat the automatic close.
     */
    public static String withYesFlag(String command) {
        if (command == null) return null;
        String line = command.trim();
        if (!isInstallCommand(line) || YES_FLAG.matcher(line).find()) return line;
        Matcher m = INSTALL_PREFIX.matcher(line);
        if (m.find()) return line.substring(0, m.end()) + " -y" + line.substring(m.end());
        return line;
    }
}
