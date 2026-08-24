//由AI修改
package com.labdatahub.protocol.utils;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 寄存器原始值 → 物模型类型值 的转换工具类
 * PLC寄存器是16bit字，Modbus/Fins轮询读到的是一组Integer（每个0~65535）。
 * 根据物模型 dataType/字节序/有符号/缩放/偏移，转换为 bool/int/float/double/string。
 */
public class RegisterValueUtils {

    private RegisterValueUtils() {
        throw new UnsupportedOperationException("该类为静态工具类，禁止实例化");
    }

    /**
     * 把一组寄存器值转换为指定类型的值
     *
     * @param regs      寄存器值列表（16bit字，0~65535）
     * @param dataType  物模型数据类型: bool/int/float/double/string；为空时按长度走老逻辑（1个字→int，2个字→大端float）
     * @param byteOrder 字节序: big-大端 little-小端，默认big
     * @param isSigned  是否有符号: "1"有符号 "0"无符号，默认有符号
     * @param scale     缩放系数，默认1
     * @param offset    偏移量，默认0
     * @return 转换后的值，无法解析返回null
     */
    public static Object convert(List<Integer> regs, String dataType, String byteOrder, String isSigned, Double scale, Double offset) {
        if (regs == null || regs.isEmpty()) {
            return null;
        }
        boolean bigEndian = isBigEndian(byteOrder);
        boolean signed = isSigned(isSigned);
        String type = dataType == null ? "" : dataType.trim().toLowerCase();
        Object value;
        switch (type) {
            case "bool":
            case "boolean": {
                value = (regs.get(0) & 0xFFFF) != 0;
                break;
            }
            case "int":
            case "integer":
            case "short":
            case "long": {
                value = toInteger(regs, bigEndian, signed);
                break;
            }
            case "float":
            case "double": {
                value = toFloat(regs, bigEndian);
                break;
            }
            case "string": {
                value = toString(regs, bigEndian);
                break;
            }
            default: {
                // dataType未配置/不支持：保持老逻辑，1个字→int，2个字→大端float
                if (regs.size() == 1) {
                    value = regs.get(0);
                } else {
                    long combined = (long) (regs.get(0) & 0xFFFF) << 16 | (regs.get(1) & 0xFFFF);
                    value = Float.intBitsToFloat((int) (combined & 0xFFFFFFFFL));
                }
                break;
            }
        }
        return applyScale(value, scale, offset);
    }

    /**
     * 对已经是数值的值做缩放/偏移（S7读取时已按块类型转好类型，这里只套系数）
     *
     * @param value  原始值
     * @param scale  缩放系数
     * @param offset 偏移量
     * @return 缩放/偏移后的值；非数值或系数为默认值则原样返回
     */
    public static Object applyScale(Object value, Double scale, Double offset) {
        double s = scale == null ? 1.0 : scale;
        double o = offset == null ? 0.0 : offset;
        if ((s == 1.0 && o == 0.0) || !(value instanceof Number)) {
            return value;
        }
        return ((Number) value).doubleValue() * s + o;
    }

    /**
     * 对已按寄存器解出的数值做有符号扩展
     * 目前只处理 16 位字（0~65535，如 S7 DBW 读取值按有符号扩展），非 Integer（float/bool/string）原样返回
     *
     * @param value    原始值
     * @param isSigned 是否有符号: "1"有符号 "0"无符号，默认有符号
     * @return 有符号扩展后的值
     */
    public static Object applySigned(Object value, String isSigned) {
        if (!(value instanceof Integer) || !isSigned(isSigned)) {
            return value;
        }
        int v = (Integer) value & 0xFFFF;
        if (v > 0x7FFF) {
            v -= 0x10000;
        }
        return v;
    }

    /**
     * 把一组寄存器字按字节序合并成一个长整型位模式
     */
    private static long combine(List<Integer> regs, boolean bigEndian) {
        long result = 0;
        for (int i = 0; i < regs.size(); i++) {
            int word = regs.get(i) & 0xFFFF;
            if (bigEndian) {
                result = (result << 16) | word;
            } else {
                result |= ((long) word) << (16 * i);
            }
        }
        return result;
    }

    /**
     * 整数解析：1个字→int16，2个字→int32，4个字→int64；超过4个字无法表达，返回null避免溢出成垃圾值
     */
    private static Object toInteger(List<Integer> regs, boolean bigEndian, boolean signed) {
        int n = regs.size();
        if (n > 4) {
            return null;
        }
        long combined = combine(regs, bigEndian);
        if (n <= 1) {
            long v = combined & 0xFFFF;
            return signed ? (short) v : v;
        } else if (n <= 2) {
            long v = combined & 0xFFFFFFFFL;
            return signed ? (int) v : v;
        } else if (n == 3) {
            // 48位：有符号时符号扩展为64位，避免高位0把负数读成正的垃圾大数
            long v = combined & 0xFFFFFFFFFFFFL;
            if (signed && (v & 0x800000000000L) != 0) {
                v |= ~0xFFFFFFFFFFFFL;
            }
            return v;
        } else {
            return combined;
        }
    }

    /**
     * 浮点解析：2个字→float32，4个字→double64；超过4个字无法表达，返回null避免溢出成垃圾值
     */
    private static Object toFloat(List<Integer> regs, boolean bigEndian) {
        // 浮点必须 2 个字（float32）或 4 个字（double64），1/3 个字无法构成有效浮点位模式，返回null避免垃圾值
        if (regs.size() != 2 && regs.size() != 4) {
            return null;
        }
        long combined = combine(regs, bigEndian);
        if (regs.size() == 4) {
            return Double.longBitsToDouble(combined);
        }
        int bits = (int) (combined & 0xFFFFFFFFL);
        return Float.intBitsToFloat(bits);
    }

    /**
     * 字符串解析：每个寄存器字按字节序拆成两个字节，去掉结尾\0后按UTF-8解码（兼容ASCII）
     */
    private static String toString(List<Integer> regs, boolean bigEndian) {
        byte[] bytes = new byte[regs.size() * 2];
        for (int i = 0; i < regs.size(); i++) {
            int word = regs.get(i) & 0xFFFF;
            int hi = (word >> 8) & 0xFF;
            int lo = word & 0xFF;
            if (bigEndian) {
                bytes[i * 2] = (byte) hi;
                bytes[i * 2 + 1] = (byte) lo;
            } else {
                bytes[i * 2] = (byte) lo;
                bytes[i * 2 + 1] = (byte) hi;
            }
        }
        int len = bytes.length;
        while (len > 0 && bytes[len - 1] == 0) {
            len--;
        }
        return new String(bytes, 0, len, StandardCharsets.UTF_8).trim();
    }

    /**
     * 字节序解析：big/大端/BE 视为大端，其余（little/小端/LE）视为小端，空默认大端
     */
    private static boolean isBigEndian(String byteOrder) {
        if (byteOrder == null || byteOrder.trim().isEmpty()) {
            return true;
        }
        String b = byteOrder.trim().toLowerCase();
        return "big".equals(b) || "be".equals(b) || "大端".equals(b);
    }

    /**
     * 有符号解析：0/false/无符号 视为无符号，其余默认有符号
     */
    private static boolean isSigned(String isSigned) {
        if (isSigned == null || isSigned.trim().isEmpty()) {
            return true;
        }
        String s = isSigned.trim().toLowerCase();
        return !("0".equals(s) || "false".equals(s) || "无符号".equals(s));
    }
}
