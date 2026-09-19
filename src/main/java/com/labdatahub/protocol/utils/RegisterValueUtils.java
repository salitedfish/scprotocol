//由AI修改
package com.labdatahub.protocol.utils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 寄存器原始值 → 物模型类型值 的转换工具类
 * PLC寄存器是16bit字，Modbus/Fins轮询读到的是一组Integer（每个0~65535）。
 * 根据物模型 dataType/字节序/有符号/缩放/偏移，转换为 bool/int/float/double/string。
 */
public class RegisterValueUtils {

    /** MODBUS「写多个寄存器」（功能码 16）单次可写的寄存器个数上限（协议规范值） */
    public static final int MAX_WRITE_REGISTERS_MODBUS = 123;

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
     * 把值按物模型解析参数编码成寄存器字列表 —— convert 的逆运算（写值方向）
     *
     * <p>值语义与读方向严格镜像：{@code dataType} 决定走哪条编码路径，{@code count}（寄存器个数）
     * 决定编多长。所以 {@code data_type="float"} 配 count=4 编的是 double64；配 count=2 才是 float32
     * —— 与 convert 的 {@code case "float"/"double"} 合用一个分支、长度由 regs.size() 决定完全对称。
     *
     * <p><b>不做"尽力而为"的编码</b>：任何校验不过一律抛 {@link IllegalArgumentException}
     * （api 侧映射为 422）。写错寄存器比拒绝写入危险得多。
     *
     * <p>已知的不严格互逆：convert 的字符串分支末尾有 {@code .trim()}，所以写 {@code "AB "}
     * 读回来是 {@code "AB"}。这是既有行为，此处照做（补 \0），不要当 bug 改。
     *
     * @param value     写入值，任意 JSON 标量（String / Number / Boolean），内部先 String.valueOf 归一
     * @param dataType  物模型数据类型：bool/int/float/double/string；为空走 default 分支（镜像 convert）
     * @param byteOrder 字节序：big-大端（高位字在前）/ little-小端（低位字在前），默认 big
     * @param isSigned  是否有符号："1"有符号 "0"无符号，默认有符号
     * @param scale     缩放系数，默认 1
     * @param offset    偏移量，默认 0
     * @param count     寄存器个数（由协议点位的 register_range 解析得出），1~123
     * @return 编码后的 16bit 字列表，长度恒为 count；即终态，调用方直接塞进报文载荷
     */
    public static List<Integer> encode(Object value, String dataType, String byteOrder,
                                       String isSigned, Double scale, Double offset, int count) {
        return encode(value, dataType, byteOrder, isSigned, scale, offset, count, MAX_WRITE_REGISTERS_MODBUS);
    }

    /**
     * 同 {@link #encode(Object, String, String, String, Double, Double, int)}，但由调用方指定寄存器个数上限
     *
     * <p>上限不是「工具类的规矩」而是<b>各协议自己的报文限制</b>，所以必须由协议实现传进来：
     * <ul>
     *   <li>MODBUS「写多个寄存器」（功能码 16）：{@value #MAX_WRITE_REGISTERS_MODBUS} 个（读 ≤125、写 ≤123，两个数不对称）</li>
     *   <li>FINS「内存区写入」（01 02）：1000 个（OMRON CS/CJ/CP 单条命令的项数上限，与读侧一致）</li>
     * </ul>
     * 本方法只负责按上限拒绝，<b>不判断上限对当前协议是否合理</b> —— 那是协议实现的事。
     *
     * @param maxCount  本协议单次可写的寄存器个数上限（含）
     */
    public static List<Integer> encode(Object value, String dataType, String byteOrder,
                                       String isSigned, Double scale, Double offset, int count, int maxCount) {
        if (count < 1 || count > maxCount) {
            throw new IllegalArgumentException("寄存器个数 " + count + " 超出可写范围 1-" + maxCount
                    + (maxCount == MAX_WRITE_REGISTERS_MODBUS ? "（写多寄存器功能码 16 的协议上限）" : ""));
        }
        // scale=0 时 (v-o)/s 不抛异常，会静默得到 Infinity/NaN 并写出合法但无意义的位模式 —— 只能靠预校验拦
        double s = scale == null ? 1.0 : scale;
        if (s == 0.0) {
            throw new IllegalArgumentException("缩放系数 scale=0，无法反缩放（读方向恒为 0，写入无意义）");
        }
        double o = offset == null ? 0.0 : offset;
        boolean bigEndian = isBigEndian(byteOrder);
        String type = dataType == null ? "" : dataType.trim().toLowerCase();
        // 归一：请求经 JSON 反序列化后实际类型不可控（1.5→BigDecimal、true→Boolean），统一走字符串再按 data_type 解析
        String raw = value == null ? "" : String.valueOf(value);

        switch (type) {
            case "bool":
            case "boolean": {
                // convert 只看 regs.get(0)，其余补 0；布尔跳过反缩放（与 applyScale 的 !(value instanceof Number) 短路一致）
                List<Integer> words = zeroWords(count);
                words.set(0, parseBoolean(raw) ? 1 : 0);
                return words;
            }
            case "int":
            case "integer":
            case "short":
            case "long": {
                BigInteger scaled = reverseScaleToInteger(raw, s, o, count, isSigned(isSigned), type);
                return integerToWords(scaled, count, bigEndian);
            }
            case "float":
            case "double": {
                // 两者合用一条路径，长度差异完全由 count 决定：2→float32，4→double64（与 convert 的 toFloat 对称）
                if (count != 2 && count != 4) {
                    throw new IllegalArgumentException("数据类型 " + type + " 需要 2 个寄存器（float32）或 4 个寄存器（double64），当前 count=" + count);
                }
                double d = reverseScaleToDouble(raw, s, o, type);
                if (Double.isNaN(d) || Double.isInfinite(d)) {
                    throw new IllegalArgumentException("浮点值 " + raw + " 不是有限数（NaN / Infinity）");
                }
                if (count == 4) {
                    return integerToWords(BigInteger.valueOf(Double.doubleToLongBits(d)), 4, bigEndian);
                }
                return integerToWords(BigInteger.valueOf(Float.floatToIntBits((float) d) & 0xFFFFFFFFL), 2, bigEndian);
            }
            case "string": {
                // UTF-8 编码后按 byteOrder 拆字节入字，不足补 \0 至 count*2 字节；超长必须拒绝而不是截断
                byte[] bytes = raw.getBytes(StandardCharsets.UTF_8);
                int capacity = count * 2;
                if (bytes.length > capacity) {
                    throw new IllegalArgumentException("值 \"" + raw + "\" 的 UTF-8 长度 " + bytes.length
                            + " 超过 count=" + count + " 可容纳的 " + capacity + " 字节");
                }
                byte[] padded = new byte[capacity];
                System.arraycopy(bytes, 0, padded, 0, bytes.length);
                return bytesToWords(padded, bigEndian);
            }
            default: {
                // 镜像 convert 的 default 分支：1 个字→原始 int，≥2 个字→大端 float32
                // 读方向对未知/空 data_type 是合法的，写方向必须同样能写，否则出现「读得出来、写不进去」的半残点位
                if (count == 1) {
                    return integerToWords(reverseScaleToInteger(raw, s, o, 1, false, "未配置的 data_type"), 1, bigEndian);
                }
                double d = reverseScaleToDouble(raw, s, o, "未配置的 data_type");
                if (Double.isNaN(d) || Double.isInfinite(d)) {
                    throw new IllegalArgumentException("浮点值 " + raw + " 不是有限数（NaN / Infinity）");
                }
                return integerToWords(BigInteger.valueOf(Float.floatToIntBits((float) d) & 0xFFFFFFFFL), count, true);
            }
        }
    }

    /**
     * 反缩放并转为整数：raw = (value - offset) / scale
     *
     * <p>顺序不能反 —— convert 的最后一步才是 applyScale，此时 value 已是数值；写侧归一后是 String，
     * 若照字面把字符串传进 applyScale，其 {@code !(value instanceof Number)} 恒为真 → 反缩放永远不执行，
     * 缩放点位会静默写错值。
     *
     * <p>scale=1 且 offset=0（占绝大多数）时走 BigInteger 直通，避免 64 位整数经 double 丢精度。
     */
    private static BigInteger reverseScaleToInteger(String raw, double scale, double offset,
                                                    int count, boolean signed, String typeName) {
        BigInteger v = reverseScaleToIntegerRaw(raw, scale, offset, typeName);
        int bits = count * 16;
        checkIntegerRange(v, bits, signed, count + " 个寄存器（" + bits + " 位）");
        return v;
    }

    /**
     * 反缩放成整数，<b>不做范围校验</b>：raw = (value - offset) / scale
     *
     * <p>范围按位宽校验，而「位宽怎么算」是调用方的事（MODBUS 按寄存器个数×16，S7 按字节数×8），
     * 所以范围检查拆出去由调用方用 {@link #checkIntegerRange} 各自表达，这里只管反缩放本身。
     */
    private static BigInteger reverseScaleToIntegerRaw(String raw, double scale, double offset, String typeName) {
        try {
            if (scale == 1.0 && offset == 0.0) {
                return new BigInteger(raw.trim());
            }
            BigDecimal scaled = new BigDecimal(raw.trim())
                    .subtract(BigDecimal.valueOf(offset))
                    .divide(BigDecimal.valueOf(scale), MathContext.DECIMAL64);
            // 缩放后必须仍是整数，否则写进去读回来就不是原值
            if (scaled.stripTrailingZeros().scale() > 0) {
                throw new IllegalArgumentException("值 " + raw + " 经反缩放 (value-offset)/scale 后不是整数：" + scaled.toPlainString());
            }
            return scaled.toBigIntegerExact();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("值 \"" + raw + "\" 不是合法的整数（数据类型 " + typeName + "）");
        }
    }

    /**
     * 按位宽做有符号/无符号范围校验，越界抛 {@link IllegalArgumentException}
     *
     * @param v       待校验的值
     * @param bits    位宽（寄存器个数 ×16 或 字节数 ×8）
     * @param signed  是否有符号
     * @param subject 位宽的描述，用于拼错误信息（如 "4 字节（32 位）"）
     */
    private static void checkIntegerRange(BigInteger v, int bits, boolean signed, String subject) {
        BigInteger min = signed ? BigInteger.ONE.shiftLeft(bits - 1).negate() : BigInteger.ZERO;
        BigInteger max = signed ? BigInteger.ONE.shiftLeft(bits - 1).subtract(BigInteger.ONE)
                                : BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE);
        if (v.compareTo(min) < 0 || v.compareTo(max) > 0) {
            throw new IllegalArgumentException("值 " + v + " 超出 " + subject + " 的"
                    + (signed ? "有符号" : "无符号") + "范围 [" + min + ", " + max + "]");
        }
    }

    /**
     * 把值编码成 S7 的字节序列（写值方向，S7 用）
     *
     * <p>与 {@link #encode} 的分工：那个编的是「寄存器字列表」（Modbus 的报文单位，16 位字），
     * 这个编的是「字节序列」（S7 的报文单位，大端）。两条路共用同一套标量归一与反缩放。
     *
     * <p><b>判据严格镜像读方向 {@code S7DataReader} 的 DBD 分支</b>，保证「读得出来就写得进去」：
     * <ul>
     *   <li>2 字节（DBW）：恒按 16 位整型，不看 dataType —— 读方向 DBW 就是 IntegerConverter 出的 16 位整数</li>
     *   <li>4 字节（DBD）：dataType 是 int 系列 → 32 位整型（DINT）；其余（含 float/double/未配置）→ 32 位浮点（Real）</li>
     * </ul>
     * 也就是说未知/未配置的 dataType 在这里按读方向的老逻辑降级，不额外拒绝 —— 否则会出现
     * 「读得出来、写不进去」的半残点位。真正的「类型与块类型不兼容」（如 DBD 配 string、DBW 配 float）
     * 由协议层 {@code S71200TcpDeal} 拦，那里才看得到块类型。
     *
     * <p>不做「尽力而为」的编码：校验不过一律抛 {@link IllegalArgumentException}（api 侧映射为 422）。
     *
     * @param value     写入值，任意 JSON 标量（String / Number / Boolean），内部先 String.valueOf 归一
     * @param dataType  物模型数据类型
     * @param byteOrder 字节序：S7 恒为 big（大端），传别的也按大端写 —— 见 {@link #isBigEndian}
     * @param isSigned  是否有符号："1"有符号 "0"无符号，默认有符号
     * @param scale     缩放系数，默认 1
     * @param offset    偏移量，默认 0
     * @param byteCount 目标字节数：DBW=2 / DBD=4
     * @return 编码后的字节数组，长度恒为 byteCount
     */
    public static byte[] encodeBytes(Object value, String dataType, String byteOrder, String isSigned,
                                     Double scale, Double offset, int byteCount) {
        if (byteCount != 2 && byteCount != 4) {
            throw new IllegalArgumentException("S7 数值写入只支持 2 字节（DBW 16 位整型）或 4 字节（DBD 32 位整型/浮点），当前 " + byteCount + " 字节");
        }
        // scale=0 时 (v-o)/s 静默得到 Infinity/NaN 并写出合法但无意义的位模式 —— 只能靠预校验拦（与 encode 一致）
        double s = scale == null ? 1.0 : scale;
        if (s == 0.0) {
            throw new IllegalArgumentException("缩放系数 scale=0，无法反缩放（读方向恒为 0，写入无意义）");
        }
        double o = offset == null ? 0.0 : offset;
        boolean bigEndian = isBigEndian(byteOrder);
        String type = dataType == null ? "" : dataType.trim().toLowerCase();
        String raw = value == null ? "" : String.valueOf(value);

        if (byteCount == 2) {
            BigInteger v = reverseScaleToIntegerRaw(raw, s, o, type);
            checkIntegerRange(v, 16, isSigned(isSigned), "2 字节（16 位）");
            return integerToBytes(v, 2, bigEndian);
        }
        if (isIntType(type)) {
            BigInteger v = reverseScaleToIntegerRaw(raw, s, o, type);
            checkIntegerRange(v, 32, isSigned(isSigned), "4 字节（32 位）");
            return integerToBytes(v, 4, bigEndian);
        }
        double d = reverseScaleToDouble(raw, s, o, type);
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            throw new IllegalArgumentException("浮点值 " + raw + " 不是有限数（NaN / Infinity）");
        }
        return integerToBytes(BigInteger.valueOf(Float.floatToIntBits((float) d) & 0xFFFFFFFFL), 4, bigEndian);
    }

    /**
     * 是否 int 系列数据类型（DBD 按 32 位整型写；否则按 32 位浮点写）
     *
     * <p>⚠️ 与 api 侧 {@code S7DataReader.isIntType} 是同一套判据的两份实现 —— 协议库与 api
     * 是两个编译单元，共享不了。改一边必须同步另一边，否则写进去的值读出来类型就变了。
     */
    private static boolean isIntType(String dataType) {
        if (dataType == null) {
            return false;
        }
        String type = dataType.trim().toLowerCase();
        return "int".equals(type) || "integer".equals(type) || "short".equals(type) || "long".equals(type) || "byte".equals(type);
    }

    /**
     * 整数位模式 → byteCount 字节：大端高位字节在前，小端低位字节在前
     *
     * <p>负数走 BigInteger 的算术右移 + 取低 8 位，拿到的正是补码字节，不需要额外处理符号。
     */
    private static byte[] integerToBytes(BigInteger v, int byteCount, boolean bigEndian) {
        BigInteger mask = BigInteger.valueOf(0xFFL);
        byte[] bytes = new byte[byteCount];
        for (int i = 0; i < byteCount; i++) {
            int shift = bigEndian ? 8 * (byteCount - 1 - i) : 8 * i;
            bytes[i] = v.shiftRight(shift).and(mask).byteValue();
        }
        return bytes;
    }

    /**
     * 反缩放并转为浮点：raw = (value - offset) / scale
     */
    private static double reverseScaleToDouble(String raw, double scale, double offset, String typeName) {
        double v;
        try {
            v = Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("值 \"" + raw + "\" 不是合法的数值（数据类型 " + typeName + "）");
        }
        return (v - offset) / scale;
    }

    /**
     * 整数位模式 → count 个 16bit 字：大端高位字在前（words[0] = 最高 16 位），小端低位字在前
     */
    private static List<Integer> integerToWords(BigInteger v, int count, boolean bigEndian) {
        BigInteger mask = BigInteger.valueOf(0xFFFFL);
        List<Integer> words = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int shift = bigEndian ? 16 * (count - 1 - i) : 16 * i;
            words.add(v.shiftRight(shift).and(mask).intValue());
        }
        return words;
    }

    /**
     * 字节数组 → 16bit 字列表（长度必须是偶数）：大端高字节在前，小端低字节在前
     */
    private static List<Integer> bytesToWords(byte[] bytes, boolean bigEndian) {
        List<Integer> words = new ArrayList<>(bytes.length / 2);
        for (int i = 0; i < bytes.length; i += 2) {
            int b0 = bytes[i] & 0xFF;
            int b1 = bytes[i + 1] & 0xFF;
            words.add(bigEndian ? (b0 << 8) | b1 : (b1 << 8) | b0);
        }
        return words;
    }

    /**
     * 生成 count 个 0 字
     */
    private static List<Integer> zeroWords(int count) {
        List<Integer> words = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            words.add(0);
        }
        return words;
    }

    /**
     * 布尔解析：只认 true/false/1/0，其余拒绝（Boolean.parseBoolean 会把任何非 "true" 静默当 false）
     *
     * <p>公开给协议实现复用（如 S7 的 DBX 位写）。
     */
    public static boolean parseBoolean(String raw) {
        String v = raw.trim().toLowerCase();
        if ("true".equals(v) || "1".equals(v)) {
            return true;
        }
        if ("false".equals(v) || "0".equals(v)) {
            return false;
        }
        throw new IllegalArgumentException("值 \"" + raw + "\" 不是合法的布尔值（只接受 true/false/1/0）");
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
