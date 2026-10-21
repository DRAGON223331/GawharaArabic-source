package net.gawhara.arabic;

import java.text.Bidi;
import java.util.HashMap;
import java.util.Map;

/**
 * محرك تشكيل العربي لـ Bedrock.
 * 1) تشكيل الحروف (منفصل / أول / وسط / آخر + لام-ألف) بـ Presentation Forms.
 * 2) ترتيب بصري (Bidi) بحيث العربي يتعرض من اليمين لليسار والإنجليزي والأرقام يفضلوا سليمين.
 * الناتج نص "بصري" جاهز يتعرض من الشمال لليمين زي ما Bedrock بيعمل.
 */
public final class ArabicShaper {

    /** إعدادات التحويل. */
    public record Options(boolean stripDiacritics, boolean westernDigits, boolean forceRtl) {
        public static final Options DEFAULT = new Options(true, false, true);
    }

    // الحرف -> {منفصل, آخر, أول, وسط}   (0 = الشكل ده مش موجود)
    private static final Map<Character, char[]> FORMS = new HashMap<>();

    /** حرف بيتوصل من الجهتين. */
    private static void dual(int c, int iso, int fin, int ini, int med) {
        FORMS.put((char) c, new char[]{(char) iso, (char) fin, (char) ini, (char) med});
    }

    /** حرف بيتوصل بالحرف اللي قبله بس. */
    private static void right(int c, int iso, int fin) {
        FORMS.put((char) c, new char[]{(char) iso, (char) fin, 0, 0});
    }

    static {
        right(0x0621, 0xFE80, 0);        // ء
        right(0x0622, 0xFE81, 0xFE82);   // آ
        right(0x0623, 0xFE83, 0xFE84);   // أ
        right(0x0624, 0xFE85, 0xFE86);   // ؤ
        right(0x0625, 0xFE87, 0xFE88);   // إ
        dual(0x0626, 0xFE89, 0xFE8A, 0xFE8B, 0xFE8C); // ئ
        right(0x0627, 0xFE8D, 0xFE8E);   // ا
        dual(0x0628, 0xFE8F, 0xFE90, 0xFE91, 0xFE92); // ب
        right(0x0629, 0xFE93, 0xFE94);   // ة
        dual(0x062A, 0xFE95, 0xFE96, 0xFE97, 0xFE98); // ت
        dual(0x062B, 0xFE99, 0xFE9A, 0xFE9B, 0xFE9C); // ث
        dual(0x062C, 0xFE9D, 0xFE9E, 0xFE9F, 0xFEA0); // ج
        dual(0x062D, 0xFEA1, 0xFEA2, 0xFEA3, 0xFEA4); // ح
        dual(0x062E, 0xFEA5, 0xFEA6, 0xFEA7, 0xFEA8); // خ
        right(0x062F, 0xFEA9, 0xFEAA);   // د
        right(0x0630, 0xFEAB, 0xFEAC);   // ذ
        right(0x0631, 0xFEAD, 0xFEAE);   // ر
        right(0x0632, 0xFEAF, 0xFEB0);   // ز
        dual(0x0633, 0xFEB1, 0xFEB2, 0xFEB3, 0xFEB4); // س
        dual(0x0634, 0xFEB5, 0xFEB6, 0xFEB7, 0xFEB8); // ش
        dual(0x0635, 0xFEB9, 0xFEBA, 0xFEBB, 0xFEBC); // ص
        dual(0x0636, 0xFEBD, 0xFEBE, 0xFEBF, 0xFEC0); // ض
        dual(0x0637, 0xFEC1, 0xFEC2, 0xFEC3, 0xFEC4); // ط
        dual(0x0638, 0xFEC5, 0xFEC6, 0xFEC7, 0xFEC8); // ظ
        dual(0x0639, 0xFEC9, 0xFECA, 0xFECB, 0xFECC); // ع
        dual(0x063A, 0xFECD, 0xFECE, 0xFECF, 0xFED0); // غ
        dual(0x0641, 0xFED1, 0xFED2, 0xFED3, 0xFED4); // ف
        dual(0x0642, 0xFED5, 0xFED6, 0xFED7, 0xFED8); // ق
        dual(0x0643, 0xFED9, 0xFEDA, 0xFEDB, 0xFEDC); // ك
        dual(0x0644, 0xFEDD, 0xFEDE, 0xFEDF, 0xFEE0); // ل
        dual(0x0645, 0xFEE1, 0xFEE2, 0xFEE3, 0xFEE4); // م
        dual(0x0646, 0xFEE5, 0xFEE6, 0xFEE7, 0xFEE8); // ن
        dual(0x0647, 0xFEE9, 0xFEEA, 0xFEEB, 0xFEEC); // ه
        right(0x0648, 0xFEED, 0xFEEE);   // و
        right(0x0649, 0xFEEF, 0xFEF0);   // ى
        dual(0x064A, 0xFEF1, 0xFEF2, 0xFEF3, 0xFEF4); // ي

        // حروف إضافية (فارسي / ڤ) - بتتعرض لو الخط بتاع Bedrock فيه الجلايفات
        dual(0x067E, 0xFB56, 0xFB57, 0xFB58, 0xFB59); // پ
        dual(0x0686, 0xFB7A, 0xFB7B, 0xFB7C, 0xFB7D); // چ
        right(0x0698, 0xFB8A, 0xFB8B);   // ژ
        dual(0x06A4, 0xFB6A, 0xFB6B, 0xFB6C, 0xFB6D); // ڤ
        dual(0x06A9, 0xFB8E, 0xFB8F, 0xFB90, 0xFB91); // ک
        dual(0x06AF, 0xFB92, 0xFB93, 0xFB94, 0xFB95); // گ
        dual(0x06CC, 0xFBFC, 0xFBFD, 0xFBFE, 0xFBFF); // ی
    }

    private ArabicShaper() {
    }

    /** فيه حروف عربي لسه متشكلتش؟ (النص المتشكّل بالفعل مش بيتعالج تاني). */
    public static boolean containsArabic(String s) {
        if (s == null) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (FORMS.containsKey(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /** نص بصري + مكان كل حرف في النص الأصلي (عشان نحافظ على الألوان). */
    public record Mapped(String text, int[] src) {
    }

    /** النص فيه Presentation Forms يعني اتعالج قبل كده (بنتجنب نعكسه مرتين). */
    public static boolean containsShaped(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 0xFB50 && c <= 0xFDFF) || (c >= 0xFE70 && c <= 0xFEFF)) {
                return true;
            }
        }
        return false;
    }

    public static String fix(String input) {
        return fix(input, Options.DEFAULT);
    }

    public static String fix(String input, Options opt) {
        if (!containsArabic(input)) {
            return input;
        }
        return fixMapped(input, opt).text();
    }

    /** زي fix بس بيرجّع كمان src[i] = index الحرف الأصلي اللي طلع منه الحرف i في الناتج. */
    public static Mapped fixMapped(String input, Options opt) {
        // 1) تجهيز (شيل التشكيل / تحويل الأرقام) مع تتبع المكان الأصلي
        StringBuilder sb = new StringBuilder(input.length());
        int[] idx = new int[input.length()];
        int n = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (opt.stripDiacritics() && isDiacritic(c)) {
                continue;
            }
            if (opt.westernDigits()) {
                if (c >= 0x0660 && c <= 0x0669) {
                    c = (char) ('0' + (c - 0x0660));
                } else if (c >= 0x06F0 && c <= 0x06F9) {
                    c = (char) ('0' + (c - 0x06F0));
                }
            }
            sb.append(c);
            idx[n++] = i;
        }
        String s = sb.toString();

        // 2) التشكيل
        StringBuilder shaped = new StringBuilder(s.length());
        int[] sidx = new int[s.length()];
        int m = 0;
        boolean prevConnects = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            char[] f = FORMS.get(c);
            if (f == null) {
                shaped.append(c);
                sidx[m++] = idx[i];
                prevConnects = false;
                continue;
            }
            if (c == '\u0644' && i + 1 < s.length()) { // لام-ألف
                char[] lig = lamAlef(s.charAt(i + 1));
                if (lig != null) {
                    shaped.append(prevConnects ? lig[1] : lig[0]);
                    sidx[m++] = idx[i];
                    prevConnects = false;
                    i++;
                    continue;
                }
            }
            boolean joinsNext = false;
            if (f[2] != 0 && i + 1 < s.length()) {
                char[] nf = FORMS.get(s.charAt(i + 1));
                joinsNext = nf != null && nf[1] != 0;
            }
            char glyph;
            if (prevConnects && joinsNext) {
                glyph = f[3];
            } else if (prevConnects) {
                glyph = f[1] != 0 ? f[1] : f[0];
            } else if (joinsNext) {
                glyph = f[2];
            } else {
                glyph = f[0];
            }
            shaped.append(glyph);
            sidx[m++] = idx[i];
            prevConnects = joinsNext;
        }

        // 3) الاتجاه (Bidi)
        return reorder(shaped.toString(), sidx, opt.forceRtl());
    }

    // ---------------------------------------------------------------- مساعدات

    private static boolean isDiacritic(char c) {
        return (c >= 0x064B && c <= 0x065F) || c == 0x0670 || c == 0x0640 // تشكيل + ألف خنجرية + تطويل
                || (c >= 0x06D6 && c <= 0x06ED);                          // علامات قرآنية
    }

    /** لام + ألف => {منفصل, آخر}، أو null. */
    private static char[] lamAlef(char next) {
        return switch (next) {
            case '\u0627' -> new char[]{'\uFEFB', '\uFEFC'}; // لا
            case '\u0623' -> new char[]{'\uFEF7', '\uFEF8'}; // لأ
            case '\u0625' -> new char[]{'\uFEF9', '\uFEFA'}; // لإ
            case '\u0622' -> new char[]{'\uFEF5', '\uFEF6'}; // لآ
            default -> null;
        };
    }

    private static char mirror(char c) {
        return switch (c) {
            case '(' -> ')';
            case ')' -> '(';
            case '[' -> ']';
            case ']' -> '[';
            case '{' -> '}';
            case '}' -> '{';
            case '<' -> '>';
            case '>' -> '<';
            case '\u00AB' -> '\u00BB';
            case '\u00BB' -> '\u00AB';
            default -> c;
        };
    }

    private static Mapped reorder(String text, int[] src, boolean forceRtl) {
        Bidi bidi = new Bidi(text, forceRtl ? Bidi.DIRECTION_RIGHT_TO_LEFT : Bidi.DIRECTION_DEFAULT_RIGHT_TO_LEFT);
        if (bidi.isLeftToRight()) {
            return new Mapped(text, java.util.Arrays.copyOf(src, text.length()));
        }
        int n = bidi.getRunCount();
        byte[] levels = new byte[n];
        Integer[] runs = new Integer[n];
        for (int i = 0; i < n; i++) {
            levels[i] = (byte) bidi.getRunLevel(i);
            runs[i] = i;
        }
        Bidi.reorderVisually(levels, 0, runs, 0, n);

        StringBuilder out = new StringBuilder(text.length());
        int[] outSrc = new int[text.length()];
        int o = 0;
        for (int k = 0; k < n; k++) {
            int r = runs[k];
            int a = bidi.getRunStart(r);
            int b = bidi.getRunLimit(r);
            if ((bidi.getRunLevel(r) & 1) == 1) { // run عربي => نعكسه
                for (int i = b - 1; i >= a; i--) {
                    char ch = text.charAt(i);
                    if (Character.isLowSurrogate(ch) && i - 1 >= a && Character.isHighSurrogate(text.charAt(i - 1))) {
                        out.append(text.charAt(i - 1)).append(ch); // إيموجي: منقلبش الزوج
                        outSrc[o++] = src[i - 1];
                        outSrc[o++] = src[i];
                        i--;
                        continue;
                    }
                    out.append(mirror(ch));
                    outSrc[o++] = src[i];
                }
            } else {
                for (int i = a; i < b; i++) {
                    out.append(text.charAt(i));
                    outSrc[o++] = src[i];
                }
            }
        }
        return new Mapped(out.toString(), outSrc);
    }
}
