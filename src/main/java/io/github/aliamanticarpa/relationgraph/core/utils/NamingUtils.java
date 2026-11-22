package io.github.aliamanticarpa.relationgraph.core.utils;

public final class NamingUtils {
    private NamingUtils() {}

    public static String toSnakeCase(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        // Вставляет подчеркивание перед заглавными буквами, не стоящими в начале строки,
        // корректно обрабатывая аббревиатуры (например, HTMLParser -> html_parser)
        return input
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
                .replaceAll("([a-z\\d])([A-Z])", "$1_$2")
                .toLowerCase();
    }
}
