package dev.roflsunriz.povo.automation;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PromoCodeExtractor {
    private static final String DATE_BODY = "(20\\d{2})\\s*(?:年|[/-])\\s*(\\d{1,2})\\s*(?:月|[/-])\\s*(\\d{1,2})\\s*日?(?:\\s*(\\d{1,2})[:：](\\d{2}))?";
    private static final Pattern LABELED_CODE = Pattern.compile(
            "(?i)(?:プリペイド|プロモ|promo|prepaid)\\s*(?:コード|code)?\\s*[:：]?[\\s\\r\\n]*([A-Z0-9][A-Z0-9-]{7,63})"
    );
    private static final Pattern CODE = Pattern.compile("(?i)(?<![A-Z0-9-])([A-Z0-9][A-Z0-9-]{7,63})(?![A-Z0-9-])");
    private static final Pattern DATE = Pattern.compile(DATE_BODY);
    private static final Pattern LABELED_DEADLINE = Pattern.compile(
            "(?:入力|利用|引換|引き換え|コード)\\s*(?:期限|締切|締め切り)[^0-9]{0,40}" + DATE_BODY
    );
    private static final Pattern DURATION = Pattern.compile(
            "(?i)(?:使い放題|unlimited)[^\\r\\n0-9]{0,30}(\\d{1,4})\\s*(時間|日間|日|hours?|days?)"
    );
    private static final Pattern REMAINING_CODE_USES = Pattern.compile(
            "(?:残り|残る)\\s*(\\d{1,3})\\s*回分[^\\r\\n。]{0,100}(?:プリペイド|プロモ|引き換え|引換)\\s*コード"
                    + "|(?:プリペイド|プロモ|引き換え|引換)\\s*コード[^\\r\\n。]{0,100}(?:残り|残る)\\s*(\\d{1,3})\\s*回分"
    );
    private static final Pattern CODE_USES = Pattern.compile(
            "(?:プリペイド|プロモ|引き換え|引換)\\s*コード[^\\r\\n。]{0,100}?(\\d{1,3})\\s*回分"
                    + "|(\\d{1,3})\\s*回分[^\\r\\n。]{0,100}?(?:プリペイド|プロモ|引き換え|引換)\\s*コード"
    );
    private static final Pattern PACKAGE_USES = Pattern.compile(
            "(?:使い放題|unlimited)[^\\r\\n。]{0,60}?(\\d{1,3})\\s*回分",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern IMMEDIATE_USE = Pattern.compile(
            "(?:1\\s*回(?:分|目)?[^\\r\\n。]{0,80}(?:即時|すぐに)\\s*適用)"
                    + "|(?:(?:即時|すぐに)\\s*適用[^\\r\\n。]{0,80}1\\s*回(?:分|目)?)"
    );

    private static final KnownPattern[] KNOWN_PATTERNS = {
            new KnownPattern(168, 24, 24),
            new KnownPattern(24, 5, 5),
            new KnownPattern(168, 12, 11),
    };

    private PromoCodeExtractor() {}

    static Result extract(String input) {
        if (input == null) return new Result("", 0L, false, PromoProduct.unknown(0));
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC).trim();
        int durationHours = durationHours(normalized);
        PromoProduct product = product(normalized, durationHours);
        Matcher labeled = LABELED_CODE.matcher(normalized);
        if (labeled.find()) {
            return new Result(labeled.group(1).toUpperCase(Locale.ROOT), deadline(normalized), true, product);
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
        return new Result(code.toUpperCase(Locale.ROOT), deadline(normalized), emailLike, product);
    }

    private static PromoProduct product(String value, int durationHours) {
        int remainingUses = firstNumber(REMAINING_CODE_USES.matcher(value));
        int directCodeUses = firstNumber(CODE_USES.matcher(value));
        int packageUses = firstNumber(PACKAGE_USES.matcher(value));
        int immediateUses = IMMEDIATE_USE.matcher(value).find() ? 1 : 0;

        int codeUses = remainingUses > 0 ? remainingUses : directCodeUses;
        if (codeUses <= 0 && packageUses > 0 && immediateUses > 0) {
            codeUses = Math.max(1, packageUses - immediateUses);
        }
        if (codeUses <= 0) {
            codeUses = knownCodeUses(durationHours, packageUses);
        }
        if (codeUses <= 0 && packageUses > 0) codeUses = packageUses;
        if (immediateUses == 0 && packageUses > codeUses) immediateUses = packageUses - codeUses;

        PromoProduct.Type type;
        if (durationHours <= 0) {
            type = PromoProduct.Type.UNKNOWN;
        } else if (codeUses > 1) {
            type = PromoProduct.Type.REPEATABLE_TIME_CODE;
        } else if (value.matches("(?s).*(?:プリペイド|プロモ|引き換え|引換)\\s*コード.*")) {
            type = PromoProduct.Type.SINGLE_TIME_CODE;
            codeUses = 1;
        } else {
            type = PromoProduct.Type.UNKNOWN;
            codeUses = 1;
        }
        int totalUses = packageUses > 0 ? packageUses : codeUses + immediateUses;
        return new PromoProduct(type, durationHours, codeUses, totalUses, immediateUses);
    }

    private static int firstNumber(Matcher matcher) {
        if (!matcher.find()) return 0;
        for (int group = 1; group <= matcher.groupCount(); group++) {
            String value = matcher.group(group);
            if (value == null) continue;
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static int knownCodeUses(int durationHours, int packageUses) {
        for (KnownPattern known : KNOWN_PATTERNS) {
            if (known.durationHours == durationHours && known.packageUses == packageUses) {
                return known.codeUses;
            }
        }
        return 0;
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

    private static long deadline(String value) {
        Matcher labeled = LABELED_DEADLINE.matcher(value);
        if (labeled.find()) return parseDate(labeled);

        Matcher matcher = DATE.matcher(value);
        long latest = 0L;
        while (matcher.find()) {
            latest = Math.max(latest, parseDate(matcher));
        }
        return latest;
    }

    private static long parseDate(Matcher matcher) {
        String date = matcher.group(1) + "-" + matcher.group(2) + "-" + matcher.group(3)
                + " " + (matcher.group(4) == null ? "23" : matcher.group(4))
                + ":" + (matcher.group(5) == null ? "59" : matcher.group(5));
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-M-d H:mm", Locale.ROOT);
            format.setLenient(false);
            format.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"));
            Date parsed = format.parse(date);
            return parsed == null ? 0L : parsed.getTime();
        } catch (ParseException ignored) {
            return 0L;
        }
    }

    static final class Result {
        final String code;
        final long deadline;
        final boolean emailLike;
        final int durationHours;
        final PromoProduct product;

        Result(String code, long deadline, boolean emailLike, PromoProduct product) {
            this.code = code;
            this.deadline = deadline;
            this.emailLike = emailLike;
            this.product = product;
            this.durationHours = product.durationHours;
        }
    }

    private static final class KnownPattern {
        final int durationHours;
        final int packageUses;
        final int codeUses;

        KnownPattern(int durationHours, int packageUses, int codeUses) {
            this.durationHours = durationHours;
            this.packageUses = packageUses;
            this.codeUses = codeUses;
        }
    }
}
