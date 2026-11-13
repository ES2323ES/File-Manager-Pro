package com.aishhatorah.filemanagerpro;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * סעיף 16: מפענח מובנה (בסגנון MT Manager) לקובץ AndroidManifest.xml הבינארי (AXML)
 * ולקבצי DEX מתוך חבילות APK.
 */
public final class ApkSourceParser {

    private ApkSourceParser() {
    }

    public static String decodeBinaryXml(byte[] data) {
        if (data == null || data.length < 16) return "קובץ XML קצר מדי";
        String prefix = new String(data, 0, Math.min(20, data.length), StandardCharsets.UTF_8).trim();
        if (prefix.startsWith("<")) {
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
            int pos = headerSize;
            StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
            int indent = 0;

            while (pos + 8 <= data.length) {
                int chunkType = bb.getShort(pos) & 0xFFFF;
                int chunkHdrSize = bb.getShort(pos + 2) & 0xFFFF;
                int chunkSize = bb.getInt(pos + 4);
                if (chunkSize < 8 || pos + chunkSize > data.length) break;

                if (chunkType == 0x0001) { // String Pool
                    stringPool = parseStringPool(data, pos, bb);
                } else if (chunkType == 0x0102) { // Start Tag
                    int nameIdx = bb.getInt(pos + 20);
                    int attrStart = bb.getShort(pos + 24) & 0xFFFF;
                    int attrSize = bb.getShort(pos + 26) & 0xFFFF;
                    int attrCount = bb.getShort(pos + 28) & 0xFFFF;
                    String tagName = getStr(stringPool, nameIdx, "tag");
                    appendIndent(xml, indent);
                    xml.append("<").append(tagName);
                    int attrPos = pos + chunkHdrSize + (attrStart > 20 ? 0 : 0);
                    if (attrStart > 0 && attrPos + attrCount * Math.max(20, attrSize) > pos + chunkSize) {
                        attrPos = pos + 36;
                    } else if (attrPos < pos + 36) {
                        attrPos = pos + 36;
                    }
                    for (int a = 0; a < attrCount && attrPos + 20 <= pos + chunkSize; a++) {
                        int aNsIdx = bb.getInt(attrPos);
                        int aNameIdx = bb.getInt(attrPos + 4);
                        int aRawIdx = bb.getInt(attrPos + 8);
                        int aValType = (bb.getInt(attrPos + 12) >>> 24) & 0xFF;
                        int aValData = bb.getInt(attrPos + 16);
                        String attrName = getStr(stringPool, aNameIdx, "attr");
                        String nsPrefix = aNsIdx >= 0 ? "android:" : "";
                        String val = formatAxmlValue(stringPool, aRawIdx, aValType, aValData);
                        xml.append("\n");
                        appendIndent(xml, indent + 1);
                        xml.append(nsPrefix).append(attrName).append("=\"").append(val).append("\"");
                        attrPos += Math.max(20, attrSize);
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
            return "שגיאה בפענוח AXML: " + t.getMessage();
        }
    }

    private static List<String> parseStringPool(byte[] data, int pos, ByteBuffer bb) {
        List<String> list = new ArrayList<>();
        int stringCount = bb.getInt(pos + 8);
        int flags = bb.getInt(pos + 16);
        boolean utf8 = (flags & 0x0100) != 0;
        int stringsStart = pos + bb.getInt(pos + 20);
        int offsetsBase = pos + 28;
        for (int i = 0; i < stringCount && offsetsBase + i * 4 + 4 <= data.length; i++) {
            int strOffset = stringsStart + bb.getInt(offsetsBase + i * 4);
            if (strOffset < 0 || strOffset >= data.length - 2) {
                list.add("");
                continue;
            }
            if (utf8) {
                int p = strOffset;
                int u16len = data[p++] & 0xFF;
                if ((u16len & 0x80) != 0 && p < data.length) p++;
                int u8len = data[p++] & 0xFF;
                if ((u8len & 0x80) != 0 && p < data.length) {
                    u8len = ((u8len & 0x7F) << 8) | (data[p++] & 0xFF);
                }
                int safeLen = Math.min(u8len, Math.max(0, data.length - p));
                list.add(new String(data, p, safeLen, StandardCharsets.UTF_8));
            } else {
                int len = bb.getShort(strOffset) & 0xFFFF;
                int start = strOffset + 2;
                int byteLen = Math.min(len * 2, Math.max(0, data.length - start));
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
        if (type == 0x03 && data >= 0 && data < pool.size()) return pool.get(data);
        if (type == 0x10) return String.valueOf(data);
        if (type == 0x11) return "0x" + Integer.toHexString(data);
        if (type == 0x12) return data != 0 ? "true" : "false";
        if (type == 0x01) return "@res/0x" + Integer.toHexString(data);
        return String.valueOf(data);
    }

    private static void appendIndent(StringBuilder sb, int indent) {
        for (int i = 0; i < indent; i++) sb.append("  ");
    }

    public static Map<String, List<String>> parseDexPackagesAndClasses(byte[] dexBytes) {
        Map<String, List<String>> grouped = new TreeMap<>();
        if (dexBytes == null || dexBytes.length < 112) return grouped;
        if (dexBytes[0] != 'd' || dexBytes[1] != 'e' || dexBytes[2] != 'x' || dexBytes[3] != '\n') {
            return grouped;
        }
        ByteBuffer bb = ByteBuffer.wrap(dexBytes).order(ByteOrder.LITTLE_ENDIAN);
        int stringIdsSize = bb.getInt(56);
        int stringIdsOff = bb.getInt(60);
        int typeIdsSize = bb.getInt(64);
        int typeIdsOff = bb.getInt(68);
        int classDefsSize = bb.getInt(96);
        int classDefsOff = bb.getInt(100);

        for (int i = 0; i < classDefsSize; i++) {
            int defOff = classDefsOff + i * 32;
            if (defOff + 4 > dexBytes.length) break;
            int classTypeIdx = bb.getInt(defOff);
            if (classTypeIdx < 0 || classTypeIdx >= typeIdsSize) continue;
            int descStringIdx = bb.getInt(typeIdsOff + classTypeIdx * 4);
            if (descStringIdx < 0 || descStringIdx >= stringIdsSize) continue;
            int stringDataOff = bb.getInt(stringIdsOff + descStringIdx * 4);
            String desc = readDexMutf8String(dexBytes, stringDataOff);
            if (desc.startsWith("L") && desc.endsWith(";")) {
                String fqcn = desc.substring(1, desc.length() - 1).replace('/', '.');
                int lastDot = fqcn.lastIndexOf('.');
                String pkg = lastDot > 0 ? fqcn.substring(0, lastDot) : "(default)";
                String cls = lastDot > 0 ? fqcn.substring(lastDot + 1) : fqcn;
                grouped.computeIfAbsent(pkg, k -> new ArrayList<>()).add(cls);
            }
        }
        for (List<String> list : grouped.values()) {
            Collections.sort(list);
        }
        return grouped;
    }

    private static String readDexMutf8String(byte[] dex, int off) {
        if (off < 0 || off >= dex.length) return "";
        int p = off;
        // skip uleb128 length
        while (p < dex.length && (dex[p++] & 0x80) != 0) {
        }
        int start = p;
        while (p < dex.length && dex[p] != 0) p++;
        return new String(dex, start, Math.max(0, p - start), StandardCharsets.UTF_8);
    }
}
