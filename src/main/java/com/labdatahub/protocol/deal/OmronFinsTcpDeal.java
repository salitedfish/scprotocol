//由AI修改
package com.labdatahub.protocol.deal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.labdatahub.protocol.message.DecodeMessage;
import com.labdatahub.protocol.message.EncodeMessage;
import com.labdatahub.protocol.parent.OmronFinsTcpProtocol;
import com.labdatahub.protocol.utils.RegisterValueUtils;

/**
 *
* @ClassName: OmronFinsTcpDeal
* @Description: OmronFinsTcp消息处理
* @author xwb
* @date 2026年4月1日
 */
public class OmronFinsTcpDeal implements OmronFinsTcpProtocol {

	@Override
    public DecodeMessage decode(JSONObject objecotData) throws Exception {
		String code = objecotData.getString("code");
		String deviceSn = objecotData.getString("deviceSn");
		String dataType = objecotData.getString("dataType");
		String byteOrder = objecotData.getString("byteOrder");
		String isSigned = objecotData.getString("isSigned");
		Double scale = objecotData.getDouble("scale");
		Double offset = objecotData.getDouble("offset");
        DecodeMessage decodeMessage = new DecodeMessage();
        decodeMessage.setDeviceSn(deviceSn);
        Map<String,Object> properties = new HashMap<>();
        JSONArray jsonArray = objecotData.getJSONArray("jsonArray");
        if (jsonArray != null) {
            List<Integer> data = JSONArray.parseArray(jsonArray.toJSONString(), Integer.class);
            Object value = RegisterValueUtils.convert(data, dataType, byteOrder, isSigned, scale, offset);
            properties.put(code, value);
        }
        decodeMessage.setProperties(properties);
        //解析消息
        return decodeMessage;
    }

    // ===== otherConfig 键名（入向）：与 api 侧 com.labdatahub.business.domain.OpenApiEncodeKeys 一一对应 =====
    // ⚠️ 跨 classloader 的字符串契约，两侧无编译期检查，改一处必须同步另一处
    /** 物模型数据类型 */
    private static final String KEY_DATA_TYPE = "dataType";
    /** 字节序（FINS 恒大端，仅作透传） */
    private static final String KEY_BYTE_ORDER = "byteOrder";
    /** 是否有符号 */
    private static final String KEY_IS_SIGNED = "isSigned";
    /** 缩放系数 */
    private static final String KEY_SCALE = "scale";
    /** 偏移量 */
    private static final String KEY_OFFSET = "offset";
    /** 写入值（JDK 标量：String / Number / Boolean） */
    private static final String KEY_VALUE = "value";
    /** 起始<b>字</b>地址（不是字节，也不是位号——位号另走 {@link #KEY_BIT_ADDRESS}） */
    private static final String KEY_ADDRESS = "address";
    /** 写入长度：字区=字个数；位区=位个数（位点位恒为 1） */
    private static final String KEY_COUNT = "count";
    /** 存储区码（字区如 DM=0x82，位区如 DM位=0x02） */
    private static final String KEY_AREA_CODE = "areaCode";
    /** 位号（仅位区用，0-15；字区为 null） */
    private static final String KEY_BIT_ADDRESS = "bitAddress";

    // ===== 回传载荷键名（出向）：与 api 侧 FinsPointWriter 的解析键一一对应 =====
    // ⚠️ 同样是跨 classloader 契约。回传槽是 EncodeMessage.modbusWriteJson（沿用 Modbus 的字段名，
    //    与协议无关）
    /** 存储区码 */
    private static final String OUT_AREA_CODE = "areaCode";
    /** 起始字地址 */
    private static final String OUT_START_ADDRESS = "startAddress";
    /** 位号（字区为 null，fastjson2 默认不输出 null 键） */
    private static final String OUT_BIT_ADDRESS = "bitAddress";
    /** 写入项数（字区=字个数，位区=位个数） */
    private static final String OUT_COUNT = "count";
    /** 编码后的 16bit 字列表（字区，每个 0-65535）或位列表（位区，每个 0/1） */
    private static final String OUT_WORDS = "words";
    /** 物模型类型（仅回显给日志用，写侧不依赖它判断） */
    private static final String OUT_DATA_TYPE = "dataType";

    /**
     * 位区码：这些区码下地址第三字节是<b>位号</b>，每点占 1 位
     * （请求/响应数据每点 1 字节 0/1，而不是字区的每点 2 字节大端）
     */
    private static final Set<Integer> BIT_AREA_CODES = new HashSet<>(Arrays.asList(
            0x30, 0x31, 0x32, 0x33,          // CIO位 / WR位 / HR位 / AR位
            0x02, 0x18,                      // DM位 / EM当前库位
            0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27,  // EM库0-7位
            0x28, 0x29, 0x2A, 0x2B, 0x2C, 0x2D, 0x2E, 0x2F   // EM库8-15位
    ));

    /** 单条内存区写命令的最大项数（OMRON CS/CJ/CP 系列上限，与读侧 length 的 1-1000 一致） */
    private static final int MAX_ITEMS = 1000;

    /**
     * 写值方向编码：值 → FINS 字列表（或位列表）→ 报文载荷
     *
     * <p>替换了原先的硬编码 stub（{@code fun1} 返回 {@code RangeItem(0,2,[22,4])}、否则 {@code [11,2]}）
     * —— 那段返回值与写入值毫无关系，是读方向遗留的占位。本方法只读 {@code otherConfig}，
     * 其余入参一律不参与计算（{@code functionCode} 在本链路恒为 null）。
     *
     * <p><b>载荷形态与 Modbus 不同</b>：Modbus 是 {@code RangeItem} 数组，FINS 回传的是<b>单个 JSON 对象</b>：
     * <pre>{"areaCode":130,"startAddress":100,"count":1,"words":[1234],"dataType":"int"}</pre>
     * <pre>{"areaCode":2,"startAddress":100,"bitAddress":3,"count":1,"words":[1],"dataType":"bool"}</pre>
     * 由 api 侧 {@code FinsPointWriter} 交由 {@code FinsDataReader.writeMemoryArea} 下发。
     *
     * <p><b>字区/位区分叉</b>（严格镜像 api 侧 {@code FinsDataReader} 的读法，读得出来就写得进去）：
     * <ul>
     *   <li><b>字区</b>：复用 {@link RegisterValueUtils#encode}，值与 {@code count} 共同决定编码
     *       （count=1→16 位整型、2→float32/int32、4→double64/int64…与读侧 convert 完全对称）</li>
     *   <li><b>位区</b>：只接受布尔值，产出 {@code [0]} 或 {@code [1]}；{@code count} 必须为 1
     *       （一个位点位就是一个值，不存在「一次写多个位」的业务场景）</li>
     * </ul>
     *
     * @throws IllegalArgumentException 取值/参数不合法（api 侧经反射还原为 422）
     */
    @Override
    public EncodeMessage encode(String functionCode, String deviceSn, Map<String, Object> properties,
                                String params, String customConfig, Map<String, Object> otherConfig) throws Exception {
        if (otherConfig == null) {
            throw new IllegalArgumentException("写值载荷 otherConfig 为空");
        }
        Integer areaCode = toInteger(otherConfig.get(KEY_AREA_CODE));
        Integer address = toInteger(otherConfig.get(KEY_ADDRESS));
        Integer count = toInteger(otherConfig.get(KEY_COUNT));
        String dataType = toStr(otherConfig.get(KEY_DATA_TYPE));
        if (areaCode == null) {
            throw new IllegalArgumentException("缺少存储区码 " + KEY_AREA_CODE);
        }
        if (address == null) {
            throw new IllegalArgumentException("缺少起始地址 " + KEY_ADDRESS);
        }
        if (count == null || count < 1) {
            throw new IllegalArgumentException("缺少或非法的写入长度 " + KEY_COUNT
                    + "：" + otherConfig.get(KEY_COUNT));
        }
        // 上限自查放在这里，而不是靠 RegisterValueUtils.encode 内部那个 123 ——
        // 那个数是 Modbus 功能码 16 的规范值，FINS 单条写命令能收 1000 项，
        // 拿 Modbus 的上限拦 FINS 会把合法点位挡掉，错误文案也会提到不相干的功能码。
        if (count > MAX_ITEMS) {
            throw new IllegalArgumentException("FINS 单次写入项数 " + count + " 超出上限 " + MAX_ITEMS);
        }

        boolean bitArea = isBitArea(areaCode);
        Object value = otherConfig.get(KEY_VALUE);
        List<Integer> words;
        if (bitArea) {
            if (count != 1) {
                throw new IllegalArgumentException("位区（存储区码 0x" + Integer.toHexString(areaCode)
                        + "）点位一次只能写 1 个位，当前 " + KEY_COUNT + "=" + count);
            }
            words = new ArrayList<>(1);
            words.add(RegisterValueUtils.parseBoolean(value == null ? "" : String.valueOf(value)) ? 1 : 0);
        } else {
            words = RegisterValueUtils.encode(value, dataType, toStr(otherConfig.get(KEY_BYTE_ORDER)),
                    toStr(otherConfig.get(KEY_IS_SIGNED)), toDouble(otherConfig.get(KEY_SCALE)),
                    toDouble(otherConfig.get(KEY_OFFSET)), count, MAX_ITEMS);
        }

        JSONObject payload = new JSONObject();
        payload.put(OUT_AREA_CODE, areaCode);
        payload.put(OUT_START_ADDRESS, address);
        payload.put(OUT_BIT_ADDRESS, toInteger(otherConfig.get(KEY_BIT_ADDRESS)));
        payload.put(OUT_COUNT, words.size());
        payload.put(OUT_WORDS, words);
        payload.put(OUT_DATA_TYPE, dataType);

        return EncodeMessage.builder()
                .isSend(true)
                .deviceSn(deviceSn)
                .modbusWriteJson(payload.toJSONString())
                .build();
    }

    /**
     * 是否位区码
     *
     * <p>⚠️ 与 api 侧 {@code FinsDataReader.isBitArea} 是同一套判据的两份实现 —— 协议库与 api
     * 是两个编译单元，共享不了。改一边必须同步另一边，否则写进去的位会被当字读出来。
     */
    private static boolean isBitArea(int areaCode) {
        return BIT_AREA_CODES.contains(areaCode);
    }

    /**
     * 把 otherConfig 里的值转成 Integer
     *
     * <p>Map 经 JSON 往返后同一个「地址」可能是 Integer / Long / BigDecimal，不能直接强转。
     */
    private static Integer toInteger(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : Integer.valueOf(s);
    }

    /** 把 otherConfig 里的值转成 Double（空值/空串一律 null，交给下游按默认值处理） */
    private static Double toDouble(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : Double.valueOf(s);
    }

    /** 把 otherConfig 里的值转成 String（null 原样返回，不转成 "null" 这种脏值） */
    private static String toStr(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
