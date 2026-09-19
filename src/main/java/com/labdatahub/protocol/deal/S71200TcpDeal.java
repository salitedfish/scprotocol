//由AI修改
package com.labdatahub.protocol.deal;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson2.JSONObject;
import com.labdatahub.protocol.message.DecodeMessage;
import com.labdatahub.protocol.message.EncodeMessage;
import com.labdatahub.protocol.parent.S71200TcpProtocol;
import com.labdatahub.protocol.utils.RegisterValueUtils;

/**
 * 
* @ClassName: S71200TcpDeal  
* @Description: S71200Tcp消息处理
* @author xwb  
* @date 2026年3月24日
 */
public class S71200TcpDeal implements S71200TcpProtocol {

	@Override
    public DecodeMessage decode(JSONObject objecotData) throws Exception {
		String code = objecotData.getString("code");
		String deviceSn = objecotData.getString("deviceSn");
		String isSigned = objecotData.getString("isSigned");
		Double scale = objecotData.getDouble("scale");
		Double offset = objecotData.getDouble("offset");
        DecodeMessage decodeMessage = new DecodeMessage();
        decodeMessage.setDeviceSn(deviceSn);
        Map<String,Object> properties = new HashMap<>();
        // S7DataReader 对 DBW 按 16 位无符号读取，这里按物模型 isSigned 做符号扩展后再缩放
        Object value = objecotData.get("data");
        // DBW 是 16 位整型，按物模型 isSigned 做符号扩展；
        // DBD(32位整型/浮点)/DBX(位)/DBB(字符串) 已在读取侧按 dataType 转好类型，不 applySigned，避免把 32 位值按 16 位截断
        String blockType = objecotData.getString("blockType");
        if (blockType == null || "DBW".equals(blockType)) {
            value = RegisterValueUtils.applySigned(value, isSigned);
        }
        value = RegisterValueUtils.applyScale(value, scale, offset);
        properties.put(code,value);
        decodeMessage.setProperties(properties);
        //解析消息
        return decodeMessage;
    }
	
    // ===== otherConfig 键名（入向）：与 api 侧 com.labdatahub.business.domain.OpenApiEncodeKeys 一一对应 =====
    // ⚠️ 跨 classloader 的字符串契约，两侧无编译期检查，改一处必须同步另一处（见方案 4.4.2 / 4.4.4）
    /** 物模型数据类型 */
    private static final String KEY_DATA_TYPE = "dataType";
    /** 字节序（S7 恒大端，仅作透传） */
    private static final String KEY_BYTE_ORDER = "byteOrder";
    /** 是否有符号 */
    private static final String KEY_IS_SIGNED = "isSigned";
    /** 缩放系数 */
    private static final String KEY_SCALE = "scale";
    /** 偏移量 */
    private static final String KEY_OFFSET = "offset";
    /** 写入值（JDK 标量：String / Number / Boolean） */
    private static final String KEY_VALUE = "value";
    /** 起始字节偏移（S7 语义，见 api 侧 OpenApiEncodeKeys.ADDRESS 注释） */
    private static final String KEY_ADDRESS = "address";
    /** DB 块号（M/I/Q 区不使用，但配置里仍带） */
    private static final String KEY_DB_NUMBER = "dbNumber";
    /** 区类型：DB / M / I / Q */
    private static final String KEY_AREA_TYPE = "areaType";
    /** 块类型：DBX(位) / DBW(2字节整型) / DBD(4字节整型或浮点) / DBB(西门子字符串) */
    private static final String KEY_BLOCK_TYPE = "blockType";
    /** 位偏移（仅 DBX 用，0-7） */
    private static final String KEY_BIT_OFFSET = "bitOffset";
    /** 读取长度（仅 DBB 用，即字符串所占字节数，含 2 字节长度头） */
    private static final String KEY_LENGTH = "length";

    // ===== 回传载荷键名（出向）：与 api 侧 S7PointWriter 的解析键一一对应 =====
    // ⚠️ 同样是跨 classloader 契约。回传槽是 EncodeMessage.modbusWriteJson（沿用 Modbus 的字段名，
    //    与协议无关，见方案 4.4.4）。
    /** 区类型 */
    private static final String OUT_AREA_TYPE = "areaType";
    /** DB 块号 */
    private static final String OUT_DB_NUMBER = "dbNumber";
    /** 起始字节偏移 */
    private static final String OUT_ADDRESS = "address";
    /** 位偏移 */
    private static final String OUT_BIT_OFFSET = "bitOffset";
    /** 块类型 */
    private static final String OUT_BLOCK_TYPE = "blockType";
    /** 物模型类型（仅回显给日志用，写侧不依赖它判断） */
    private static final String OUT_DATA_TYPE = "dataType";
    /** 待写入的字节值数组（<b>无符号</b> 0-255 的整数列表，见 {@link #toUnsignedList}） */
    private static final String OUT_BYTES = "bytes";

    /** 默认区类型：DB 数据块 */
    private static final String AREA_DB = "DB";
    /** 西门子 STRING 的字节上限：长度头 2 字节 + 内容最多 254 字节 */
    private static final int MAX_STRING_BYTES = 256;

    /**
     * 写值方向编码：值 → S7 字节序列 → 报文载荷
     *
     * <p>替换了原先的硬编码 demo（{@code RangeItem(0,2,[22,4])}）—— 那段是读方向遗留的占位，
     * 返回值与写入值毫无关系，一直没被真正调通过。本方法只读 {@code otherConfig}，
     * 其余入参一律不参与计算（{@code functionCode} 在本链路恒为 null）。
     *
     * <p><b>载荷形态与 Modbus 不同</b>：Modbus 是 {@code RangeItem} 数组（起始寄存器 + 寄存器字列表），
     * S7 没有寄存器概念，回传的是<b>单个 JSON 对象</b>：
     * <pre>{"areaType":"DB","dbNumber":1,"address":4,"bitOffset":0,"blockType":"DBD","dataType":"float","bytes":[66,42,0,0]}</pre>
     * 由 api 侧 {@code S7PointWriter} 直接 {@code connector.write(area, dbNumber, address, bytes)}。
     *
     * <p>块类型决定编码方式，四种各自独立（读侧 {@code S7DataReader.readDB} 的判据逐一镜像）：
     * <ul>
     *   <li><b>DBX</b>（1 字节位）：值必须是 bool，产出 {@code [0]} 或 {@code [1]}；
     *       真正的位读-改-写在 api 侧写值器里做（库无位写 API）</li>
     *   <li><b>DBW</b>（2 字节）：恒按 16 位整型，不看 dataType（读侧就是 IntegerConverter 出的 16 位整数）</li>
     *   <li><b>DBD</b>（4 字节）：dataType 为 int 系列 → 32 位整型（DINT）；其余 → 32 位浮点（Real）</li>
     *   <li><b>DBB</b>（length 字节）：西门子 STRING，{@code [0]}=最大长度、{@code [1]}=实际长度、内容从 index 2 起</li>
     * </ul>
     *
     * <p>校验不过一律抛 {@link IllegalArgumentException}（api 侧映射为 422）。写错字节比拒绝写入危险得多。
     *
     * @param functionCode 旧链路的业务指令串，本链路传 null
     * @param deviceSn     原样透传，便于协议层日志定位
     * @param properties   本链路传空 Map（不传 null，避免协议实现直接操作 Map 时 NPE）
     * @param otherConfig  唯一有效载荷，键名见上方常量
     */
    @Override
    public EncodeMessage encode(String functionCode, String deviceSn, Map<String, Object> properties, String params, String customConfig, Map<String, Object> otherConfig) throws Exception {
        // 防御性：正常调用不会出现（api 侧必传）
        if (otherConfig == null) {
            return EncodeMessage.builder()
                    .isSend(false)
                    .deviceSn(deviceSn)
                    .build();
        }
        String blockType = trimUpper(asString(otherConfig.get(KEY_BLOCK_TYPE)));
        if (blockType.isEmpty()) {
            throw new IllegalArgumentException("写值元数据缺失：" + KEY_BLOCK_TYPE + " 为空，无法确定按 DBX/DBW/DBD/DBB 哪种块类型编码");
        }
        Integer dbNumber = toInt(otherConfig.get(KEY_DB_NUMBER));
        if (dbNumber == null) {
            throw new IllegalArgumentException("写值元数据缺失：" + KEY_DB_NUMBER + "=" + otherConfig.get(KEY_DB_NUMBER));
        }
        Integer address = toInt(otherConfig.get(KEY_ADDRESS));
        if (address == null) {
            throw new IllegalArgumentException("写值元数据缺失：" + KEY_ADDRESS + "=" + otherConfig.get(KEY_ADDRESS));
        }
        Integer bitOffset = toInt(otherConfig.get(KEY_BIT_OFFSET));
        Object value = otherConfig.get(KEY_VALUE);
        String dataType = asString(otherConfig.get(KEY_DATA_TYPE));

        byte[] bytes;
        switch (blockType) {
            case "DBX": {
                int bit = bitOffset == null ? 0 : bitOffset;
                if (bit < 0 || bit > 7) {
                    throw new IllegalArgumentException("位偏移 " + bit + " 超出 0-7 范围（一个字节只有 8 位）");
                }
                bytes = new byte[]{(byte) (RegisterValueUtils.parseBoolean(value == null ? "" : String.valueOf(value)) ? 1 : 0)};
                break;
            }
            case "DBW": {
                bytes = RegisterValueUtils.encodeBytes(value, dataType,
                        asString(otherConfig.get(KEY_BYTE_ORDER)), asString(otherConfig.get(KEY_IS_SIGNED)),
                        asDouble(otherConfig.get(KEY_SCALE)), asDouble(otherConfig.get(KEY_OFFSET)), 2);
                break;
            }
            case "DBD": {
                bytes = RegisterValueUtils.encodeBytes(value, dataType,
                        asString(otherConfig.get(KEY_BYTE_ORDER)), asString(otherConfig.get(KEY_IS_SIGNED)),
                        asDouble(otherConfig.get(KEY_SCALE)), asDouble(otherConfig.get(KEY_OFFSET)), 4);
                break;
            }
            case "DBB": {
                // 不看 dataType：读侧 DBB 恒按西门子 STRING 解出字符串，写侧按同一个约定编回去，
                // 否则「DBB 配了 int」这种点位会读得出来、写不进去
                bytes = encodeSiemensString(value, toInt(otherConfig.get(KEY_LENGTH)));
                break;
            }
            default:
                throw new IllegalArgumentException("不支持的块类型：" + blockType + "（只支持 DBX/DBW/DBD/DBB）");
        }

        JSONObject payload = new JSONObject();
        payload.put(OUT_AREA_TYPE, trimUpper(asString(otherConfig.get(KEY_AREA_TYPE))).isEmpty()
                ? AREA_DB : trimUpper(asString(otherConfig.get(KEY_AREA_TYPE))));
        payload.put(OUT_DB_NUMBER, dbNumber);
        payload.put(OUT_ADDRESS, address);
        payload.put(OUT_BIT_OFFSET, bitOffset == null ? 0 : bitOffset);
        payload.put(OUT_BLOCK_TYPE, blockType);
        payload.put(OUT_DATA_TYPE, dataType);
        payload.put(OUT_BYTES, toUnsignedList(bytes));
        return EncodeMessage.builder()
                .isSend(true)
                .deviceSn(deviceSn)
                .modbusWriteJson(payload.toJSONString())
                .build();
    }

    /**
     * 西门子 STRING 编码：{@code [0]}=最大长度、{@code [1]}=实际长度、内容从 index 2 起，其余补 0
     *
     * <p>⚠️ 用 <b>GBK</b> 编码 —— 读侧 {@code S7DataReader} 就是 {@code new String(data, 2, len, "GBK")} 解的。
     * 两边编码不一致的话，中文写进去读出来是乱码，而 ASCII（数字/英文）下两者等价，
     * 只拿英文测永远发现不了。
     *
     * @param value  写入值，取字符串形式
     * @param length 该点位的读取长度（字节数，含 2 字节长度头）
     */
    private static byte[] encodeSiemensString(Object value, Integer length) {
        if (length == null || length < 2) {
            throw new IllegalArgumentException("字符串点位缺少可用的读取长度（length）：西门子 STRING 至少 2 字节存放长度头，当前 "
                    + length);
        }
        if (length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("读取长度 " + length + " 超过西门子 STRING 上限 " + MAX_STRING_BYTES
                    + " 字节（内容最多 " + (MAX_STRING_BYTES - 2) + " 字节）");
        }
        String raw = value == null ? "" : String.valueOf(value);
        byte[] content;
        try {
            content = raw.getBytes("GBK");
        } catch (UnsupportedEncodingException e) {
            content = raw.getBytes(StandardCharsets.ISO_8859_1);
        }
        int capacity = length - 2;
        if (content.length > capacity) {
            throw new IllegalArgumentException("值 \"" + raw + "\" 编码后长 " + content.length
                    + " 字节，超过该点位可容纳的 " + capacity + " 字节");
        }
        byte[] out = new byte[length];
        out[0] = (byte) capacity;          // 最大长度（该 DB 变量声明的长度）
        out[1] = (byte) content.length;    // 实际长度，读侧按它截取
        System.arraycopy(content, 0, out, 2, content.length);
        return out;
    }

    /**
     * byte[] → 无符号整数列表（0-255）
     *
     * <p>⚠️ <b>不能直接把 byte[] 塞进 payload</b>：fastjson2 默认把 byte[] 序列化成 base64 字符串
     * （WriteByteArrayAsBase64），到了 api 侧根本认不出这是字节数组。转成整数列表是显式的、
     * 与序列化配置无关的写法；取无符号是为了让载荷里的数字跟「DB1.DBB0 = 16#42」这种
     * 现场说法对得上（有符号的话 0x80 会写成 -128，对着 PLC 看的人会懵）。
     */
    private static List<Integer> toUnsignedList(byte[] bytes) {
        List<Integer> list = new ArrayList<>(bytes.length);
        for (byte b : bytes) {
            list.add(b & 0xFF);
        }
        return list;
    }

    /**
     * 取字符串值，null 原样返回（由 RegisterValueUtils 决定默认语义）
     */
    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /**
     * 取字符串并去空白转大写（areaType/blockType 这类枚举值，前端可能带空格或小写）
     */
    private static String trimUpper(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }

    /**
     * 取整数值，兼容 Integer / Long / Number 及其字符串形式
     */
    private static Integer toInt(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        String s = String.valueOf(o).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("期望整数，实际：" + o);
        }
    }

    /**
     * 取 Double 值，null 原样返回（由 RegisterValueUtils 兜底成 1.0 / 0.0）
     */
    private static Double asDouble(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        String s = String.valueOf(o).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Double.valueOf(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("期望数值，实际：" + o);
        }
    }
}
