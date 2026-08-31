package dev.roflsunriz.povo.automation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

public final class PromoCodeExtractorTest {
    @Test
    public void extractsLabeledCodeFromFullJapaneseEmail() throws Exception {
        String email = "povo2.0をご利用いただきありがとうございます。\n"
                + "プリペイドコードデータ使い放題(7日間)24回分\n"
                + "プリペイドコード：AB12-CD34-EF56\n"
                + "入力期限 2027年4月6日 23:59\n"
                + "データ追加0.1GB(24時間)は購入後、即時適用されます。";

        PromoCodeExtractor.Result result = PromoCodeExtractor.extract(email);

        assertEquals("AB12-CD34-EF56", result.code);
        assertTrue(result.emailLike);
        assertEquals(168, result.durationHours);
        assertEquals(PromoProduct.Type.REPEATABLE_TIME_CODE, result.product.type);
        assertEquals(24, result.product.codeUses);
        assertEquals(24, result.product.packageUses);
        assertEquals(0, result.product.immediateUses);
        assertTrue(result.product.hasRemainingUses(4));
        assertEquals(5, result.product.nextAppliedUses(4));
        assertFalse(result.product.hasRemainingUses(24));
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"));
        assertEquals(format.parse("2027-04-06 23:59").getTime(), result.deadline);
    }

    @Test
    public void preservesSimpleManualCode() {
        PromoCodeExtractor.Result result = PromoCodeExtractor.extract("ab12cd34ef56");

        assertEquals("AB12CD34EF56", result.code);
        assertFalse(result.emailLike);
        assertEquals(0L, result.deadline);
        assertEquals(0, result.durationHours);
    }

    @Test
    public void prefersCandidateWithLettersAndDigitsOverDate() {
        PromoCodeExtractor.Result result = PromoCodeExtractor.extract(
                "有効期限: 2027-04-06\nコードは ZX90-YT87-QP65 です"
        );

        assertEquals("ZX90-YT87-QP65", result.code);
    }

    @Test
    public void extractsHourlyDurationFromEmail() {
        PromoCodeExtractor.Result result = PromoCodeExtractor.extract(
                "プリペイドコードデータ使い放題(24時間)5回分\n"
                        + "プロモコード: ZX90-YT87-QP65\n"
                        + "入力期限 2026年9月21日23:59"
        );

        assertEquals(24, result.durationHours);
        assertEquals(PromoProduct.Type.REPEATABLE_TIME_CODE, result.product.type);
        assertEquals(5, result.product.codeUses);
        assertTrue(result.product.hasRemainingUses(4));
        assertFalse(result.product.hasRemainingUses(5));
    }

    @Test
    public void subtractsImmediateUseFromSevenDayTwelvePack() {
        PromoCodeExtractor.Result result = PromoCodeExtractor.extract(
                "データ使い放題(7日間)12回分\n"
                        + "本トッピング購入後、1回分のデータ使い放題(7日間)が即時適用されます。\n"
                        + "残り11回分のデータ使い放題(7日間)の提供はプリペイドコードにて行います。\n"
                        + "プリペイドコード: TW12-EL34-VE56\n"
                        + "入力期限 2027/01/31 23:59"
        );

        assertEquals(168, result.product.durationHours);
        assertEquals(PromoProduct.Type.REPEATABLE_TIME_CODE, result.product.type);
        assertEquals(12, result.product.packageUses);
        assertEquals(1, result.product.immediateUses);
        assertEquals(11, result.product.codeUses);
        assertTrue(result.product.hasRemainingUses(10));
        assertEquals(11, result.product.nextAppliedUses(10));
        assertEquals(11, result.product.nextAppliedUses(11));
        assertFalse(result.product.hasRemainingUses(11));
    }

    @Test
    public void neverRepeatsSingleTwoHourMonthEndCode() throws Exception {
        String email = "【月末っちょ】データ追加1GB(7日間)+データ使い放題(2時間)\n"
                + "データ追加1GBは購入後、即時適用されます。\n"
                + "プリペイドコードデータ使い放題(2時間)\n"
                + "プリペイドコード: MM12-AT34-CH56\n"
                + "入力期限 2026年9月15日23:59\n"
                + "次回販売予定は2026年10月31日です。";

        PromoCodeExtractor.Result result = PromoCodeExtractor.extract(email);

        assertEquals(2, result.product.durationHours);
        assertEquals(PromoProduct.Type.SINGLE_TIME_CODE, result.product.type);
        assertEquals(1, result.product.codeUses);
        assertFalse(result.product.hasRemainingUses(0));
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"));
        assertEquals(format.parse("2026-09-15 23:59").getTime(), result.deadline);
    }

    @Test
    public void legacySevenDayTwentyFourStateKeepsFourthActiveUse() {
        PromoProduct migrated = PromoProduct.legacyRepeatable(24, 168);

        assertEquals(PromoProduct.Type.REPEATABLE_TIME_CODE, migrated.type);
        assertEquals(24, migrated.codeUses);
        assertEquals(168, migrated.durationHours);
        assertEquals(4, migrated.clampAppliedUses(4));
        assertTrue(migrated.hasRemainingUses(4));
        assertFalse(migrated.hasRemainingUses(24));
    }

    @Test
    public void usesKnownSevenDayTwelveStructureOnlyAsFallback() {
        PromoCodeExtractor.Result result = PromoCodeExtractor.extract(
                "データ使い放題(7日間)12回分\n"
                        + "プリペイドコード: FB12-AC34-KP56\n"
                        + "入力期限 2027年1月31日23:59"
        );

        assertEquals(PromoProduct.Type.REPEATABLE_TIME_CODE, result.product.type);
        assertEquals(12, result.product.packageUses);
        assertEquals(1, result.product.immediateUses);
        assertEquals(11, result.product.codeUses);
    }

    @Test
    public void ignoresUnrelatedPromoResultsAndConsumesExpectedResultOnce() {
        PromoResultGate gate = new PromoResultGate();

        assertFalse(gate.consumeExpectedResult());
        gate.expectResult();
        assertTrue(gate.consumeExpectedResult());
        assertFalse(gate.consumeExpectedResult());
        gate.expectResult();
        gate.cancel();
        assertFalse(gate.consumeExpectedResult());
    }
}
