package com.aishhatorah.filemanagerpro;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class ApkSourceParserTest {

    @Test
    public void handlesPlainTextManifest() {
        byte[] xml = "<manifest package=\"com.test\"/>".getBytes(StandardCharsets.UTF_8);
        String out = ApkSourceParser.decodeBinaryXml(xml);
        assertTrue(out.contains("com.test"));
    }

    @Test
    public void rejectsTruncatedBinaryXmlGracefully() {
        String out = ApkSourceParser.decodeBinaryXml(new byte[]{1, 2, 3});
        assertNotNull(out);
        assertFalse(out.isEmpty());
    }

    @Test
    public void parsesMinimalSyntheticDexHeaderAndClassDef() {
        // Minimal synthetic DEX buffer with magic header + 1 string + 1 type + 1 classDef
        byte[] dex = new byte[256];
        System.arraycopy("dex\n035\0".getBytes(StandardCharsets.US_ASCII), 0, dex, 0, 8);
        ByteBuffer bb = ByteBuffer.wrap(dex).order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(56, 1);   // stringIdsSize = 1
        bb.putInt(60, 112); // stringIdsOff = 112
        bb.putInt(64, 1);   // typeIdsSize = 1
        bb.putInt(68, 120); // typeIdsOff = 120
        bb.putInt(96, 1);   // classDefsSize = 1
        bb.putInt(100, 128);// classDefsOff = 128

        bb.putInt(112, 168); // stringDataOff = 168
        bb.putInt(120, 0);   // type[0] -> string[0]
        bb.putInt(128, 0);   // classDef[0].classTypeIdx = 0

        String desc = "Lcom/example/app/DemoClass;";
        dex[168] = (byte) desc.length();
        byte[] descBytes = desc.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(descBytes, 0, dex, 169, descBytes.length);
        dex[169 + descBytes.length] = 0;

        Map<String, List<String>> parsed = ApkSourceParser.parseDexPackagesAndClasses(dex);
        assertTrue(parsed.containsKey("com.example.app"));
        assertTrue(parsed.get("com.example.app").contains("DemoClass"));
    }
}
