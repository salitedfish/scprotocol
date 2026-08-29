//由AI修改
package com.labdatahub.protocol.deal;

import java.util.HashMap;
import java.util.Map;

import com.alibaba.fastjson2.JSONObject;
import com.labdatahub.protocol.message.DecodeMessage;
import com.labdatahub.protocol.message.EncodeMessage;
import com.labdatahub.protocol.parent.MitsubishiCncTcpProtocol;
import com.labdatahub.protocol.utils.RegisterValueUtils;

/**
 * @ClassName: MitsubishiCncTcpDeal
 * @Description: 三菱 CNC（MELDAS MOCHA）消息处理
 *               MOCHA 数据由后端 component 模块经 TCP 683 端口读出，decode 收到的是按点位地址
 *               （readType + 轴号）取出的原始值字符串 value，这里按物模型 dataType 转类型并套缩放偏移
 * @author labdatahub
 */
public class MitsubishiCncTcpDeal implements MitsubishiCncTcpProtocol {

	@Override
	public DecodeMessage decode(JSONObject objecotData) throws Exception {
		String code = objecotData.getString("code");
		String deviceSn = objecotData.getString("deviceSn");
		String value = objecotData.getString("value");
		String dataType = objecotData.getString("dataType");
		Double scale = objecotData.getDouble("scale");
		Double offset = objecotData.getDouble("offset");
		DecodeMessage decodeMessage = new DecodeMessage();
		decodeMessage.setDeviceSn(deviceSn);
		Map<String, Object> properties = new HashMap<>();
		// MOCHA 读出的是原始值字符串，按物模型 dataType 转类型后再套缩放偏移
		Object converted = convertValue(value, dataType);
		converted = RegisterValueUtils.applyScale(converted, scale, offset);
		properties.put(code, converted);
		decodeMessage.setProperties(properties);
		// 解析消息
		return decodeMessage;
	}

	/**
	 * 把三菱 CNC MOCHA 响应字段的原始字符串按物模型 dataType 转成对应类型的值
	 * 坐标/转速等数值字段如 "123.456"、"0.000"，枚举字段如 "0"、"1"
	 *
	 * @param value    响应字段原始字符串
	 * @param dataType 物模型数据类型: bool/int/float/double/string；为空时按老逻辑，纯数字转double，否则保持字符串
	 * @return 转换后的值，无法解析返回null
	 */
	private Object convertValue(String value, String dataType) {
		if (value == null) {
			return null;
		}
		String v = value.trim();
		String type = dataType == null ? "" : dataType.trim().toLowerCase();
		switch (type) {
			case "bool":
			case "boolean": {
				return "1".equals(v) || "true".equalsIgnoreCase(v);
			}
			case "int":
			case "integer":
			case "short":
			case "long": {
				try {
					// 优先整数解析；带小数（如 "1500.0"）时按浮点解析后取整
					return Long.parseLong(v);
				} catch (NumberFormatException e) {
					try {
						return (long) Double.parseDouble(v);
					} catch (NumberFormatException e2) {
						return null;
					}
				}
			}
			case "float":
			case "double": {
				try {
					return Double.parseDouble(v);
				} catch (NumberFormatException e) {
					return null;
				}
			}
			case "string":
			case "text":
			case "varchar": {
				// 字符串类型：原样返回，不做数字解析
				return v;
			}
			default: {
				// dataType 未配置：纯数字转 double，其余保持字符串
				try {
					return Double.parseDouble(v);
				} catch (NumberFormatException e) {
					return v;
				}
			}
		}
	}

	@Override
	public EncodeMessage encode(String functionCode, String deviceSn, Map<String, Object> properties, String params, String customConfig, Map<String, Object> otherConfig) throws Exception {
		// 三菱 CNC MOCHA 协议为只读采集，暂不支持下行指令，返回 isSend=false 由平台直接忽略
		return EncodeMessage.builder()
				.isSend(false)
				.deviceSn(deviceSn)
				.build();
	}
}
