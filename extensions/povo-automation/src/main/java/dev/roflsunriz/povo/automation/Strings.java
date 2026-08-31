package dev.roflsunriz.povo.automation;

import java.util.Locale;

final class Strings {
    private Strings() {}

    private static String pick(String ja, String en, String zh, String hi, String es, String fr,
                               String ar, String pt, String bn, String ru, String ur) {
        String language = Locale.getDefault().getLanguage();
        switch (language) {
            case "ja": return ja;
            case "zh": return zh;
            case "hi": return hi;
            case "es": return es;
            case "fr": return fr;
            case "ar": return ar;
            case "pt": return pt;
            case "bn": return bn;
            case "ru": return ru;
            case "ur": return ur;
            default: return en;
        }
    }

    static String channelName() {
        return pick("povo 自動更新", "povo automatic renewal", "povo 自动续订", "povo स्वचालित नवीनीकरण",
                "Renovación automática de povo", "Renouvellement automatique povo", "تجديد povo التلقائي",
                "Renovação automática do povo", "povo স্বয়ংক্রিয় নবায়ন", "Автопродление povo", "povo خودکار تجدید");
    }

    static String foregroundTitle() {
        return pick("次のプロモコードを適用中", "Applying the next promo code", "正在应用下一个促销代码",
                "अगला प्रोमो कोड लागू हो रहा है", "Aplicando el siguiente código", "Application du prochain code",
                "جارٍ تطبيق رمز العرض التالي", "Aplicando o próximo código", "পরবর্তী কোড প্রয়োগ হচ্ছে",
                "Применяется следующий промокод", "اگلا پرومو کوڈ لاگو ہو رہا ہے");
    }

    static String codeSaved() {
        return pick("プリペイドコードを暗号化して保存しました", "Prepaid code saved securely", "预付代码已安全保存",
                "प्रीपेड कोड सुरक्षित सहेजा गया", "Código guardado de forma segura", "Code enregistré de façon sécurisée",
                "تم حفظ الرمز بأمان", "Código salvo com segurança", "কোড নিরাপদে সংরক্ষিত হয়েছে",
                "Код безопасно сохранён", "کوڈ محفوظ کر لیا گیا");
    }

    static String encryptionFailed() {
        return pick("コードを安全に保存できませんでした", "Could not store the code securely", "无法安全保存代码",
                "कोड सुरक्षित रूप से सहेजा नहीं जा सका", "No se pudo guardar el código", "Impossible d’enregistrer le code",
                "تعذر حفظ الرمز بأمان", "Não foi possível salvar o código", "কোড নিরাপদে রাখা যায়নি",
                "Не удалось безопасно сохранить код", "کوڈ محفوظ نہیں کیا جا سکا");
    }

    static String success() {
        return pick("プロモコードを適用しました", "Promo code applied", "促销代码已应用", "प्रोमो कोड लागू हुआ",
                "Código aplicado", "Code appliqué", "تم تطبيق الرمز", "Código aplicado", "কোড প্রয়োগ হয়েছে",
                "Промокод применён", "پرومو کوڈ لاگو ہو گیا");
    }

    static String authRequired() {
        return pick("povo 2.0へ再ログインしてください。コードは保留されています", "Sign in to povo 2.0 again. The code is on hold",
                "请重新登录 povo 2.0，代码已保留", "povo 2.0 में फिर साइन इन करें; कोड सुरक्षित है",
                "Vuelve a iniciar sesión en povo 2.0; el código está en espera", "Reconnectez-vous à povo 2.0 ; le code est conservé",
                "سجّل الدخول إلى povo 2.0 مجددًا؛ الرمز محفوظ", "Entre novamente no povo 2.0; o código está em espera",
                "povo 2.0-এ আবার লগ ইন করুন; কোডটি রাখা হয়েছে", "Войдите в povo 2.0 снова; код сохранён",
                "povo 2.0 میں دوبارہ سائن ان کریں؛ کوڈ محفوظ ہے");
    }

    static String exactAlarmRequired() {
        return pick("途切れを抑えるため「アラームとリマインダー」を許可してください", "Allow Alarms & reminders to minimize gaps",
                "请允许“闹钟和提醒”以减少中断", "रुकावट घटाने के लिए अलार्म की अनुमति दें",
                "Permite alarmas para minimizar cortes", "Autorisez les alarmes pour limiter les coupures",
                "اسمح بالمنبهات لتقليل الانقطاع", "Permita alarmes para reduzir interrupções",
                "বিরতি কমাতে অ্যালার্ম অনুমতি দিন", "Разрешите будильники, чтобы уменьшить перерывы",
                "وقفہ کم کرنے کے لیے الارم کی اجازت دیں");
    }

    static String settingsTitle() {
        return pick("povo プロモコード自動更新", "povo promo-code automation", "povo 促销代码自动续订",
                "povo प्रोमो-कोड स्वचालन", "Automatización de códigos povo", "Automatisation des codes povo",
                "أتمتة رموز povo", "Automação de códigos povo", "povo কোড অটোমেশন",
                "Автоматизация промокодов povo", "povo کوڈ آٹومیشن");
    }

    static String pasteHint() {
        return pick("povoから届いたメール本文を貼り付け", "Paste the email body from povo", "粘贴 povo 邮件正文",
                "povo ईमेल का पूरा पाठ चिपकाएँ", "Pega el correo completo de povo", "Collez le courriel complet de povo",
                "الصق نص رسالة povo", "Cole o e-mail completo do povo", "povo ইমেলের সম্পূর্ণ লেখা পেস্ট করুন",
                "Вставьте текст письма povo", "povo ای میل کا متن چسپاں کریں");
    }

    static String save() { return pick("保存して有効化", "Save and enable", "保存并启用", "सहेजें और चालू करें", "Guardar y activar", "Enregistrer et activer", "حفظ وتفعيل", "Salvar e ativar", "সংরক্ষণ ও চালু", "Сохранить и включить", "محفوظ اور فعال کریں"); }
    static String enable() { return pick("自動更新を有効化", "Enable automation", "启用自动续订", "स्वचालन चालू करें", "Activar", "Activer", "تفعيل", "Ativar", "চালু করুন", "Включить", "فعال کریں"); }
    static String disable() { return pick("自動更新を一時停止", "Pause automation", "暂停自动续订", "स्वचालन रोकें", "Pausar", "Suspendre", "إيقاف مؤقت", "Pausar", "বিরতি দিন", "Приостановить", "عارضی روکیں"); }
    static String clear() { return pick("コードと履歴を削除", "Delete code and history", "删除代码和历史", "कोड और इतिहास मिटाएँ", "Eliminar código e historial", "Supprimer code et historique", "حذف الرمز والسجل", "Excluir código e histórico", "কোড ও ইতিহাস মুছুন", "Удалить код и историю", "کوڈ اور تاریخ حذف کریں"); }
    static String requestExact() { return pick("正確なアラームを許可", "Allow exact alarms", "允许精确闹钟", "सटीक अलार्म की अनुमति", "Permitir alarmas exactas", "Autoriser les alarmes exactes", "السماح بالمنبهات الدقيقة", "Permitir alarmes exatos", "নির্ভুল অ্যালার্ম অনুমতি", "Разрешить точные будильники", "درست الارم کی اجازت"); }
    static String noCode() { return pick("コード未登録", "No code registered", "未注册代码", "कोड पंजीकृत नहीं", "Sin código", "Aucun code", "لا يوجد رمز", "Sem código", "কোড নেই", "Код не задан", "کوڈ درج نہیں"); }
}
