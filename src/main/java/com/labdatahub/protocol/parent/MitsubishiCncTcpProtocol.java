//由AI修改
package com.labdatahub.protocol.parent;

import java.util.Map;

import com.alibaba.fastjson2.JSONObject;
import com.labdatahub.protocol.message.DecodeMessage;
import com.labdatahub.protocol.message.EncodeMessage;

/**
 * 三菱 数控（MELDAS MOCHA）协议包必须实现的统一接口
 * 对接三菱 CNC 机床，TCP 683 端口只读采集
 */
public interface MitsubishiCncTcpProtocol {

	/**
	 * 解析三菱 CNC TCP（MOCHA）响应数据
	 * @param objecotData 携带 {deviceSn, code, value, dataType, byteOrder, isSigned, scale, offset} 的解析参数
	 *                    value 为按点位地址（readType + 轴号）读出的原始值字符串
	 * @return 解析后的标准化数据，必须是DecodeMessage类
	 * @throws Exception 解析异常
	 */
	DecodeMessage decode(JSONObject objecotData) throws Exception;

	/**
	 * 功能下行指令（平台向设备发送数据）。三菱 CNC TCP 协议只读，暂不支持下行
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
