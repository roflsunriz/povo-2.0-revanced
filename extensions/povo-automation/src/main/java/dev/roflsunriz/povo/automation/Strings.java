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
    static String updateSettings() { return pick("設定を更新して有効化", "Update and enable", "更新并启用", "अपडेट करके चालू करें", "Actualizar y activar", "Mettre à jour et activer", "تحديث وتفعيل", "Atualizar e ativar", "আপডেট ও চালু", "Обновить и включить", "اپ ڈیٹ اور فعال کریں"); }
    static String enable() { return pick("自動更新を有効化", "Enable automation", "启用自动续订", "स्वचालन चालू करें", "Activar", "Activer", "تفعيل", "Ativar", "চালু করুন", "Включить", "فعال کریں"); }
    static String disable() { return pick("自動更新を一時停止", "Pause automation", "暂停自动续订", "स्वचालन रोकें", "Pausar", "Suspendre", "إيقاف مؤقت", "Pausar", "বিরতি দিন", "Приостановить", "عارضی روکیں"); }
    static String clear() { return pick("コードと履歴を削除", "Delete code and history", "删除代码和历史", "कोड और इतिहास मिटाएँ", "Eliminar código e historial", "Supprimer code et historique", "حذف الرمز والسجل", "Excluir código e histórico", "কোড ও ইতিহাস মুছুন", "Удалить код и историю", "کوڈ اور تاریخ حذف کریں"); }
    static String noCode() { return pick("コード未登録", "No code registered", "未注册代码", "कोड पंजीकृत नहीं", "Sin código", "Aucun code", "لا يوجد رمز", "Sem código", "কোড নেই", "Код не задан", "کوڈ درج نہیں"); }
    static String tapToRegister() { return pick("タップしてメール本文を登録", "Tap to register the email", "点按以注册邮件", "ईमेल पंजीकृत करने के लिए टैप करें", "Toca para registrar el correo", "Touchez pour enregistrer l’e-mail", "اضغط لتسجيل البريد", "Toque para registrar o e-mail", "ইমেল নিবন্ধন করতে ট্যাপ করুন", "Нажмите, чтобы добавить письмо", "ای میل درج کرنے کے لیے ٹیپ کریں"); }
    static String automationEnabled() { return pick("自動更新: 有効", "Automation: enabled", "自动续订：已启用", "स्वचालन: चालू", "Automatización: activa", "Automatisation : active", "الأتمتة: مفعلة", "Automação: ativa", "স্বয়ংক্রিয়তা: চালু", "Автоматизация: включена", "خودکاری: فعال"); }
    static String automationPaused() { return pick("自動更新: 一時停止", "Automation: paused", "自动续订：已暂停", "स्वचालन: रुका", "Automatización: pausada", "Automatisation : suspendue", "الأتمتة: متوقفة", "Automação: pausada", "স্বয়ংক্রিয়তা: বিরত", "Автоматизация: приостановлена", "خودکاری: رکی ہوئی"); }
    static String expiryNotDetected() { return pick("終了時刻を取得中", "Detecting the expiry time", "正在检测结束时间", "समाप्ति समय खोजा जा रहा है", "Detectando la hora de fin", "Détection de l’heure de fin", "جارٍ اكتشاف وقت الانتهاء", "Detectando o horário final", "শেষ সময় শনাক্ত হচ্ছে", "Определяется время окончания", "اختتامی وقت معلوم ہو رہا ہے"); }
    static String nextAt(String value) { return pick("次回 " + value, "Next " + value, "下次 " + value, "अगला " + value, "Próximo " + value, "Prochain " + value, "التالي " + value, "Próximo " + value, "পরবর্তী " + value, "Следующий " + value, "اگلا " + value); }
    static String setupGuide() { return pick("1. povoから届いたメール本文を下へ貼り付け\n2. 利用中なら現在の終了日時を入力\n3. 「保存して有効化」を押す\n4. 次回時刻と有効状態を確認する", "1. Paste the full email from povo below\n2. If already active, enter its expiry\n3. Tap Save and enable\n4. Confirm the next time and status", "1. 在下方粘贴 povo 的完整邮件\n2. 如正在使用，请输入结束时间\n3. 点按保存并启用\n4. 确认下次时间和状态", "1. povo का पूरा ईमेल नीचे चिपकाएँ\n2. सक्रिय होने पर समाप्ति दर्ज करें\n3. सहेजें और चालू करें\n4. अगला समय जाँचें", "1. Pega abajo el correo completo de povo\n2. Si está activo, indica el fin\n3. Guarda y activa\n4. Confirma la próxima hora", "1. Collez le courriel povo ci-dessous\n2. S’il est actif, indiquez sa fin\n3. Enregistrez et activez\n4. Vérifiez la prochaine heure", "1. الصق رسالة povo كاملة أدناه\n2. أدخل وقت الانتهاء إن كانت فعالة\n3. احفظ وفعّل\n4. تحقق من الموعد التالي", "1. Cole o e-mail completo do povo abaixo\n2. Se ativo, informe o fim\n3. Salve e ative\n4. Confirme o próximo horário", "1. povo-এর সম্পূর্ণ ইমেল নিচে পেস্ট করুন\n2. সক্রিয় হলে শেষ সময় দিন\n3. সংরক্ষণ ও চালু করুন\n4. পরবর্তী সময় নিশ্চিত করুন", "1. Вставьте письмо povo ниже\n2. Если пакет активен, укажите окончание\n3. Сохраните и включите\n4. Проверьте следующее время", "1. povo کی مکمل ای میل نیچے چسپاں کریں\n2. فعال ہو تو اختتامی وقت درج کریں\n3. محفوظ اور فعال کریں\n4. اگلا وقت دیکھیں"); }
    static String toppingDetected(int hours) { return pick(hours + "時間トッピングの終了時刻を取得しました", "Detected the " + hours + "-hour topping expiry", "已检测到" + hours + "小时套餐结束时间", hours + " घंटे की समाप्ति मिली", "Se detectó el fin del topping de " + hours + " horas", "Fin du forfait de " + hours + " heures détectée", "تم اكتشاف انتهاء باقة " + hours + " ساعة", "Fim do pacote de " + hours + " horas detectado", hours + " ঘণ্টার মেয়াদ শনাক্ত হয়েছে", "Обнаружено окончание пакета на " + hours + " часов", hours + " گھنٹے کی میعاد مل گئی"); }
    static String expiryInputHint() { return pick("現在の終了日時（例: 2026-08-31 16:42）", "Current expiry (for example: 2026-08-31 16:42)", "当前结束时间（例如：2026-08-31 16:42）", "वर्तमान समाप्ति (उदा. 2026-08-31 16:42)", "Fin actual (ej.: 2026-08-31 16:42)", "Fin actuelle (ex. : 2026-08-31 16:42)", "وقت الانتهاء الحالي (مثال: 2026-08-31 16:42)", "Fim atual (ex.: 2026-08-31 16:42)", "বর্তমান শেষ সময় (যেমন: 2026-08-31 16:42)", "Текущее окончание (напр. 2026-08-31 16:42)", "موجودہ اختتام (مثال: 2026-08-31 16:42)"); }
    static String manualExpirySaved() { return pick("現在の終了日時を保存しました", "Current expiry saved", "当前结束时间已保存", "वर्तमान समाप्ति सहेजी गई", "Fin actual guardado", "Fin actuelle enregistrée", "تم حفظ وقت الانتهاء", "Fim atual salvo", "বর্তমান শেষ সময় সংরক্ষিত", "Текущее окончание сохранено", "موجودہ اختتام محفوظ ہو گیا"); }
    static String invalidExpiry() { return pick("未来の日時を YYYY-MM-DD HH:mm で入力してください", "Enter a future time as YYYY-MM-DD HH:mm", "请按 YYYY-MM-DD HH:mm 输入未来时间", "भविष्य का समय YYYY-MM-DD HH:mm में दें", "Introduce una hora futura como YYYY-MM-DD HH:mm", "Entrez une date future au format YYYY-MM-DD HH:mm", "أدخل وقتًا مستقبليًا بصيغة YYYY-MM-DD HH:mm", "Digite um horário futuro como YYYY-MM-DD HH:mm", "YYYY-MM-DD HH:mm আকারে ভবিষ্যৎ সময় দিন", "Введите будущее время как YYYY-MM-DD HH:mm", "مستقبل کا وقت YYYY-MM-DD HH:mm میں درج کریں"); }
    static String maxUsesHint() { return pick("最大回数（通常24）", "Maximum uses (usually 24)", "最大次数（通常24）", "अधिकतम उपयोग (आमतौर पर 24)", "Usos máximos (normalmente 24)", "Utilisations maximales (24 en général)", "الحد الأقصى للاستخدام (عادة 24)", "Usos máximos (normalmente 24)", "সর্বোচ্চ ব্যবহার (সাধারণত ২৪)", "Максимум использований (обычно 24)", "زیادہ سے زیادہ استعمال (عموماً 24)"); }
    static String currentUseHint() { return pick("現在利用中の回数（今回は4）", "Current active use (4 this time)", "当前使用次数（本次为4）", "वर्तमान सक्रिय उपयोग (इस बार 4)", "Uso activo actual (4 esta vez)", "Utilisation active actuelle (4 ici)", "رقم الاستخدام الحالي (4 هذه المرة)", "Uso ativo atual (4 desta vez)", "বর্তমান সক্রিয় ব্যবহার (এবার ৪)", "Текущее активное использование (сейчас 4)", "موجودہ فعال استعمال (اس بار 4)"); }
    static String durationHoursHint() { return pick("1回の有効時間（時間、7日なら168）", "Duration per use in hours (168 for 7 days)", "每次有效小时数（7天为168）", "हर उपयोग की अवधि घंटे में (7 दिन के लिए 168)", "Horas por uso (168 para 7 días)", "Durée par utilisation en heures (168 pour 7 jours)", "مدة كل استخدام بالساعات (168 لسبعة أيام)", "Horas por uso (168 para 7 dias)", "প্রতি ব্যবহারের সময় ঘণ্টায় (৭ দিনে ১৬৮)", "Часов на одно использование (168 для 7 дней)", "فی استعمال گھنٹے (7 دن کے لیے 168)"); }
    static String invalidUseProgress() { return pick("回数の大小関係と、1回の有効時間（1〜8760時間）を確認してください", "Check the use counts and duration (1–8760 hours)", "请检查次数和有效小时数（1至8760）", "उपयोग गिनती और अवधि (1–8760 घंटे) जाँचें", "Comprueba los usos y la duración (1–8760 horas)", "Vérifiez les compteurs et la durée (1–8760 heures)", "تحقق من العدد والمدة (1–8760 ساعة)", "Verifique as contagens e a duração (1–8760 horas)", "ব্যবহারের সংখ্যা ও সময় (১–৮৭৬০ ঘণ্টা) দেখুন", "Проверьте количество и длительность (1–8760 часов)", "تعداد اور مدت (1–8760 گھنٹے) چیک کریں"); }
    static String usesProgress(int current, int maximum) { return pick("利用回数 " + current + "/" + maximum, "Uses " + current + "/" + maximum, "使用次数 " + current + "/" + maximum, "उपयोग " + current + "/" + maximum, "Usos " + current + "/" + maximum, "Utilisations " + current + "/" + maximum, "الاستخدامات " + current + "/" + maximum, "Usos " + current + "/" + maximum, "ব্যবহার " + current + "/" + maximum, "Использования " + current + "/" + maximum, "استعمال " + current + "/" + maximum); }
    static String durationPerUse(int hours) { return pick("1回 " + hours + "時間", hours + " hours/use", "每次 " + hours + " 小时", "प्रति उपयोग " + hours + " घंटे", hours + " horas/uso", hours + " h/utilisation", hours + " ساعة/استخدام", hours + " horas/uso", "প্রতি ব্যবহার " + hours + " ঘণ্টা", hours + " ч/использование", hours + " گھنٹے/استعمال"); }
    static String successCount(int count) { return pick("自動適用成功 " + count + "回", "Automatic applications " + count, "自动应用成功 " + count + " 次", "स्वचालित सफलताएँ " + count, "Aplicaciones automáticas " + count, "Applications automatiques " + count, "مرات التطبيق التلقائي " + count, "Aplicações automáticas " + count, "স্বয়ংক্রিয় প্রয়োগ " + count, "Автоприменений " + count, "خودکار اطلاق " + count); }
    static String allUsesCompleted() { return pick("全回数の適用が完了しました", "All uses completed", "所有次数均已完成", "सभी उपयोग पूरे हुए", "Se completaron todos los usos", "Toutes les utilisations sont terminées", "اكتملت جميع الاستخدامات", "Todos os usos foram concluídos", "সব ব্যবহার সম্পন্ন", "Все использования завершены", "تمام استعمال مکمل ہو گئے"); }
}
