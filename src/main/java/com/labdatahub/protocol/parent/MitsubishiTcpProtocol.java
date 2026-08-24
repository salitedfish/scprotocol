//由AI修改
package com.labdatahub.protocol.parent;

import java.util.Map;

import com.alibaba.fastjson2.JSONObject;
import com.labdatahub.protocol.message.DecodeMessage;
import com.labdatahub.protocol.message.EncodeMessage;

/**
 * 三菱 MC 协议（MITSUBISHI_TCP，QnA兼容3E帧）必须实现的统一接口。
 * 方法签名必须与 api 侧 ProtocolManager 反射路径一致：decode(JSONObject) / encode(String,String,Map,String,String,Map)。
 */
public interface MitsubishiTcpProtocol {

    /**
     * 解析设备寄存器的数据
     * @param objecotData 读取数据（含 deviceSn/code/jsonArray/dataType/byteOrder/isSigned/scale/offset）
     * @return 解析后的标准化数据，必须是DecodeMessage类
     * @throws Exception 解析异常
     */
    DecodeMessage decode(JSONObject objecotData) throws Exception;

    /**
     * 功能下行指令（平台向设备发送数据）
     *
     * @param functionCode 功能编码
     * @param deviceSn     设备标识
     * @param properties   设备最新全属性状态
     * @param params       用户自定义参数
     * @param customConfig 设备自定义配置
     * @return 需要下发的配置信息
     * @throws Exception 编码异常
     */
    EncodeMessage encode(String functionCode, String deviceSn, Map<String, Object> properties, String params, String customConfig, Map<String, Object> otherConfig) throws Exception;
}
