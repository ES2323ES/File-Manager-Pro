package com.aishhatorah.filemanagerpro;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * סעיף 16: מפענח מובנה מלא לקובצי XML בינארי (Manifest + res/*.xml),
 * קבצי DEX (מחלקות, מתודות, שדות, טיפוסים ומחרוזות) וקובץ resources.arsc.
 */
public final class ApkSourceParser {

    private ApkSourceParser() {
    }

    private static final Map<Integer, String> ANDROID_ATTR_IDS = new HashMap<>();

    static {
        ANDROID_ATTR_IDS.put(0x01010000, "theme");
        ANDROID_ATTR_IDS.put(0x01010001, "label");
        ANDROID_ATTR_IDS.put(0x01010002, "icon");
        ANDROID_ATTR_IDS.put(0x01010003, "name");
        ANDROID_ATTR_IDS.put(0x01010006, "permission");
        ANDROID_ATTR_IDS.put(0x0101000f, "enabled");
        ANDROID_ATTR_IDS.put(0x01010010, "exported");
        ANDROID_ATTR_IDS.put(0x01010013, "debuggable");
        ANDROID_ATTR_IDS.put(0x01010018, "authorities");
        ANDROID_ATTR_IDS.put(0x0101001e, "launchMode");
        ANDROID_ATTR_IDS.put(0x0101001f, "configChanges");
        ANDROID_ATTR_IDS.put(0x01010025, "host");
        ANDROID_ATTR_IDS.put(0x01010026, "mimeType");
        ANDROID_ATTR_IDS.put(0x01010027, "scheme");
        ANDROID_ATTR_IDS.put(0x0101002a, "pathPattern");
        ANDROID_ATTR_IDS.put(0x010100d0, "id");
        ANDROID_ATTR_IDS.put(0x010100d1, "tag");
        ANDROID_ATTR_IDS.put(0x010100d4, "background");
        ANDROID_ATTR_IDS.put(0x010100d5, "padding");
        ANDROID_ATTR_IDS.put(0x010100d6, "paddingLeft");
        ANDROID_ATTR_IDS.put(0x010100d7, "paddingTop");
        ANDROID_ATTR_IDS.put(0x010100d8, "paddingRight");
        ANDROID_ATTR_IDS.put(0x010100d9, "paddingBottom");
        ANDROID_ATTR_IDS.put(0x010100da, "focusable");
        ANDROID_ATTR_IDS.put(0x010100dc, "visibility");
        ANDROID_ATTR_IDS.put(0x010100e5, "clickable");
        ANDROID_ATTR_IDS.put(0x010100f4, "layout_width");
        ANDROID_ATTR_IDS.put(0x010100f5, "layout_height");
        ANDROID_ATTR_IDS.put(0x010100f6, "layout_margin");
        ANDROID_ATTR_IDS.put(0x010100fb, "orientation");
        ANDROID_ATTR_IDS.put(0x010100af, "gravity");
        ANDROID_ATTR_IDS.put(0x01010095, "textSize");
        ANDROID_ATTR_IDS.put(0x01010097, "textStyle");
        ANDROID_ATTR_IDS.put(0x01010098, "textColor");
        ANDROID_ATTR_IDS.put(0x01010119, "src");
        ANDROID_ATTR_IDS.put(0x0101011d, "scaleType");
        ANDROID_ATTR_IDS.put(0x0101014f, "text");
        ANDROID_ATTR_IDS.put(0x01010150, "hint");
        ANDROID_ATTR_IDS.put(0x0101020c, "minSdkVersion");
        ANDROID_ATTR_IDS.put(0x0101021b, "versionCode");
        ANDROID_ATTR_IDS.put(0x0101021c, "versionName");
        ANDROID_ATTR_IDS.put(0x01010270, "targetSdkVersion");
        ANDROID_ATTR_IDS.put(0x01010280, "allowBackup");
        ANDROID_ATTR_IDS.put(0x010103af, "supportsRtl");
        ANDROID_ATTR_IDS.put(0x010103f4, "roundIcon");
        ANDROID_ATTR_IDS.put(0x01010572, "compileSdkVersion");
    }

    public static String decodeBinaryXml(byte[] data) {
        if (data == null || data.length < 16) return "קובץ XML קצר מדי";
        String prefixCheck = new String(data, 0, Math.min(40, data.length), StandardCharsets.UTF_8).trim();
        if (prefixCheck.startsWith("<")) {
            return new String(data, StandardCharsets.UTF_8);
        }

        try {
            ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            int fileType = bb.getShort(0) & 0xFFFF;
            if (fileType != 0x0003) {
                return "פורמט XML בינארי לא מזוהה (0x" + Integer.toHexString(fileType) + ")";
            }
            int headerSize = bb.getShort(2) & 0xFFFF;
            List<String> stringPool = new ArrayList<>();
            int[] resourceMap = new int[0];
            Map<String, String> nsPrefixByUri = new HashMap<>();
            nsPrefixByUri.put("http://schemas.android.com/apk/res/android", "android");
            nsPrefixByUri.put("http://schemas.android.com/apk/res-auto", "app");
            nsPrefixByUri.put("http://schemas.android.com/tools", "tools");

            int pos = headerSize;
            StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
            int indent = 0;

            while (pos + 8 <= data.length) {
                int chunkType = bb.getShort(pos) & 0xFFFF;
                int chunkHdrSize = bb.getShort(pos + 2) & 0xFFFF;
                int chunkSize = bb.getInt(pos + 4);
                if (chunkSize < 8 || pos + chunkSize > data.length) break;

                if (chunkType == 0x0001) { // RES_STRING_POOL_TYPE
                    stringPool = parseStringPool(data, pos, bb);
                } else if (chunkType == 0x0180) { // RES_XML_RESOURCE_MAP_TYPE
                    int count = Math.max(0, (chunkSize - chunkHdrSize) / 4);
                    resourceMap = new int[count];
                    int off = pos + chunkHdrSize;
                    for (int i = 0; i < count && off + 4 <= pos + chunkSize; i++) {
                        resourceMap[i] = bb.getInt(off);
                        off += 4;
                    }
                } else if (chunkType == 0x0100) { // Start Namespace
                    int nsPrefixIdx = bb.getInt(pos + 16);
                    int nsUriIdx = bb.getInt(pos + 20);
                    String nsP = getStr(stringPool, nsPrefixIdx, "");
                    String nsU = getStr(stringPool, nsUriIdx, "");
                    if (!nsU.isEmpty() && !nsP.isEmpty()) {
                        nsPrefixByUri.put(nsU, nsP);
                    }
                } else if (chunkType == 0x0102) { // Start Tag
                    int nameIdx = bb.getInt(pos + 20);
                    int attrStart = bb.getShort(pos + 24) & 0xFFFF;
                    int attrSize = Math.max(20, bb.getShort(pos + 26) & 0xFFFF);
                    int attrCount = bb.getShort(pos + 28) & 0xFFFF;
                    String tagName = getStr(stringPool, nameIdx, "tag");
                    appendIndent(xml, indent);
                    xml.append("<").append(tagName);
                    int attrPos = pos + chunkHdrSize + attrStart;
                    if (attrStart < 20 || attrPos + attrCount * attrSize > pos + chunkSize) {
                        attrPos = pos + 36;
                    }
                    for (int a = 0; a < attrCount && attrPos + 20 <= pos + chunkSize; a++) {
                        int aNsIdx = bb.getInt(attrPos);
                        int aNameIdx = bb.getInt(attrPos + 4);
                        int aRawIdx = bb.getInt(attrPos + 8);
                        int aValType = (bb.getInt(attrPos + 12) >>> 24) & 0xFF;
                        int aValData = bb.getInt(attrPos + 16);

                        String attrName = getStr(stringPool, aNameIdx, "");
                        if (attrName.isEmpty() && aNameIdx >= 0 && aNameIdx < resourceMap.length) {
                            int resId = resourceMap[aNameIdx];
                            String mapped = ANDROID_ATTR_IDS.get(resId);
                            attrName = mapped != null ? mapped : ("attr_0x" + Integer.toHexString(resId));
                        }
                        if (attrName.isEmpty()) attrName = "attr_" + a;

                        String nsPrefix = "";
                        if (aNsIdx >= 0) {
                            String uri = getStr(stringPool, aNsIdx, "");
                            String mappedP = nsPrefixByUri.get(uri);
                            nsPrefix = (mappedP != null && !mappedP.isEmpty()) ? (mappedP + ":") : "android:";
                        }

                        String val = formatAxmlValue(stringPool, aRawIdx, aValType, aValData);
                        xml.append("\n");
                        appendIndent(xml, indent + 1);
                        xml.append(nsPrefix).append(attrName).append("=\"").append(escapeXmlAttr(val)).append("\"");
                        attrPos += attrSize;
                    }
                    xml.append(">\n");
                    indent++;
                } else if (chunkType == 0x0103) { // End Tag
                    int nameIdx = bb.getInt(pos + 20);
                    indent = Math.max(0, indent - 1);
                    appendIndent(xml, indent);
                    xml.append("</").append(getStr(stringPool, nameIdx, "tag")).append(">\n");
                }
                pos += chunkSize;
            }
            return xml.toString();
        } catch (Throwable t) {
            return "שגיאה בפענוח XML בינארי: " + t.getMessage();
        }
    }

    private static String escapeXmlAttr(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static List<String> parseStringPool(byte[] data, int pos, ByteBuffer bb) {
        List<String> list = new ArrayList<>();
        if (pos + 28 > data.length) return list;
        int stringCount = bb.getInt(pos + 8);
        int flags = bb.getInt(pos + 16);
        boolean utf8 = (flags & 0x0100) != 0;
        int stringsStart = pos + bb.getInt(pos + 20);
        int offsetsBase = pos + 28;
        if (stringCount < 0 || stringCount > 1_000_000) return list;

        for (int i = 0; i < stringCount && offsetsBase + i * 4 + 4 <= data.length; i++) {
            int relOff = bb.getInt(offsetsBase + i * 4);
            int strOffset = stringsStart + relOff;
            if (relOff < 0 || strOffset < 0 || strOffset >= data.length - 1) {
                list.add("");
                continue;
            }
            if (utf8) {
                int p = strOffset;
                int u16len = data[p++] & 0xFF;
                if ((u16len & 0x80) != 0 && p < data.length) p++;
                if (p >= data.length) {
                    list.add("");
                    continue;
                }
                int u8len = data[p++] & 0xFF;
                if ((u8len & 0x80) != 0 && p < data.length) {
                    u8len = ((u8len & 0x7F) << 8) | (data[p++] & 0xFF);
                }
                int safeLen = Math.min(Math.max(0, u8len), Math.max(0, data.length - p));
                list.add(new String(data, p, safeLen, StandardCharsets.UTF_8));
            } else {
                if (strOffset + 2 > data.length) {
                    list.add("");
                    continue;
                }
                int len = bb.getShort(strOffset) & 0xFFFF;
                if ((len & 0x8000) != 0 && strOffset + 4 <= data.length) {
                    len = ((len & 0x7FFF) << 16) | (bb.getShort(strOffset + 2) & 0xFFFF);
                    strOffset += 2;
                }
                int start = strOffset + 2;
                int byteLen = Math.min(Math.max(0, len * 2), Math.max(0, data.length - start));
                list.add(new String(data, start, byteLen, StandardCharsets.UTF_16LE));
            }
        }
        return list;
    }

    private static String getStr(List<String> pool, int idx, String def) {
        if (idx >= 0 && idx < pool.size()) return pool.get(idx);
        return def;
    }

    private static String formatAxmlValue(List<String> pool, int rawIdx, int type, int data) {
        if (rawIdx >= 0 && rawIdx < pool.size()) return pool.get(rawIdx);
        switch (type) {
            case 0x00:
                return "@null";
            case 0x01:
                return String.format(Locale.US, "@res/0x%08x", data);
            case 0x02:
                return String.format(Locale.US, "?attr/0x%08x", data);
            case 0x03:
                return (data >= 0 && data < pool.size()) ? pool.get(data) : "";
            case 0x04:
                return String.valueOf(Float.intBitsToFloat(data));
            case 0x05:
                return formatComplexDimension(data);
            case 0x06:
                return formatComplexFraction(data);
            case 0x10:
                return String.valueOf(data);
            case 0x11:
                return "0x" + Integer.toHexString(data);
            case 0x12:
                return data != 0 ? "true" : "false";
            case 0x1c:
            case 0x1d:
            case 0x1e:
            case 0x1f:
                return String.format(Locale.US, "#%08X", data);
            default:
                return String.valueOf(data);
        }
    }

    private static final String[] DIMEN_UNITS = {"px", "dp", "sp", "pt", "in", "mm"};
    private static final float[] RADIX_MULTS = {1.0f, 1.0f / (1 << 7), 1.0f / (1 << 15), 1.0f / (1 << 23)};

    private static float complexToFloat(int data) {
        return (data & 0xFFFFFF00) * RADIX_MULTS[(data >> 4) & 3];
    }

    private static String formatComplexDimension(int data) {
        int unit = data & 0xF;
        String u = (unit >= 0 && unit < DIMEN_UNITS.length) ? DIMEN_UNITS[unit] : "u";
        float val = complexToFloat(data);
        if (Math.abs(val - Math.round(val)) < 0.001f) {
            return Math.round(val) + u;
        }
        return String.format(Locale.US, "%.2f%s", val, u);
    }

    private static String formatComplexFraction(int data) {
        int unit = data & 0xF;
        float val = complexToFloat(data) * 100f;
        return String.format(Locale.US, "%.1f%s", val, unit == 1 ? "%p" : "%");
    }

    private static void appendIndent(StringBuilder sb, int indent) {
        for (int i = 0; i < indent; i++) sb.append("  ");
    }

    // ==================== DEX Inspector (Full Classes, Methods, Fields, Types, Strings) ====================

    public static class DexClassDetails {
        public final String packageName;
        public final String className;
        public final String superClassName;
        public final int accessFlags;
        public final List<String> fields = new ArrayList<>();
        public final List<String> methods = new ArrayList<>();

        DexClassDetails(String pkg, String cls, String superCls, int flags) {
            this.packageName = pkg;
            this.className = cls;
            this.superClassName = superCls;
            this.accessFlags = flags;
        }

        public String formatFlags() {
            StringBuilder sb = new StringBuilder();
            if ((accessFlags & 0x0001) != 0) sb.append("public ");
            if ((accessFlags & 0x0200) != 0) sb.append("interface ");
            else if ((accessFlags & 0x4000) != 0) sb.append("enum ");
            else {
                if ((accessFlags & 0x0400) != 0) sb.append("abstract ");
                if ((accessFlags & 0x0010) != 0) sb.append("final ");
                sb.append("class ");
            }
            return sb.toString().trim();
        }
    }

    public static class DexInspectionResult {
        public String dexVersion = "035";
        public int stringCount;
        public int typeCount;
        public int protoCount;
        public int fieldCount;
        public int methodCount;
        public int classCount;

        public final Map<String, List<DexClassDetails>> packages = new TreeMap<>();
        public final List<String> methods = new ArrayList<>();
        public final List<String> fields = new ArrayList<>();
        public final List<String> strings = new ArrayList<>();
        public final List<String> types = new ArrayList<>();
    }

    public static DexInspectionResult parseDexFull(byte[] dexBytes) {
        DexInspectionResult res = new DexInspectionResult();
        if (dexBytes == null || dexBytes.length < 112) return res;
        if (dexBytes[0] != 'd' || dexBytes[1] != 'e' || dexBytes[2] != 'x' || dexBytes[3] != '\n') {
            return res;
        }

        res.dexVersion = new String(dexBytes, 4, 3, StandardCharsets.US_ASCII);
        ByteBuffer bb = ByteBuffer.wrap(dexBytes).order(ByteOrder.LITTLE_ENDIAN);

        int stringIdsSize = Math.max(0, bb.getInt(56));
        int stringIdsOff = bb.getInt(60);
        int typeIdsSize = Math.max(0, bb.getInt(64));
        int typeIdsOff = bb.getInt(68);
        int protoIdsSize = Math.max(0, bb.getInt(72));
        int protoIdsOff = bb.getInt(76);
        int fieldIdsSize = Math.max(0, bb.getInt(80));
        int fieldIdsOff = bb.getInt(84);
        int methodIdsSize = Math.max(0, bb.getInt(88));
        int methodIdsOff = bb.getInt(92);
        int classDefsSize = Math.max(0, bb.getInt(96));
        int classDefsOff = bb.getInt(100);

        res.stringCount = stringIdsSize;
        res.typeCount = typeIdsSize;
        res.protoCount = protoIdsSize;
        res.fieldCount = fieldIdsSize;
        res.methodCount = methodIdsSize;
        res.classCount = classDefsSize;

        // Decode strings cache
        String[] strCache = new String[Math.min(stringIdsSize, 250_000)];
        for (int i = 0; i < strCache.length; i++) {
            int ptrOff = stringIdsOff + i * 4;
            if (ptrOff < 0 || ptrOff + 4 > dexBytes.length) break;
            String s = readDexMutf8String(dexBytes, bb.getInt(ptrOff));
            strCache[i] = s;
            if (res.strings.size() < 6000 && !s.isEmpty() && s.length() <= 240) {
                res.strings.add(s);
            }
        }

        // Decode type cache
        String[] typeCache = new String[Math.min(typeIdsSize, 120_000)];
        for (int i = 0; i < typeCache.length; i++) {
            int tOff = typeIdsOff + i * 4;
            if (tOff < 0 || tOff + 4 > dexBytes.length) break;
            int sIdx = bb.getInt(tOff);
            String desc = (sIdx >= 0 && sIdx < strCache.length && strCache[sIdx] != null) ? strCache[sIdx] : "?";
            String readable = formatDexType(desc);
            typeCache[i] = readable;
            if (res.types.size() < 6000) {
                res.types.add(readable);
            }
        }

        // Decode fields and index per defining class
        Map<Integer, List<String>> classFieldsMap = new HashMap<>();
        for (int i = 0; i < fieldIdsSize; i++) {
            int fOff = fieldIdsOff + i * 8;
            if (fOff < 0 || fOff + 8 > dexBytes.length) break;
            int classIdx = bb.getShort(fOff) & 0xFFFF;
            int typeIdx = bb.getShort(fOff + 2) & 0xFFFF;
            int nameIdx = bb.getInt(fOff + 4);
            String clsName = getSafeArr(typeCache, classIdx, "?");
            String fType = getSafeArr(typeCache, typeIdx, "?");
            String fName = getSafeArr(strCache, nameIdx, "field");
            String decl = fType + " " + fName;
            classFieldsMap.computeIfAbsent(classIdx, k -> new ArrayList<>()).add(decl);
            if (res.fields.size() < 8000) {
                res.fields.add(clsName + " -> " + decl);
            }
        }

        // Decode methods and index per defining class
        Map<Integer, List<String>> classMethodsMap = new HashMap<>();
        for (int i = 0; i < methodIdsSize; i++) {
            int mOff = methodIdsOff + i * 8;
            if (mOff < 0 || mOff + 8 > dexBytes.length) break;
            int classIdx = bb.getShort(mOff) & 0xFFFF;
            int protoIdx = bb.getShort(mOff + 2) & 0xFFFF;
            int nameIdx = bb.getInt(mOff + 4);
            String clsName = getSafeArr(typeCache, classIdx, "?");
            String mName = getSafeArr(strCache, nameIdx, "method");
            String sig = formatProtoSignature(dexBytes, bb, protoIdx, protoIdsSize, protoIdsOff, typeCache);
            String decl = mName + sig;
            classMethodsMap.computeIfAbsent(classIdx, k -> new ArrayList<>()).add(decl);
            if (res.methods.size() < 10000) {
                res.methods.add(clsName + "." + decl);
            }
        }

        // Decode class_defs
        for (int i = 0; i < classDefsSize; i++) {
            int defOff = classDefsOff + i * 32;
            if (defOff < 0 || defOff + 32 > dexBytes.length) break;
            int classTypeIdx = bb.getInt(defOff);
            int accessFlags = bb.getInt(defOff + 4);
            int superIdx = bb.getInt(defOff + 8);
            String fqcn = getSafeArr(typeCache, classTypeIdx, "");
            if (fqcn.isEmpty()) continue;

            String superName = superIdx >= 0 ? getSafeArr(typeCache, superIdx, "") : "";
            int lastDot = fqcn.lastIndexOf('.');
            String pkg = lastDot > 0 ? fqcn.substring(0, lastDot) : "(default)";
            String cls = lastDot > 0 ? fqcn.substring(lastDot + 1) : fqcn;

            DexClassDetails details = new DexClassDetails(pkg, cls, superName, accessFlags);
            List<String> fList = classFieldsMap.get(classTypeIdx);
            if (fList != null) details.fields.addAll(fList);
            List<String> mList = classMethodsMap.get(classTypeIdx);
            if (mList != null) details.methods.addAll(mList);

            res.packages.computeIfAbsent(pkg, k -> new ArrayList<>()).add(details);
        }

        for (List<DexClassDetails> list : res.packages.values()) {
            Collections.sort(list, (a, b) -> a.className.compareToIgnoreCase(b.className));
        }
        return res;
    }

    private static String formatProtoSignature(byte[] dexBytes, ByteBuffer bb, int protoIdx,
                                                int protoIdsSize, int protoIdsOff, String[] typeCache) {
        if (protoIdx < 0 || protoIdx >= protoIdsSize) return "()";
        int pOff = protoIdsOff + protoIdx * 12;
        if (pOff < 0 || pOff + 12 > dexBytes.length) return "()";
        int retTypeIdx = bb.getInt(pOff + 4);
        int paramsOff = bb.getInt(pOff + 8);
        String retType = getSafeArr(typeCache, retTypeIdx, "void");

        StringBuilder sb = new StringBuilder("(");
        if (paramsOff > 0 && paramsOff + 4 <= dexBytes.length) {
            int pCount = bb.getInt(paramsOff);
            if (pCount > 0 && pCount <= 64) {
                for (int p = 0; p < pCount; p++) {
                    int itemOff = paramsOff + 4 + p * 2;
                    if (itemOff + 2 > dexBytes.length) break;
                    int tIdx = bb.getShort(itemOff) & 0xFFFF;
                    if (p > 0) sb.append(", ");
                    sb.append(shortTypeName(getSafeArr(typeCache, tIdx, "?")));
                }
            }
        }
        sb.append(") : ").append(shortTypeName(retType));
        return sb.toString();
    }

    private static String shortTypeName(String full) {
        if (full == null) return "?";
        int dot = full.lastIndexOf('.');
        return dot >= 0 ? full.substring(dot + 1) : full;
    }

    private static String formatDexType(String desc) {
        if (desc == null || desc.isEmpty()) return "?";
        int dims = 0;
        int idx = 0;
        while (idx < desc.length() && desc.charAt(idx) == '[') {
            dims++;
            idx++;
        }
        if (idx >= desc.length()) return desc;
        char c = desc.charAt(idx);
        String base;
        switch (c) {
            case 'V': base = "void"; break;
            case 'Z': base = "boolean"; break;
            case 'B': base = "byte"; break;
            case 'S': base = "short"; break;
            case 'C': base = "char"; break;
            case 'I': base = "int"; break;
            case 'J': base = "long"; break;
            case 'F': base = "float"; break;
            case 'D': base = "double"; break;
            case 'L':
                base = desc.endsWith(";")
                        ? desc.substring(idx + 1, desc.length() - 1).replace('/', '.')
                        : desc.substring(idx + 1).replace('/', '.');
                break;
            default:
                base = desc;
                break;
        }
        if (dims == 0) return base;
        StringBuilder sb = new StringBuilder(base);
        for (int i = 0; i < dims; i++) sb.append("[]");
        return sb.toString();
    }

    private static String getSafeArr(String[] arr, int idx, String def) {
        if (arr == null || idx < 0 || idx >= arr.length) return def;
        return arr[idx] != null ? arr[idx] : def;
    }

    public static Map<String, List<String>> parseDexPackagesAndClasses(byte[] dexBytes) {
        DexInspectionResult full = parseDexFull(dexBytes);
        Map<String, List<String>> grouped = new TreeMap<>();
        for (Map.Entry<String, List<DexClassDetails>> e : full.packages.entrySet()) {
            List<String> clsNames = new ArrayList<>();
            for (DexClassDetails d : e.getValue()) clsNames.add(d.className);
            grouped.put(e.getKey(), clsNames);
        }
        return grouped;
    }

    private static String readDexMutf8String(byte[] dex, int off) {
        if (off < 0 || off >= dex.length) return "";
        int p = off;
        while (p < dex.length && (dex[p++] & 0x80) != 0) {
        }
        int start = p;
        while (p < dex.length && dex[p] != 0) p++;
        return new String(dex, start, Math.max(0, p - start), StandardCharsets.UTF_8);
    }

    // ==================== resources.arsc Decoder ====================

    public static class ArscResourceEntry {
        public final int resourceId;
        public final String typeName;
        public final String entryName;
        public final String valueDisplay;

        ArscResourceEntry(int resourceId, String typeName, String entryName, String valueDisplay) {
            this.resourceId = resourceId;
            this.typeName = typeName;
            this.entryName = entryName;
            this.valueDisplay = valueDisplay;
        }
    }

    public static class ArscInspectionResult {
        public String packageName = "";
        public int packageCount;
        public final Map<String, List<ArscResourceEntry>> byType = new LinkedHashMap<>();
        public int totalEntries;
    }

    public static ArscInspectionResult parseResourcesArsc(byte[] data) {
        ArscInspectionResult result = new ArscInspectionResult();
        if (data == null || data.length < 12) return result;

        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int rootType = bb.getShort(0) & 0xFFFF;
        int rootHdrSize = bb.getShort(2) & 0xFFFF;
        if (rootType != 0x0002 || rootHdrSize > data.length) {
            return result;
        }
        result.packageCount = bb.getInt(8);

        List<String> globalStrings = new ArrayList<>();
        int pos = rootHdrSize;

        while (pos + 8 <= data.length) {
            int chunkType = bb.getShort(pos) & 0xFFFF;
            int chunkHdrSize = bb.getShort(pos + 2) & 0xFFFF;
            int chunkSize = bb.getInt(pos + 4);
            if (chunkSize < 8 || pos + chunkSize > data.length) break;

            if (chunkType == 0x0001) { // Global Value String Pool
                globalStrings = parseStringPool(data, pos, bb);
            } else if (chunkType == 0x0200) { // RES_TABLE_PACKAGE_TYPE
                parseArscPackageChunk(data, bb, pos, chunkHdrSize, chunkSize, globalStrings, result);
            }
            pos += chunkSize;
        }
        return result;
    }

    private static void parseArscPackageChunk(byte[] data, ByteBuffer bb, int pkgPos,
                                              int pkgHdrSize, int pkgSize,
                                              List<String> globalStrings,
                                              ArscInspectionResult result) {
        if (pkgPos + 284 > pkgPos + pkgSize) return;
        int pkgId = bb.getInt(pkgPos + 8);
        String pkgName = readFixedUtf16(data, pkgPos + 12, 128);
        if (result.packageName.isEmpty() && !pkgName.isEmpty()) {
            result.packageName = pkgName;
        }

        int typeStringsOff = bb.getInt(pkgPos + 268);
        int keyStringsOff = bb.getInt(pkgPos + 276);

        List<String> typeStrings = Collections.emptyList();
        if (typeStringsOff > 0 && pkgPos + typeStringsOff + 8 <= data.length) {
            typeStrings = parseStringPool(data, pkgPos + typeStringsOff, bb);
        }
        List<String> keyStrings = Collections.emptyList();
        if (keyStringsOff > 0 && pkgPos + keyStringsOff + 8 <= data.length) {
            keyStrings = parseStringPool(data, pkgPos + keyStringsOff, bb);
        }

        int pkgEnd = Math.min(data.length, pkgPos + pkgSize);
        int subPos = pkgPos + Math.max(pkgHdrSize, 284);
        // If string pools came right after package header, iterate chunks starting cleanly from pkgPos + pkgHdrSize
        subPos = pkgPos + pkgHdrSize;

        while (subPos + 8 <= pkgEnd) {
            int subType = bb.getShort(subPos) & 0xFFFF;
            int subHdr = bb.getShort(subPos + 2) & 0xFFFF;
            int subSize = bb.getInt(subPos + 4);
            if (subSize < 8 || subPos + subSize > pkgEnd) break;

            if (subType == 0x0201 && subPos + subHdr <= pkgEnd) { // RES_TABLE_TYPE_TYPE
                int typeId = data[subPos + 8] & 0xFF;
                int entryCount = bb.getInt(subPos + 12);
                int entriesStart = subPos + bb.getInt(subPos + 16);
                String typeName = getStr(typeStrings, typeId - 1, "type_" + typeId);
                int offsetsStart = subPos + subHdr;

                int maxEntries = Math.min(entryCount, 40000);
                for (int eIdx = 0; eIdx < maxEntries; eIdx++) {
                    if (offsetsStart + eIdx * 4 + 4 > subPos + subSize) break;
                    int relEntryOff = bb.getInt(offsetsStart + eIdx * 4);
                    if (relEntryOff == -1) continue; // NO_ENTRY

                    int eOff = entriesStart + relEntryOff;
                    if (eOff < subPos || eOff + 8 > subPos + subSize) continue;

                    int eSize = bb.getShort(eOff) & 0xFFFF;
                    int eFlags = bb.getShort(eOff + 2) & 0xFFFF;
                    int keyIdx = bb.getInt(eOff + 4);
                    String keyName = getStr(keyStrings, keyIdx, "res_" + eIdx);
                    int resId = (pkgId << 24) | (typeId << 16) | eIdx;

                    String valStr;
                    boolean isComplex = (eFlags & 0x0001) != 0;
                    if (isComplex) {
                        int count = (eOff + 16 <= subPos + subSize) ? bb.getInt(eOff + 12) : 0;
                        valStr = "[Complex/Bag • " + Math.max(0, count) + " items]";
                    } else if (eOff + eSize + 8 <= subPos + subSize) {
                        int valType = data[eOff + eSize + 3] & 0xFF;
                        int valData = bb.getInt(eOff + eSize + 4);
                        valStr = formatAxmlValue(globalStrings, -1, valType, valData);
                    } else {
                        valStr = "(empty)";
                    }

                    List<ArscResourceEntry> bucket = result.byType.computeIfAbsent(typeName, k -> new ArrayList<>());
                    if (bucket.size() < 3000) {
                        bucket.add(new ArscResourceEntry(resId, typeName, keyName, valStr));
                    }
                    result.totalEntries++;
                }
            }
            subPos += subSize;
        }
    }

    private static String readFixedUtf16(byte[] data, int off, int maxChars) {
        int len = 0;
        while (len < maxChars && off + len * 2 + 1 < data.length) {
            if (data[off + len * 2] == 0 && data[off + len * 2 + 1] == 0) break;
            len++;
        }
        return new String(data, off, len * 2, StandardCharsets.UTF_16LE).trim();
    }
}

