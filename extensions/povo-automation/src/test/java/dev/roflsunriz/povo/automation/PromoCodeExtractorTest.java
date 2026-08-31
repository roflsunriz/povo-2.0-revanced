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
                + "プリペイドコード：AB12-CD34-EF56\n"
                + "入力期限 2027年4月6日 23:59";

        PromoCodeExtractor.Result result = PromoCodeExtractor.extract(email);

        assertEquals("AB12-CD34-EF56", result.code);
        assertTrue(result.emailLike);
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
    }

    @Test
    public void prefersCandidateWithLettersAndDigitsOverDate() {
        PromoCodeExtractor.Result result = PromoCodeExtractor.extract(
                "有効期限: 2027-04-06\nコードは ZX90-YT87-QP65 です"
        );

        assertEquals("ZX90-YT87-QP65", result.code);
    }
}
