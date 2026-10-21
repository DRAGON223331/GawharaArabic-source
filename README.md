# GawharaArabic 1.1

بلجن بيصلّح العربي للاعبين **Bedrock** (Geyser/Floodgate). Bedrock مبيعملش تشكيل للحروف (أول/وسط/آخر) ولا بيعرف اتجاه RTL،
فبيعرض الحروف منفصلة ومعكوسة. البلجن بيعمل التشكيل والاتجاه على السيرفر **لكل مستقبل لوحده**،
فلاعبين Java بيشوفوا النص الأصلي، ولاعبين Bedrock بيشوفوه متظبط.

## إزاي بيشتغل
1. يعرف إن المستقبل Bedrock (Floodgate API لو موجود، أو الـ UUID، أو بريفكس `.` قبل الاسم).
2. يحوّل الحروف لـ Arabic Presentation Forms (شامل لام-ألف).
3. يرتّب النص بصريًا بـ Bidi: العربي من اليمين لليسار، والإنجليزي والأرقام والأقواس تفضل سليمة.
4. بيحافظ على الألوان والـ hover/click لأنه بيشتغل على الـ Component مش على نص مسطّح.

## التركيب
- حط `GawharaArabic.jar` في `plugins/`.
- **PacketEvents** (من Modrinth) في `plugins/` عشان رسايل كل البلجنات تتصلّح. من غيره الشات بس هو اللي بيشتغل.
- Floodgate اختياري. Geyser لازم يكون بلجن على نفس السيرفر.
- الإعدادات في `plugins/GawharaArabic/config.yml`.

## الأوامر
- `/arabicfix` (أو `/arfix`) — يعرض وضعك الحالي.
- `/arabicfix on | off | auto` — كل لاعب يتحكم لنفسه (بيتحفظ في `data.yml`).
- `/arabicfix test` — يبعتلك جملة قبل وبعد الإصلاح عشان تتأكد.
- `/arabicfix reload` — صلاحية `gawhararabic.admin`.

## للمطورين (استدعاء من بلجنات تانية)
```java
ArabicPlugin ar = (ArabicPlugin) Bukkit.getPluginManager().getPlugin("GawharaArabic");
if (ar != null && ar.shouldFix(player)) {
    player.sendMessage(ar.shape(component)); // أو ar.fix("نص")
}
```
مع PacketEvents مش محتاج تعمل ده، الباكتات بتتصلّح لوحدها.

## المغطّى حاليًا
- الشات (مع الحفاظ على فورمات LuckPerms وغيره).
- مع PacketEvents: رسايل أي بلجن (system chat)، Title/Subtitle، ActionBar، Tab list header/footer، عناوين الـ GUI.
- **مش مغطّى:** Scoreboard، Holograms، اللافتات، أسماء الـ items، البوس بار.

## ملاحظات
- الرسايل الطويلة اللي بتنزل سطر جديد في Bedrock ممكن تتلخبط ترتيب أسطرها، لأن Bedrock هو اللي بيقطّع السطر بعد التحويل.
- الحروف الفارسية (پ چ ژ گ ک ی) وڤ متضافة، بس بتظهر لو خط Bedrock فيه الجلايفات دي.
- الاختبار الأهم: `/arabicfix test` من جهاز Bedrock حقيقي (الموبايل ساعات بيختلف عن Windows).

البناء: JDK 25 + `mvn clean package` → `target/GawharaArabic.jar`
