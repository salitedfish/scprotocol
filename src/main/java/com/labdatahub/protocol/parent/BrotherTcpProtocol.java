//由AI修改
package com.labdatahub.protocol.parent;

import java.util.Map;

import com.alibaba.fastjson2.JSONObject;
import com.labdatahub.protocol.message.DecodeMessage;
import com.labdatahub.protocol.message.EncodeMessage;

/**
 * Brother NC（兄弟数控）协议包必须实现的统一接口
 * 对接 Brother 数控系统 TCP 10000 端口，只读采集
 */
public interface BrotherTcpProtocol {

	/**
	 * 解析 Brother NC 响应数据
	 * @param objecotData 携带 {deviceSn, code, value, dataType, byteOrder, isSigned, scale, offset} 的解析参数
	 *                    value 为响应行按点位地址取出的原始字符串
	 * @return 解析后的标准化数据，必须是DecodeMessage类
	 * @throws Exception 解析异常
	 */
	DecodeMessage decode(JSONObject objecotData) throws Exception;

	/**
	 * 功能下行指令（平台向设备发送数据）。Brother NC 协议只读，暂不支持下行
	 *
	 * @param functionCode 功能编码
	 * @param deviceSn     设备标识
	 * @param properties   设备最新全属性状态
	 * @param params       用户自定义参数
	 * @param customConfig 设备自定义配置
	 * @param otherConfig  其他配置
	 * @return 需要下发的配置信息
	 * @throws Exception 编码异常
	 */
	EncodeMessage encode(String functionCode, String deviceSn, Map<String, Object> properties, String params, String customConfig, Map<String, Object> otherConfig) throws Exception;
}
