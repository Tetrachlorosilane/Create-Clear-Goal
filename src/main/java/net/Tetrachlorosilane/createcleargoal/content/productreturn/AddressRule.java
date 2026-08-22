package net.Tetrachlorosilane.createcleargoal.content.productreturn;

import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Product Return Station address rule.
 * <p>
 * By default the address is matched using Create's glob address semantics and
 * capture groups are disabled. If the configured source starts with
 * {@code regex:} (case-insensitive), the remainder is treated as a Java regex
 * that must match the whole address, and the output template may use capture
 * groups ({@code $1}, {@code ${name}}).
 * <p>
 * The glob matcher is injected so this class stays free of Minecraft imports
 * and can be unit-tested as plain Java.
 */
public record AddressRule(String source, Pattern pattern, String outputTemplate, boolean regexMode,
	BiPredicate<String, String> globMatcher) {

	/** Maximum length of a final resolved Create address. */
	public static final int MAX_RESOLVED_ADDRESS_LENGTH = 25;
	/** Case-insensitive prefix that switches an address into Java regex mode. */
	public static final String REGEX_PREFIX = "regex:";

	public AddressRule {
		outputTemplate = outputTemplate == null ? "" : outputTemplate;
	}

	public static boolean isRegex(String source) {
		return source != null && source.regionMatches(true, 0, REGEX_PREFIX, 0, REGEX_PREFIX.length());
	}

	public static AddressRule compile(String source, String outputTemplate, BiPredicate<String, String> globMatcher) {
		if (isRegex(source)) {
			String regex = source.substring(REGEX_PREFIX.length());
			return new AddressRule(source, Pattern.compile(regex), outputTemplate, true, globMatcher);
		}
		return new AddressRule(source, null, outputTemplate, false, globMatcher);
	}

	/** Returns a copy with a different output template; a compiled regex pattern is reused. */
	public AddressRule withOutputTemplate(String newOutputTemplate) {
		return new AddressRule(source, pattern, newOutputTemplate, regexMode, globMatcher);
	}

	/**
	 * Resolves an address to its output route.
	 * <p>
	 * In glob mode the output template is returned literally (no capture-group
	 * expansion). In regex mode the template is expanded with the same
	 * {@link Matcher} that performed {@code matches()}.
	 *
	 * @return the resolved output address, or empty when the address does not
	 *         match, a regex template references a missing capture group, or the
	 *         resolved address exceeds the Create address length limit.
	 */
	public Optional<String> resolve(String address) {
		if (regexMode) {
			if (pattern == null)
				return Optional.empty();
			Matcher matcher = pattern.matcher(address);
			if (!matcher.matches())
				return Optional.empty();
			try {
				StringBuilder output = new StringBuilder();
				matcher.appendReplacement(output, outputTemplate);
				matcher.appendTail(output);
				String resolved = output.toString();
				if (resolved.length() > MAX_RESOLVED_ADDRESS_LENGTH)
					return Optional.empty();
				return Optional.of(resolved);
			} catch (IllegalArgumentException | IndexOutOfBoundsException e) {
				return Optional.empty();
			}
		}

		if (globMatcher == null || !globMatcher.test(source, address))
			return Optional.empty();
		if (outputTemplate.length() > MAX_RESOLVED_ADDRESS_LENGTH)
			return Optional.empty();
		return Optional.of(outputTemplate);
	}
}
