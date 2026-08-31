package dev.roflsunriz.povo.automation

import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.firstMethod
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.w3c.dom.Document
import org.w3c.dom.Element

private const val PACKAGE_NAME = "com.kddi.kdla.jp"
private const val TEST_PACKAGE_NAME = "com.kddi.kdla.jp.revanced"
private const val APPLICATION_CLASS = "Lcom/circles/selfcare/AmApplication;"
private const val PROMO_MODEL = "Lcom/circles/api/model/account/PromoCodeModel;"
private const val EXTENSION_CLASS = "Ldev/roflsunriz/povo/automation/Automation;"
private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"

private val automationManifestPatch = resourcePatch(
    name = "povo automation components",
    description = "自動更新に必要なバックグラウンドコンポーネントを登録します。",
) {
    compatibleWith(PACKAGE_NAME)

    apply {
        document("AndroidManifest.xml").use { document ->
            listOf(
                "android.permission.RECEIVE_BOOT_COMPLETED",
                "android.permission.SCHEDULE_EXACT_ALARM",
                "android.permission.ACCESS_NETWORK_STATE",
                "android.permission.WAKE_LOCK",
                "android.permission.FOREGROUND_SERVICE",
                "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
            ).forEach(document::addPermission)

            val application = document.getElementsByTagName("application").item(0) as Element
            application.addComponent(
                document,
                "activity",
                "dev.roflsunriz.povo.automation.AutomationSettingsActivity",
                mapOf(
                    "exported" to "false",
                    "theme" to "@android:style/Theme.Material.Light.NoActionBar",
                ),
            )
            application.addComponent(
                document,
                "service",
                "dev.roflsunriz.povo.automation.AutomationService",
                mapOf(
                    "exported" to "false",
                    "foregroundServiceType" to "dataSync",
                ),
            )
            application.addComponent(
                document,
                "receiver",
                "dev.roflsunriz.povo.automation.AutomationAlarmReceiver",
                mapOf("exported" to "false"),
            )
            application.addBootReceiver(document)
        }
    }
}

@Suppress("unused")
val povoPromoCodeAutomationPatch = bytecodePatch(
    name = "プロモコード自動更新",
    description = "ログイン済みセッションと実際の終了時刻を使い、プリペイドコードを繰り返し自動適用します。",
) {
    compatibleWith(PACKAGE_NAME)
    dependsOn(automationManifestPatch)
    extendWith("extensions/povo-automation.rve")

    apply {
        val promoInputMethod = firstMethod("promoCode") {
            parameterTypes.map(CharSequence::toString) == listOf("Ljava/lang/String;") &&
                returnType != "V" &&
                classDefs[definingClass]?.methods?.any { candidate ->
                    candidate.returnType == "V" &&
                        candidate.parameterTypes.lastOrNull()?.toString() == PROMO_MODEL
                } == true
        }

        val controllerCall = promoInputMethod.implementation!!.instructions
            .asSequence()
            .filter { it.opcode == Opcode.INVOKE_INTERFACE }
            .mapNotNull { (it as? ReferenceInstruction)?.reference as? MethodReference }
            .first { reference ->
                reference.parameterTypes.map(CharSequence::toString) == listOf("Ljava/lang/String;")
            }

        promoInputMethod.addInstructions(
            0,
            """
                invoke-static/range {p1 .. p1}, $EXTENSION_CLASS->preparePromoInput(Ljava/lang/String;)Ljava/lang/String;
                move-result-object p1
            """.trimIndent(),
        )

        val promoResultMethod = firstMethod("result") {
            definingClass == promoInputMethod.definingClass &&
                returnType == "V" &&
                parameterTypes.lastOrNull()?.toString() == PROMO_MODEL
        }
        promoResultMethod.addInstructions(
            0,
            "invoke-static/range {p1 .. p2}, $EXTENSION_CLASS->onPromoResult(Ljava/lang/Object;Ljava/lang/Object;)V",
        )

        val addonParserMethod = firstMethod("expiry_date", "start_date", "current", "future") {
            parameterTypes.map(CharSequence::toString) == listOf("Lorg/json/JSONObject;", "Z") &&
                returnType == "Lcom/circles/api/model/account/GeneralAddonModel;"
        }
        addonParserMethod.addInstructions(
            0,
            "invoke-static/range {p0 .. p0}, $EXTENSION_CLASS->onAddonPayload(Ljava/lang/Object;)V",
        )

        val userPlanParserMethod = firstMethod("addons_subscribed", "billing_cycle", "basic_plan", "monkey") {
            parameterTypes.map(CharSequence::toString) == listOf("Ljava/lang/Object;") &&
                returnType == "Ljava/lang/Object;"
        }
        userPlanParserMethod.addInstructions(
            0,
            "invoke-static/range {p1 .. p1}, $EXTENSION_CLASS->onUserPlanPayload(Ljava/lang/Object;)V",
        )

        val koinGetMethod = firstMethod("KoinApplication has not been started") {
            parameterTypes.map(CharSequence::toString) == listOf("Ljava/lang/Class;") &&
                returnType == "Ljava/lang/Object;"
        }
        val koinClassName = koinGetMethod.definingClass
            .removePrefix("L")
            .removeSuffix(";")
            .replace('/', '.')

        val onCreate = firstMethod {
            definingClass == APPLICATION_CLASS &&
                name == "onCreate" &&
                parameterTypes.isEmpty() &&
                returnType == "V"
        }
        val superOnCreateIndex = onCreate.implementation!!.instructions.indexOfFirst { instruction ->
            instruction.opcode == Opcode.INVOKE_SUPER &&
                ((instruction as? ReferenceInstruction)?.reference as? MethodReference)?.let { reference ->
                    reference.definingClass == "Landroid/app/Application;" && reference.name == "onCreate"
                } == true
        }
        check(superOnCreateIndex >= 0) { "Application.onCreate super call was not found" }
        onCreate.addInstructions(
            superOnCreateIndex + 1,
            """
                invoke-static/range {p0 .. p0}, $EXTENSION_CLASS->initialize(Landroid/app/Application;)V
                const-class v0, ${controllerCall.definingClass}
                const-string v1, "$koinClassName"
                const-string v2, "${koinGetMethod.name}"
                const-string v3, "${controllerCall.name}"
                invoke-static {v0, v1, v2, v3}, $EXTENSION_CLASS->configurePromoController(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V
            """.trimIndent(),
        )
    }
}

@Suppress("unused")
val povoTestPackagePatch = resourcePatch(
    name = "検証用別パッケージID",
    description = "公式版を残したまま実機検証できるよう、パッケージIDと衝突する識別子を別名化します。",
    use = false,
) {
    compatibleWith(PACKAGE_NAME)
    dependsOn(povoPromoCodeAutomationPatch)

    apply {
        document("AndroidManifest.xml").use { document ->
            val manifest = document.documentElement
            check(manifest.getAttribute("package") == PACKAGE_NAME) {
                "Unexpected package name: ${manifest.getAttribute("package")}"
            }
            manifest.setAttribute("package", TEST_PACKAGE_NAME)

            val elements = document.getElementsByTagName("*")
            (0 until elements.length).forEach { index ->
                val element = elements.item(index) as Element
                element.renameConflictingAttributes()
            }
        }
    }
}

private fun Document.addPermission(name: String) {
    val existing = getElementsByTagName("uses-permission")
    if ((0 until existing.length).any { index ->
            (existing.item(index) as? Element)?.getAttributeNS(ANDROID_NAMESPACE, "name") == name
        }
    ) return

    val permission = createElement("uses-permission")
    permission.setAttributeNS(ANDROID_NAMESPACE, "android:name", name)
    documentElement.insertBefore(permission, documentElement.firstChild)
}

private fun Element.addComponent(
    document: Document,
    tag: String,
    name: String,
    attributes: Map<String, String>,
): Element {
    val existing = getElementsByTagName(tag)
    (0 until existing.length).forEach { index ->
        val element = existing.item(index) as? Element ?: return@forEach
        if (element.getAttributeNS(ANDROID_NAMESPACE, "name") == name) return element
    }
    return document.createElement(tag).also { element ->
        element.setAttributeNS(ANDROID_NAMESPACE, "android:name", name)
        attributes.forEach { (key, value) ->
            element.setAttributeNS(ANDROID_NAMESPACE, "android:$key", value)
        }
        appendChild(element)
    }
}

private fun Element.addBootReceiver(document: Document) {
    val receiver = addComponent(
        document,
        "receiver",
        "dev.roflsunriz.povo.automation.AutomationBootReceiver",
        mapOf(
            "enabled" to "true",
            "exported" to "false",
        ),
    )
    if (receiver.getElementsByTagName("intent-filter").length > 0) return
    val filter = document.createElement("intent-filter")
    listOf("android.intent.action.BOOT_COMPLETED", "android.intent.action.MY_PACKAGE_REPLACED").forEach { actionName ->
        val action = document.createElement("action")
        action.setAttributeNS(ANDROID_NAMESPACE, "android:name", actionName)
        filter.appendChild(action)
    }
    receiver.appendChild(filter)
}

private fun Element.renameConflictingAttributes() {
    if (tagName in setOf("permission", "uses-permission", "action")) {
        replaceAndroidAttributePrefix("name")
    }

    listOf(
        "authorities",
        "permission",
        "readPermission",
        "writePermission",
        "taskAffinity",
        "targetPackage",
        "process",
    ).forEach(::replaceAndroidAttributePrefix)
}

private fun Element.replaceAndroidAttributePrefix(attribute: String) {
    val qualifiedName = "android:$attribute"
    val value = getAttribute(qualifiedName).ifEmpty {
        getAttributeNS(ANDROID_NAMESPACE, attribute)
    }
    if (!value.contains(PACKAGE_NAME)) return
    val renamed = value.replace(PACKAGE_NAME, TEST_PACKAGE_NAME)
    if (hasAttribute(qualifiedName)) {
        setAttribute(qualifiedName, renamed)
    } else {
        setAttributeNS(ANDROID_NAMESPACE, qualifiedName, renamed)
    }
}
