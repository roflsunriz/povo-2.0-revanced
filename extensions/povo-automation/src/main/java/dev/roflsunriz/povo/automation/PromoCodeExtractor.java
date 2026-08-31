package dev.roflsunriz.povo.automation;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PromoCodeExtractor {
    private static final Pattern LABELED_CODE = Pattern.compile(
            "(?i)(?:プリペイド|プロモ|promo|prepaid)\\s*(?:コード|code)?\\s*[:：]?[\\s\\r\\n]*([A-Z0-9][A-Z0-9-]{7,63})"
    );
    private static final Pattern CODE = Pattern.compile("(?i)(?<![A-Z0-9-])([A-Z0-9][A-Z0-9-]{7,63})(?![A-Z0-9-])");
    private static final Pattern DATE = Pattern.compile(
            "(20\\d{2})\\s*(?:年|[/-])\\s*(\\d{1,2})\\s*(?:月|[/-])\\s*(\\d{1,2})\\s*日?(?:\\s*(\\d{1,2})[:：](\\d{2}))?"
    );
    private static final Pattern DURATION = Pattern.compile(
            "(?i)(?:使い放題|unlimited)[^\\r\\n0-9]{0,30}(\\d{1,4})\\s*(時間|日間|日|hours?|days?)"
    );

    private PromoCodeExtractor() {}

    static Result extract(String input) {
        if (input == null) return new Result("", 0L, false, 0);
        String normalized = input.trim();
        int durationHours = durationHours(normalized);
        Matcher labeled = LABELED_CODE.matcher(normalized);
        if (labeled.find()) {
            return new Result(labeled.group(1).toUpperCase(Locale.ROOT), latestDate(normalized), true, durationHours);
        }

        List<String> candidates = new ArrayList<>();
        Matcher matcher = CODE.matcher(normalized);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (containsLetterAndDigit(candidate) && !candidate.startsWith("20")) {
                candidates.add(candidate);
            }
        }
        String code = candidates.isEmpty() ? normalized : candidates.get(candidates.size() - 1);
        boolean emailLike = normalized.indexOf('\n') >= 0 || normalized.indexOf('\r') >= 0 || normalized.length() > 80;
        return new Result(code.toUpperCase(Locale.ROOT), latestDate(normalized), emailLike, durationHours);
    }

    private static int durationHours(String value) {
        Matcher matcher = DURATION.matcher(value);
        if (!matcher.find()) return 0;
        int amount;
        try {
            amount = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
        String unit = matcher.group(2).toLowerCase(Locale.ROOT);
        int hours = unit.startsWith("日") || unit.startsWith("day") ? amount * 24 : amount;
        return hours >= 1 && hours <= 8760 ? hours : 0;
    }

    private static boolean containsLetterAndDigit(String value) {
        boolean letter = false;
        boolean digit = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            letter |= Character.isLetter(c);
            digit |= Character.isDigit(c);
        }
        return letter && digit;
    }

    private static long latestDate(String value) {
        Matcher matcher = DATE.matcher(value);
        long latest = 0L;
        while (matcher.find()) {
            String date = matcher.group(1) + "-" + matcher.group(2) + "-" + matcher.group(3)
                    + " " + (matcher.group(4) == null ? "23" : matcher.group(4))
                    + ":" + (matcher.group(5) == null ? "59" : matcher.group(5));
            try {
                SimpleDateFormat format = new SimpleDateFormat("yyyy-M-d H:mm", Locale.ROOT);
                format.setLenient(false);
                format.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"));
                Date parsed = format.parse(date);
                if (parsed != null && parsed.getTime() > latest) latest = parsed.getTime();
            } catch (ParseException ignored) {
                // Ignore unrelated dates in the pasted email.
            }
        }
        return latest;
    }

    static final class Result {
        final String code;
        final long deadline;
        final boolean emailLike;
        final int durationHours;

        Result(String code, long deadline, boolean emailLike, int durationHours) {
            this.code = code;
            this.deadline = deadline;
            this.emailLike = emailLike;
            this.durationHours = durationHours;
        }
    }
}
