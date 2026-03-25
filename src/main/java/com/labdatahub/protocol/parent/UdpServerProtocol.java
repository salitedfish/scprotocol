package com.labdatahub.protocol.parent;

import com.alibaba.fastjson2.JSONObject;
import com.labdatahub.protocol.message.DecodeMessage;
import com.labdatahub.protocol.message.EncodeMessage;

import java.util.Map;

/**
 * UDP协议包必须实现的统一接口。
 */
public interface UdpServerProtocol {

    /**
     * 解析设备上传的数据
     * @param rawData 原始字节数据
     * @return 解析后的标准化数据（可以是JSON字符串、Map、自定义POJO）
     * @throws Exception 解析异常
     */
//    DecodeMessage decode(String rawData) throws Exception;
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
    EncodeMessage encode(String functionCode, String deviceSn, Map<String, Object> properties, String params,String customConfig,Map<String,Object> otherConfig) throws Exception;
}
