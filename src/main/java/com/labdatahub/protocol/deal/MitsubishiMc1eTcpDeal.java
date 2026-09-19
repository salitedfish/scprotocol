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
import com.labdatahub.protocol.parent.MitsubishiMc1eTcpProtocol;
import com.labdatahub.protocol.utils.RegisterValueUtils;

/**
 *
* @ClassName: MitsubishiMc1eTcpDeal
* @Description: 三菱 MC 协议消息处理（QnA 系列 1E 帧）
* @author xwb
* @date 2026年9月19日
*
* <p><b>本类为什么能和 3E 那份长得几乎一样</b>：协议库这一层**不含网络 I/O、不感知寻址**，
* 干的两件事都与帧型无关 ——
* <ol>
*   <li>{@code decode}：把 api 侧读出的原始字块（{@code jsonArray}）按物模型的
*       dataType/byteOrder/isSigned/scale/offset 转成业务值。帧头长什么样、长度字段几个字节，
*       在这一步之前就已经被 api 侧剥掉了，这里看不到也不需要看到。</li>
*   <li>{@code encode}：把写入值转成「起始软元件 + 点数 + 字/位值列表」这个**与帧无关的中间载荷**
*       （{@code {areaCode,startAddress,count,words,dataType}}），真正拼 19 字节 1E 帧的是 api 侧。</li>
* </ol>
* 而 QnA 系列 1E 帧与 3E 帧**共用同一套命令（批量读 0x0401 / 批量写 0x1401，子命令 0x0000 字 /
* 0x0001 位）与同一张软元件码表**，两者的差异只在帧头（3E 是 9 字节头 + 副头部 50 00，
* 1E 是 6 字节头且无副头部）。所以本文件与 {@link MitsubishiMc3eTcpDeal} 的编解码逻辑是同源的，
* 差异全部落在 api 侧的 DataReader（组帧）上。
*
* <p><b>⚠️ 本协议当前不可用</b> —— 缺的是 api 侧帧层（{@code mitsubishi_mc1e_tcp} 组件包 + 平台接线），
* 详见 {@link MitsubishiMc1eTcpProtocol} 的接口注释。真机到位后按下面顺序补全：
* <ol>
*   <li>api 侧复制 {@code mitsubishi_mc3e_tcp} 组件包为 {@code mitsubishi_mc1e_tcp}，
*       {@code MitsubishiMc1eDataReader#readMemoryArea} 按 1E 帧重组：请求 19 字节 =
*       6 字节头（{@code [00][网络号][FF 0A 00]}，其中 {@code 0x0AFF} 是目标模块 I/O 号小端）
*       + 2 字节数据长度（小端）+ 监视定时器 {@code 10 00} + 命令 + 子命令 + 软元件码 1B
*       + 起始地址 **2B**（3E 是 3B，这是最易错的一处）小端 + 点数 2B 小端；
*       响应头 8 字节（副头部回显 {@code 0x10} + 2 字节长度，后接 2 字节结束码），
*       软元件码与数据区解析与 3E 共用。</li>
*   <li>平台接线：{@code NetType} 加枚举、{@code MAIN_CLASS_PATH} 加
*       {@code MITSUBISHI_MC1E_TCP → com.labdatahub.protocol.parent.MitsubishiMc1eTcpProtocol}、
*       component open/close case、{@code TimerTask} 起调度、建表脚本、前端三处下拉 + 详情页配置 tab。</li>
*   <li>{@code PointWriteMetaResolver} 的准入白名单加 {@code MITSUBISHI_MC1E_TCP}（现在非
*       Modbus/S7/FINS 一律 409），{@code PointWriter} 加分派。</li>
* </ol>
*
* <p>⚠️ <b>软元件码表与写入约束在本包内已出现三份</b>（本类、{@link MitsubishiMc3eTcpDeal}，
* 以及 api 侧 {@code MitsubishiMc3eDataReader}）—— 协议库与 api 是两个编译单元共享不了，
* 协议库内部三菱这几种帧也没做公共父类（拆开的用意就是让它们各自独立演化）。
* 判错**不报错、只是写错地方**，改一处必须同步其余几处。
 */
public class MitsubishiMc1eTcpDeal implements MitsubishiMc1eTcpProtocol {

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
    /**
     * 字节序。三菱 MC 与 FINS 不同，<b>这个键是有意义的</b> ——
     * MC 帧内多字节字段是小端，api 侧 DataReader 读出来的是宿主序 16bit 字列表，
     * 所以这里只决定<b>多字值的字序</b>（int32 占 2 个字时低字在前还是高字在前），
     * 与读侧 {@code RegisterValueUtils.convert} 用同一个判据，读得出来就写得进去。
     */
    private static final String KEY_BYTE_ORDER = "byteOrder";
    /** 是否有符号 */
    private static final String KEY_IS_SIGNED = "isSigned";
    /** 缩放系数 */
    private static final String KEY_SCALE = "scale";
    /** 偏移量 */
    private static final String KEY_OFFSET = "offset";
    /** 写入值（JDK 标量：String / Number / Boolean） */
    private static final String KEY_VALUE = "value";
    /** 起始软元件地址（X/Y 为八进制写法；位设备直接是位编号，没有「字地址+位号」这回事） */
    private static final String KEY_ADDRESS = "address";
    /** 写入点数：字设备=字数；位设备=位数（位点位恒为 1） */
    private static final String KEY_COUNT = "count";
    /** 软元件代码（字设备 D=0xA8；位设备 M=0x90 等） */
    private static final String KEY_AREA_CODE = "areaCode";

    // ===== 回传载荷键名（出向）：与 api 侧 MitsubishiMc1ePointWriter 的解析键一一对应 =====
    // ⚠️ 同样是跨 classloader 契约。回传槽是 EncodeMessage.modbusWriteJson（沿用 Modbus 的字段名，与协议无关）。
    // startAddress / words 两个键与 FINS、三菱 3E 刻意取同名：载荷形态本来就一样（起始地址 + 字/位值列表），
    // 这样 api 侧 ProtocolEncodeInvoker#parseWriteEcho 的回显解析不必再分一支。
    /** 软元件代码 */
    private static final String OUT_AREA_CODE = "areaCode";
    /** 起始软元件地址 */
    private static final String OUT_START_ADDRESS = "startAddress";
    /** 写入点数 */
    private static final String OUT_COUNT = "count";
    /** 编码后的字值列表（字设备，每个 0-65535）或位值列表（位设备，每个 0/1） */
    private static final String OUT_WORDS = "words";
    /** 物模型类型（仅回显给日志用，写侧不依赖它判断） */
    private static final String OUT_DATA_TYPE = "dataType";

    /**
     * 字设备软元件码：这些码按字访问（子命令 0x0000），每点占 2 字节小端
     */
    private static final Set<Integer> WORD_DEVICE_CODES = new HashSet<>(Arrays.asList(
            0xA8, // D  数据寄存器
            0xB4, // W  链接寄存器
            0xAF, // R  文件寄存器
            0xB0, // ZR 文件寄存器(ZR)
            0xA9  // SD 特殊寄存器
    ));

    /**
     * 位设备软元件码：这些码按位访问（子命令 0x0001），每点占 1 bit（每字节 2 点）
     */
    private static final Set<Integer> BIT_DEVICE_CODES = new HashSet<>(Arrays.asList(
            0x90, // M  内部继电器
            0x92, // L  锁存继电器
            0xA0, // B  链接继电器
            0x9C, // X  输入继电器（地址八进制）
            0x9D, // Y  输出继电器（地址八进制）
            0x98, // S  步进继电器
            0x91, // SM 特殊继电器
            0x93  // F  报警器
    ));

    /** 字设备单次写入点数上限（与 api 侧 DataReader 的 MAX_WORD_COUNT 一致） */
    private static final int MAX_WORD_COUNT = 960;
    /** 位设备单次写入点数上限（与 api 侧 DataReader 的 MAX_BIT_COUNT 一致） */
    private static final int MAX_BIT_COUNT = 2000;

    /**
     * 写值方向编码：值 → MC 字/位值列表 → 报文载荷
     *
     * <p>只读 {@code otherConfig}，其余入参一律不参与计算（{@code functionCode} 在本链路恒为 null）。
     * 载荷是<b>单个 JSON 对象</b>（不是 Modbus 那种区间数组），形态与 FINS、三菱 3E 一致：
     * <pre>{"areaCode":168,"startAddress":100,"count":1,"words":[8888],"dataType":"int"}  // D 区字写
     * {"areaCode":144,"startAddress":11, "count":1,"words":[1],   "dataType":"bool"} // M 区位写</pre>
     * 由 api 侧 PointWriter 交由 1E 帧的 {@code writeMemoryArea} 下发
     * （批量写命令 {@code 0x1401}，子命令按字/位切换，与 3E 相同）。
     *
     * <p><b>字设备/位设备分叉</b>（严格镜像 api 侧读法）：
     * <ul>
     *   <li><b>字设备</b>（D/W/R/ZR/SD）：复用 {@link RegisterValueUtils#encode}，值与 {@code count}
     *       共同决定编码（count=1→16 位整型、2→int32/float32、4→int64/double64，与读侧 convert 完全对称）</li>
     *   <li><b>位设备</b>（M/L/B/X/Y/S/SM/F）：只接受布尔值，产出 {@code [0]} 或 {@code [1]}；
     *       {@code count} 必须为 1（一个位点位就是一个值，不存在「一次写多个位」的业务场景，
     *       与 FINS 位区、三菱 3E 同一条约束）</li>
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
            throw new IllegalArgumentException("缺少软元件码 " + KEY_AREA_CODE);
        }
        if (address == null) {
            throw new IllegalArgumentException("缺少起始地址 " + KEY_ADDRESS);
        }
        if (address < 0) {
            throw new IllegalArgumentException("起始地址不能为负数：" + address);
        }
        if (count == null || count < 1) {
            throw new IllegalArgumentException("缺少或非法的写入点数 " + KEY_COUNT
                    + "：" + otherConfig.get(KEY_COUNT));
        }

        boolean wordDevice;
        if (WORD_DEVICE_CODES.contains(areaCode)) {
            wordDevice = true;
        } else if (BIT_DEVICE_CODES.contains(areaCode)) {
            wordDevice = false;
        } else {
            throw new IllegalArgumentException("未知的软元件码 0x" + Integer.toHexString(areaCode)
                    + "（既不是字设备 D/W/R/ZR/SD，也不是位设备 M/L/B/X/Y/S/SM/F），拒绝写入");
        }

        // 上限自查用自己的常量，而不是靠 RegisterValueUtils.encode 内部那个 123 ——
        // 那个数是 Modbus 功能码 16 的规范值，MC 字写能到 960 点，拿它拦会把合法点位挡掉
        int maxCount = wordDevice ? MAX_WORD_COUNT : MAX_BIT_COUNT;
        if (count > maxCount) {
            throw new IllegalArgumentException("MC " + (wordDevice ? "字" : "位") + "设备单次写入点数 "
                    + count + " 超出上限 " + maxCount);
        }

        Object value = otherConfig.get(KEY_VALUE);
        List<Integer> words;
        if (wordDevice) {
            words = RegisterValueUtils.encode(value, dataType, toStr(otherConfig.get(KEY_BYTE_ORDER)),
                    toStr(otherConfig.get(KEY_IS_SIGNED)), toDouble(otherConfig.get(KEY_SCALE)),
                    toDouble(otherConfig.get(KEY_OFFSET)), count, maxCount);
        } else {
            if (count != 1) {
                throw new IllegalArgumentException("位设备（软元件码 0x" + Integer.toHexString(areaCode)
                        + "）点位一次只能写 1 个位，当前 " + KEY_COUNT + "=" + count
                        + "（请把点位配置的读取数量改为 1）");
            }
            words = new ArrayList<>(1);
            words.add(RegisterValueUtils.parseBoolean(value == null ? "" : String.valueOf(value)) ? 1 : 0);
        }

        JSONObject payload = new JSONObject();
        payload.put(OUT_AREA_CODE, areaCode);
        payload.put(OUT_START_ADDRESS, address);
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
