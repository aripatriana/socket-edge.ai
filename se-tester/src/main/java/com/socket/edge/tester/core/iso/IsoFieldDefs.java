package com.socket.edge.tester.core.iso;

import java.util.HashMap;
import java.util.Map;

public final class IsoFieldDefs {

    public enum FieldType { FIXED, LLVAR, LLLVAR }

    public record FieldDef(int de, FieldType type, int maxLength) {}

    private static final Map<Integer, FieldDef> DEFS = new HashMap<>();

    static {
        def(2,   FieldType.LLVAR,   19);
        def(3,   FieldType.FIXED,    6);
        def(4,   FieldType.FIXED,   12);
        def(5,   FieldType.FIXED,   12);
        def(6,   FieldType.FIXED,   12);
        def(7,   FieldType.FIXED,   10);
        def(8,   FieldType.FIXED,    8);
        def(9,   FieldType.FIXED,    8);
        def(10,  FieldType.FIXED,    8);
        def(11,  FieldType.FIXED,    6);
        def(12,  FieldType.FIXED,    6);
        def(13,  FieldType.FIXED,    4);
        def(14,  FieldType.FIXED,    4);
        def(15,  FieldType.FIXED,    4);
        def(16,  FieldType.FIXED,    4);
        def(17,  FieldType.FIXED,    4);
        def(18,  FieldType.FIXED,    4);
        def(19,  FieldType.FIXED,    3);
        def(20,  FieldType.FIXED,    3);
        def(21,  FieldType.FIXED,    3);
        def(22,  FieldType.FIXED,    3);
        def(23,  FieldType.FIXED,    3);
        def(24,  FieldType.FIXED,    3);
        def(25,  FieldType.FIXED,    2);
        def(26,  FieldType.FIXED,    2);
        def(27,  FieldType.FIXED,    1);
        def(28,  FieldType.FIXED,    9);
        def(29,  FieldType.FIXED,    9);
        def(30,  FieldType.FIXED,    9);
        def(31,  FieldType.FIXED,    9);
        def(32,  FieldType.LLVAR,   11);
        def(33,  FieldType.LLVAR,   11);
        def(34,  FieldType.LLVAR,   28);
        def(35,  FieldType.LLVAR,   37);
        def(36,  FieldType.LLLVAR, 104);
        def(37,  FieldType.FIXED,   12);
        def(38,  FieldType.FIXED,    6);
        def(39,  FieldType.FIXED,    2);
        def(40,  FieldType.FIXED,    3);
        def(41,  FieldType.FIXED,    8);
        def(42,  FieldType.FIXED,   15);
        def(43,  FieldType.FIXED,   40);
        def(44,  FieldType.LLVAR,   25);
        def(45,  FieldType.LLVAR,   76);
        def(46,  FieldType.LLLVAR, 999);
        def(47,  FieldType.LLLVAR, 999);
        def(48,  FieldType.LLLVAR, 999);
        def(49,  FieldType.FIXED,    3);
        def(50,  FieldType.FIXED,    3);
        def(51,  FieldType.FIXED,    3);
        def(52,  FieldType.FIXED,   16); // PIN Data — stored as hex string
        def(53,  FieldType.FIXED,   16);
        def(54,  FieldType.LLLVAR, 120);
        def(55,  FieldType.LLLVAR, 999);
        def(56,  FieldType.LLLVAR, 999);
        def(57,  FieldType.LLLVAR, 999);
        def(58,  FieldType.LLLVAR, 999);
        def(59,  FieldType.LLLVAR, 999);
        def(60,  FieldType.LLLVAR, 999);
        def(61,  FieldType.LLLVAR, 999);
        def(62,  FieldType.LLLVAR, 999);
        def(63,  FieldType.LLLVAR, 999);
        def(64,  FieldType.FIXED,   16); // MAC
        def(65,  FieldType.FIXED,    1);
        def(66,  FieldType.FIXED,    1);
        def(67,  FieldType.FIXED,    2);
        def(70,  FieldType.FIXED,    3);
        def(71,  FieldType.FIXED,    4);
        def(72,  FieldType.LLLVAR, 999);
        def(73,  FieldType.FIXED,    6);
        def(74,  FieldType.FIXED,   10);
        def(75,  FieldType.FIXED,   10);
        def(76,  FieldType.FIXED,   10);
        def(77,  FieldType.FIXED,   10);
        def(78,  FieldType.FIXED,   10);
        def(79,  FieldType.FIXED,   10);
        def(80,  FieldType.FIXED,   10);
        def(81,  FieldType.FIXED,   10);
        def(82,  FieldType.FIXED,   12);
        def(83,  FieldType.FIXED,   12);
        def(84,  FieldType.FIXED,   12);
        def(85,  FieldType.FIXED,   12);
        def(86,  FieldType.FIXED,   16);
        def(87,  FieldType.FIXED,   16);
        def(88,  FieldType.FIXED,   16);
        def(89,  FieldType.FIXED,   16);
        def(90,  FieldType.FIXED,   42);
        def(91,  FieldType.FIXED,    1);
        def(92,  FieldType.FIXED,    2);
        def(93,  FieldType.FIXED,    5);
        def(94,  FieldType.FIXED,    7);
        def(95,  FieldType.FIXED,   42);
        def(96,  FieldType.FIXED,   16);
        def(97,  FieldType.FIXED,   17);
        def(98,  FieldType.FIXED,   25);
        def(99,  FieldType.LLVAR,   11);
        def(100, FieldType.LLVAR,   11);
        def(101, FieldType.LLVAR,   17);
        def(102, FieldType.LLVAR,   28);
        def(103, FieldType.LLVAR,   28);
        def(104, FieldType.LLLVAR, 100);
        def(105, FieldType.LLLVAR, 999);
        def(106, FieldType.LLLVAR, 999);
        def(107, FieldType.LLLVAR, 999);
        def(108, FieldType.LLLVAR, 999);
        def(109, FieldType.LLLVAR, 999);
        def(110, FieldType.LLLVAR, 999);
        def(111, FieldType.LLLVAR, 999);
        def(112, FieldType.LLLVAR, 999);
        def(113, FieldType.LLVAR,   11);
        def(114, FieldType.LLLVAR, 999);
        def(115, FieldType.LLLVAR, 999);
        def(116, FieldType.LLLVAR, 999);
        def(117, FieldType.LLLVAR, 999);
        def(118, FieldType.LLLVAR, 999);
        def(119, FieldType.LLLVAR, 999);
        def(120, FieldType.LLLVAR, 999);
        def(121, FieldType.LLLVAR, 999);
        def(122, FieldType.LLLVAR, 999);
        def(123, FieldType.LLLVAR, 999);
        def(124, FieldType.LLLVAR, 999);
        def(125, FieldType.LLLVAR, 999);
        def(126, FieldType.LLLVAR, 999);
        def(127, FieldType.LLLVAR, 999);
        def(128, FieldType.FIXED,   16); // MAC 2
    }

    private static void def(int de, FieldType type, int maxLen) {
        DEFS.put(de, new FieldDef(de, type, maxLen));
    }

    public static FieldDef get(int de) {
        FieldDef def = DEFS.get(de);
        if (def == null) throw new IllegalArgumentException("Unknown DE: " + de);
        return def;
    }

    public static boolean has(int de) {
        return DEFS.containsKey(de);
    }

    private IsoFieldDefs() {}
}