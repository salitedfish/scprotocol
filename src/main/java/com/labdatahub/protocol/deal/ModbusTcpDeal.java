//由AI修改
package com.labdatahub.protocol.deal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import com.labdatahub.protocol.message.DecodeMessage;
import com.labdatahub.protocol.message.EncodeMessage;
import com.labdatahub.protocol.message.RangeItem;
import com.labdatahub.protocol.parent.ModbusTcpProtocol;
import com.labdatahub.protocol.utils.RegisterValueUtils;

/**
 * @Description: UDP消息处理
 * @Author: labdatahub
 * @CreateTime: 2025-09-17
 */
public class ModbusTcpDeal implements ModbusTcpProtocol {

//    @Override
//    public DecodeMessage decode(String deviceSn, Integer slaveId, String code, String registerRange, JSONArray jsonArray) throws Exception {
//        List<RangeItem> itemList = JSONArray.parseArray(jsonArray.toJSONString(),RangeItem.class);
//        DecodeMessage decodeMessage = new DecodeMessage();
//        decodeMessage.setDeviceSn(deviceSn);
//        Map<String,Object> properties = new HashMap<>();
//        if(itemList.size()>0){
//            for (int i = 0; i < itemList.size(); i++) {
//                RangeItem rangeItem = itemList.get(i);
//                List<Integer> data = rangeItem.getRegisterList();
//                properties.put("temperature",data.get(0));
//                properties.put("windSpeed",data.get(1));
//            }
//        }
//        decodeMessage.setProperties(properties);
//        //解析消息
//        return decodeMessage;
//    }
	
	public DecodeMessage decode(JSONObject objecotData) throws Exception {
		String code = objecotData.getString("code");
		String deviceSn = objecotData.getString("deviceSn");
		String dataType = objecotData.getString("dataType");
		String byteOrder = objecotData.getString("byteOrder");
		String isSigned = objecotData.getString("isSigned");
		Double scale = objecotData.getDouble("scale");
		Double offset = objecotData.getDouble("offset");
		JSONArray jsonArray = objecotData.getJSONArray("jsonArray");
		List<RangeItem> itemList = JSONArray
				.parseArray(jsonArray.toJSONString(), RangeItem.class);
		DecodeMessage decodeMessage = new DecodeMessage();
		decodeMessage.setDeviceSn(deviceSn);
		Map<String, Object> properties = new HashMap<>();
		if (itemList != null)
			for (int i = 0; i < itemList.size(); i++) {
				RangeItem rangeItem = itemList.get(i);
				List<Integer> data = rangeItem.getRegisterList();
				Object value = RegisterValueUtils.convert(data, dataType, byteOrder, isSigned, scale, offset);
				properties.put(code, value);
			}
		decodeMessage.setProperties(properties);
		return decodeMessage;
	}
		
    // ===== otherConfig 键名：与 api 侧 com.labdatahub.business.domain.OpenApiEncodeKeys 一一对应 =====
    // ⚠️ 跨 classloader 的字符串契约，两侧无编译期检查，改一处必须同步另一处（见方案 4.4.2 / 4.4.4）
    /** 物模型数据类型 */
    private static final String KEY_DATA_TYPE = "dataType";
    /** 字节序 */
    private static final String KEY_BYTE_ORDER = "byteOrder";
    /** 是否有符号 */
    private static final String KEY_IS_SIGNED = "isSigned";
    /** 缩放系数 */
    private static final String KEY_SCALE = "scale";
    /** 偏移量 */
    private static final String KEY_OFFSET = "offset";
    /** 写入值（JDK 标量：String / Number / Boolean） */
    private static final String KEY_VALUE = "value";
    /** 起始寄存器地址 */
    private static final String KEY_ADDRESS = "address";
    /** 寄存器个数 */
    private static final String KEY_COUNT = "count";

    /**
     * 写值方向编码：值 → 寄存器字列表 → 报文载荷
     *
     * <p>替换了原先的硬编码 demo（{@code RangeItem(0,2,[22,4])}）—— 那段是读方向遗留的占位，
     * 一直没被真正调通过。本方法只读 {@code otherConfig}，其余入参一律不参与计算。
     *
     * <p>第一段（值 → 字列表）委托 {@link RegisterValueUtils#encode}，与读方向的 {@code convert} 同处一室、互为逆运算；
     * 第二段（字列表 → 载荷）是协议自己的事：塞进 {@code RangeItem} 并序列化进 {@code modbusWriteJson}。
     *
     * @param functionCode 旧链路的业务指令串，本链路传 null
     * @param deviceSn     原样透传，便于协议层日志定位
     * @param properties   本链路传空 Map（不传 null，避免协议实现直接操作 Map 时 NPE）
     * @param otherConfig  唯一有效载荷，键名见上方常量
     */
    @Override
    public EncodeMessage encode(String functionCode, String deviceSn, Map<String, Object> properties, String params, String customConfig,Map<String,Object> otherConfig) throws Exception {
        // 防御性：正常调用不会出现（api 侧必传）
        if (otherConfig == null) {
            return EncodeMessage.builder()
                    .isSend(false)
                    .deviceSn(deviceSn)
                    .build();
        }
        Integer address = toInt(otherConfig.get(KEY_ADDRESS));
        Integer count = toInt(otherConfig.get(KEY_COUNT));
        if (address == null || count == null) {
            throw new IllegalArgumentException("写值元数据缺失：" + KEY_ADDRESS + "=" + otherConfig.get(KEY_ADDRESS)
                    + "，" + KEY_COUNT + "=" + otherConfig.get(KEY_COUNT));
        }
        List<Integer> registers = RegisterValueUtils.encode(
                otherConfig.get(KEY_VALUE),
                asString(otherConfig.get(KEY_DATA_TYPE)),
                asString(otherConfig.get(KEY_BYTE_ORDER)),
                asString(otherConfig.get(KEY_IS_SIGNED)),
                asDouble(otherConfig.get(KEY_SCALE)),
                asDouble(otherConfig.get(KEY_OFFSET)),
                count);
        List<RangeItem> list = new ArrayList<>();
        list.add(new RangeItem(address, count, registers));
        return EncodeMessage.builder()
                .isSend(true)
                .deviceSn(deviceSn)
                .modbusWriteJson(JSONArray.toJSONString(list))
                .build();
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
        try {
            return Integer.valueOf(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("期望整数，实际：" + o);
        }
    }

    /**
     * 取字符串值，null 原样返回（由 RegisterValueUtils 决定默认语义）
     */
    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
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
        try {
            return Double.valueOf(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("期望数值，实际：" + o);
        }
    }
}
